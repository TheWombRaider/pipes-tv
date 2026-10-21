package io.github.thewombraider.pipestv;

/**
 * Where the camera may sit. It looks at the middle of the lattice from a random direction, and it must never end
 * up inside the lattice: a pipe that crosses the lens shows its hollow inside, which is what a camera placed too
 * close to a diagonal view of a cube does (the cube's corner is much farther from its middle than its face is).
 */
final class CameraPlacement {
    /** The near clipping plane of the projection. Geometry closer than this to the lens is cut away. */
    static final float NEAR_PLANE = 1f;

    /** Extra room kept between the nearest pipe surface and the near plane. */
    static final float CLEARANCE = 0.8f;

    private CameraPlacement() {}

    /**
     * How far the lattice reaches toward a camera looking along {@code (dx, dy, dz)}, a unit vector pointing from
     * the middle of the lattice to the camera: the largest value of (point . direction) over the lattice's corners.
     */
    static float reachToward(float dx, float dy, float dz, int grid) {
        float half = (grid - 1) / 2f;
        return half * (Math.abs(dx) + Math.abs(dy) + Math.abs(dz));
    }

    /**
     * The distance from the middle of the lattice to put the camera at: the one asked for, or farther if that
     * would put the camera in or too near the lattice. {@code surfaceReach} is how far pipe surfaces stick out
     * beyond a node (the ball joint radius).
     */
    static float distance(float dx, float dy, float dz, int grid, float requested, float surfaceReach) {
        float nearest = reachToward(dx, dy, dz, grid) + surfaceReach;
        return Math.max(requested, nearest + NEAR_PLANE + CLEARANCE);
    }
}
