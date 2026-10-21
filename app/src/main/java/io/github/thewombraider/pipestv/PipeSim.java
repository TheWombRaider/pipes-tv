package io.github.thewombraider.pipestv;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The growth rules of the Windows 3D Pipes screensaver, with no graphics in it.
 *
 * Pipes grow one lattice unit at a time through a 3D grid. At each node a pipe either carries on
 * straight or turns 90 degrees, never doubling back and never entering a node any pipe has used. A
 * turn is drawn as a rounded elbow or a ball joint; a pipe that has nowhere left to go ends in a
 * ball. Several pipes grow at once, and when the lattice has filled up the whole scene clears and
 * starts again.
 *
 * The output is a list of {@link Piece}s in world units, one lattice unit apart and centred on the
 * origin, which a renderer draws as cylinders, spheres and torus sections.
 */
public final class PipeSim {
    public enum Type { RUN, BALL, ELBOW, TEAPOT }

    public enum JointMode { ELBOW, BALL, MIXED }

    /** One thing to draw. Position and direction fields are used differently per type. */
    public static final class Piece {
        public final Type type;
        /** RUN: start. BALL/TEAPOT: centre. ELBOW: arc centre. */
        public float x, y, z;
        /** RUN: unit direction. ELBOW: unit vector from the arc centre to the arc's start. */
        public float ax, ay, az;
        /** ELBOW: unit vector from the arc centre to the arc's end. */
        public float bx, by, bz;
        /** RUN only: grows while the pipe's head is still extending. */
        public float length;
        public final float r, g, b;

        Piece(Type type, float[] rgb) {
            this.type = type;
            this.r = rgb[0];
            this.g = rgb[1];
            this.b = rgb[2];
        }
    }

    public interface Listener {
        void nodeEntered(int pipe, int x, int y, int z);

        void reset();
    }

    public static final class Params {
        /** Lattice units per second. */
        public float speed = 12f;
        /** Chance that a pipe turns at a node where it could also go straight. */
        public float turnChance = 0.42f;
        /** Pipes growing at once. */
        public int maxPipes = 6;
        /** Seconds between new pipes joining a growing scene (the first pipe starts at once). */
        public float spawnMinSeconds = 5f;
        public float spawnMaxSeconds = 9f;
        /** Scene clears once this fraction of the lattice is used. */
        public float resetFill = 0.95f;
        /** Bend radius of an elbow, in lattice units. At most 0.5. */
        public float bend = 0.4f;
        public JointMode joints = JointMode.MIXED;
        /** In mixed mode, the chance that a turn is a ball joint rather than an elbow. */
        public float ballChance = 1f / 3f;
        /** The chance that any turn is the Utah teapot instead (1 in 1000 in the original). */
        public float teapotChance = 1f / 1000f;
        /**
         * The colours pipes take, in turn, in an order shuffled for each scene. Null (the default)
         * gives every pipe a random colour from the whole RGB cube, as the original does.
         */
        public float[][] palette = null;
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final int nx, ny, nz;
    private final Random rng;
    private final Params params;
    private final Listener listener;
    private final boolean[] used;
    private int usedCount;
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Piece> finished = new ArrayList<>();
    private final List<Pipe> pipes = new ArrayList<>();
    private int nextPipeId;
    private int generation;
    private int[] paletteOrder = new int[0];
    private int paletteNext;
    private float sceneTime;
    private float nextSpawn;

    private final class Pipe {
        final int id = nextPipeId++;
        int x, y, z;
        int dx, dy, dz;
        float progress;
        float sx, sy, sz;
        Piece run;
        final float[] color;
        boolean done;

        Pipe(float[] color) {
            this.color = color;
        }
    }

    public PipeSim(int nx, int ny, int nz, Random rng, Params params, Listener listener) {
        if (nx < 1 || ny < 1 || nz < 1) {
            throw new IllegalArgumentException("the lattice needs at least one node on each side: " + nx + "x" + ny + "x" + nz);
        }
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.rng = rng;
        this.params = params;
        this.listener = listener;
        this.used = new boolean[nx * ny * nz];
    }

    public List<Piece> pieces() {
        return pieces;
    }

    /** Bumps every time the scene is cleared, so a renderer knows to throw away what it cached. */
    public int generation() {
        return generation;
    }

    /** The straight runs that are still extending at the head of a pipe. */
    public void activeRuns(List<Piece> into) {
        for (Pipe pipe : pipes) {
            if (!pipe.done) into.add(pipe.run);
        }
    }

    /** Hands over, once each, the pieces whose geometry will no longer change. */
    public void drainFinished(List<Piece> into) {
        into.addAll(finished);
        finished.clear();
    }

    public float occupiedFraction() {
        return usedCount / (float) used.length;
    }

    public void update(float dt) {
        float distance = dt * params.speed;
        for (Pipe pipe : pipes) {
            if (!pipe.done) advance(pipe, distance);
        }
        pipes.removeIf(p -> p.done);
        sceneTime += dt;
        if (occupiedFraction() >= params.resetFill) clear();
        boolean due = sceneTime >= nextSpawn;
        if (pipes.size() < params.maxPipes && (due || pipes.isEmpty())) {
            if (spawn()) {
                nextSpawn = sceneTime + params.spawnMinSeconds
                        + rng.nextFloat() * (params.spawnMaxSeconds - params.spawnMinSeconds);
            } else if (pipes.isEmpty()) {
                clear();
            }
        }
    }

    /** Empties the lattice and starts a new scene. */
    public void clear() {
        pieces.clear();
        finished.clear();
        generation++;
        sceneTime = 0f;
        nextSpawn = 0f;
        shufflePalette();
        pipes.clear();
        java.util.Arrays.fill(used, false);
        usedCount = 0;
        nextPipeId = 0;
        if (listener != null) listener.reset();
    }

    private boolean spawn() {
        for (int attempt = 0; attempt < 60; attempt++) {
            int x = rng.nextInt(nx), y = rng.nextInt(ny), z = rng.nextInt(nz);
            if (isUsed(x, y, z)) continue;
            List<int[]> options = new ArrayList<>();
            for (int[] d : DIRS) {
                if (free(x + d[0], y + d[1], z + d[2])) options.add(d);
            }
            if (options.isEmpty()) continue;
            int[] d = options.get(rng.nextInt(options.size()));
            Pipe pipe = new Pipe(nextColor());
            pipe.x = x;
            pipe.y = y;
            pipe.z = z;
            pipe.dx = d[0];
            pipe.dy = d[1];
            pipe.dz = d[2];
            mark(x, y, z);
            visit(pipe);
            addBall(Type.BALL, x, y, z, pipe.color);
            pipe.sx = wx(x);
            pipe.sy = wy(y);
            pipe.sz = wz(z);
            pipe.run = newRun(pipe.sx, pipe.sy, pipe.sz, d, pipe.color);
            mark(x + d[0], y + d[1], z + d[2]);
            pipes.add(pipe);
            return true;
        }
        return false;
    }

    private void advance(Pipe pipe, float distance) {
        while (distance > 0f && !pipe.done) {
            float remaining = 1f - pipe.progress;
            if (distance < remaining) {
                pipe.progress += distance;
                distance = 0f;
            } else {
                distance -= remaining;
                pipe.progress = 0f;
                arrive(pipe);
            }
        }
        if (!pipe.done) headLength(pipe);
    }

    /** Extends the growing run so its end sits at the pipe's head. */
    private void headLength(Pipe pipe) {
        float hx = wx(pipe.x) + pipe.dx * pipe.progress;
        float hy = wy(pipe.y) + pipe.dy * pipe.progress;
        float hz = wz(pipe.z) + pipe.dz * pipe.progress;
        float len = (hx - pipe.sx) * pipe.dx + (hy - pipe.sy) * pipe.dy + (hz - pipe.sz) * pipe.dz;
        pipe.run.length = Math.max(0f, len);
    }

    private void arrive(Pipe pipe) {
        pipe.x += pipe.dx;
        pipe.y += pipe.dy;
        pipe.z += pipe.dz;
        visit(pipe);
        int[] next = chooseDirection(pipe);
        float px = wx(pipe.x), py = wy(pipe.y), pz = wz(pipe.z);
        if (next == null) {
            pipe.run.length = (px - pipe.sx) * pipe.dx + (py - pipe.sy) * pipe.dy + (pz - pipe.sz) * pipe.dz;
            finished.add(pipe.run);
            addBall(Type.BALL, pipe.x, pipe.y, pipe.z, pipe.color);
            pipe.done = true;
            return;
        }
        boolean straight = next[0] == pipe.dx && next[1] == pipe.dy && next[2] == pipe.dz;
        if (!straight) {
            boolean teapot = rng.nextFloat() < params.teapotChance;
            boolean elbow = !teapot && chooseElbow();
            if (elbow) {
                float[] arc = elbowGeometry(px, py, pz, pipe.dx, pipe.dy, pipe.dz, next[0], next[1], next[2], params.bend);
                pipe.run.length = (arc[9] - pipe.sx) * pipe.dx + (arc[10] - pipe.sy) * pipe.dy + (arc[11] - pipe.sz) * pipe.dz;
                finished.add(pipe.run);
                Piece e = new Piece(Type.ELBOW, pipe.color);
                e.x = arc[0];
                e.y = arc[1];
                e.z = arc[2];
                e.ax = arc[3];
                e.ay = arc[4];
                e.az = arc[5];
                e.bx = arc[6];
                e.by = arc[7];
                e.bz = arc[8];
                pieces.add(e);
                finished.add(e);
                pipe.sx = arc[12];
                pipe.sy = arc[13];
                pipe.sz = arc[14];
            } else {
                pipe.run.length = (px - pipe.sx) * pipe.dx + (py - pipe.sy) * pipe.dy + (pz - pipe.sz) * pipe.dz;
                finished.add(pipe.run);
                addBall(teapot ? Type.TEAPOT : Type.BALL, pipe.x, pipe.y, pipe.z, pipe.color);
                pipe.sx = px;
                pipe.sy = py;
                pipe.sz = pz;
            }
            pipe.run = newRun(pipe.sx, pipe.sy, pipe.sz, next, pipe.color);
        }
        pipe.dx = next[0];
        pipe.dy = next[1];
        pipe.dz = next[2];
        mark(pipe.x + pipe.dx, pipe.y + pipe.dy, pipe.z + pipe.dz);
    }

    private boolean chooseElbow() {
        switch (params.joints) {
            case ELBOW:
                return true;
            case BALL:
                return false;
            default:
                return rng.nextFloat() >= params.ballChance;
        }
    }

    private int[] chooseDirection(Pipe pipe) {
        boolean straightFree = free(pipe.x + pipe.dx, pipe.y + pipe.dy, pipe.z + pipe.dz);
        if (straightFree && rng.nextFloat() >= params.turnChance) {
            return new int[] {pipe.dx, pipe.dy, pipe.dz};
        }
        List<int[]> turns = new ArrayList<>();
        for (int[] d : DIRS) {
            boolean same = d[0] == pipe.dx && d[1] == pipe.dy && d[2] == pipe.dz;
            boolean back = d[0] == -pipe.dx && d[1] == -pipe.dy && d[2] == -pipe.dz;
            if (same || back) continue;
            if (free(pipe.x + d[0], pipe.y + d[1], pipe.z + d[2])) turns.add(d);
        }
        if (!turns.isEmpty()) return turns.get(rng.nextInt(turns.size()));
        return straightFree ? new int[] {pipe.dx, pipe.dy, pipe.dz} : null;
    }

    /**
     * Where an elbow goes when a pipe at node P turns from direction d1 to d2.
     *
     * @return {centre(3), u(3), v(3), start(3), end(3)}: the arc runs from the centre plus u*bend to the
     *     centre plus v*bend, with u = -d2 and v = d1. The straight run before it stops at start; the
     *     straight run after it begins at end.
     */
    static float[] elbowGeometry(float px, float py, float pz, int d1x, int d1y, int d1z, int d2x, int d2y, int d2z, float bend) {
        float ax = px - d1x * bend, ay = py - d1y * bend, az = pz - d1z * bend;
        float cx = ax + d2x * bend, cy = ay + d2y * bend, cz = az + d2z * bend;
        float bx = cx + d1x * bend, by = cy + d1y * bend, bz = cz + d1z * bend;
        return new float[] {cx, cy, cz, -d2x, -d2y, -d2z, d1x, d1y, d1z, ax, ay, az, bx, by, bz};
    }

    private Piece newRun(float x, float y, float z, int[] d, float[] color) {
        Piece run = new Piece(Type.RUN, color);
        run.x = x;
        run.y = y;
        run.z = z;
        run.ax = d[0];
        run.ay = d[1];
        run.az = d[2];
        run.length = 0f;
        pieces.add(run);
        return run;
    }

    private void addBall(Type type, int x, int y, int z, float[] color) {
        Piece ball = new Piece(type, color);
        ball.x = wx(x);
        ball.y = wy(y);
        ball.z = wz(z);
        pieces.add(ball);
        finished.add(ball);
    }

    private void shufflePalette() {
        if (params.palette == null) {
            paletteOrder = new int[0];
            return;
        }
        paletteOrder = new int[params.palette.length];
        for (int i = 0; i < paletteOrder.length; i++) paletteOrder[i] = i;
        for (int i = paletteOrder.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = paletteOrder[i];
            paletteOrder[i] = paletteOrder[j];
            paletteOrder[j] = t;
        }
        paletteNext = 0;
    }

    /** The next colour from the palette, so six pipes growing together are six different colours. */
    private float[] nextColor() {
        if (params.palette == null) return new float[] {rng.nextFloat(), rng.nextFloat(), rng.nextFloat()};
        if (paletteOrder.length != params.palette.length) shufflePalette();
        float[] c = params.palette[paletteOrder[paletteNext % paletteOrder.length]];
        paletteNext++;
        return c.clone();
    }

    private void visit(Pipe pipe) {
        if (listener != null) listener.nodeEntered(pipe.id, pipe.x, pipe.y, pipe.z);
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && x < nx && y >= 0 && y < ny && z >= 0 && z < nz;
    }

    private boolean isUsed(int x, int y, int z) {
        return used[(z * ny + y) * nx + x];
    }

    private boolean free(int x, int y, int z) {
        return inside(x, y, z) && !isUsed(x, y, z);
    }

    private void mark(int x, int y, int z) {
        int i = (z * ny + y) * nx + x;
        if (!used[i]) {
            used[i] = true;
            usedCount++;
        }
    }

    private float wx(int x) {
        return x - (nx - 1) / 2f;
    }

    private float wy(int y) {
        return y - (ny - 1) / 2f;
    }

    private float wz(int z) {
        return z - (nz - 1) / 2f;
    }
}
