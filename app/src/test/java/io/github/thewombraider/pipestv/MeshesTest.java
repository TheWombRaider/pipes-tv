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
        assertEquals(16 * 6 * STRIDE, Meshes.cylinder(16, false).length);
        assertEquals(16 * 12 * STRIDE, Meshes.cylinder(16, true).length);
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
        float[] m = Meshes.sphere(12, 18);
        assertUnitNormals(m);
        for (int i = 0; i < m.length; i += STRIDE) {
            float r = (float) Math.sqrt(m[i] * m[i] + m[i + 1] * m[i + 1] + m[i + 2] * m[i + 2]);
            assertEquals(1f, r, 1e-5f);
            // On a sphere centred at the origin the normal is the position.
            assertEquals(m[i], m[i + 3], 1e-5f);
        }
        for (int t = 0; t < m.length / (3 * STRIDE); t++) assertTrue("triangle " + t + " is degenerate", area(m, t) > 1e-8f);
    }

    @Test
    public void torusQuarterFollowsAQuarterCircleOfTheGivenRadius() {
        float major = 0.4f, tube = 0.17f;
        float[] m = Meshes.torusQuarter(major, tube, 6, 12);
        assertUnitNormals(m);
        for (int i = 0; i < m.length; i += STRIDE) {
            // Step back along the normal by the tube radius to land on the tube's centre line.
            float cx = m[i] - tube * m[i + 3], cy = m[i + 1] - tube * m[i + 4], cz = m[i + 2] - tube * m[i + 5];
            assertEquals("centre line stays in the XY plane", 0f, cz, 1e-5f);
            assertEquals("centre line is a circle of the major radius", major, (float) Math.hypot(cx, cy), 1e-5f);
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
    }
}
