"""Splits a recording into scenes using the per-frame statistics from frame_stats.py, and measures each one.

A scene runs from the first non-black frame to the frame where the screen is black again. Within it:
  grow      seconds from the first pipe to the last frame where a new piece appeared
  hold      seconds the finished picture stays on screen before the clear starts
  dissolve  seconds from the first cleared block to a black screen
  lit       fraction of the screen covered when growth stopped
  cadence   median frames between frames that show new pieces (1 = every frame)
  together  median number of separate new pieces on a frame that shows any (pipes growing at once)

Scenes cut off by the start or end of the recording are dropped.

Usage: python -I tools/reference/scenes.py <stats.csv> [fps]
"""
import csv
import statistics
import sys

BLACK = 0.0008


def load(path):
    with open(path) as f:
        rows = list(csv.DictReader(f))
    return [(float(r["lit"]), float(r["diff"]), int(r["blobs"])) for r in rows]


def scenes(rows, fps):
    out = []
    n = len(rows)
    i = 0
    # Skip a scene already running when the recording starts.
    while i < n and rows[i][0] > BLACK:
        i += 1
    while i < n:
        while i < n and rows[i][0] <= BLACK:
            i += 1
        start = i
        while i < n and rows[i][0] > BLACK:
            i += 1
        end = i
        if end >= n or end - start < fps:
            continue
        lit = [rows[k][0] for k in range(start, end)]
        peak = max(range(len(lit)), key=lambda k: lit[k])
        # The clear starts at the last frame from which coverage only falls.
        k = len(lit) - 1
        while k > 0 and lit[k - 1] >= lit[k] - 1e-5:
            k -= 1
        clear = k
        while clear > 0 and lit[clear - 1] - lit[clear] < 0.002 and rows[start + clear][2] == 0:
            clear -= 1
        dissolve_from = clear
        for j in range(clear, len(lit) - 1):
            if lit[j] - lit[j + 1] > 0.002:
                dissolve_from = j
                break
        growth = [k for k in range(dissolve_from) if rows[start + k][2] > 0]
        if not growth:
            continue
        last = growth[-1]
        gaps = [b - a for a, b in zip(growth, growth[1:]) if b - a <= 8]
        out.append({
            "start": start / fps,
            "grow": (last + 1) / fps,
            "hold": (dissolve_from - last) / fps,
            "dissolve": (len(lit) - dissolve_from) / fps,
            "lit": lit[min(last, len(lit) - 1)],
            "peak_lit": lit[peak],
            "cadence": statistics.median(gaps) if gaps else float("nan"),
            "together": statistics.median(rows[start + k][2] for k in growth),
        })
    return out


def main():
    rows = load(sys.argv[1])
    fps = float(sys.argv[2]) if len(sys.argv) > 2 else 60.0
    found = scenes(rows, fps)
    print(f"{'start':>7} {'grow':>6} {'hold':>5} {'dissolve':>8} {'lit':>5} {'cadence':>7} {'together':>8}")
    for s in found:
        print(f"{s['start']:7.1f} {s['grow']:6.1f} {s['hold']:5.2f} {s['dissolve']:8.2f} {s['lit']:5.2f} {s['cadence']:7.1f} {s['together']:8.1f}")
    if found:
        for key in ("grow", "hold", "dissolve", "lit", "cadence", "together"):
            vals = sorted(s[key] for s in found)
            print(f"{key:>9}: median {statistics.median(vals):.2f}  range {vals[0]:.2f}-{vals[-1]:.2f}  n={len(vals)}")


if __name__ == "__main__":
    main()
