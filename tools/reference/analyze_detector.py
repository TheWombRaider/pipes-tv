"""The calibrated image statistic for the ball share: run the automatic detector over renders of known share and over
the footage, fit a line through the renders, and read the footage's share off it.

For each frame the statistic is the density of bulges (ball_detector.analyze: bulges per pipe radius of visible
length). Renders come from the app with a known share of ball joints, logged by the app; the line is
density = a + b * share, fitted over every render frame. A footage frame's share is (density - a) / b, and the
footage's share is read from its mean density, with a 95 % interval from a bootstrap that resamples footage frames
and render frames separately, so the calibration's uncertainty is included.

Usage: python -I tools/reference/analyze_detector.py <cache.json> <renders: name=dir,truth.txt ...> -- <footage: name=dir ...>
  e.g. render=shots/calib,shots/calib/truth.txt  ...  -- tCpn_0=final/tCpn_0 ...
Detector results are cached in <cache.json> per image path, so a rerun only does new frames.
"""
import json
import os
import random
import re
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ball_detector  # noqa: E402


def measure(path, cache):
    """(bulge density, big bulges) for one image, cached."""
    if path not in cache:
        n, big, length = ball_detector.analyze(cv2.imread(path))
        cache[path] = [n, big, length]
    n, big, length = cache[path]
    return n / max(length, 1.0), big


def density(path, cache):
    return measure(path, cache)[0]


def read_truth(path):
    """{render name: (ball share of turns, teapots in the scene)} from the app's own scene-over log lines."""
    out = {}
    for line in open(path, encoding="utf-8"):
        m = re.search(r"balls=(\d+) elbows=(\d+) teapotTurns=(\d+) teapots=(\d+)", line)
        if m:
            b, e, _, t = (int(v) for v in m.groups())
            out[line.split()[0]] = (b / (b + e), t)
    return out


def fit(xs, ys):
    b, a = np.polyfit(xs, ys, 1)
    return a, b


def main():
    cache_path = sys.argv[1]
    cache = json.load(open(cache_path)) if os.path.exists(cache_path) else {}
    split = sys.argv.index("--")
    renders, footage = sys.argv[2:split], sys.argv[split + 1:]
    r_share, r_dens, r_tea, r_big = [], [], [], []
    for spec in renders:
        _, rest = spec.split("=", 1)
        folder, truth = rest.split(",")
        for name, (share, teapots) in read_truth(truth).items():
            img = os.path.join(folder, name + ".png")
            if os.path.exists(img):
                d, big = measure(img, cache)
                r_share.append(share)
                r_dens.append(d)
                r_tea.append(teapots)
                r_big.append(big)
    json.dump(cache, open(cache_path, "w"))
    r_share, r_dens, r_tea, r_big = np.array(r_share), np.array(r_dens), np.array(r_tea), np.array(r_big)
    ta, tb = fit(r_tea, r_big)
    print(f"teapot calibration on {len(r_tea)} render frames holding {r_tea.sum()} teapots: big bulges = {ta:.2f} + {tb:.2f} x teapots; correlation {np.corrcoef(r_tea, r_big)[0, 1]:.2f}")
    a, b = fit(r_share, r_dens)
    print(f"calibration on {len(r_share)} render frames: density = {a:.4f} + {b:.4f} x share; correlation {np.corrcoef(r_share, r_dens)[0, 1]:.2f}")
    for level in sorted({round(x * 12) / 12 for x in r_share}):
        sel = np.abs(r_share - level) < 1 / 24
        if sel.any():
            print(f"  true share near {level:.2f}: mean density {r_dens[sel].mean():.4f} over {sel.sum()} frames")
    rng = random.Random(5)
    groups = {}
    for spec in footage:
        name, folder = spec.split("=", 1)
        vals = [measure(os.path.join(folder, f), cache) for f in sorted(os.listdir(folder)) if f.endswith(".png")]
        groups[name] = np.array(vals)
        json.dump(cache, open(cache_path, "w"))
    groups["all footage together"] = np.concatenate(list(groups.values()))
    # the cache stores per image; groups hold (density, big) rows
    for name, dd in groups.items():
        d, bigs = dd[:, 0], dd[:, 1]
        teapots_per_scene = (bigs.mean() - ta) / tb if tb > 0 else float("nan")
        est = (d.mean() - a) / b
        boots = []
        for _ in range(2000):
            rs = np.array([rng.randrange(len(r_share)) for _ in r_share])
            if np.ptp(r_share[rs]) < 0.3:
                continue
            ra, rb = fit(r_share[rs], r_dens[rs])
            fs = d[[rng.randrange(len(d)) for _ in d]]
            boots.append((fs.mean() - ra) / rb)
        boots.sort()
        lo, hi = boots[int(0.025 * len(boots))], boots[int(0.975 * len(boots))]
        print(f"{name:>22}: {len(d):3d} frames, mean density {d.mean():.4f} -> ball share {est:.3f} [{lo:.3f}, {hi:.3f}]; "
              f"big bulges {bigs.mean():.2f} a frame -> {teapots_per_scene:.2f} teapots a scene")


if __name__ == "__main__":
    main()
