package io.github.thewombraider.pipestv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class PipeSimTest {
    /** Records every node a pipe enters, per pipe, and every reset. */
    private static final class Recorder implements PipeSim.Listener {
        final Map<Integer, List<int[]>> paths = new HashMap<>();
        int resets;

        @Override
        public void nodeEntered(int pipe, int x, int y, int z) {
            paths.computeIfAbsent(pipe, k -> new ArrayList<>()).add(new int[] {x, y, z});
        }

        @Override
        public void reset() {
            resets++;
            paths.clear();
        }
    }

    private static PipeSim.Params params() {
        PipeSim.Params p = new PipeSim.Params();
        p.maxPipes = 3;
        p.spawnMinSeconds = 0.1f;
        p.spawnMaxSeconds = 0.3f;
        return p;
    }

    private static void run(PipeSim sim, float seconds) {
        for (float t = 0; t < seconds; t += 1f / 60f) sim.update(1f / 60f);
    }

    @Test
    public void pipesMoveOneNodeAtATimeAndNeverReverse() {
        Recorder rec = new Recorder();
        PipeSim sim = new PipeSim(14, 8, 8, new Random(7), params(), rec);
        run(sim, 20f);
        assertFalse(rec.paths.isEmpty());
        for (List<int[]> path : rec.paths.values()) {
            for (int i = 1; i < path.size(); i++) {
                int[] a = path.get(i - 1);
                int[] b = path.get(i);
                int dist = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
                assertEquals("each step is exactly one lattice unit", 1, dist);
                if (i >= 2) {
                    int[] c = path.get(i - 2);
                    boolean reversed = c[0] == b[0] && c[1] == b[1] && c[2] == b[2];
                    assertFalse("a pipe never doubles back on itself", reversed);
                }
            }
        }
    }

    @Test
    public void noNodeIsUsedTwiceWithinOneScene() {
        Recorder rec = new Recorder();
        PipeSim sim = new PipeSim(14, 8, 8, new Random(11), params(), rec);
        for (int frame = 0; frame < 60 * 20; frame++) {
            sim.update(1f / 60f);
            Set<String> seen = new HashSet<>();
            for (List<int[]> path : rec.paths.values()) {
                for (int[] n : path) {
                    assertTrue("node " + n[0] + "," + n[1] + "," + n[2] + " used twice", seen.add(n[0] + "," + n[1] + "," + n[2]));
                }
            }
        }
    }

    @Test
    public void everyNodeStaysInsideTheLattice() {
        Recorder rec = new Recorder();
        PipeSim sim = new PipeSim(10, 6, 6, new Random(3), params(), rec);
        run(sim, 30f);
        for (List<int[]> path : rec.paths.values()) {
            for (int[] n : path) {
                assertTrue(n[0] >= 0 && n[0] < 10);
                assertTrue(n[1] >= 0 && n[1] < 6);
                assertTrue(n[2] >= 0 && n[2] < 6);
            }
        }
    }

    @Test
    public void theSceneClearsItselfOnceItFillsUp() {
        Recorder rec = new Recorder();
        PipeSim.Params p = params();
        p.resetFill = 0.2f;
        PipeSim sim = new PipeSim(8, 5, 5, new Random(5), p, rec);
        run(sim, 120f);
        assertTrue("scene should have been cleared at least once", rec.resets >= 1);
        assertTrue("occupancy stays at or below the reset threshold plus one pipe's slack", sim.occupiedFraction() < 0.5f);
    }

    @Test
    public void sameSeedGivesTheSameScene() {
        PipeSim a = new PipeSim(14, 8, 8, new Random(42), params(), null);
        PipeSim b = new PipeSim(14, 8, 8, new Random(42), params(), null);
        run(a, 15f);
        run(b, 15f);
        assertEquals(a.pieces().size(), b.pieces().size());
        for (int i = 0; i < a.pieces().size(); i++) {
            PipeSim.Piece pa = a.pieces().get(i);
            PipeSim.Piece pb = b.pieces().get(i);
            assertEquals(pa.type, pb.type);
            assertEquals(pa.x, pb.x, 1e-5f);
            assertEquals(pa.length, pb.length, 1e-5f);
        }
    }

    @Test
    public void straightRunsHaveNonNegativeLengthAndUnitDirections() {
        PipeSim sim = new PipeSim(14, 8, 8, new Random(9), params(), null);
        run(sim, 25f);
        int runs = 0;
        for (PipeSim.Piece p : sim.pieces()) {
            if (p.type != PipeSim.Type.RUN) continue;
            runs++;
            assertTrue("run length " + p.length, p.length >= 0f);
            float norm = p.ax * p.ax + p.ay * p.ay + p.az * p.az;
            assertEquals(1f, norm, 1e-6f);
        }
        assertTrue(runs > 0);
    }

    @Test
    public void elbowArcJoinsTheTwoStraightRunsTangentially() {
        // A pipe heading +X turns to +Y at the node (5,0,0) with bend radius 0.4.
        float[] arc = PipeSim.elbowGeometry(5f, 0f, 0f, 1, 0, 0, 0, 1, 0, 0.4f);
        // arc = {cx,cy,cz, ux,uy,uz, vx,vy,vz, ax,ay,az (start), bx,by,bz (end)}
        float cx = arc[0], cy = arc[1];
        // The first run must stop 0.4 before the node, the second must start 0.4 past it.
        assertEquals(4.6f, arc[9], 1e-5f);
        assertEquals(0f, arc[10], 1e-5f);
        assertEquals(5f, arc[12], 1e-5f);
        assertEquals(0.4f, arc[13], 1e-5f);
        // Both ends sit exactly one bend radius from the arc centre.
        assertEquals(0.4f, (float) Math.hypot(arc[9] - cx, arc[10] - cy), 1e-5f);
        assertEquals(0.4f, (float) Math.hypot(arc[12] - cx, arc[13] - cy), 1e-5f);
        // The start offset from the centre is -d2 * radius and the end offset is +d1 * radius.
        assertEquals(0f, arc[3] - (-0f), 1e-5f);
        assertEquals(-1f, arc[4], 1e-5f);
        assertEquals(1f, arc[6], 1e-5f);
    }

    @Test
    public void everyPipeEndsWithABallCapOnceItIsBlocked() {
        PipeSim.Params p = params();
        p.maxPipes = 1;
        p.resetFill = 0.9f;
        PipeSim sim = new PipeSim(4, 3, 3, new Random(2), p, null);
        run(sim, 60f);
        int balls = 0;
        for (PipeSim.Piece piece : sim.pieces()) {
            if (piece.type == PipeSim.Type.BALL) balls++;
        }
        assertTrue("start and end caps are balls", balls >= 2);
    }

    @Test
    public void finishedPiecesAreHandedOverOnceAndNeverChangeAgain() {
        PipeSim sim = new PipeSim(21, 21, 21, new Random(21), params(), null);
        List<PipeSim.Piece> handed = new ArrayList<>();
        List<Float> lengths = new ArrayList<>();
        for (int frame = 0; frame < 60 * 12; frame++) {
            sim.update(1f / 60f);
            int before = handed.size();
            sim.drainFinished(handed);
            for (int i = before; i < handed.size(); i++) lengths.add(handed.get(i).length);
        }
        assertFalse(handed.isEmpty());
        Set<PipeSim.Piece> unique = new HashSet<>(handed);
        assertEquals("no piece is handed over twice", handed.size(), unique.size());
        for (int i = 0; i < handed.size(); i++) {
            assertEquals("a handed-over piece must not grow any more", lengths.get(i), handed.get(i).length, 0f);
        }
        List<PipeSim.Piece> again = new ArrayList<>();
        sim.drainFinished(again);
        assertTrue("draining twice in a row yields nothing new", again.isEmpty());
    }

    @Test
    public void clearingStartsANewGeneration() {
        PipeSim sim = new PipeSim(10, 10, 10, new Random(1), params(), null);
        run(sim, 2f);
        int before = sim.generation();
        sim.clear();
        assertEquals(before + 1, sim.generation());
        assertEquals(0f, sim.occupiedFraction(), 0f);
        assertTrue(sim.pieces().isEmpty());
    }

    @Test
    public void aSceneStartsWithOnePipeAndOthersJoinGradually() {
        // The reference footage: one pipe at first, about one more every 7 seconds, three or four by the end.
        PipeSim.Params p = new PipeSim.Params();
        Recorder rec = new Recorder();
        PipeSim sim = new PipeSim(15, 15, 15, new Random(8), p, rec);
        run(sim, 3f);
        assertEquals("only the first pipe in the opening seconds", 1, rec.paths.size());
        run(sim, 17f);
        int atTwenty = rec.paths.size();
        assertTrue("two to four pipes by 20 seconds, got " + atTwenty, atTwenty >= 2 && atTwenty <= 5);
    }

    @Test
    public void everyPipeGetsItsOwnRandomRgbColour() {
        PipeSim.Params p = params();
        PipeSim sim = new PipeSim(15, 15, 15, new Random(4), p, null);
        run(sim, 30f);
        Set<String> colours = new HashSet<>();
        for (PipeSim.Piece piece : sim.pieces()) {
            assertTrue(piece.r >= 0f && piece.r <= 1f && piece.g >= 0f && piece.g <= 1f && piece.b >= 0f && piece.b <= 1f);
            colours.add(piece.r + "," + piece.g + "," + piece.b);
        }
        assertTrue("pipes should differ in colour, got " + colours.size(), colours.size() >= 3);
    }

    @Test
    public void aGivenPaletteIsUsedWhenOneIsSet() {
        PipeSim.Params p = params();
        p.palette = new float[][] {{1f, 0f, 0f}, {0f, 0f, 1f}};
        PipeSim sim = new PipeSim(15, 15, 15, new Random(4), p, null);
        run(sim, 20f);
        for (PipeSim.Piece piece : sim.pieces()) {
            boolean red = piece.r == 1f && piece.g == 0f && piece.b == 0f;
            boolean blue = piece.r == 0f && piece.g == 0f && piece.b == 1f;
            assertTrue("colour outside the palette", red || blue);
        }
    }

    @Test
    public void growthRunsAtTheConfiguredSpeed() {
        PipeSim.Params p = params();
        p.maxPipes = 1;
        p.speed = 10f;
        Recorder rec = new Recorder();
        PipeSim sim = new PipeSim(15, 15, 15, new Random(6), p, rec);
        run(sim, 3f);
        int nodes = rec.paths.values().iterator().next().size();
        // Ten units a second for three seconds is about thirty steps (plus the starting node).
        assertTrue("nodes entered " + nodes, nodes >= 27 && nodes <= 33);
    }

    @Test
    public void anImpossibleLatticeIsRefusedNotHalfBuilt() {
        for (int[] bad : new int[][] {{0, 5, 5}, {5, 0, 5}, {5, 5, 0}, {-1, 5, 5}}) {
            try {
                new PipeSim(bad[0], bad[1], bad[2], new Random(1), params(), null);
                org.junit.Assert.fail("accepted a lattice of " + bad[0] + "x" + bad[1] + "x" + bad[2]);
            } catch (IllegalArgumentException expected) {
                // the message names the offending size
                assertTrue(expected.getMessage().contains(bad[0] + "x" + bad[1] + "x" + bad[2]));
            }
        }
    }

    @Test
    public void aTinyLatticeKeepsRunningAndRestartsInsteadOfStalling() {
        PipeSim sim = new PipeSim(2, 2, 2, new Random(3), params(), null);
        int before = sim.generation();
        run(sim, 30f);
        assertTrue("a full 2x2x2 lattice must keep clearing and restarting", sim.generation() > before);
    }
}
