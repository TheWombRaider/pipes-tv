"""How many different pipe colors a finished scene shows, and what each color looks like, in footage and renders.

Every lit pixel (peak channel 0.25 to 0.95) is matched by chromaticity, (r, g, b) / (r + g + b), to the nearest of
the app's 16 materials' mid-tones (1.1 x ambient + 0.7 x diffuse), within a squared distance of 0.004, as in
docs/reference/colors.txt. Materials that chromaticity cannot tell apart are merged into one group first (single
link below MERGE). A group is present in a frame if it holds over SHARE of the matched pixels.

Usage: python -I tools/reference/color_variety.py <label>=<folder-of-pngs> [<label>=<folder> ...]
Prints, per label: frames, mean distinct groups per frame (bootstrap 95 % interval), the distribution, and per group
the share of frames it appears in and its mean saturation and brightness (HSV) where present.
"""
import colorsys
import glob
import os
import random
import re
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, "..", "..", "app", "src", "main", "java", "io", "github", "thewombraider", "pipestv", "Materials.java")
MERGE = 0.003
SHARE = 0.08
TOL = 0.004


def materials():
    src = open(JAVA, encoding="utf-8").read()
    names = re.findall(r'"([a-z ]+)"', src[src.index("NAMES"):src.index("};", src.index("NAMES"))])
    block = src[src.index("DATA = {"):]
    rows = [[float(x) for x in re.findall(r"(\d*\.\d+|\d+)f", line)] for line in block.splitlines() if line.strip().startswith("{")]
    rows = [r for r in rows if len(r) == 10][:len(names)]
    mid = np.array([[1.1 * r[i] + 0.7 * r[3 + i] for i in range(3)] for r in rows])
    return names, mid


def groups(names, mid):
    ch = mid / mid.sum(1, keepdims=True)
    d = ((ch[:, None] - ch[None]) ** 2).sum(2)
    label = list(range(len(names)))
    for i in range(len(names)):
        for j in range(len(names)):
            if d[i, j] < MERGE and label[i] != label[j]:
                old, new = label[j], label[i]
                label = [new if x == old else x for x in label]
    out = {}
    for i, g in enumerate(label):
        out.setdefault(g, []).append(i)
    return list(out.values()), ch


def analyze(path, ch, member):
    a = np.asarray(Image.open(path).convert("RGB").resize((480, 270))).reshape(-1, 3).astype(float) / 255.0
    peak = a.max(1)
    a = a[(peak > 0.25) & (peak < 0.95)]
    if len(a) == 0:
        return set(), {}
    c = a / a.sum(1, keepdims=True)
    d = ((c[:, None] - ch[None]) ** 2).sum(2)
    near = d.argmin(1)
    ok = d[np.arange(len(near)), near] < TOL
    g = member[near[ok]]
    px = a[ok]
    counts = np.bincount(g, minlength=member.max() + 1)
    present = {int(k) for k in np.nonzero(counts > SHARE * max(1, ok.sum()))[0]}
    looks = {}
    for k in present:
        hsv = np.array([colorsys.rgb_to_hsv(*p) for p in px[g == k][::7]])
        looks[k] = (float(hsv[:, 1].mean()), float(hsv[:, 2].mean()))
    return present, looks


def main():
    names, mid = materials()
    grp, ch = groups(names, mid)
    member = np.zeros(len(names), int)
    for k, ms in enumerate(grp):
        member[ms] = k
    gname = ["/".join(names[m] for m in ms) for ms in grp]
    print(f"{len(grp)} color groups:", "; ".join(gname))
    rng = random.Random(1)
    for arg in sys.argv[1:]:
        label, _, folder = arg.partition("=")
        paths = sorted(p for f in folder.split(",") for p in glob.glob(os.path.join(f, "*.png")))
        counts, seen, look = [], {}, {}
        for p in paths:
            present, looks = analyze(p, ch, member)
            counts.append(len(present))
            for k, v in looks.items():
                seen[k] = seen.get(k, 0) + 1
                look.setdefault(k, []).append(v)
        if not counts:
            print(label, "no frames")
            continue
        boots = sorted(np.mean([rng.choice(counts) for _ in counts]) for _ in range(2000))
        dist = np.bincount(counts, minlength=8)[:8]
        print(f"\n{label}: {len(counts)} frames, distinct groups per frame {np.mean(counts):.2f} "
              f"(95 % {boots[50]:.2f} to {boots[1949]:.2f}); frames with 0..7 groups: {list(dist)}")
        for k in sorted(seen, key=lambda k: -seen[k]):
            s = np.mean([v[0] for v in look[k]]); v = np.mean([v[1] for v in look[k]])
            print(f"  {gname[k]:45s} in {seen[k] / len(counts):5.0%} of frames, saturation {s:.2f}, brightness {v:.2f}")


if __name__ == "__main__":
    main()
