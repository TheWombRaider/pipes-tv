package io.github.thewombraider.pipestv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Base shapes as plain triangle lists: six floats per vertex (position, then normal), three
 * vertices per triangle. The renderer moves and turns copies of them into one big batch.
 */
final class Meshes {
    static final float TEAPOT_SCALE = 2.5f * PipeSim.RADIUS;
    private Meshes() {}

    /** A growable float list. */
    static final class Floats {
        float[] data;
        int size;

        Floats(int capacity) {
            data = new float[Math.max(16, capacity)];
        }

        void add(float v) {
            if (size == data.length) data = java.util.Arrays.copyOf(data, size * 2);
            data[size++] = v;
        }

        void vertex(float x, float y, float z, float nx, float ny, float nz) {
            add(x);
            add(y);
            add(z);
            add(nx);
            add(ny);
            add(nz);
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    /** A tube of radius 1 along +Z from 0 to 1, with flat end caps when {@code caps} is set. */
    static float[] cylinder(int segments, boolean caps) {
        Floats f = new Floats(segments * 240);
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            f.vertex(c0, s0, 0, c0, s0, 0);
            f.vertex(c1, s1, 0, c1, s1, 0);
            f.vertex(c1, s1, 1, c1, s1, 0);
            f.vertex(c0, s0, 0, c0, s0, 0);
            f.vertex(c1, s1, 1, c1, s1, 0);
            f.vertex(c0, s0, 1, c0, s0, 0);
            if (!caps) continue;
            f.vertex(0, 0, 0, 0, 0, -1);
            f.vertex(c1, s1, 0, 0, 0, -1);
            f.vertex(c0, s0, 0, 0, 0, -1);
            f.vertex(0, 0, 1, 0, 0, 1);
            f.vertex(c0, s0, 1, 0, 0, 1);
            f.vertex(c1, s1, 1, 0, 0, 1);
        }
        return f.toArray();
    }

    /** A sphere of radius 1 at the origin. */
    static float[] sphere(int stacks, int slices) {
        Floats f = new Floats(stacks * slices * 36);
        for (int i = 0; i < stacks; i++) {
            double p0 = Math.PI * i / stacks, p1 = Math.PI * (i + 1) / stacks;
            for (int j = 0; j < slices; j++) {
                double t0 = 2 * Math.PI * j / slices, t1 = 2 * Math.PI * (j + 1) / slices;
                float[] a = point(p0, t0), b = point(p0, t1), c = point(p1, t1), d = point(p1, t0);
                if (i != 0) {
                    f.vertex(a[0], a[1], a[2], a[0], a[1], a[2]);
                    f.vertex(b[0], b[1], b[2], b[0], b[1], b[2]);
                    f.vertex(c[0], c[1], c[2], c[0], c[1], c[2]);
                }
                if (i != stacks - 1) {
                    f.vertex(a[0], a[1], a[2], a[0], a[1], a[2]);
                    f.vertex(c[0], c[1], c[2], c[0], c[1], c[2]);
                    f.vertex(d[0], d[1], d[2], d[0], d[1], d[2]);
                }
            }
        }
        return f.toArray();
    }

    private static float[] point(double phi, double theta) {
        return new float[] {
            (float) (Math.sin(phi) * Math.cos(theta)), (float) Math.cos(phi), (float) (Math.sin(phi) * Math.sin(theta))
        };
    }

    /**
     * A quarter of a torus in the XY plane: the tube's center line runs along a circle of radius
     * {@code major} from +X round to +Y, and the tube has radius {@code tube}.
     */
    static float[] torusQuarter(float major, float tube, int arcSegments, int tubeSegments) {
        Floats f = new Floats(arcSegments * tubeSegments * 36);
        for (int i = 0; i < arcSegments; i++) {
            double th0 = Math.PI / 2 * i / arcSegments, th1 = Math.PI / 2 * (i + 1) / arcSegments;
            for (int j = 0; j < tubeSegments; j++) {
                double ph0 = 2 * Math.PI * j / tubeSegments, ph1 = 2 * Math.PI * (j + 1) / tubeSegments;
                float[] a = torusPoint(major, tube, th0, ph0), b = torusPoint(major, tube, th1, ph0);
                float[] c = torusPoint(major, tube, th1, ph1), d = torusPoint(major, tube, th0, ph1);
                f.vertex(a[0], a[1], a[2], a[3], a[4], a[5]);
                f.vertex(b[0], b[1], b[2], b[3], b[4], b[5]);
                f.vertex(c[0], c[1], c[2], c[3], c[4], c[5]);
                f.vertex(a[0], a[1], a[2], a[3], a[4], a[5]);
                f.vertex(c[0], c[1], c[2], c[3], c[4], c[5]);
                f.vertex(d[0], d[1], d[2], d[3], d[4], d[5]);
            }
        }
        return f.toArray();
    }

    private static float[] torusPoint(float major, float tube, double theta, double phi) {
        float ct = (float) Math.cos(theta), st = (float) Math.sin(theta);
        float cp = (float) Math.cos(phi), sp = (float) Math.sin(phi);
        float nx = cp * ct, ny = cp * st, nz = sp;
        return new float[] {major * ct + tube * nx, major * st + tube * ny, tube * nz, nx, ny, nz};
    }

    /** Reads teapot.bin (see tools/gen_teapot.py) and expands it to a plain triangle list. */
    static float[] teapot(InputStream in) throws IOException {
        final int maxBytes = 3_000_000;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int n; (n = in.read(chunk)) != -1; ) {
            if (n == 0) throw new IOException("teapot: input made no progress");
            if (bytes.size() > maxBytes - n) throw new IOException("teapot: asset exceeds size limit");
            bytes.write(chunk, 0, n);
        }
        byte[] all = bytes.toByteArray();
        if (all.length < 8) throw new IOException("teapot: truncated header");
        ByteBuffer buf = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        int vertexCount = buf.getInt();
        int indexCount = buf.getInt();
        if (vertexCount < 1 || vertexCount > 65536 || indexCount < 3 || indexCount > 400_000 || indexCount % 3 != 0) {
            throw new IOException("teapot: invalid vertex/index counts");
        }
        long expected = 8L + vertexCount * 24L + indexCount * 2L;
        if (expected != all.length) throw new IOException("teapot: expected " + expected + " bytes, found " + all.length);
        float[] verts = new float[vertexCount * 6];
        for (int i = 0; i < verts.length; i++) {
            verts[i] = buf.getFloat();
            if (!Float.isFinite(verts[i])) throw new IOException("teapot: nonfinite vertex attribute");
        }
        for (int i = 0; i < verts.length; i += 6) {
            double length2 = verts[i + 3] * verts[i + 3] + verts[i + 4] * verts[i + 4] + verts[i + 5] * verts[i + 5];
            if (Math.abs(length2 - 1) > 1e-4) throw new IOException("teapot: normal is not unit length");
        }
        float[] out = new float[indexCount * 6];
        for (int i = 0; i < indexCount; i++) {
            int v = buf.getShort() & 0xFFFF;
            if (v >= vertexCount) throw new IOException("teapot: index " + v + " exceeds vertex count " + vertexCount);
            System.arraycopy(verts, v * 6, out, i * 6, 6);
        }
        return out;
    }
}
