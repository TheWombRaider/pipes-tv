"""Recompute second-review summaries from committed evidence, without images or downloads.

Run: python -I tools/reference/review_evidence.py [--speed]
--speed also fits the 162 native-aspect single-pipe scenes and bootstraps the pooled fit (200 samples).
"""
import argparse
from collections import Counter
import csv
from datetime import datetime
import gzip
import json
import math
from pathlib import Path
import random
import re
import statistics
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import scenes
import sim_model
from speed_fit import best_rate

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/reference"


def read_labels(folder):
    rows = [json.loads(line) for path in sorted(folder.glob("batch-*.jsonl"))
            for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    ids = [row["id"] for row in rows]
    assert len(ids) == len(set(ids)), f"Duplicate IDs in {folder}"
    return {row["id"]: row for row in rows}


def scene_tables():
    tables = {}
    for path in sorted(DATA.glob("scenes-*.txt")):
        rows = [list(map(float, line.split())) for line in path.read_text(encoding="utf-8").splitlines()
                if re.match(r"^\s*\d", line)]
        tables[path.stem.removeprefix("scenes-")] = rows
        print(f"{path.name}: n={len(rows)}, growth median={statistics.median(r[1] for r in rows):.2f}, "
              f"coverage median={statistics.median(r[4] for r in rows):.3f}")
    print("Total table scenes:", sum(map(len, tables.values())))
    tc = [r for name, rows in tables.items() if name.startswith("tCpn") for r in rows]
    coverage = sorted(r[4] for r in tc)
    print(f"tCpn n={len(tc)}, pooled growth median={statistics.median(r[1] for r in tc):.2f}, "
          f"coverage median={statistics.median(coverage):.3f}, "
          f"10-90%={coverage[int(.1 * len(tc))]:.2f}-{coverage[int(.9 * len(tc))]:.2f}")


def labels():
    for folder in [DATA / "labels", DATA / "labels/round-2"]:
        key = json.loads((folder / "key.json").read_text(encoding="utf-8"))
        truth = {(v["source"], v["frame"]): v["truth"] for v in key.values() if v["truth"] is not None}
        print(f"{folder.relative_to(DATA)}: {len(key)} crops; render frames={len(truth)}, "
              f"true teapots={sum(t[2] for t in truth.values())}")
        for counter in ["counter-A", "counter-B"]:
            rows = read_labels(folder / counter)
            assert rows.keys() == key.keys(), f"Missing/extra labels in {folder / counter}"
            for kind in ["footage", "render"]:
                selected = [k for k, v in key.items() if v["kind"] == kind]
                sums = [sum(rows[k][field] for k in selected) for field in ["B", "E", "T"]]
                frames = {(key[k]["source"], key[k]["frame"]) for k in selected}
                print(f"  {counter} {kind}: frames={len(frames)}, B/E/T={sums}")
    folder = DATA / "teapot-candidates"
    key = json.loads((folder / "key.json").read_text(encoding="utf-8"))
    print("Candidate sources:", dict(Counter(k["source"] for k in key.values())))
    for viewer in ["viewer-A", "viewer-B"]:
        rows = read_labels(folder / viewer)
        assert rows.keys() == key.keys(), f"Missing/extra labels in {viewer}"
        print(viewer, "positive candidates:", dict(Counter(key[k]["source"] for k, r in rows.items() if r["teapot"])))
    turns, p = 480, .001
    print(f"For {turns} turns at p={p}: expected count={turns * p:.4f}, P(at least one)={1 - (1 - p) ** turns:.4f}")
    for reported, footage_count in [(3, 6), (4, 7)]:
        n, z = 38, 1.959963984540054
        ratio = reported / n
        center = (ratio + z * z / (2 * n)) / (1 + z * z / n)
        half = z * math.sqrt(ratio * (1 - ratio) / n + z * z / (4 * n * n)) / (1 + z * z / n)
        print(f"Reported/true ratio {reported}/{n}={ratio:.6f}; assumed-binomial Wilson interval "
              f"{center - half:.6f}-{center + half:.6f}; "
              f"naive footage rate under zero-FP/transfer assumptions={footage_count / 136 / ratio:.4f}")


def soak():
    records = []
    for line in (DATA / "renders/soak-log.txt").read_text(encoding="utf-8").splitlines():
        match = re.search(r"scene over: ticks=(\d+) used=([\d.]+).*balls=(\d+) elbows=(\d+) teapotTurns=(\d+) teapots=(\d+)", line)
        if match:
            records.append((datetime.strptime("2026-" + line[:18], "%Y-%m-%d %H:%M:%S.%f"), *map(float, match.groups())))
    intervals = [(b[0] - a[0]).total_seconds() for a, b in zip(records, records[1:])]
    balls, elbows, teapots = (sum(r[i] for r in records) for i in [3, 4, 6])
    print(f"Soak completed scenes={len(records)}, B/E/T={balls:.0f}/{elbows:.0f}/{teapots:.0f}, "
          f"ball share B/(B+E)={balls / (balls + elbows):.6f}, teapots/scene={teapots / len(records):.6f}")
    print(f"Scene-over gaps n={len(intervals)}, median={statistics.median(intervals):.3f}s; "
          f"growth estimate after subtracting nominal hold+clear={statistics.median(intervals) - 1.7 - 1 / 30:.3f}s")


def speed():
    _, ticks, _, _ = sim_model.run(16 / 9, False, 3000)
    allgrow = []
    for path in sorted(DATA.glob("frame-stats-*.csv.gz")):
        name = path.name.removeprefix("frame-stats-").removesuffix(".csv.gz")
        if name in {"72zBfGRQlVA", "MKqrLGFoK9E"}:
            continue
        with gzip.open(path, "rt", encoding="utf-8", newline="") as stream:
            rows = [(float(r["lit"]), float(r["diff"]), int(r["blobs"])) for r in csv.DictReader(stream)]
        fps = 60 if name == "pBSq6RCJRCA" else 30
        grow = [s["grow"] for s in scenes.scenes(rows, fps)]
        allgrow.extend(grow)
        rate, distance = best_rate(grow, ticks)
        print(f"Speed {name}: n={len(grow)}, rate={rate:.2f}, KS={distance:.5f}", flush=True)
    rate, distance = best_rate(allgrow, ticks)
    rng = random.Random(3)
    rates = sorted(best_rate([rng.choice(allgrow) for _ in allgrow], ticks)[0] for _ in range(200))
    print(f"Pooled speed n={len(allgrow)}, rate={rate:.2f}, KS={distance:.5f}, "
          f"bootstrap[5,194]={rates[5]:.2f}-{rates[194]:.2f} (seed=3, 200 resamples)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--speed", action="store_true")
    args = parser.parse_args()
    scene_tables()
    labels()
    soak()
    if args.speed:
        speed()
