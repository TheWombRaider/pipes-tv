package io.github.thewombraider.pipestv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The growth rules of the original 3D Pipes screensaver, with no graphics in it. See docs/REFERENCE_ANALYSIS.md for
 * where each rule comes from.
 *
 * Pipes move through a lattice of nodes one spacing apart, one whole segment per {@link #tick()}. At each node a pipe
 * goes straight or turns 90 degrees into a free node; it never enters a node any pipe has used. A pipe that is
 * trapped ends in a ball and a new one starts somewhere free, until the scene has had its quota of pipes. The scene
 * is over when the last one is trapped.
 *
 * Output is a list of {@link Piece}s in lattice units (nodes 1 apart, centered on the origin), each of which is
 * final once made, so a renderer can draw new pieces as they arrive and never touch them again.
 */
public final class PipeSim {
    public enum Type { RUN, BALL, ELBOW, TEAPOT }

    public enum JointMode { ELBOW, BALL, MIXED }

    /** Pipe radius, in node spacings: the original's pipes have radius 1 on a lattice 7 apart. */
    public static final float RADIUS = 1f / 7f;

    /** One shape to draw. */
    public static final class Piece {
        public final Type type;
        /** Index into {@link Materials}. */
        public final int material;
        /** RUN: start. BALL, TEAPOT: center. ELBOW: center of the bend's arc. */
        public float x, y, z;
        /** RUN: unit direction. ELBOW: unit vector from the arc's center to its start. */
        public float ax, ay, az;
        /** ELBOW: unit vector from the arc's center to its end. */
        public float bx, by, bz;
        /** RUN: length. */
        public float length;

        Piece(Type type, int material) {
            this.type = type;
            this.material = material;
        }
    }

    public static final class Params {
        /** Several pipes at once (7 per scene, 2 to 4 growing together) rather than one at a time (5 per scene). */
        public boolean multiple = false;
        public JointMode joints = JointMode.MIXED;
        /** In mixed mode, the chance that a turn is a ball joint rather than an elbow. */
        public float ballChance = 1f / 3f;
        /**
         * The chance that a turn in mixed mode is the Utah teapot instead: 1 in 1000, as in the original's code. The
         * footage agrees: counters found teapots in the single-pipe recordings at about the rate that rate predicts
         * once the many they miss are allowed for (docs/reference/JOINTS.md).
         */
        public float teapotChance = 1f / 1000f;
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int MAX_WEIGHT = 100;
    /** sqrt(2) / cos(180 deg / 8): the original's ball radius for 8-sided pipes, as a multiple of the pipe radius. */
    public static final float BALL_SCALE = (float) (Math.sqrt(2) / Math.cos(Math.PI / 8));

    private final int nx, ny, nz;
    private final Random rng;
    private final Params params;
    private final boolean[] used;
    private final List<Piece> fresh = new ArrayList<>();
    private final List<Pipe> pipes = new ArrayList<>();
    private int quota;
    private int turnBalls, turnElbows, turnTeapots;
    private int started;
    private boolean chase;
    private Pipe lead;
    private boolean over;

    private final class Pipe {
        final int weight;
        int material;
        int x, y, z;
        int dir;
        boolean stuck;
        boolean chaser;

        Pipe() {
            weight = rng.nextInt(20) == 0 ? MAX_WEIGHT / 4 + rng.nextInt(MAX_WEIGHT - MAX_WEIGHT / 4 + 1) : 1 + rng.nextInt(4);
        }
    }

    public PipeSim(int nx, int ny, int nz, Random rng, Params params) {
        if (nx < 1 || ny < 1 || nz < 1) {
            throw new IllegalArgumentException("the lattice needs at least one node on each side: " + nx + "x" + ny + "x" + nz);
        }
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.rng = rng;
        this.params = params;
        this.used = new boolean[nx * ny * nz];
        reset();
    }

    /** The original's lattice for a screen of this shape: 15 nodes along the long side and in depth. */
    public static int[] latticeFor(int width, int height) {
        float aspect = width / (float) Math.max(1, height);
        if (aspect >= 1f) return new int[] {15, Math.max(1, (int) (15 / aspect)), 15};
        return new int[] {Math.max(1, (int) (15 * aspect)), 15, 15};
    }

    /** Empties the lattice and starts a new scene. */
    public void reset() {
        Arrays.fill(used, false);
        fresh.clear();
        pipes.clear();
        over = false;
        lead = null;
        turnBalls = turnElbows = turnTeapots = 0;
        quota = 5;
        int together = 1;
        if (params.multiple) {
            quota = (int) (quota * 1.5f);
            together = Math.min(quota, 2 + rng.nextInt(3));
            // Once a scene has chased, every later one does too, as in the original.
            if (!chase && rng.nextInt(5) == 0) chase = true;
        }
        started = 0;
        for (int i = 0; i < together; i++) {
            Pipe pipe = new Pipe();
            if (chase && i == 0) lead = pipe;
            pipe.chaser = chase && i > 0;
            pipes.add(pipe);
            start(pipe);
            started++;
        }
    }

    /** How many turns of this scene were ball joints, elbows and teapots (the boxed-in start teapot is not a turn). */
    public int[] turns() {
        return new int[] {turnBalls, turnElbows, turnTeapots};
    }

    /** How many pipes are growing right now (not trapped). */
    public int growingPipes() {
        int n = 0;
        for (Pipe pipe : pipes) if (!pipe.stuck) n++;
        return n;
    }

    /** True once the last pipe of the scene is trapped. */
    public boolean isOver() {
        return over;
    }

    /** Hands over, once each, the pieces made since the last call. */
    public void drain(List<Piece> into) {
        into.addAll(fresh);
        fresh.clear();
    }

    /** One timer tick: trapped pipes are replaced or retired, then every growing pipe adds one segment. */
    public void tick() {
        if (over) return;
        for (int i = 0; i < pipes.size(); ) {
            Pipe pipe = pipes.get(i);
            if (pipe.stuck) {
                started++;
                if (started > quota) {
                    pipes.remove(i);
                    if (pipe == lead) lead = null;
                    continue;
                }
                if (!start(pipe)) quota = started;
            }
            i++;
        }
        if (pipes.isEmpty()) {
            over = true;
            return;
        }
        if (chase && lead == null) {
            lead = pipes.get(rng.nextInt(pipes.size()));
            lead.chaser = false;
        }
        for (Pipe pipe : pipes) {
            if (!pipe.stuck) grow(pipe);
        }
    }

    public float usedFraction() {
        int n = 0;
        for (boolean u : used) if (u) n++;
        return n / (float) used.length;
    }

    private boolean start(Pipe pipe) {
        int node = randomFreeNode();
        if (node < 0) {
            pipe.stuck = true;
            return false;
        }
        pipe.x = node % nx;
        pipe.y = (node / nx) % ny;
        pipe.z = node / (nx * ny);
        pipe.material = Materials.randomPipeMaterial(rng);
        pipe.dir = rng.nextInt(6);
        int d = chooseDirection(pipe);
        if (d < 0) {
            // Boxed in from the start: the original leaves a teapot here.
            addAt(Type.TEAPOT, pipe.x, pipe.y, pipe.z, pipe.material);
            pipe.stuck = true;
            return true;
        }
        pipe.stuck = false;
        addAt(Type.BALL, pipe.x, pipe.y, pipe.z, pipe.material);
        addRun(pipe, d, RADIUS);
        move(pipe, d);
        return true;
    }

    private void grow(Pipe pipe) {
        int d = chooseDirection(pipe);
        if (d < 0) {
            addAt(Type.BALL, pipe.x, pipe.y, pipe.z, pipe.material);
            pipe.stuck = true;
            return;
        }
        if (d == pipe.dir) {
            addRun(pipe, d, -RADIUS);
        } else {
            joint(pipe, d);
            addRun(pipe, d, RADIUS);
        }
        move(pipe, d);
    }

    private void move(Pipe pipe, int d) {
        pipe.x += DIRS[d][0];
        pipe.y += DIRS[d][1];
        pipe.z += DIRS[d][2];
        pipe.dir = d;
    }

    /** A run from the pipe's node toward the next one, starting {@code offset} along the way and stopping one radius short. */
    private void addRun(Pipe pipe, int d, float offset) {
        Piece run = new Piece(Type.RUN, pipe.material);
        run.ax = DIRS[d][0];
        run.ay = DIRS[d][1];
        run.az = DIRS[d][2];
        run.x = wx(pipe.x) + run.ax * offset;
        run.y = wy(pipe.y) + run.ay * offset;
        run.z = wz(pipe.z) + run.az * offset;
        run.length = 1f - RADIUS - offset;
        fresh.add(run);
    }

    private void joint(Pipe pipe, int d) {
        Type type;
        switch (params.joints) {
            case ELBOW:
                type = Type.ELBOW;
                break;
            case BALL:
                type = Type.BALL;
                break;
            default:
                if (rng.nextFloat() < params.teapotChance) type = Type.TEAPOT;
                else type = rng.nextFloat() < params.ballChance ? Type.BALL : Type.ELBOW;
        }
        if (type == Type.BALL) turnBalls++;
        else if (type == Type.TEAPOT) turnTeapots++;
        else turnElbows++;
        if (type != Type.ELBOW) {
            addAt(type, pipe.x, pipe.y, pipe.z, pipe.material);
            return;
        }
        int[] d1 = DIRS[pipe.dir], d2 = DIRS[d];
        float[] arc = elbowGeometry(wx(pipe.x), wy(pipe.y), wz(pipe.z), d1[0], d1[1], d1[2], d2[0], d2[1], d2[2], RADIUS);
        Piece e = new Piece(Type.ELBOW, pipe.material);
        e.x = arc[0];
        e.y = arc[1];
        e.z = arc[2];
        e.ax = arc[3];
        e.ay = arc[4];
        e.az = arc[5];
        e.bx = arc[6];
        e.by = arc[7];
        e.bz = arc[8];
        fresh.add(e);
    }

    /**
     * Where an elbow goes when a pipe at node P turns from direction d1 to d2.
     *
     * @return {center(3), u(3), v(3), start(3), end(3)}: the arc runs from the center plus u*bend to the
     *     center plus v*bend, with u = -d2 and v = d1. The run before it stops at start (one bend radius before P);
     *     the run after it begins at end (one bend radius after P).
     */
    static float[] elbowGeometry(float px, float py, float pz, int d1x, int d1y, int d1z, int d2x, int d2y, int d2z, float bend) {
        float ax = px - d1x * bend, ay = py - d1y * bend, az = pz - d1z * bend;
        float cx = ax + d2x * bend, cy = ay + d2y * bend, cz = az + d2z * bend;
        float bx = cx + d1x * bend, by = cy + d1y * bend, bz = cz + d1z * bend;
        return new float[] {cx, cy, cz, -d2x, -d2y, -d2z, d1x, d1y, d1z, ax, ay, az, bx, by, bz};
    }

    /**
     * The next direction, or -1 if the pipe is trapped. A chasing pipe heads for the lead pipe when it can; any
     * other pipe goes straight with its own weight against a weight of 1 for each free turn. The node chosen is
     * claimed at once.
     */
    private int chooseDirection(Pipe pipe) {
        if (pipe.chaser && lead != null && lead != pipe) {
            int d = chase(pipe);
            if (d != -2) return d;
        }
        int straight = pipe.dir;
        int weight = free(pipe, straight) ? pipe.weight : 0;
        if (weight == MAX_WEIGHT) return claim(pipe, straight);
        int[] turns = new int[6];
        int n = 0;
        for (int d = 0; d < 6; d++) {
            if (d != straight && free(pipe, d)) turns[n++] = d;
        }
        if (weight + n == 0) return -1;
        int pick = rng.nextInt(weight + n);
        return claim(pipe, pick < weight ? straight : turns[pick - weight]);
    }

    /** Toward the lead pipe on any axis where it is ahead; -2 if the lead is at the same node. */
    private int chase(Pipe pipe) {
        int[] towards = new int[3];
        int n = 0;
        if (lead.x != pipe.x) towards[n++] = lead.x > pipe.x ? 0 : 1;
        if (lead.y != pipe.y) towards[n++] = lead.y > pipe.y ? 2 : 3;
        if (lead.z != pipe.z) towards[n++] = lead.z > pipe.z ? 4 : 5;
        if (n == 0) return -2;
        int[] open = new int[6];
        int m = 0;
        for (int i = 0; i < n; i++) if (free(pipe, towards[i])) open[m++] = towards[i];
        if (m == 0) {
            for (int d = 0; d < 6; d++) if (free(pipe, d)) open[m++] = d;
        }
        if (m == 0) return -1;
        return claim(pipe, open[rng.nextInt(m)]);
    }

    private int claim(Pipe pipe, int d) {
        mark(pipe.x + DIRS[d][0], pipe.y + DIRS[d][1], pipe.z + DIRS[d][2]);
        return d;
    }

    private int randomFreeNode() {
        int total = used.length;
        for (int attempt = 0; attempt < 2 * total; attempt++) {
            int i = rng.nextInt(total);
            if (!used[i]) {
                used[i] = true;
                return i;
            }
        }
        for (int i = 0; i < total; i++) {
            if (!used[i]) {
                used[i] = true;
                return i;
            }
        }
        return -1;
    }

    private void addAt(Type type, int x, int y, int z, int material) {
        Piece p = new Piece(type, material);
        p.x = wx(x);
        p.y = wy(y);
        p.z = wz(z);
        fresh.add(p);
    }

    private boolean free(Pipe pipe, int d) {
        int x = pipe.x + DIRS[d][0], y = pipe.y + DIRS[d][1], z = pipe.z + DIRS[d][2];
        return x >= 0 && x < nx && y >= 0 && y < ny && z >= 0 && z < nz && !used[(z * ny + y) * nx + x];
    }

    private void mark(int x, int y, int z) {
        used[(z * ny + y) * nx + x] = true;
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
