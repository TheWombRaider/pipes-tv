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
 * Draws the pipes. Follows 1j01/pipes (MIT, Isaiah Odhner) for the look and the timing: a
 * 21 x 21 x 21 lattice, pipe radius 0.2, glossy Phong material with a pale cyan highlight, one white
 * light, a 45 degree camera 14 units out, and a scene that dissolves away in black squares every
 * 16 to 24 seconds. See NOTICE.
 *
 * Everything that has stopped growing goes into one big vertex buffer, so a full scene is a single
 * draw call; only the heads that are still extending are rebuilt each frame.
 */
final class PipesRenderer implements GLSurfaceView.Renderer {
    /** Measured from the reference recording: scenes last 16 to 34 seconds, then dissolve for 1.6. */
    private static final float SCENE_MIN_SECONDS = 18f;
    private static final float SCENE_MAX_SECONDS = 34f;
    /** Estimated from the reference: a wide angle of about 60 degrees. */
    private static final int BATCH_VERTEX_CAPACITY = 900_000;
    private static final int HEAD_VERTEX_CAPACITY = 16 * 1024;
    private static final int FLOATS_PER_VERTEX = 9;
    private static final int STRIDE = FLOATS_PER_VERTEX * 4;

    private final Context context;
    private final Random random = new Random();
    private final PipeSim.Params params = new PipeSim.Params();
    private PipeSim sim;

    private float[] cylinder, headCylinder, sphere, elbowMesh, teapot;

    private int sceneProgram, batchVbo, headVbo, overlayProgram;
    private int aPos, aNor, aCol, uVP, uEye, uLight;
    private int oPos;
    private int batchVertices;
    private boolean batchFull;
    private int seenGeneration = -1;

    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] eye = new float[3];
    private float cameraDistance = 16f;
    private final float timeScale;
    private final boolean debuggable;
    /** The look of the scene. The defaults are the faithful settings; a test launch can override them. */
    private final int grid;
    private final float pipeRadius, ballRadius, teapotSize, fieldOfView, cameraMin, cameraMax;
    /** Measured from the reference: the clear takes 1.6 seconds. */
    private final float dissolveSeconds;
    private final float[] light = new float[] {-0.4f, 0.6f, 0.7f};
    private int width = 1, height = 1;

    private long lastNanos, statsNanos;
    private int frames;
    private float sceneSeconds;
    private float sceneLength = 26f;
    private float dissolveProgress = -1f;
    private int uDissolve;

    private final Meshes.Floats scratch = new Meshes.Floats(1 << 16);
    private FloatBuffer upload = ByteBuffer.allocateDirect(1 << 20).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final List<PipeSim.Piece> drained = new ArrayList<>();
    private final List<PipeSim.Piece> heads = new ArrayList<>();

    /** {@code timeScale} above 1 speeds the whole scene up; it exists only so a test run can reach dense scenes quickly. */
    PipesRenderer(Context context, float teapotChance, float timeScale, android.os.Bundle tuning) {
        this.context = context.getApplicationContext();
        this.debuggable = (this.context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (teapotChance >= 0f) params.teapotChance = teapotChance;
        this.timeScale = clamp(timeScale > 0f ? timeScale : 1f, 0.1f, 20f);
        android.os.Bundle t = tuning != null ? tuning : new android.os.Bundle();
        // The launch extras exist for testing and any app can send them, so every value is clamped to a sane range.
        grid = clamp(t.getInt("grid", 15), 3, 40);
        pipeRadius = clamp(t.getFloat("radius", 0.17f), 0.03f, 0.3f);
        ballRadius = pipeRadius * 1.5f;
        teapotSize = ballRadius;
        fieldOfView = clamp(t.getFloat("fov", 65f), 20f, 120f);
        cameraMin = clamp(t.getFloat("dmin", 9f), 1.5f, 200f);
        cameraMax = Math.max(cameraMin, clamp(t.getFloat("dmax", 13f), 1.5f, 200f));
        dissolveSeconds = clamp(t.getFloat("dissolve", 1.6f), 0.1f, 60f);
        String joints = t.getString("joints", "mixed");
        params.joints = "elbow".equals(joints) ? PipeSim.JointMode.ELBOW : "ball".equals(joints) ? PipeSim.JointMode.BALL : PipeSim.JointMode.MIXED;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        // Finished pipes are open tubes: their ends are buried in joints. Growing heads need a cap.
        cylinder = Meshes.cylinder(16, false);
        headCylinder = Meshes.cylinder(16, true);
        sphere = Meshes.sphere(12, 18);
        // Same number of sides as the straight pipes (16), so the rims meet vertex for vertex with no lip.
        elbowMesh = Meshes.torusQuarter(params.bend, pipeRadius, 8, 16);
        try (InputStream in = context.getAssets().open("teapot.bin")) {
            teapot = Meshes.teapot(in);
        } catch (Exception e) {
            teapot = sphere;
        }

        sceneProgram = link(SCENE_VERTEX, SCENE_FRAGMENT);
        aPos = GLES20.glGetAttribLocation(sceneProgram, "aPos");
        aNor = GLES20.glGetAttribLocation(sceneProgram, "aNor");
        aCol = GLES20.glGetAttribLocation(sceneProgram, "aCol");
        uVP = GLES20.glGetUniformLocation(sceneProgram, "uVP");
        uEye = GLES20.glGetUniformLocation(sceneProgram, "uEye");
        uLight = GLES20.glGetUniformLocation(sceneProgram, "uLight");
        overlayProgram = link(OVERLAY_VERTEX, OVERLAY_FRAGMENT);
        oPos = GLES20.glGetAttribLocation(overlayProgram, "aPos");
        uDissolve = GLES20.glGetUniformLocation(overlayProgram, "uProgress");

        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        batchVbo = buffers[0];
        headVbo = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, batchVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, BATCH_VERTEX_CAPACITY * STRIDE, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, headVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, HEAD_VERTEX_CAPACITY * STRIDE, null, GLES20.GL_DYNAMIC_DRAW);

        sim = new PipeSim(grid, grid, grid, random, params, null);
        seenGeneration = -1;
        startScene();
        lastNanos = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        GLES20.glViewport(0, 0, width, height);
        Matrix.perspectiveM(proj, 0, fieldOfView, width / (float) height, CameraPlacement.NEAR_PLANE, 100000f);
        updateView();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        frames++;
        if (now - statsNanos > 2_000_000_000L) {
            if (debuggable) android.util.Log.i("XPPipes", "fps=" + (frames * 1e9f / (now - statsNanos)) + " sceneT=" + sceneSeconds + " dissolve=" + dissolveProgress + " batchVertices=" + batchVertices + " full=" + batchFull);
            frames = 0;
            statsNanos = now;
        }

        if (dissolveProgress < 0f) {
            float sceneDt = dt * timeScale;
            sim.update(sceneDt);
            sceneSeconds += sceneDt;
            if (sceneSeconds >= sceneLength) dissolveProgress = 0f;
        } else {
            dissolveProgress += dt / dissolveSeconds;
            if (dissolveProgress >= 1f) startScene();
        }
        syncBatch();

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        drawScene();
        if (dissolveProgress >= 0f) drawDissolve();
    }

    private void startScene() {
        sim.clear();
        sceneSeconds = 0f;
        sceneLength = SCENE_MIN_SECONDS + random.nextFloat() * (SCENE_MAX_SECONDS - SCENE_MIN_SECONDS);
        cameraDistance = cameraMin + random.nextFloat() * (cameraMax - cameraMin);
        dissolveProgress = -1f;
        look();
    }

    /**
     * Half the scenes are head-on; the others look in from a random direction. Either way the camera is placed
     * outside the lattice with room to spare, so no pipe can pass through the lens (see {@link CameraPlacement}).
     */
    private void look() {
        float dx, dy, dz;
        if (random.nextBoolean()) {
            dx = 0f;
            dy = 0f;
            dz = 1f;
        } else {
            float kx = random.nextFloat() * 2 - 1;
            float ky = random.nextFloat() * 2 - 1;
            float kz = random.nextFloat() * 2 - 1;
            float len = (float) Math.sqrt(kx * kx + ky * ky + kz * kz);
            if (len < 1e-3f) {
                kx = 0f;
                ky = 1f;
                kz = 0f;
                len = 1f;
            }
            kx /= len;
            ky /= len;
            kz /= len;
            // The unit vector (1, 0, 0) turned a quarter turn about the axis k: (k x v) + k (k . v).
            dx = kx * kx;
            dy = kz + ky * kx;
            dz = -ky + kz * kx;
            float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= dl;
            dy /= dl;
            dz /= dl;
        }
        cameraDistance = CameraPlacement.distance(dx, dy, dz, grid, cameraDistance, ballRadius);
        eye[0] = dx * cameraDistance;
        eye[1] = dy * cameraDistance;
        eye[2] = dz * cameraDistance;
        updateView();
    }

    private void updateView() {
        boolean nearVertical = Math.abs(eye[1]) > cameraDistance * 0.98f;
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], 0f, 0f, 0f, 0f, nearVertical ? 0f : 1f, nearVertical ? 1f : 0f);
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0);
        // The light is fixed to the camera (up and to the left, a little in front), as in the original,
        // so every scene is lit from the same side of the screen whatever way it is viewed.
        float fx = -eye[0], fy = -eye[1], fz = -eye[2];
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        float upx = 0f, upy = nearVertical ? 0f : 1f, upz = nearVertical ? 1f : 0f;
        float sx = fy * upz - fz * upy, sy = fz * upx - fx * upz, sz = fx * upy - fy * upx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;
        float ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        float lx = -0.55f, ly = 0.75f, lz = 0.9f; // camera space: x right, y up, z toward the viewer
        light[0] = lx * sx + ly * ux - lz * fx;
        light[1] = lx * sy + ly * uy - lz * fy;
        light[2] = lx * sz + ly * uz - lz * fz;
        float ll = (float) Math.sqrt(light[0] * light[0] + light[1] * light[1] + light[2] * light[2]);
        light[0] /= ll;
        light[1] /= ll;
        light[2] /= ll;
    }

    /** Moves newly finished pieces into the big buffer. */
    private void syncBatch() {
        if (sim.generation() != seenGeneration) {
            seenGeneration = sim.generation();
            batchVertices = 0;
            batchFull = false;
        }
        drained.clear();
        sim.drainFinished(drained);
        if (drained.isEmpty() || batchFull) return;
        scratch.size = 0;
        for (PipeSim.Piece p : drained) write(p, scratch, false);
        int count = scratch.size / FLOATS_PER_VERTEX;
        if (batchVertices + count > BATCH_VERTEX_CAPACITY) {
            batchFull = true;
            return;
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, batchVbo);
        put(scratch);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, batchVertices * STRIDE, scratch.size * 4, upload);
        batchVertices += count;
    }

    private void drawScene() {
        GLES20.glUseProgram(sceneProgram);
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0);
        GLES20.glUniform3f(uEye, eye[0], eye[1], eye[2]);
        GLES20.glUniform3f(uLight, light[0], light[1], light[2]);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aNor);
        GLES20.glEnableVertexAttribArray(aCol);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, batchVbo);
        attribs();
        if (batchVertices > 0) GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, batchVertices);

        heads.clear();
        sim.activeRuns(heads);
        scratch.size = 0;
        for (PipeSim.Piece p : heads) write(p, scratch, true);
        int headVertices = scratch.size / FLOATS_PER_VERTEX;
        if (headVertices > 0 && headVertices <= HEAD_VERTEX_CAPACITY) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, headVbo);
            put(scratch);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, scratch.size * 4, upload);
            attribs();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, headVertices);
        }
    }

    private void attribs() {
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, 0);
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, STRIDE, 12);
        GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, STRIDE, 24);
    }

    private void put(Meshes.Floats f) {
        if (upload.capacity() < f.size) {
            upload = ByteBuffer.allocateDirect(f.size * 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        upload.clear();
        upload.put(f.data, 0, f.size);
        upload.position(0);
    }

    /**
     * The original's clear: the frozen picture is eaten away pixel by pixel in a fixed random order,
     * so the tubes turn speckled and then vanish. Each pixel has a fixed random threshold; once the
     * progress passes it, the pixel is black.
     */
    private void drawDissolve() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(overlayProgram);
        GLES20.glUniform1f(uDissolve, Math.min(1f, dissolveProgress));
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

    // ---- turning pieces into coloured triangles ----

    private void write(PipeSim.Piece p, Meshes.Floats out, boolean head) {
        switch (p.type) {
            case RUN:
                run(p, out, head ? headCylinder : cylinder);
                break;
            case BALL:
                scaledAt(sphere, p, ballRadius, 0, out);
                break;
            case TEAPOT:
                scaledAt(teapot, p, teapotSize, teapotTurns(p), out);
                break;
            default:
                elbow(p, out);
                break;
        }
    }

    private void run(PipeSim.Piece p, Meshes.Floats out, float[] cylinder) {
        if (p.length < 1e-4f) return;
        float dx = p.ax, dy = p.ay, dz = p.az;
        // Two more vectors that complete an orthonormal frame with the direction.
        float hx = Math.abs(dy) < 0.9f ? 0f : 1f;
        float hy = Math.abs(dy) < 0.9f ? 1f : 0f;
        float xx = hy * dz, xy = -hx * dz, xz = hx * dy - hy * dx;
        float xl = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= xl;
        xy /= xl;
        xz /= xl;
        float yx = dy * xz - dz * xy, yy = dz * xx - dx * xz, yz = dx * xy - dy * xx;
        for (int i = 0; i < cylinder.length; i += 6) {
            float px = cylinder[i], py = cylinder[i + 1], pz = cylinder[i + 2];
            float nx = cylinder[i + 3], ny = cylinder[i + 4], nz = cylinder[i + 5];
            out.vertex(
                    p.x + (xx * px + yx * py) * pipeRadius + dx * pz * p.length,
                    p.y + (xy * px + yy * py) * pipeRadius + dy * pz * p.length,
                    p.z + (xz * px + yz * py) * pipeRadius + dz * pz * p.length,
                    xx * nx + yx * ny + dx * nz,
                    xy * nx + yy * ny + dy * nz,
                    xz * nx + yz * ny + dz * nz);
            color(p, out);
        }
    }

    private void elbow(PipeSim.Piece p, Meshes.Floats out) {
        float ux = p.ax, uy = p.ay, uz = p.az, vx = p.bx, vy = p.by, vz = p.bz;
        float wx = uy * vz - uz * vy, wy = uz * vx - ux * vz, wz = ux * vy - uy * vx;
        for (int i = 0; i < elbowMesh.length; i += 6) {
            float px = elbowMesh[i], py = elbowMesh[i + 1], pz = elbowMesh[i + 2];
            float nx = elbowMesh[i + 3], ny = elbowMesh[i + 4], nz = elbowMesh[i + 5];
            out.vertex(
                    p.x + ux * px + vx * py + wx * pz,
                    p.y + uy * px + vy * py + wy * pz,
                    p.z + uz * px + vz * py + wz * pz,
                    ux * nx + vx * ny + wx * nz,
                    uy * nx + vy * ny + wy * nz,
                    uz * nx + vz * ny + wz * nz);
            color(p, out);
        }
    }

    /** A shape of radius {@code scale} at the piece's position, turned in quarter turns about each axis. */
    private void scaledAt(float[] mesh, PipeSim.Piece p, float scale, int turns, Meshes.Floats out) {
        int tx = turns & 3, ty = (turns >> 2) & 3, tz = (turns >> 4) & 3;
        float[] v = new float[3];
        float[] n = new float[3];
        for (int i = 0; i < mesh.length; i += 6) {
            v[0] = mesh[i];
            v[1] = mesh[i + 1];
            v[2] = mesh[i + 2];
            n[0] = mesh[i + 3];
            n[1] = mesh[i + 4];
            n[2] = mesh[i + 5];
            if (turns != 0) quarter(v, n, tx, ty, tz);
            out.vertex(p.x + v[0] * scale, p.y + v[1] * scale, p.z + v[2] * scale, n[0], n[1], n[2]);
            color(p, out);
        }
    }

    private static void quarter(float[] v, float[] n, int tx, int ty, int tz) {
        for (int k = 0; k < tx; k++) {
            float t = v[1];
            v[1] = -v[2];
            v[2] = t;
            t = n[1];
            n[1] = -n[2];
            n[2] = t;
        }
        for (int k = 0; k < ty; k++) {
            float t = v[2];
            v[2] = -v[0];
            v[0] = t;
            t = n[2];
            n[2] = -n[0];
            n[0] = t;
        }
        for (int k = 0; k < tz; k++) {
            float t = v[0];
            v[0] = -v[1];
            v[1] = t;
            t = n[0];
            n[0] = -n[1];
            n[1] = t;
        }
    }

    /** Each teapot lands in one of the 64 quarter-turn orientations, fixed by where it sits. */
    private static int teapotTurns(PipeSim.Piece p) {
        int h = Float.floatToIntBits(p.x * 31f + p.y * 17f + p.z * 7f) * 0x9E3779B1;
        return (h >>> 8) & 63;
    }

    private static void color(PipeSim.Piece p, Meshes.Floats out) {
        out.add(p.r);
        out.add(p.g);
        out.add(p.b);
    }

    // ---- shaders ----

    private static final String SCENE_VERTEX =
            "uniform mat4 uVP;\n"
                    + "attribute vec3 aPos; attribute vec3 aNor; attribute vec3 aCol;\n"
                    + "varying vec3 vPos; varying vec3 vNor; varying vec3 vCol;\n"
                    + "void main() { vPos = aPos; vNor = aNor; vCol = aCol; gl_Position = uVP * vec4(aPos, 1.0); }\n";

    /** Phong as in the reference: emissive 0.3 x colour, ambient 0x111111, one white light, specular 0xa9fcff, shininess 100. */
    private static final String SCENE_FRAGMENT =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                    + "uniform vec3 uEye; uniform vec3 uLight;\n"
                    + "varying vec3 vPos; varying vec3 vNor; varying vec3 vCol;\n"
                    + "void main() {\n"
                    + "  vec3 n = normalize(vNor);\n"
                    + "  if (!gl_FrontFacing) n = -n;\n"
                    + "  vec3 v = normalize(uEye - vPos);\n"
                    + "  vec3 h = normalize(uLight + v);\n"
                    + "  float diff = max(dot(n, uLight), 0.0);\n"
                    + "  float spec = pow(max(dot(n, h), 0.0), 48.0) * step(0.0, dot(n, uLight));\n"
                    + "  vec3 c = vCol * (0.22 + 0.9 * diff) + vec3(1.0) * (0.38 * spec);\n"
                    + "  gl_FragColor = vec4(c, 1.0);\n"
                    + "}\n";

    private static final String OVERLAY_VERTEX =
            "attribute vec2 aPos; void main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n";
    private static final String OVERLAY_FRAGMENT =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                    + "uniform float uProgress;\n"
                    + "void main() {\n"
                    + "  float r = fract(sin(dot(floor(gl_FragCoord.xy), vec2(12.9898, 78.233))) * 43758.5453);\n"
                    + "  if (r < uProgress) gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); else discard;\n"
                    + "}\n";

    private static int link(String vertex, String fragment) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        return program;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
