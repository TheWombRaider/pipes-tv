package io.github.thewombraider.pipestv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class PipeSimTest {
    private static final float R = PipeSim.RADIUS;

    @Test
    public void defaultsAreOnePipeAtATimeWithBallsAndElbowsMixed() {
        PipeSim.Params p = new PipeSim.Params();
        assertFalse("one pipe grows at a time", p.multiple);
        assertEquals(PipeSim.JointMode.MIXED, p.joints);
        assertEquals("a turn is a ball joint 1 time in 3", 1f / 3f, p.ballChance, 1e-6f);
    }

    @Test
    public void thePipeGrowsAloneAndSceneTurnsMixBallsAndElbowsInTheDefaultShare() {
        PipeSim.Params p = new PipeSim.Params();
        p.teapotChance = 0f;
        PipeSim sim = new PipeSim(15, 8, 15, new Random(21), p);
        long balls = 0, elbows = 0;
        for (int s = 0; s < 100; s++) {
            int growing = 0;
            while (!sim.isOver()) {
                sim.tick();
                growing = Math.max(growing, sim.growingPipes());
            }
            assertTrue("never more than one pipe growing, saw " + growing, growing <= 1);
            int[] t = sim.turns();
            balls += t[0];
            elbows += t[1];
            sim.reset();
        }
        double share = balls / (double) (balls + elbows);
        assertTrue("ball share " + share + " of " + (balls + elbows) + " turns", Math.abs(share - 1.0 / 3.0) < 0.02);
    }

    private static PipeSim.Params params(boolean multiple) {
        PipeSim.Params p = new PipeSim.Params();
        p.multiple = multiple;
        return p;
    }

    /** Runs one scene to its end and returns everything it made, failing if it never ends. */
    private static List<PipeSim.Piece> scene(PipeSim sim, int[] ticks) {
        List<PipeSim.Piece> out = new ArrayList<>();
        sim.drain(out);
        int n = 0;
        while (!sim.isOver()) {
            sim.tick();
            sim.drain(out);
            if (++n > 100_000) fail("the scene never ended");
        }
        if (ticks != null) ticks[0] = n;
        return out;
    }

    private static String node(float x, float y, float z) {
        return Math.round(x * 2) + "," + Math.round(y * 2) + "," + Math.round(z * 2);
    }

    @Test
    public void theLatticeFollowsTheScreensShape() {
        assertArrayEquals(new int[] {15, 8, 15}, PipeSim.latticeFor(1920, 1080));
        assertArrayEquals(new int[] {15, 11, 15}, PipeSim.latticeFor(1024, 768));
        assertArrayEquals(new int[] {8, 15, 15}, PipeSim.latticeFor(1080, 1920));
        assertArrayEquals(new int[] {15, 1, 15}, PipeSim.latticeFor(5000, 10));
    }

    @Test
    public void runsAreWholeSegmentsAlongTheAxes() {
        PipeSim sim = new PipeSim(15, 8, 15, new Random(3), params(true));
        for (int s = 0; s < 20; s++) {
            for (PipeSim.Piece p : scene(sim, null)) {
                if (p.type != PipeSim.Type.RUN) continue;
                assertEquals("unit direction on one axis", 1f, Math.abs(p.ax) + Math.abs(p.ay) + Math.abs(p.az), 1e-6f);
                // Straight on: node to node, offset back by a radius. After a start or a turn: one radius clear of each node.
                boolean straight = Math.abs(p.length - 1f) < 1e-5f;
                boolean afterTurn = Math.abs(p.length - (1f - 2 * R)) < 1e-5f;
                assertTrue("length " + p.length, straight || afterTurn);
            }
            sim.reset();
        }
    }

    @Test
    public void noNodeIsEnteredTwiceInAScene() {
        PipeSim sim = new PipeSim(15, 8, 15, new Random(11), params(true));
        for (int s = 0; s < 30; s++) {
            Set<String> entered = new HashSet<>();
            for (PipeSim.Piece p : scene(sim, null)) {
                if (p.type != PipeSim.Type.RUN) continue;
                // The node the run heads for: its end plus one radius.
                float ex = p.x + p.ax * (p.length + R), ey = p.y + p.ay * (p.length + R), ez = p.z + p.az * (p.length + R);
                assertTrue("node " + node(ex, ey, ez) + " entered twice", entered.add(node(ex, ey, ez)));
            }
            sim.reset();
        }
    }

    @Test
    public void everythingStaysInsideTheLattice() {
        PipeSim sim = new PipeSim(15, 8, 15, new Random(5), params(true));
        for (int s = 0; s < 10; s++) {
            for (PipeSim.Piece p : scene(sim, null)) {
                assertTrue(Math.abs(p.x) <= 7f + 1e-4f && Math.abs(p.y) <= 3.5f + 1e-4f && Math.abs(p.z) <= 7f + 1e-4f);
            }
            sim.reset();
        }
    }

    /** The same rules as tools/reference/sim_model.py, which the footage was measured against: about 300 ticks a scene. */
    @Test
    public void scenesLastAsLongAsTheReferenceModelSays() {
        PipeSim multi = new PipeSim(15, 8, 15, new Random(1), params(true));
        PipeSim single = new PipeSim(15, 8, 15, new Random(2), params(false));
        assertEquals(309, meanTicks(multi, 400), 25);
        assertEquals(776, meanTicks(single, 200), 40);
    }

    private static double meanTicks(PipeSim sim, int scenes) {
        long total = 0;
        int[] ticks = new int[1];
        for (int s = 0; s < scenes; s++) {
            scene(sim, ticks);
            total += ticks[0];
            sim.reset();
        }
        return total / (double) scenes;
    }

    @Test
    public void aSceneUsesAboutTwoFifthsOfTheLattice() {
        PipeSim sim = new PipeSim(15, 8, 15, new Random(9), params(true));
        double sum = 0;
        for (int s = 0; s < 200; s++) {
            scene(sim, null);
            sum += sim.usedFraction();
            sim.reset();
        }
        assertEquals(0.38, sum / 200, 0.04);
    }

    @Test
    public void everyPipeGetsOneOfTheSixteenMaterialsAndAllTurnUp() {
        PipeSim sim = new PipeSim(15, 8, 15, new Random(4), params(true));
        Set<Integer> materials = new HashSet<>();
        for (int s = 0; s < 200; s++) {
            for (PipeSim.Piece p : scene(sim, null)) {
                assertTrue(p.material >= 0 && p.material < Materials.COUNT);
                materials.add(p.material);
            }
            sim.reset();
        }
        assertEquals(Materials.COUNT, materials.size());
    }

    @Test
    public void multiplePipesModeStartsSevenPipesAndSingleModeFive() {
        assertEquals(7, startsPerScene(true));
        assertEquals(5, startsPerScene(false));
    }

    /** The most pipes started in any of 100 scenes, counted by their start caps. */
    private static int startsPerScene(boolean multiple) {
        PipeSim.Params p = params(multiple);
        p.joints = PipeSim.JointMode.ELBOW;
        p.teapotChance = 0f;
        PipeSim sim = new PipeSim(15, 8, 15, new Random(12), p);
        int most = 0;
        for (int s = 0; s < 100; s++) {
            int balls = 0;
            for (PipeSim.Piece piece : scene(sim, null)) if (piece.type == PipeSim.Type.BALL) balls++;
            // With elbows only, every ball is a start cap or an end cap: two per pipe.
            most = Math.max(most, balls / 2);
            sim.reset();
        }
        return most;
    }

    @Test
    public void elbowOnlyAndBallOnlyModesMakeOnlyThatJoint() {
        PipeSim.Params p = params(true);
        p.joints = PipeSim.JointMode.ELBOW;
        int balls = 0, elbows = 0;
        for (PipeSim.Piece piece : scene(new PipeSim(15, 8, 15, new Random(6), p), null)) {
            if (piece.type == PipeSim.Type.ELBOW) elbows++;
            if (piece.type == PipeSim.Type.TEAPOT) fail("no teapots outside mixed mode");
        }
        assertTrue(elbows > 0);
        p.joints = PipeSim.JointMode.BALL;
        for (PipeSim.Piece piece : scene(new PipeSim(15, 8, 15, new Random(6), p), null)) {
            if (piece.type == PipeSim.Type.ELBOW) fail("no elbows in ball mode");
            if (piece.type == PipeSim.Type.BALL) balls++;
        }
        assertTrue(balls > 0);
    }

    @Test
    public void mixedModeMakesTeapotsWhenAskedTo() {
        PipeSim.Params p = params(true);
        p.joints = PipeSim.JointMode.MIXED;
        p.teapotChance = 1f;
        boolean teapot = false;
        for (PipeSim.Piece piece : scene(new PipeSim(15, 8, 15, new Random(8), p), null)) teapot |= piece.type == PipeSim.Type.TEAPOT;
        assertTrue(teapot);
    }

    @Test
    public void aBallIsOneAndAHalfPipesAcrossAsInTheOriginal() {
        assertEquals(1.5307f, PipeSim.BALL_SCALE, 1e-4f);
    }

    @Test
    public void anElbowStartsAndEndsOneBendRadiusFromTheNode() {
        float[] g = PipeSim.elbowGeometry(0f, 0f, 0f, 1, 0, 0, 0, 1, 0, R);
        // Center, start and end, for a pipe heading +X that turns to +Y.
        assertArrayEquals(new float[] {-R, R, 0f}, new float[] {g[0], g[1], g[2]}, 1e-6f);
        assertArrayEquals(new float[] {-R, 0f, 0f}, new float[] {g[9], g[10], g[11]}, 1e-6f);
        assertArrayEquals(new float[] {0f, R, 0f}, new float[] {g[12], g[13], g[14]}, 1e-6f);
        // Both ends sit exactly one bend radius from the arc's center.
        assertEquals(R, (float) Math.hypot(g[9] - g[0], g[10] - g[1]), 1e-6f);
        assertEquals(R, (float) Math.hypot(g[12] - g[0], g[13] - g[1]), 1e-6f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anEmptyLatticeIsRefused() {
        new PipeSim(0, 8, 15, new Random(1), params(true));
    }

    @Test
    public void aOneNodeLatticeEndsAtOnce() {
        PipeSim sim = new PipeSim(1, 1, 1, new Random(1), params(true));
        int[] ticks = new int[1];
        scene(sim, ticks);
        assertTrue(ticks[0] < 20);
    }
}
