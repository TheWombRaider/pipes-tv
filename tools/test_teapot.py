"""Independent source/geometry oracle. Run: python -I tools/test_teapot.py."""
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import random
import re
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("generator", ROOT / "tools/gen_teapot.py")
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


def source_tables():
    source = (ROOT / "tools/teapot/glut_teapot.c").read_text()
    tables = []
    for name in ("patchdata", "cpdata"):
        body = re.search(name + r"\[\]\[\d+\]\s*=\s*\{(.*?)\};", source, re.S).group(1)
        body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
        tables.append([[float(x) for x in row.split(",") if x.strip()]
                       for row in re.findall(r"\{([^{}]+)\}", body)])
    return tables


def reference_patches():
    indices, points = source_tables()
    result = []
    for base, ids in enumerate(indices):
        for reflection in range(4 if base < 6 else 2):
            grid = []
            for row in range(4):
                line = []
                for column in range(4):
                    c = 3 - column if reflection in (1, 2) else column
                    x, y, z = points[int(ids[4 * row + c])]
                    line.append((x * (-1 if reflection >= 2 else 1),
                                 y * (-1 if reflection in (1, 3) else 1), z))
                grid.append(line)
            result.append(grid)
    return result


def bernstein(t):
    return [(1 - t) ** 3, 3 * t * (1 - t) ** 2, 3 * t * t * (1 - t), t ** 3]


def derivative(t):
    return [-3 * (1 - t) ** 2, 3 * (1 - t) * (1 - 3 * t), 3 * t * (2 - 3 * t), 3 * t * t]


def oracle(grid, u, v):
    a, b, da, db = bernstein(u), bernstein(v), derivative(u), derivative(v)
    p = [sum(grid[r][c][k] * a[r] * b[c] for r in range(4) for c in range(4)) for k in range(3)]
    du = [sum(grid[r][c][k] * da[r] * b[c] for r in range(4) for c in range(4)) for k in range(3)]
    dv = [sum(grid[r][c][k] * a[r] * db[c] for r in range(4) for c in range(4)) for k in range(3)]
    n = [dv[1] * du[2] - dv[2] * du[1], dv[2] * du[0] - dv[0] * du[2], dv[0] * du[1] - dv[1] * du[0]]
    length = math.sqrt(sum(x * x for x in n))
    if length < 1e-12:
        n = [0, 0, 1 if p[2] > 1.5 else -1]
    else:
        n = [x / length for x in n]
    return [p[0] * .5, (p[2] - 1.5) * .5, -p[1] * .5], [n[0], n[2], -n[1]]


class TeapotTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.binary = (ROOT / "app/src/main/assets/teapot.bin").read_bytes()
        cls.nv, cls.ni = struct.unpack_from("<II", cls.binary)
        cls.vertices = list(struct.iter_unpack("<6f", cls.binary[8:8 + cls.nv * 24]))
        cls.indices = struct.unpack_from(f"<{cls.ni}H", cls.binary, 8 + cls.nv * 24)
        cls.patches = reference_patches()

    def test_source_tables_and_pinned_hashes(self):
        indices, points = source_tables()
        canonical = json.loads((ROOT / "tools/teapot/control-points.json").read_text())
        self.assertEqual(canonical, {"patches": indices, "points": points})
        generator.load_data()
        manifest = json.loads((ROOT / "tools/teapot/manifest.json").read_text())
        self.assertEqual(manifest["asset_sha256"], hashlib.sha256(self.binary).hexdigest())

    def test_every_stored_vertex_and_normal_against_source(self):
        self.assertEqual((7200, 37296), (self.nv, self.ni))
        max_error, min_dot = 0, 1
        for patch, grid in enumerate(self.patches):
            for row in range(15):
                for col in range(15):
                    p, n = oracle(grid, row / 14, col / 14)
                    actual = self.vertices[patch * 225 + row * 15 + col]
                    for expected, value in zip(p, actual[:3]):
                        max_error = max(max_error, abs(expected - value))
                        self.assertAlmostEqual(expected, value, delta=2e-6 * max(1, abs(expected)))
                    length = math.sqrt(sum(x * x for x in actual[3:]))
                    self.assertAlmostEqual(1, length, delta=1e-5)
                    agreement = sum(a * b for a, b in zip(n, actual[3:])) / length
                    min_dot = min(min_dot, agreement)
                    self.assertGreaterEqual(agreement, .99999)
        print(f"source oracle: max stored-position error={max_error:.3g}, min normal dot={min_dot:.12f}")

    def test_seeded_interior_samples_and_normal_quantization(self):
        rng = random.Random(1975)
        for grid in self.patches:
            for _ in range(50):
                u, v = rng.uniform(.001, .999), rng.uniform(.001, .999)
                p, n = oracle(grid, u, v)
                actual, normal = generator.evaluate(grid, u, v)
                for a, b in zip(p, actual):
                    self.assertAlmostEqual(a, b, delta=1e-6)
                self.assertGreaterEqual(sum(a * b for a, b in zip(n, normal)), .99999)
                packed = [math.floor(x * 127 + .5) / 127 for x in normal]
                length = math.sqrt(sum(x * x for x in packed))
                self.assertGreater(sum(a * b / length for a, b in zip(normal, packed)), .99995)

    def test_winding_patch_coverage_and_no_zero_area_faces(self):
        counts = [0] * 32
        for i in range(0, self.ni, 3):
            ids = self.indices[i:i + 3]
            self.assertLess(max(ids), self.nv)
            self.assertEqual(1, len({j // 225 for j in ids}))
            counts[ids[0] // 225] += 1
            a, b, c = [self.vertices[j] for j in ids]
            u, v = [b[k] - a[k] for k in range(3)], [c[k] - a[k] for k in range(3)]
            face = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
            self.assertGreater(sum(x * x for x in face), 1e-20)
            self.assertGreater(sum(face[k] * (a[k + 3] + b[k + 3] + c[k + 3]) for k in range(3)), 0)
        self.assertEqual([392] * 12 + [378] * 4 + [392] * 4 + [378] * 4 + [392] * 8, counts)
        # A body patch's exterior points away from the axis; agreement alone cannot prove outward normals.
        for grid in self.patches[4:12]:
            p, n = oracle(grid, .5, .5)
            self.assertGreater(p[0] * n[0] + p[2] * n[2], 0)

    def test_matching_source_edges_remain_identical(self):
        edges = {}
        pairs = 0
        for grid in self.patches:
            for edge in (grid[0], grid[3], [r[0] for r in grid], [r[3] for r in grid]):
                key = tuple(tuple(p) for p in edge)
                reverse = tuple(reversed(key))
                canonical = min(key, reverse)
                if canonical in edges:
                    other = edges[canonical]
                    for i in range(15):
                        t = i / 14
                        actual = generator.curve(edge, t)
                        expected = generator.curve(other, t if key == tuple(map(tuple, other)) else 1 - t)
                        for a, b in zip(actual, expected):
                            self.assertAlmostEqual(a, b, delta=1e-12)
                    pairs += 1
                else:
                    edges[canonical] = edge
        self.assertGreater(pairs, 30)

    def test_reproducible_asset_and_invalid_subdivision(self):
        self.assertEqual(self.binary, generator.generate())
        for invalid in (-1, 0, 1, 45, 999999, 2.5):
            with self.assertRaises(ValueError):
                generator.generate(invalid)


if __name__ == "__main__":
    unittest.main(verbosity=2)
