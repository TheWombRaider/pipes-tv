"""Ranks candidate screenshots so the best few can be judged by eye.

A good showcase frame has: several distinct colors, a lot of the picture covered but not all of it, little
clipped at the edges (whole clusters, not giant cropped pipes), and a centered composition.

Usage: python -I tools/score_frames.py <folder-of-pngs> [top-n]
"""
import colorsys
import glob
import os
import sys

import numpy as np
from PIL import Image


def score(path):
    im = Image.open(path).convert("RGB").resize((320, 180))
    a = np.asarray(im).astype(float) / 255.0
    lum = a.sum(axis=2)
    lit = lum > 0.25
    coverage = float(lit.mean())
    if coverage < 0.02:
        return 0.0, {}
    # Distinct hues among the lit pixels: bins of 30 degrees that hold at least 1.5 % of them.
    flat = a[lit]
    hues = np.array([colorsys.rgb_to_hsv(*p)[0] for p in flat[:: max(1, len(flat) // 4000)]])
    sats = np.array([colorsys.rgb_to_hsv(*p)[1] for p in flat[:: max(1, len(flat) // 4000)]])
    chromatic = hues[sats > 0.25]
    bins = np.bincount((chromatic * 12).astype(int) % 12, minlength=12) / max(1, len(chromatic))
    colors = int((bins > 0.06).sum())
    # How much of the lit area touches the outer 5 % border.
    h, w = lit.shape
    bh, bw = int(h * 0.05), int(w * 0.05)
    border = lit.copy()
    border[bh:h - bh, bw:w - bw] = False
    clipped = float(border.sum()) / max(1, lit.sum())
    # Center of mass distance from the middle, as a fraction of the half-diagonal.
    ys, xs = np.nonzero(lit)
    off = float(np.hypot(xs.mean() / w - 0.5, ys.mean() / h - 0.5))
    cover_term = 1.0 - min(1.0, abs(coverage - 0.32) / 0.32)
    total = colors * 1.0 + cover_term * 3.0 + (1.0 - min(1.0, clipped / 0.25)) * 3.0 + (1.0 - min(1.0, off / 0.25)) * 1.5
    return total, {"colors": colors, "coverage": round(coverage, 2), "clipped": round(clipped, 2), "off": round(off, 2)}


folder = sys.argv[1]
top = int(sys.argv[2]) if len(sys.argv) > 2 else 6
rows = []
for p in sorted(glob.glob(os.path.join(folder, "*.png"))):
    s, info = score(p)
    rows.append((s, os.path.basename(p), info))
for s, name, info in sorted(rows, reverse=True)[:top]:
    print(f"{s:5.2f} {name} {info}")
