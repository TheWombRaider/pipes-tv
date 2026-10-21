package io.github.thewombraider.pipestv;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The original's fixed camera never ends up inside a pipe, whatever the scene's turn. */
public class CameraTest {
    @Test
    public void everyPipeStaysBeyondTheNearPlaneAtEveryTurn() {
        float ball = PipeSim.RADIUS * PipeSim.BALL_SCALE;
        for (int[] size : new int[][] {PipeSim.latticeFor(1920, 1080), PipeSim.latticeFor(1024, 768), PipeSim.latticeFor(1080, 1920)}) {
            float hx = (size[0] - 1) / 2f, hz = (size[2] - 1) / 2f;
            for (int i = 0; i < 3600; i++) {
                double a = Math.toRadians(i / 10.0);
                // How far the lattice reaches toward a camera turned this far around the vertical axis.
                float reach = (float) (hx * Math.abs(Math.sin(a)) + hz * Math.abs(Math.cos(a))) + ball;
                float depth = PipesRenderer.CAMERA_DISTANCE - reach;
                assertTrue("at " + i / 10.0 + " degrees the nearest pipe is " + depth + " from the lens",
                        depth > PipesRenderer.NEAR_PLANE + 0.2f);
            }
        }
    }
}
