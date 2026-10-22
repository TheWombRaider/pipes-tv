"""Generate the classic SGI/GLAUX teapot offline. See tools/teapot/README.md.

Usage: python -I tools/gen_teapot.py [out.bin] [--segments 14] [--check]
Format: little-endian u32 vertex/index counts, xyz+normal float32, u16 indices.
Asset coordinates include GLAUX's half-scale, Z offset -1.5, and 270-degree X turn.
The renderer supplies only the caller's 2.5 * pipeRadius scale and node translation.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct

DATA = Path(__file__).resolve().parent / "teapot"
ASSET = DATA.parents[1] / "app/src/main/assets/teapot.bin"


def load_data():
    manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
    for name, key in (("glut_teapot.c", "source_sha256"), ("control-points.json", "data_sha256")):
        if hashlib.sha256((DATA / name).read_bytes()).hexdigest() != manifest[key]:
            raise ValueError(f"{name}: source hash mismatch; review provenance before regeneration")
    data = json.loads((DATA / "control-points.json").read_text(encoding="utf-8"))
    points, patches = data["points"], data["patches"]
    if len(points) != 127 or len(patches) != 10:
        raise ValueError("expected 127 control points and 10 base patches")
    if any(len(p) != 3 or not all(math.isfinite(x) for x in p) for p in points):
        raise ValueError("invalid control point")
    if any(len(p) != 16 or any(type(i) is not int or not 0 <= i < len(points) for i in p) for p in patches):
        raise ValueError("invalid patch indices")
    return points, patches


def expanded_patches(points, patches):
    for base, indices in enumerate(patches):
        # Reversing the column parameter compensates for each single-axis reflection.
        for sx, sy, reverse in ((1, 1, False), (1, -1, True), (-1, 1, True), (-1, -1, False))[:4 if base < 6 else 2]:
            yield [[[points[indices[r * 4 + (3 - c if reverse else c)]][0] * sx,
                     points[indices[r * 4 + (3 - c if reverse else c)]][1] * sy,
                     points[indices[r * 4 + (3 - c if reverse else c)]][2]] for c in range(4)] for r in range(4)]


def curve(points, t):
    work = [list(p) for p in points]
    while len(work) > 1:
        work = [[(1 - t) * a + t * b for a, b in zip(p, q)] for p, q in zip(work, work[1:])]
    return work[0]


def evaluate(patch, u, v):
    rows = [curve(row, v) for row in patch]
    position = curve(rows, u)
    du = curve([[3 * (b - a) for a, b in zip(p, q)] for p, q in zip(rows, rows[1:])], u)
    dv = curve([curve([[3 * (b - a) for a, b in zip(p, q)] for p, q in zip(row, row[1:])], v) for row in patch], u)
    n = [dv[1] * du[2] - dv[2] * du[1], dv[2] * du[0] - dv[0] * du[2], dv[0] * du[1] - dv[1] * du[0]]
    length = math.sqrt(sum(x * x for x in n))
    if length < 1e-14:
        if abs(position[0]) > 1e-12 or abs(position[1]) > 1e-12:
            raise ValueError(f"unexpected singular surface at {position}")
        n = [0, 0, 1 if position[2] > 1.5 else -1]
    else:
        n = [x / length for x in n]
    x, y, z = position
    return (x / 2, (z - 1.5) / 2, -y / 2), (n[0], n[2], -n[1])


def generate(segments=14):
    if type(segments) is not int or not 2 <= segments <= 44:
        raise ValueError("segments must be between 2 and 44 (u16 index limit)")
    points, patches = load_data()
    vertices, indices = [], []
    for patch in expanded_patches(points, patches):
        start = len(vertices)
        for row in range(segments + 1):
            for col in range(segments + 1):
                p, n = evaluate(patch, row / segments, col / segments)
                vertices.append(struct.unpack("<6f", struct.pack("<6f", *p, *n)))
        for row in range(segments):
            for col in range(segments):
                a = start + row * (segments + 1) + col
                b, d = a + 1, a + segments + 1
                for tri in ((a, b, d + 1), (a, d + 1, d)):
                    positions = [vertices[i][:3] for i in tri]
                    if len(set(positions)) == 3:
                        indices.extend(tri)
    return (struct.pack("<II", len(vertices), len(indices))
            + b"".join(struct.pack("<6f", *v) for v in vertices)
            + struct.pack(f"<{len(indices)}H", *indices))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, nargs="?", default=ASSET)
    parser.add_argument("--segments", type=int, default=14)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        binary = generate(args.segments)
        if args.check:
            if args.output.read_bytes() != binary:
                raise ValueError(f"{args.output}: asset differs from canonical generation")
        else:
            args.output.write_bytes(binary)
    except (ValueError, OSError) as error:
        parser.exit(1, f"teapot: {error}\n")
    vertices, indices = struct.unpack_from("<II", binary)
    print(f"vertices={vertices} triangles={indices // 3} sha256={hashlib.sha256(binary).hexdigest()}")


if __name__ == "__main__":
    main()
