package io.github.thewombraider.pipestv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class CameraPlacementTest {
    private static final int GRID = 15;
    private static final float BALL = 0.17f * 1.5f;

    private static float[] randomDirection(Random r) {
        float x, y, z, len;
        do {
            x = r.nextFloat() * 2 - 1;
            y = r.nextFloat() * 2 - 1;
            z = r.nextFloat() * 2 - 1;
            len = (float) Math.sqrt(x * x + y * y + z * z);
        } while (len < 0.2f);
        return new float[] {x / len, y / len, z / len};
    }

    /** The smallest distance, measured along the viewing direction, from the lens to any part of any pipe. */
    private static float nearestDepth(float[] d, float distance) {
        float half = (GRID - 1) / 2f;
        float nearest = Float.MAX_VALUE;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float along = sx * half * d[0] + sy * half * d[1] + sz * half * d[2];
                    nearest = Math.min(nearest, distance - (along + BALL));
                }
            }
        }
        return nearest;
    }

    @Test
    public void theCameraNeverEndsUpInsideTheLatticeWhateverTheView() {
        Random r = new Random(1);
        for (int i = 0; i < 50_000; i++) {
            float[] d = randomDirection(r);
            float requested = 9f + r.nextFloat() * 4f;
            float distance = CameraPlacement.distance(d[0], d[1], d[2], GRID, requested, BALL);
            // Depth is measured along the view direction, so this is the nearest any pipe can be to the lens.
            float corner = CameraPlacement.reachToward(d[0], d[1], d[2], GRID);
            float depth = distance - (corner + BALL);
            assertTrue("view " + d[0] + "," + d[1] + "," + d[2] + " leaves only " + depth + " to the lens",
                    depth >= CameraPlacement.NEAR_PLANE + CameraPlacement.CLEARANCE - 1e-4f);
        }
    }

    @Test
    public void theOldFixedDistanceReallyDidPutTheCameraInsideTheLatticeOnADiagonal() {
        // The bug this class exists for: a camera 9 units out, looking along a cube diagonal.
        float s = (float) (1 / Math.sqrt(3));
        float reach = CameraPlacement.reachToward(s, s, s, GRID);
        assertTrue("the corner of the lattice is " + reach + " from its middle, farther than the 9-unit camera",
                reach > 9f);
        assertTrue(nearestDepth(new float[] {s, s, s}, 9f) < 0f);
    }

    @Test
    public void aHeadOnViewKeepsTheRequestedDistanceWhenItIsAlreadySafe() {
        assertEquals(10f, CameraPlacement.distance(0f, 0f, 1f, GRID, 10f, BALL), 1e-6f);
        // Looking straight down an axis the lattice reaches only 7 units, so 9 is fine but 8 would be too close.
        assertTrue(CameraPlacement.distance(0f, 0f, 1f, GRID, 8f, BALL) > 8f);
    }

    @Test
    public void everyPipeStaysBeyondTheNearPlaneByTheClearance() {
        float[] d = {0.5774f, 0.5774f, 0.5774f};
        float distance = CameraPlacement.distance(d[0], d[1], d[2], GRID, 9f, BALL);
        assertTrue(nearestDepth(d, distance) >= CameraPlacement.NEAR_PLANE + CameraPlacement.CLEARANCE - 1e-3f);
    }
}
