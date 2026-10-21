"""A model of the original's growth rules, used only to interpret measurements of the footage.

The rules follow the published behavior of Microsoft's 1995 OpenGL "pipes" sample as documented by the
SspipesSharp port (see NOTICE): a lattice 15 nodes wide whose height follows the screen's aspect ratio,
one lattice step per pipe per timer tick, a per-pipe preference for going straight, and a scene that ends
once a fixed number of pipes have each run until trapped. This file is independent code written from
that description; no code was copied.

It answers: how many timer ticks does a scene take, how many nodes end up used, and how many pipes are
growing at once. Dividing a measured scene length in seconds by the modeled ticks per scene gives the
tick rate of a recording.

Usage: python -I tools/reference/sim_model.py [aspect=16/9] [multi=1] [scenes=2000]
"""
import random
import statistics
import sys

DIRS = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
MAX_WEIGHT = 100


def lattice(aspect):
    x = 15
    y = max(1, int(x / aspect)) if aspect >= 1 else 15
    if aspect < 1:
        x = max(1, int(aspect * 15))
    return x, y, 15


class Scene:
    def __init__(self, rng, size, multi, chase):
        self.rng = rng
        self.nx, self.ny, self.nz = size
        self.empty = set((x, y, z) for x in range(self.nx) for y in range(self.ny) for z in range(self.nz))
        self.max_pipes = 7 if multi else 5
        threads = min(self.max_pipes, rng.randint(2, 4)) if multi else 1
        self.chase = chase
        self.pipes = []
        self.drawn = 0
        self.lead = None
        for i in range(threads):
            p = {"weight": self.weight(), "pos": None, "dir": None, "stuck": False, "chaser": chase and i > 0}
            self.start(p)
            if chase and i == 0:
                self.lead = p
            self.pipes.append(p)
            self.drawn += 1

    def weight(self):
        if self.rng.randrange(20) == 0:
            return self.rng.randint(MAX_WEIGHT // 4, MAX_WEIGHT)
        return 1 + self.rng.randrange(4)

    def take_random(self):
        if not self.empty:
            return None
        node = self.rng.choice(tuple(self.empty))
        self.empty.discard(node)
        return node

    def step_dir(self, p):
        x, y, z = p["pos"]
        if p["chaser"] and self.lead is not None and self.lead is not p and self.lead["pos"] is not None:
            lx, ly, lz = self.lead["pos"]
            pref = [d for d in DIRS if (d[0] and (lx - x) * d[0] > 0) or (d[1] and (ly - y) * d[1] > 0) or (d[2] and (lz - z) * d[2] > 0)]
            ok = [d for d in pref if (x + d[0], y + d[1], z + d[2]) in self.empty]
            if pref:
                if not ok:
                    ok = [d for d in DIRS if (x + d[0], y + d[1], z + d[2]) in self.empty]
                if not ok:
                    return None
                d = self.rng.choice(ok)
                self.empty.discard((x + d[0], y + d[1], z + d[2]))
                return d
        d0 = p["dir"]
        straight = (x + d0[0], y + d0[1], z + d0[2])
        w = p["weight"] if straight in self.empty else 0
        if w == MAX_WEIGHT:
            self.empty.discard(straight)
            return d0
        turns = [d for d in DIRS if d != d0 and (x + d[0], y + d[1], z + d[2]) in self.empty]
        n = w + len(turns)
        if n == 0:
            return None
        c = self.rng.randrange(n)
        d = d0 if c < w else turns[c - w]
        self.empty.discard((x + d[0], y + d[1], z + d[2]))
        return d

    def start(self, p):
        node = self.take_random()
        if node is None:
            p["stuck"] = True
            return False
        p["pos"] = node
        p["dir"] = self.rng.choice(DIRS)
        d = self.step_dir(p)
        if d is None:
            p["stuck"] = True
            return True
        p["pos"] = (node[0] + d[0], node[1] + d[1], node[2] + d[2])
        p["dir"] = d
        p["stuck"] = False
        return True

    def tick(self):
        """One timer tick. Returns the number of pipes that grew, or -1 once the scene is over."""
        alive = []
        for p in self.pipes:
            if p["stuck"]:
                self.drawn += 1
                if self.drawn > self.max_pipes:
                    continue
                if not self.start(p):
                    self.max_pipes = self.drawn
            alive.append(p)
        self.pipes = alive
        if not self.pipes:
            return -1
        if self.lead is not None and self.lead not in self.pipes:
            self.lead = self.rng.choice(self.pipes)
            self.lead["chaser"] = False
        grew = 0
        for p in self.pipes:
            if p["stuck"]:
                continue
            d = self.step_dir(p)
            if d is None:
                p["stuck"] = True
                continue
            x, y, z = p["pos"]
            p["pos"] = (x + d[0], y + d[1], z + d[2])
            p["dir"] = d
            grew += 1
        return grew


def run(aspect, multi, scenes, seed=1):
    rng = random.Random(seed)
    size = lattice(aspect)
    total = size[0] * size[1] * size[2]
    ticks, used, concurrent = [], [], []
    chase = False
    for _ in range(scenes):
        if multi and not chase and rng.randrange(5) == 0:
            chase = True
        s = Scene(rng, size, multi, chase)
        t = 0
        while True:
            g = s.tick()
            if g < 0:
                break
            if g:
                concurrent.append(g)
            t += 1
        ticks.append(t)
        used.append(1 - len(s.empty) / total)
    return size, ticks, used, concurrent


def describe(xs):
    xs = sorted(xs)
    q = lambda f: xs[int(f * (len(xs) - 1))]
    return f"mean {statistics.mean(xs):.1f}, median {q(.5):.1f}, 10-90% {q(.1):.1f}-{q(.9):.1f}"


if __name__ == "__main__":
    aspect = 16 / 9
    if len(sys.argv) > 1:
        num, _, den = sys.argv[1].partition("/")
        aspect = float(num) / float(den or 1)
    multi = (sys.argv[2] != "0") if len(sys.argv) > 2 else True
    n = int(sys.argv[3]) if len(sys.argv) > 3 else 2000
    size, ticks, used, conc = run(aspect, multi, n)
    print(f"lattice {size}, multi={multi}, {n} scenes")
    print(f"ticks per scene: {describe(ticks)}")
    print(f"lattice used at the end: {describe([u * 100 for u in used])} %")
    print(f"pipes growing at once: {describe(conc)}")
