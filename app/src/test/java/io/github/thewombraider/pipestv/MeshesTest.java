package io.github.thewombraider.pipestv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class MeshesTest {
    private static final int STRIDE = 6;

    private static void assertUnitNormals(float[] mesh) {
        for (int i = 0; i < mesh.length; i += STRIDE) {
            float n = (float) Math.sqrt(mesh[i + 3] * mesh[i + 3] + mesh[i + 4] * mesh[i + 4] + mesh[i + 5] * mesh[i + 5]);
            assertEquals("normal at vertex " + i / STRIDE, 1f, n, 1e-3f);
        }
    }

    private static float area(float[] m, int tri) {
        int a = tri * 3 * STRIDE, b = a + STRIDE, c = b + STRIDE;
        float ux = m[b] - m[a], uy = m[b + 1] - m[a + 1], uz = m[b + 2] - m[a + 2];
        float vx = m[c] - m[a], vy = m[c + 1] - m[a + 1], vz = m[c + 2] - m[a + 2];
        float cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return 0.5f * (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    @Test
    public void cylinderHasTheExpectedTrianglesWithAndWithoutCaps() {
        assertEquals(8 * 6 * STRIDE, Meshes.cylinder(8, false).length);
        assertEquals(8 * 12 * STRIDE, Meshes.cylinder(8, true).length);
    }

    /** Back faces are culled, so every triangle must wind counterclockwise seen from the side its normals face. */
    private static void assertFacesOutward(String name, float[] m) {
        int flipped = 0, total = m.length / (3 * STRIDE);
        for (int t = 0; t < total; t++) {
            int a = t * 3 * STRIDE, b = a + STRIDE, c = b + STRIDE;
            float ux = m[b] - m[a], uy = m[b + 1] - m[a + 1], uz = m[b + 2] - m[a + 2];
            float vx = m[c] - m[a], vy = m[c + 1] - m[a + 1], vz = m[c + 2] - m[a + 2];
            float cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
            if (cx * cx + cy * cy + cz * cz < 1e-14f) continue; // degenerate: draws nothing either way
            float nx = m[a + 3] + m[b + 3] + m[c + 3], ny = m[a + 4] + m[b + 4] + m[c + 4], nz = m[a + 5] + m[b + 5] + m[c + 5];
            if (cx * nx + cy * ny + cz * nz <= 0f) flipped++;
        }
        assertEquals(name + ": triangles wound against their normals", 0, flipped);
    }

    @Test
    public void everyShapeFacesOutward() throws IOException {
        assertFacesOutward("cylinder", Meshes.cylinder(8, true));
        assertFacesOutward("sphere", Meshes.sphere(8, 8));
        assertFacesOutward("elbow", Meshes.torusQuarter(1f / 7f, 1f / 7f, 4, 8));
        try (InputStream in = new FileInputStream("src/main/assets/teapot.bin")) {
            assertFacesOutward("teapot", Meshes.teapot(in));
        }
    }

    @Test
    public void cylinderSitsOnUnitRadiusBetweenZeroAndOne() {
        float[] m = Meshes.cylinder(16, true);
        assertUnitNormals(m);
        for (int i = 0; i < m.length; i += STRIDE) {
            assertTrue(m[i + 2] >= -1e-6f && m[i + 2] <= 1f + 1e-6f);
            assertTrue(Math.hypot(m[i], m[i + 1]) <= 1f + 1e-5f);
        }
    }

    @Test
    public void sphereIsUnitRadiusWithNoDegenerateTriangles() {
        float[] m = Meshes.sphere(8, 8);
        assertUnitNormals(m);
        for (int i = 0; i < m.length; i += STRIDE) {
            float r = (float) Math.sqrt(m[i] * m[i] + m[i + 1] * m[i + 1] + m[i + 2] * m[i + 2]);
            assertEquals(1f, r, 1e-5f);
            // On a sphere centered at the origin the normal is the position.
            assertEquals(m[i], m[i + 3], 1e-5f);
        }
        for (int t = 0; t < m.length / (3 * STRIDE); t++) assertTrue("triangle " + t + " is degenerate", area(m, t) > 1e-8f);
    }

    @Test
    public void torusQuarterFollowsAQuarterCircleOfTheGivenRadius() {
        // The original's elbow: the bend is as tight as the pipe, so the inside of the bend is a crease.
        float major = 1f / 7f, tube = 1f / 7f;
        float[] m = Meshes.torusQuarter(major, tube, 4, 8);
        assertUnitNormals(m);
        for (int i = 0; i < m.length; i += STRIDE) {
            // Step back along the normal by the tube radius to land on the tube's center line.
            float cx = m[i] - tube * m[i + 3], cy = m[i + 1] - tube * m[i + 4], cz = m[i + 2] - tube * m[i + 5];
            assertEquals("center line stays in the XY plane", 0f, cz, 1e-5f);
            assertEquals("center line is a circle of the major radius", major, (float) Math.hypot(cx, cy), 1e-5f);
            assertTrue("and only the first quarter of it", cx >= -1e-5f && cy >= -1e-5f);
        }
    }

    @Test
    public void theTeapotAssetIsTheUtahTeapotFittedBetweenMinusOneAndOne() throws IOException {
        float[] m;
        try (InputStream in = new FileInputStream("src/main/assets/teapot.bin")) {
            m = Meshes.teapot(in);
        }
        assertEquals("4032 triangles at 8 segments per patch", 4032 * 3 * STRIDE, m.length);
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < m.length; i += STRIDE) {
            minY = Math.min(minY, m[i + 1]);
            maxY = Math.max(maxY, m[i + 1]);
            assertTrue(Float.isFinite(m[i]) && Float.isFinite(m[i + 1]) && Float.isFinite(m[i + 2]));
        }
        assertEquals(-1f, minY, 1e-3f);
        assertEquals(1f, maxY, 1e-3f);
        // A whole teapot, closed and facing out: the spout on one side, the handle on the other, and a positive
        // enclosed volume. (A sign lost while reading the source data once folded it all into one quarter.)
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        double volume = 0;
        for (int i = 0; i < m.length; i += STRIDE) {
            minX = Math.min(minX, m[i]);
            maxX = Math.max(maxX, m[i]);
        }
        for (int t = 0; t < m.length / (3 * STRIDE); t++) {
            int a = t * 3 * STRIDE, b = a + STRIDE, c = b + STRIDE;
            volume += (m[a] * (m[b + 1] * m[c + 2] - m[b + 2] * m[c + 1])
                    - m[a + 1] * (m[b] * m[c + 2] - m[b + 2] * m[c])
                    + m[a + 2] * (m[b] * m[c + 1] - m[b + 1] * m[c])) / 6.0;
        }
        assertTrue("handle reaches -x: " + minX, minX < -1.8f);
        assertTrue("spout reaches +x: " + maxX, maxX > 2.1f);
        assertTrue("encloses its volume facing out: " + volume, volume > 6.0);
    }

    /** The ring of tube-surface points at the start of an elbow, as (cos, sin) pairs relative to the tube center. */
    private static java.util.Set<String> elbowStartRing(float major, float tube, int tubeSegments) {
        float[] m = Meshes.torusQuarter(major, tube, 8, tubeSegments);
        java.util.Set<String> ring = new java.util.TreeSet<>();
        for (int i = 0; i < m.length; i += STRIDE) {
            if (Math.abs(m[i + 1]) > 1e-6f) continue; // the start ring lies in the plane y = 0
            ring.add(key((m[i] - major) / tube, m[i + 2] / tube));
        }
        return ring;
    }

    private static java.util.Set<String> cylinderRing(int segments) {
        float[] m = Meshes.cylinder(segments, false);
        java.util.Set<String> ring = new java.util.TreeSet<>();
        for (int i = 0; i < m.length; i += STRIDE) {
            if (Math.abs(m[i + 2]) > 1e-6f) continue; // the near ring sits at z = 0
            ring.add(key(m[i], m[i + 1]));
        }
        return ring;
    }

    private static String key(float a, float b) {
        return String.format(java.util.Locale.US, "%.4f,%.4f", Math.round(a * 1e4f) / 1e4f + 0f, Math.round(b * 1e4f) / 1e4f + 0f);
    }

    @Test
    public void anElbowMeetsItsStraightPipeRimVertexForVertex() {
        // Straight pipes and elbows are all aligned to the lattice axes, so any quarter-turn of the rim is the same
        // rim. They only join without a step if both are cut into the same number of sides.
        assertEquals("8-sided elbow meets an 8-sided pipe", cylinderRing(8), elbowStartRing(1f / 7f, 1f / 7f, 8));
        assertTrue("a 12-sided elbow does NOT match an 8-sided pipe",
                !cylinderRing(8).equals(elbowStartRing(1f / 7f, 1f / 7f, 12)));
    }

    @Test
    public void bothEndsOfAnElbowAreTheSameRim() {
        float major = 1f / 7f, tube = 1f / 7f;
        float[] m = Meshes.torusQuarter(major, tube, 4, 8);
        java.util.Set<String> start = new java.util.TreeSet<>(), end = new java.util.TreeSet<>();
        for (int i = 0; i < m.length; i += STRIDE) {
            if (Math.abs(m[i + 1]) < 1e-6f) start.add(key((m[i] - major) / tube, m[i + 2] / tube));
            if (Math.abs(m[i]) < 1e-6f) end.add(key((m[i + 1] - major) / tube, m[i + 2] / tube));
        }
        assertEquals(start, end);
    }
}
