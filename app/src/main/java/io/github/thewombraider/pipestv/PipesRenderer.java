package io.github.thewombraider.pipestv;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Draws the pipes the way the original does (docs/REFERENCE_ANALYSIS.md): a level camera with a 90 degree field of
 * view that turns 9.73 degrees per scene, one white light fixed to the camera from above and in front, the 16 teapot
 * materials lit per vertex as OpenGL 1.1 did, 8-sided pipes, and a clear that eats the finished picture in small
 * black squares.
 *
 * Every piece is final once made, so a scene is one vertex buffer that only ever grows, drawn with one call. The
 * picture only changes when the pipes take a step or the clear moves on, so a frame is drawn only then
 * ({@link #nextFrameNanos()}); in between the GPU is idle.
 */
final class PipesRenderer implements GLSurfaceView.Renderer {
    /** Measured: 30 steps a second (docs/REFERENCE_ANALYSIS.md, Speed). */
    static final float TICKS_PER_SECOND = 30f;
    /** Measured: the clear takes 0.45 to 0.65 s (median 0.57) in the four recordings that agree with the XP one. */
    static final float DISSOLVE_SECONDS = 0.57f;
    /** Source: the finished picture stays for one tick, then the clear starts. */
    private static final float HOLD_SECONDS = 1f / TICKS_PER_SECOND;
    private final float holdSeconds;
    /** Test only: draw the scene this many times a frame, every frame, to measure how much headroom the GPU has. */
    private final int stressDraws;
    /** The camera's distance from the lattice's center, in node spacings (75 units on a lattice 7 apart). */
    static final float CAMERA_DISTANCE = 75f / 7f;
    static final float FIELD_OF_VIEW = 90f;
    /** The near plane: the original's 1 unit, in node spacings. */
    static final float NEAR_PLANE = 1f / 7f;
    static final float TURN_PER_SCENE = 9.73156f;
    private static final int SIDES = 8;

    /** Position as three floats, then the normal as three signed bytes and the material index as a fourth. */
    private static final int STRIDE = 16;
    private static final int BATCH_VERTEX_CAPACITY = 640_000;

    private final Context context;
    private final Random random = new Random();
    private final PipeSim.Params params = new PipeSim.Params();
    private final float timeScale;
    private final float dissolveSeconds;
    private final boolean debuggable;
    private PipeSim sim;

    private float[] run, ball, elbow, teapot;
    private int program, vbo;
    private int aPos, aNorMat, uMvp, uNormal, uAmbient, uDiffuse, uSpecular;
    private int overlay, oPos, uProgress, uBlock;
    private int batchVertices;
    private boolean batchFull;

    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] mvp = new float[16];
    private final float[] normal = new float[9];
    private float rotation;
    private int width = 1, height = 1;

    private enum Phase { GROW, HOLD, DISSOLVE }

    private Phase phase = Phase.GROW;
    private long phaseStart;
    private long ticksDone;
    private volatile long nextFrame;

    private final List<PipeSim.Piece> drained = new ArrayList<>();
    private ByteBuffer upload = ByteBuffer.allocateDirect(1 << 18).order(ByteOrder.nativeOrder());

    private long statsStart;
    private int statsFrames;
    private long statsWorstNanos;

    /** {@code timeScale} above 1 speeds the whole scene up; it exists only so a test run can reach dense scenes quickly. */
    PipesRenderer(Context context, float teapotChance, float timeScale, android.os.Bundle tuning) {
        this.context = context.getApplicationContext();
        this.debuggable = (this.context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (teapotChance >= 0f) params.teapotChance = Math.min(1f, teapotChance);
        this.timeScale = clamp(timeScale > 0f ? timeScale : 1f, 0.1f, 20f);
        android.os.Bundle t = tuning != null ? tuning : new android.os.Bundle();
        // The launch extras exist for testing and any app can send them, so every value is checked.
        dissolveSeconds = clamp(t.getFloat("dissolve", DISSOLVE_SECONDS), 0.1f, 60f);
        holdSeconds = clamp(t.getFloat("hold", HOLD_SECONDS), 0f, 600f);
        stressDraws = (int) clamp(t.getFloat("stress", 1f), 1f, 8f);
        String joints = t.getString("joints", "mixed");
        params.joints = "elbow".equals(joints) ? PipeSim.JointMode.ELBOW : "ball".equals(joints) ? PipeSim.JointMode.BALL : PipeSim.JointMode.MIXED;
        params.multiple = !"single".equals(t.getString("pipes", "multiple"));
        rotation = clamp(t.getFloat("turn", 0f), 0f, 360f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    /** When the picture next changes, in {@link System#nanoTime()} terms; the view asks for a frame then. */
    long nextFrameNanos() {
        return stressDraws > 1 ? 0 : nextFrame;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        run = Meshes.cylinder(SIDES, false);
        ball = Meshes.sphere(SIDES, SIDES);
        elbow = Meshes.torusQuarter(PipeSim.RADIUS, PipeSim.RADIUS, SIDES / 2, SIDES);
        try (InputStream in = context.getAssets().open("teapot.bin")) {
            teapot = Meshes.teapot(in);
        } catch (Exception e) {
            teapot = ball;
        }

        program = link(SCENE_VERTEX, SCENE_FRAGMENT);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aNorMat = GLES20.glGetAttribLocation(program, "aNorMat");
        uMvp = GLES20.glGetUniformLocation(program, "uMvp");
        uNormal = GLES20.glGetUniformLocation(program, "uNormal");
        uAmbient = GLES20.glGetUniformLocation(program, "uAmbient");
        uDiffuse = GLES20.glGetUniformLocation(program, "uDiffuse");
        uSpecular = GLES20.glGetUniformLocation(program, "uSpecular");
        overlay = link(OVERLAY_VERTEX, OVERLAY_FRAGMENT);
        oPos = GLES20.glGetAttribLocation(overlay, "aPos");
        uProgress = GLES20.glGetUniformLocation(overlay, "uProgress");
        uBlock = GLES20.glGetUniformLocation(overlay, "uBlock");

        GLES20.glUseProgram(program);
        float[] amb = new float[Materials.COUNT * 3], dif = new float[Materials.COUNT * 3], spe = new float[Materials.COUNT * 4];
        for (int i = 0; i < Materials.COUNT; i++) {
            float[] m = Materials.get(i);
            for (int c = 0; c < 3; c++) {
                // Global ambient 1.0 plus the light's own ambient 0.1, both white.
                amb[i * 3 + c] = 1.1f * m[c];
                dif[i * 3 + c] = m[3 + c];
                spe[i * 4 + c] = m[6 + c];
            }
            spe[i * 4 + 3] = m[9];
        }
        GLES20.glUniform3fv(uAmbient, Materials.COUNT, amb, 0);
        GLES20.glUniform3fv(uDiffuse, Materials.COUNT, dif, 0);
        GLES20.glUniform4fv(uSpecular, Materials.COUNT, spe, 0);

        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        vbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, BATCH_VERTEX_CAPACITY * STRIDE, null, GLES20.GL_STATIC_DRAW);
        // The surface may be new after a pause, and its buffer is empty: start the scene over.
        sim = null;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        GLES20.glViewport(0, 0, width, height);
        Matrix.perspectiveM(proj, 0, FIELD_OF_VIEW, width / (float) height, NEAR_PLANE, 4f * CAMERA_DISTANCE);
        int[] lattice = PipeSim.latticeFor(width, height);
        sim = new PipeSim(lattice[0], lattice[1], lattice[2], random, params);
        startScene(System.nanoTime(), false);
    }

    private void startScene(long now, boolean turn) {
        if (turn) {
            rotation += TURN_PER_SCENE;
            if (rotation >= 360f) rotation -= 360f;
        }
        Matrix.setIdentityM(view, 0);
        Matrix.translateM(view, 0, 0f, 0f, -CAMERA_DISTANCE);
        Matrix.rotateM(view, 0, rotation, 0f, 1f, 0f);
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
        // The view only turns, so its upper 3 x 3 turns normals into eye space.
        for (int c = 0; c < 3; c++) System.arraycopy(view, c * 4, normal, c * 3, 3);
        sim.reset();
        batchVertices = 0;
        batchFull = false;
        phase = Phase.GROW;
        phaseStart = now;
        ticksDone = 0;
        upload();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        if (sim == null) return;
        advance(now);
        for (int i = 0; i < stressDraws; i++) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            drawScene();
        }
        if (phase == Phase.DISSOLVE) drawDissolve(seconds(now - phaseStart) / dissolveSeconds);
        stats(System.nanoTime() - now, now);
    }

    private float seconds(long nanos) {
        return nanos / 1e9f * timeScale;
    }

    /** Runs the scene up to {@code now} and works out when the next visible change is due. */
    private void advance(long now) {
        long tickNanos = (long) (1e9 / (TICKS_PER_SECOND * timeScale));
        if (phase == Phase.GROW) {
            // A quarter of a frame early counts as on time, so steps land on every other refresh of a 60 Hz screen.
            long due = (now - phaseStart + tickNanos / 4) / tickNanos;
            // After a stall (the TV was busy), skip ahead rather than racing through the backlog.
            if (due - ticksDone > 3) ticksDone = due - 3;
            while (ticksDone < due && !sim.isOver()) {
                sim.tick();
                ticksDone++;
            }
            upload();
            if (sim.isOver()) {
                phase = Phase.HOLD;
                phaseStart = now;
                if (debuggable) android.util.Log.i("XPPipes", "scene over: ticks=" + ticksDone + " used=" + sim.usedFraction() + " vertices=" + batchVertices + " turn=" + rotation);
            } else {
                nextFrame = phaseStart + (ticksDone + 1) * tickNanos - tickNanos / 4;
            }
        }
        if (phase == Phase.HOLD) {
            long hold = (long) (holdSeconds * 1e9 / timeScale);
            if (now - phaseStart >= hold) {
                phase = Phase.DISSOLVE;
                phaseStart = now;
            } else {
                nextFrame = phaseStart + hold;
            }
        }
        if (phase == Phase.DISSOLVE) {
            if (seconds(now - phaseStart) >= dissolveSeconds) {
                startScene(now, true);
                nextFrame = phaseStart + tickNanos - tickNanos / 4;
            } else {
                nextFrame = 0;
            }
        }
    }

    /** Appends the pieces made since the last frame to the scene's buffer. */
    private void upload() {
        drained.clear();
        sim.drain(drained);
        if (drained.isEmpty() || batchFull) return;
        upload.clear();
        int count = 0;
        for (PipeSim.Piece p : drained) count += write(p);
        if (batchVertices + count > BATCH_VERTEX_CAPACITY) {
            batchFull = true;
            return;
        }
        upload.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, batchVertices * STRIDE, count * STRIDE, upload);
        batchVertices += count;
    }

    private void drawScene() {
        if (batchVertices == 0) return;
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix3fv(uNormal, 1, false, normal, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aNorMat);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, 0);
        GLES20.glVertexAttribPointer(aNorMat, 4, GLES20.GL_BYTE, false, STRIDE, 12);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, batchVertices);
    }

    /** The original's clear: the frozen picture is eaten by small black squares in random order. */
    private void drawDissolve(float progress) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(overlay);
        GLES20.glUniform1f(uProgress, Math.min(1f, progress));
        // Measured: squares 1/270 of the screen height (4 pixels at 1080 lines).
        GLES20.glUniform1f(uBlock, Math.max(1f, Math.round(height / 270f)));
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glEnableVertexAttribArray(oPos);
        GLES20.glVertexAttribPointer(oPos, 2, GLES20.GL_FLOAT, false, 0, QUAD);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(oPos);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private static final FloatBuffer QUAD = ByteBuffer.allocateDirect(12 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

    static {
        QUAD.put(new float[] {-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1});
        QUAD.position(0);
    }

    private void stats(long drawNanos, long now) {
        if (!debuggable) return;
        statsFrames++;
        statsWorstNanos = Math.max(statsWorstNanos, drawNanos);
        if (now - statsStart > 5_000_000_000L) {
            android.util.Log.i("XPPipes", "frames=" + statsFrames + " in " + (now - statsStart) / 1_000_000 + "ms worstCpuMs="
                    + statsWorstNanos / 1e6f + " vertices=" + batchVertices + " used=" + sim.usedFraction() + " phase=" + phase);
            statsStart = now;
            statsFrames = 0;
            statsWorstNanos = 0;
        }
    }

    // ---- turning pieces into vertices ----

    private int write(PipeSim.Piece p) {
        switch (p.type) {
            case RUN:
                return writeRun(p);
            case BALL:
                return writeScaled(ball, p, PipeSim.RADIUS * PipeSim.BALL_SCALE);
            case TEAPOT:
                // GLAUX's teapot at 2.5 x the pipe radius: its body is 2.5 pipe radii across; the asset's is 1.27.
                return writeScaled(teapot, p, 2.5f * PipeSim.RADIUS / 1.27f);
            default:
                return writeElbow(p);
        }
    }

    private void vertex(float x, float y, float z, float nx, float ny, float nz, int material) {
        if (upload.remaining() < STRIDE) {
            ByteBuffer bigger = ByteBuffer.allocateDirect(upload.capacity() * 2).order(ByteOrder.nativeOrder());
            upload.flip();
            bigger.put(upload);
            upload = bigger;
        }
        upload.putFloat(x).putFloat(y).putFloat(z);
        upload.put((byte) Math.round(nx * 127f)).put((byte) Math.round(ny * 127f)).put((byte) Math.round(nz * 127f)).put((byte) material);
    }

    private int writeRun(PipeSim.Piece p) {
        float dx = p.ax, dy = p.ay, dz = p.az;
        // Two more vectors that complete a right-handed frame with the direction.
        float hx = Math.abs(dy) < 0.9f ? 0f : 1f;
        float hy = Math.abs(dy) < 0.9f ? 1f : 0f;
        float xx = hy * dz, xy = -hx * dz, xz = hx * dy - hy * dx;
        float xl = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= xl;
        xy /= xl;
        xz /= xl;
        float yx = dy * xz - dz * xy, yy = dz * xx - dx * xz, yz = dx * xy - dy * xx;
        float r = PipeSim.RADIUS;
        for (int i = 0; i < run.length; i += 6) {
            float px = run[i], py = run[i + 1], pz = run[i + 2];
            float nx = run[i + 3], ny = run[i + 4], nz = run[i + 5];
            vertex(p.x + (xx * px + yx * py) * r + dx * pz * p.length,
                    p.y + (xy * px + yy * py) * r + dy * pz * p.length,
                    p.z + (xz * px + yz * py) * r + dz * pz * p.length,
                    xx * nx + yx * ny + dx * nz,
                    xy * nx + yy * ny + dy * nz,
                    xz * nx + yz * ny + dz * nz,
                    p.material);
        }
        return run.length / 6;
    }

    private int writeElbow(PipeSim.Piece p) {
        float ux = p.ax, uy = p.ay, uz = p.az, vx = p.bx, vy = p.by, vz = p.bz;
        float wx = uy * vz - uz * vy, wy = uz * vx - ux * vz, wz = ux * vy - uy * vx;
        for (int i = 0; i < elbow.length; i += 6) {
            float px = elbow[i], py = elbow[i + 1], pz = elbow[i + 2];
            float nx = elbow[i + 3], ny = elbow[i + 4], nz = elbow[i + 5];
            vertex(p.x + ux * px + vx * py + wx * pz,
                    p.y + uy * px + vy * py + wy * pz,
                    p.z + uz * px + vz * py + wz * pz,
                    ux * nx + vx * ny + wx * nz,
                    uy * nx + vy * ny + wy * nz,
                    uz * nx + vz * ny + wz * nz,
                    p.material);
        }
        return elbow.length / 6;
    }

    private int writeScaled(float[] mesh, PipeSim.Piece p, float scale) {
        for (int i = 0; i < mesh.length; i += 6) {
            vertex(p.x + mesh[i] * scale, p.y + mesh[i + 1] * scale, p.z + mesh[i + 2] * scale,
                    mesh[i + 3], mesh[i + 4], mesh[i + 5], p.material);
        }
        return mesh.length / 6;
    }

    // ---- shaders ----

    /**
     * OpenGL 1.1's fixed-function lighting, per vertex: one directional white light in eye space at (0, 90, 150),
     * viewer at infinity, ambient = (global 1.0 + light 0.1) x material ambient, specular only on the lit side.
     */
    static final String SCENE_VERTEX =
            "uniform mat4 uMvp; uniform mat3 uNormal;\n"
                    + "uniform vec3 uAmbient[" + Materials.COUNT + "]; uniform vec3 uDiffuse[" + Materials.COUNT + "];\n"
                    + "uniform vec4 uSpecular[" + Materials.COUNT + "];\n"
                    + "attribute vec3 aPos; attribute vec4 aNorMat;\n"
                    + "varying vec3 vColor;\n"
                    // normalize(0, 90, 150), and the half vector between it and the view direction (0, 0, 1).
                    + "const vec3 L = vec3(0.0, 0.5144958, 0.8574929);\n"
                    + "const vec3 H = vec3(0.0, 0.2669336, 0.9637149);\n"
                    + "void main() {\n"
                    + "  int m = int(aNorMat.w + 0.5);\n"
                    + "  vec3 n = normalize(uNormal * (aNorMat.xyz / 127.0));\n"
                    + "  float d = dot(n, L);\n"
                    + "  vec3 c = uAmbient[m] + uDiffuse[m] * max(d, 0.0);\n"
                    + "  if (d > 0.0) c += uSpecular[m].rgb * pow(max(dot(n, H), 0.0), uSpecular[m].a);\n"
                    + "  vColor = min(c, vec3(1.0));\n"
                    + "  gl_Position = uMvp * vec4(aPos, 1.0);\n"
                    + "}\n";

    static final String SCENE_FRAGMENT =
            "precision mediump float;\n"
                    + "varying vec3 vColor;\n"
                    + "void main() { gl_FragColor = vec4(vColor, 1.0); }\n";

    private static final String OVERLAY_VERTEX =
            "attribute vec2 aPos; void main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n";

    /** Each square of the screen has a fixed random moment in the clear; once the clear passes it, it is black. */
    private static final String OVERLAY_FRAGMENT =
            "precision highp float;\n"
                    + "uniform float uProgress; uniform float uBlock;\n"
                    + "void main() {\n"
                    + "  vec2 cell = floor(gl_FragCoord.xy / uBlock);\n"
                    + "  float r = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);\n"
                    + "  if (r < uProgress) gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); else discard;\n"
                    + "}\n";

    private static int link(String vertex, String fragment) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new IllegalStateException("shader link failed: " + GLES20.glGetProgramInfoLog(program)
                    + " / " + GLES20.glGetShaderInfoLog(vs) + " / " + GLES20.glGetShaderInfoLog(fs));
        }
        return program;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
