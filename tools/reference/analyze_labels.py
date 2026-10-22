"""Turns the joint counts of blind counters into a ball share and a teapot rate, calibrated on the app's own renders.

Usage: python -I tools/reference/analyze_labels.py <labels-dir> <key.json> [--bootstrap 2000]
  <labels-dir> holds one folder per counter, each with batch-NN.jsonl files of {"id", "B", "E", "T"} lines.

For every counter it reports, per recording and per render level:
  - the labeled ball share: sum(B) / (sum(B) + sum(E)), with a 95 % bootstrap interval resampling whole frames
  - for renders, the true share the app was given (from its own log) beside what the counter saw
  - teapots counted per final frame, and for renders the true count beside it
Then it maps the footage's labeled share onto the true scale by interpolating between the render levels
(the calibration), and reports how well the counters agree crop by crop.
"""
import glob
import json
import os
import random
import statistics
import sys


def load_labels(folder):
    out = {}
    for path in sorted(glob.glob(os.path.join(folder, "*.jsonl"))):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if line:
                row = json.loads(line)
                out[row["id"]] = row
    return out


def per_frame(labels, key):
    """{(source, frame): [B, E, T, truth]} summed over the four quadrants of a frame."""
    frames = {}
    for cid, row in labels.items():
        k = key.get(cid)
        if not k:
            continue
        source = k["source"]
        if k["truth"] and source == "render":
            # Renders are grouped by the share of ball joints the app was told to use (0, 1/3 or 2/3).
            source = "render p=%.2f" % (round(k["truth"][0] / max(1, k["truth"][0] + k["truth"][1]) * 3) / 3)
        f = frames.setdefault((source, k["frame"]), [0, 0, 0, k["truth"]])
        f[0] += row.get("B", 0)
        f[1] += row.get("E", 0)
        f[2] += row.get("T", 0)
    return frames


def share(frames):
    b = sum(f[0] for f in frames)
    e = sum(f[1] for f in frames)
    return b / (b + e) if b + e else float("nan"), b + e


def boot(frames, rng, n):
    vals = []
    for _ in range(n):
        s = [rng.choice(frames) for _ in frames]
        vals.append(share(s)[0])
    vals.sort()
    return vals[int(0.025 * n)], vals[int(0.975 * n)]


def main():
    labels_dir, key_path = sys.argv[1], sys.argv[2]
    n_boot = int(sys.argv[sys.argv.index("--bootstrap") + 1]) if "--bootstrap" in sys.argv else 2000
    key = json.load(open(key_path))
    rng = random.Random(7)
    counters = sorted(d for d in os.listdir(labels_dir) if os.path.isdir(os.path.join(labels_dir, d)))
    loaded = {c: load_labels(os.path.join(labels_dir, c)) for c in counters}
    for c in counters:
        print(f"\n=== counter {c}: {len(loaded[c])} crops")
        frames = per_frame(loaded[c], key)
        sources = sorted({s for s, _ in frames})
        render_levels = []
        for src in sources:
            fs = [v for (s, _), v in frames.items() if s == src]
            sh, n = share(fs)
            lo, hi = boot(fs, rng, n_boot) if len(fs) > 1 else (float("nan"),) * 2
            line = f"{src:>14}: labeled ball share {sh:.3f} [{lo:.3f}, {hi:.3f}] over {n} turns in {len(fs)} frames"
            truth = [v[3] for v in fs if v[3]]
            if truth:
                tb = sum(t[0] for t in truth)
                te = sum(t[1] for t in truth)
                line += f"; true share {tb / (tb + te):.3f}"
                render_levels.append((tb / (tb + te), sh, src))
            teapots = sum(v[2] for v in fs) / len(fs)
            line += f"; teapots counted per frame {teapots:.2f}"
            if truth:
                line += f" (true {sum(t[2] for t in truth) / len(truth):.2f})"
            print(line)
        render_levels.sort()
        if len(render_levels) >= 2:
            print("calibration (true -> labeled):", ", ".join(f"{t:.3f}->{l:.3f}" for t, l, _ in render_levels))
            render_frames = {r[2]: [v for (s, _), v in frames.items() if s == r[2]] for r in render_levels}
            groups = {src: [v for (s, _), v in frames.items() if s == src] for src in sources if src not in render_frames}
            groups["all footage together"] = [v for g in groups.values() for v in g]
            for name, fs in groups.items():
                est = interpolate(render_levels, share(fs)[0])
                vals = []
                for _ in range(n_boot):
                    # Resample the footage frames and, separately, each render level's frames, so the interval
                    # carries the uncertainty of the calibration as well as of the footage.
                    levels = []
                    for true, lab, src in render_levels:
                        rs = [rng.choice(render_frames[src]) for _ in render_frames[src]]
                        tb = sum(t[3][0] for t in rs)
                        te = sum(t[3][1] for t in rs)
                        levels.append((tb / (tb + te), share(rs)[0], src))
                    levels.sort()
                    vals.append(interpolate(levels, share([rng.choice(fs) for _ in fs])[0]))
                vals.sort()
                print(f"{name:>22}: calibrated ball share {est:.3f} [{vals[int(0.025 * n_boot)]:.3f}, {vals[int(0.975 * n_boot)]:.3f}] "
                      f"(footage and calibration uncertainty both included)")
    if len(counters) >= 2:
        a, b = loaded[counters[0]], loaded[counters[1]]
        ids = sorted(set(a) & set(b))
        agree_b = [abs(a[i].get("B", 0) - b[i].get("B", 0)) for i in ids]
        agree_e = [abs(a[i].get("E", 0) - b[i].get("E", 0)) for i in ids]
        print(f"\ncounters {counters[0]} vs {counters[1]} on {len(ids)} crops: mean |dB| {statistics.mean(agree_b):.2f}, mean |dE| {statistics.mean(agree_e):.2f}")
        sa = sum(a[i].get("B", 0) for i in ids) / max(1, sum(a[i].get("B", 0) + a[i].get("E", 0) for i in ids))
        sb = sum(b[i].get("B", 0) for i in ids) / max(1, sum(b[i].get("B", 0) + b[i].get("E", 0) for i in ids))
        print(f"overall labeled share: {counters[0]} {sa:.3f}, {counters[1]} {sb:.3f}")


def interpolate(levels, x):
    """Piecewise linear map from labeled share back to true share, through the render levels."""
    pts = sorted((lab, true) for true, lab, _ in levels)
    if x <= pts[0][0]:
        return pts[0][1]
    if x >= pts[-1][0]:
        return pts[-1][1]
    for (l0, t0), (l1, t1) in zip(pts, pts[1:]):
        if l0 <= x <= l1:
            return t0 + (t1 - t0) * (x - l0) / (l1 - l0) if l1 > l0 else t0
    return x


if __name__ == "__main__":
    main()
