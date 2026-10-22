"""Growth events in single-pipe recordings, from the per-frame statistics (frame_stats.py, with blob geometry).

Three checks that need the head of the pipe followed from frame to frame:

1. Pipes per scene. A pipe that ends is replaced by one starting somewhere else, so the new growth appears far from
   the old head. A scene with 5 sequential pipes has 4 such jumps. A jump is a move of the largest new blob's centre
   by more than JUMP of the picture width between consecutive growth frames.
2. Direct count of segments added, from near-field straight runs. A straight tick adds one lattice step, 3.5 pipe
   diameters long, so a blob of length L and thickness T = 2 dr holds about L / (3.5 T) ticks. A histogram of that
   number over elongated, near-camera blobs shows whether frames hold one tick, two, or none. Foreshortening makes the
   estimate low for pipes that point toward the camera, so only the shape of the histogram is used, not its scale.
3. Teapot candidates. A teapot is drawn at a turn, about 5 pipe radii across, so the largest circle inside the new
   blob (dr) is far bigger than the radius of the pipe just before it. A frame is a candidate when dr is at least
   TEAPOT_RATIO times the median dr of the previous growth frames. Candidates are then looked at by eye.

Usage: python -I tools/reference/growth_events.py <stats.csv> <fps> [candidates.csv]
"""
import csv
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenes as S  # noqa: E402

JUMP = 0.16
WIDTH = 480
TEAPOT_RATIO = 2.1
NEAR_FIELD_DR = 3.0


def load(path):
    with open(path) as f:
        rows = list(csv.DictReader(f))
    return rows


def main():
    path, fps = sys.argv[1], float(sys.argv[2])
    out = sys.argv[3] if len(sys.argv) > 3 else None
    rows = load(path)
    found = S.scenes(S.load(path), fps)
    pipes, qs, cands = [], [], []
    for sc in found:
        a, b = int(sc["start"] * fps), int((sc["start"] + sc["grow"]) * fps)
        prev = None
        jumps = 0
        recent = []
        for i in range(a, min(b, len(rows))):
            r = rows[i]
            if int(r["blobs"]) == 0:
                continue
            cx, cy = int(r["bx"]) + int(r["bw"]) / 2, int(r["by"]) + int(r["bh"]) / 2
            dr, big = float(r["dr"]), int(r["big"])
            if prev is not None and ((cx - prev[0]) ** 2 + (cy - prev[1]) ** 2) ** 0.5 > JUMP * WIDTH and big < 400:
                jumps += 1
                recent = []
            prev = (cx, cy)
            bw, bh = int(r["bw"]), int(r["bh"])
            if dr >= NEAR_FIELD_DR and max(bw, bh) >= 2.5 * min(bw, bh) and int(r["blobs"]) == 1:
                qs.append(max(bw, bh) / (3.5 * 2 * dr))
            if len(recent) >= 6 and dr >= TEAPOT_RATIO * statistics.median(recent[-12:]) and dr >= 4.0:
                cands.append((i, i / fps, sc["start"], dr, statistics.median(recent[-12:]), int(r["bx"]), int(r["by"]), bw, bh))
            recent.append(dr)
        pipes.append(jumps + 1)
    print(f"{os.path.basename(path)}: {len(found)} scenes")
    print("pipes per scene (jumps + 1):", sorted(pipes), f"median {statistics.median(pipes)}")
    if qs:
        bins = [0] * 8
        for q in qs:
            bins[min(7, int(q * 2))] += 1
        print("ticks per near-field straight blob, histogram in steps of 0.5 (0-0.5 ... 3.5+):", bins, "n =", len(qs))
    print("teapot candidates:", len(cands))
    if out:
        with open(out, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["frame", "t", "scene_start", "dr", "recent_dr", "bx", "by", "bw", "bh"])
            for c in cands:
                w.writerow([c[0], f"{c[1]:.3f}", f"{c[2]:.2f}", f"{c[3]:.1f}", f"{c[4]:.1f}", *c[5:]])


if __name__ == "__main__":
    main()
