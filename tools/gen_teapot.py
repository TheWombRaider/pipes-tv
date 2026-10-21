"""Builds the Utah teapot mesh asset (app/src/main/assets/teapot.bin).

A direct port of the tessellation in three.js's TeapotBufferGeometry (Eric Haines, MIT; see NOTICE),
run once at build time so the app only has to read triangles. The teapot data is Martin Newell's.

Usage: python -I tools/gen_teapot.py <TeapotGeometry.js> <out.bin> [segments]
(three.js r160: examples/jsm/geometries/TeapotGeometry.js)
File format (little endian): u32 vertexCount, u32 indexCount, then vertexCount * (x y z nx ny nz) float32,
then indexCount u16 indices. The teapot is scaled to fit vertically between -1 and 1.
"""
import re
import struct
import sys

src = open(sys.argv[1], encoding="utf-8").read()
out = sys.argv[2]
segments = int(sys.argv[3]) if len(sys.argv) > 3 else 8


def numbers(name):
    m = re.search(r"(?:var|const) %s = \[(.*?)\];" % name, src, re.S)
    body = re.sub(r"/\*.*?\*/", "", m.group(1), flags=re.S)
    # three.js writes negative numbers with a space after the minus sign ("- 0.784").
    return [float(x.replace(" ", "")) for x in re.findall(r"-?\s*\d+\.?\d*(?:e-?\d+)?", body)]


patches = [int(v) for v in numbers("teapotPatches")]
verts = numbers("teapotVertices")
assert len(patches) == 32 * 16, len(patches)
assert len(verts) % 3 == 0

blinn_scale = 1.3
max_height = 3.15  # blinn = true
max_height2 = max_height / 2
true_size = 1.0 / max_height2  # size = 1: fits between -1 and 1

ms = [[-1, 3, -3, 1], [3, -6, 3, 0], [-3, 3, 0, 0], [1, 0, 0, 0]]
mst = [[ms[c][r] for c in range(4)] for r in range(4)]


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def apply(m, v):
    return [sum(m[i][j] * v[j] for j in range(4)) for i in range(4)]


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


vertices, normals, indices = [], [], []
per_row = segments + 1
surf_count = 0
for surf in range(32):
    mgm = []
    for i in range(3):
        g = [0.0] * 16
        for r in range(4):
            for c in range(4):
                g[c * 4 + r] = verts[patches[surf * 16 + r * 4 + c] * 3 + i]
                if 20 <= surf < 28 and i != 2:  # fitLid: stretch the lid a little
                    g[c * 4 + r] *= 1.077
        gmx = [g[0:4], g[4:8], g[8:12], g[12:16]]
        mgm.append(matmul(mst, matmul(gmx, ms)))
    for sstep in range(segments + 1):
        s = sstep / segments
        for tstep in range(segments + 1):
            t = tstep / segments
            sp, tp, dsp, dtp = [0.0] * 4, [0.0] * 4, [0.0] * 4, [0.0] * 4
            sval = tval = 1.0
            dsval = dtval = 0.0
            for p in (3, 2, 1, 0):
                sp[p], tp[p] = sval, tval
                sval *= s
                tval *= t
                if p == 3:
                    dsp[p] = dtp[p] = 0.0
                    dsval = dtval = 1.0
                else:
                    dsp[p] = dsval * (3 - p)
                    dtp[p] = dtval * (3 - p)
                    dsval *= s
                    dtval *= t
            vert, sdir, tdir = [0.0] * 3, [0.0] * 3, [0.0] * 3
            for i in range(3):
                vert[i] = dot(apply(mgm[i], sp), tp)
                sdir[i] = dot(apply(mgm[i], dsp), tp)
                tdir[i] = dot(apply(mgm[i], sp), dtp)
            n = cross(tdir, sdir)
            length = (n[0] ** 2 + n[1] ** 2 + n[2] ** 2) ** 0.5
            n = [c / length for c in n] if length else [0.0, 0.0, 0.0]
            if vert[0] == 0 and vert[1] == 0:
                nout = [0.0, 1.0 if vert[2] > max_height2 else -1.0, 0.0]
            else:
                nout = [n[0], n[2], -n[1]]
            vertices.append((true_size * vert[0], true_size * (vert[2] - max_height2), -true_size * vert[1]))
            normals.append(tuple(nout))
    for sstep in range(segments):
        for tstep in range(segments):
            v1 = surf_count * per_row * per_row + sstep * per_row + tstep
            v2 = v1 + 1
            v3 = v2 + per_row
            v4 = v1 + per_row
            for tri in ((v1, v2, v3), (v1, v3, v4)):
                a, b, c = (vertices[k] for k in tri)
                if a != b and a != c and b != c:
                    indices.extend(tri)
    surf_count += 1

assert max(indices) < 65536
with open(out, "wb") as f:
    f.write(struct.pack("<II", len(vertices), len(indices)))
    for v, n in zip(vertices, normals):
        f.write(struct.pack("<6f", *v, *n))
    f.write(struct.pack("<%dH" % len(indices), *indices))
ys = [v[1] for v in vertices]
print("vertices", len(vertices), "triangles", len(indices) // 3, "y range %.3f..%.3f" % (min(ys), max(ys)))
