"""Fits the original's tick rate (growth steps a second) from how long scenes last in single-pipe recordings.

A single-pipe scene lasts a number of timer ticks that depends only on the growth rules (modeled in sim_model.py),
so the tick rate is that number divided by the scene's measured length. The whole distribution is used, not just
the median: the rate is the one that makes the modeled lengths match the measured ones best (smallest
Kolmogorov-Smirnov distance), and a bootstrap over scenes gives the interval.

Growth that is too small to show in a frame (far away) makes measured scenes a little short, so the fit is a
slight overestimate of the rate. The bias is reported by fitting the modeled data against itself with the same
detection loss removed is not possible here; treat the interval as the spread, not a guarantee.

Usage: python -I tools/reference/speed_fit.py <aspect, e.g. 16/9> <stats.csv:fps> [<stats.csv:fps> ...]
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenes as S  # noqa: E402
import sim_model  # noqa: E402


def ks(a, b):
    a, b = sorted(a), sorted(b)
    if not a or not b:
        raise ValueError("KS distance requires two nonempty samples")
    i = j = 0
    d = 0.0
    while i < len(a) or j < len(b):
        x = min(a[i] if i < len(a) else float("inf"), b[j] if j < len(b) else float("inf"))
        # Both ECDFs jump at a shared value; comparing partway through a tie invents a gap.
        while i < len(a) and a[i] <= x:
            i += 1
        while j < len(b) and b[j] <= x:
            j += 1
        d = max(d, abs(i / len(a) - j / len(b)))
    return d


def best_rate(grow, ticks, lo=15.0, hi=45.0, step=0.25):
    best, best_d = lo, 9.0
    r = lo
    while r <= hi:
        d = ks(grow, [t / r for t in ticks])
        if d < best_d:
            best, best_d = r, d
        r += step
    return best, best_d


def main():
    num, _, den = sys.argv[1].partition("/")
    aspect = float(num) / float(den or 1)
    inputs = [a.rsplit(":", 1) for a in sys.argv[2:]]
    _, ticks, _, _ = sim_model.run(aspect, False, 3000)
    rng = random.Random(3)
    allgrow = []
    for path, fps in inputs:
        found = S.scenes(S.load(path), float(fps))
        grow = [s["grow"] for s in found]
        allgrow += grow
        r, d = best_rate(grow, ticks)
        rates = sorted(best_rate([rng.choice(grow) for _ in grow], ticks)[0] for _ in range(200))
        print(f"{os.path.basename(path):>22}: {len(grow)} scenes, median {sorted(grow)[len(grow) // 2]:.1f} s -> {r:.1f} ticks/s "
              f"(KS {d:.2f}; 95 % interval {rates[5]:.1f} to {rates[194]:.1f})")
    if len(inputs) > 1:
        r, d = best_rate(allgrow, ticks)
        rates = sorted(best_rate([rng.choice(allgrow) for _ in allgrow], ticks)[0] for _ in range(200))
        print(f"{'all together':>22}: {len(allgrow)} scenes -> {r:.1f} ticks/s (KS {d:.2f}; 95 % interval {rates[5]:.1f} to {rates[194]:.1f})")
    print(f"modeled ticks per scene at aspect {aspect:.2f}: median {sorted(ticks)[len(ticks) // 2]}, 10-90 % "
          f"{sorted(ticks)[len(ticks) // 10]}-{sorted(ticks)[9 * len(ticks) // 10]}")


if __name__ == "__main__":
    main()
