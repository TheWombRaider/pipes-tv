"""An automatic count of ball-joint-like bulges in a frame of pipes, for the calibrated image statistic.

A ball joint is a sphere about 1.5 times as wide as the pipes that meet at it, so on the silhouette (non-black
pixels) it is a round bulge: the largest circle that fits inside it is clearly bigger than the largest circle that
fits inside the neighboring pipe. For every candidate centre (a ridge of the distance transform) the detector
compares its radius R with the radius of the pipe found in a ring at 1.7 to 2.3 R around it, and calls it a bulge if
R is at least RATIO times that. It also requires the outline to be round: rays cast from the centre must end on the
outline within 1.2 R in at least COVERAGE of all directions. A ball with two pipes attached has about 55 %; a plain
pipe has 38 %, and two pipes crossing at different depths only 16 %, so crossings are not counted. Bulges closer
together than 1.5 R are merged.

Pipe ends have a ball too, and pipes crossing at different depths can look like a bulge, so the count is not a
count of ball joints. It does not have to be: it is calibrated on renders of the app with a known ball share, and
the footage's share is read off that curve (analyze_detector.py).

The count is divided by the visible length of pipe, measured in pipe radii (the sum of 1/R over ridge pixels), so
that it does not depend on how much of the picture is covered.

Usage: python -I tools/reference/ball_detector.py <frame.png> ...   (prints bulges, length, density per frame)
"""
import sys

import cv2
import numpy as np

THRESH = 28
RATIO = 1.3
BIG_RATIO = 1.9
BIG_MIN_RADIUS = 6.0
COVERAGE = 0.45
RAYS = 72
RAY_ANGLES = np.linspace(0, 2 * np.pi, RAYS, endpoint=False)
RCOS, RSIN = np.cos(RAY_ANGLES), np.sin(RAY_ANGLES)
MIN_RADIUS = 4.0
RING = (1.7, 2.0, 2.3)
ANGLES = np.linspace(0, 2 * np.pi, 24, endpoint=False)
COS, SIN = np.cos(ANGLES), np.sin(ANGLES)


def round_outline(mask, x, y, r):
    """True if rays from (x, y) end on the outline within 1.2 r in at least COVERAGE of the directions."""
    h, w = mask.shape
    steps = np.arange(0.5, 1.2 * r + 0.5, 0.5)
    px = np.rint(x + RCOS[:, None] * steps[None, :]).astype(int)
    py = np.rint(y + RSIN[:, None] * steps[None, :]).astype(int)
    px = np.clip(px, 0, w - 1)
    py = np.clip(py, 0, h - 1)
    ends = (mask[py, px] == 0).any(axis=1)
    return ends.mean() >= COVERAGE


def analyze(frame_bgr, detail=False):
    """Returns (bulges, big_bulges, length_in_radii) for one frame; with detail=True big_bulges is the list of
    (radius, x, y, ratio) instead of its length.

    Bulges are round swellings 1.3 times a neighboring pipe's radius or more (ball joints). Big bulges are 1.9 times
    or more, and at least BIG_MIN_RADIUS pixels across; a teapot's body is about 2.5 pipe radii and shows up here.
    """
    mask = (frame_bgr.max(axis=2) > THRESH).astype(np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    dt = cv2.distanceTransform(mask, cv2.DIST_L2, 5)
    h, w = dt.shape
    local = cv2.dilate(dt, np.ones((3, 3), np.uint8))
    ridge = (dt >= local - 1e-6) & (dt >= 2.0)
    length = float((1.0 / dt[ridge]).sum())

    big = cv2.dilate(dt, np.ones((9, 9), np.uint8))
    ys, xs = np.nonzero(ridge & (dt >= MIN_RADIUS) & (dt >= big - 1e-6))
    found = []
    for y, x in zip(ys, xs):
        r = float(dt[y, x])
        best = 0.0
        for k in RING:
            px = np.rint(x + COS * k * r).astype(int)
            py = np.rint(y + SIN * k * r).astype(int)
            inside = (px >= 0) & (px < w) & (py >= 0) & (py < h)
            if inside.any():
                best = max(best, float(dt[py[inside], px[inside]].max()))
        # A real neighbor pipe must be there: something at that distance with a radius of its own.
        if best >= 2.0 and r >= RATIO * best and round_outline(mask, x, y, r):
            found.append((r, x, y, r / best))
    found.sort(reverse=True)
    kept = []
    for r, x, y, q in found:
        if all((x - kx) ** 2 + (y - ky) ** 2 > (1.5 * max(r, kr)) ** 2 for kr, kx, ky, _ in kept):
            kept.append((r, x, y, q))
    bigs = [(r, x, y, q) for r, x, y, q in kept if q >= BIG_RATIO and r >= BIG_MIN_RADIUS]
    if detail:
        return len(kept), bigs, length
    return len(kept), len(bigs), length


def main():
    for path in sys.argv[1:]:
        frame = cv2.imread(path)
        n, big, length = analyze(frame)
        print(f"{path}: bulges {n} (big {big}), length {length:.0f} radii, density {n / max(length, 1):.4f}")


if __name__ == "__main__":
    main()
