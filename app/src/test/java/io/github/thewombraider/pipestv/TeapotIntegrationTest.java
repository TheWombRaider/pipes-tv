package io.github.thewombraider.pipestv;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class TeapotIntegrationTest {
    private static byte[] smallAsset() {
        ByteBuffer b = ByteBuffer.allocate(8 + 24 + 6).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(1).putInt(3);
        b.putFloat(0).putFloat(0).putFloat(0).putFloat(0).putFloat(1).putFloat(0);
        b.putShort((short) 0).putShort((short) 0).putShort((short) 0);
        return b.array();
    }

    private static void rejects(byte[] bytes) {
        try {
            Meshes.teapot(new ByteArrayInputStream(bytes));
            fail("accepted malformed asset");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("teapot:"));
        }
    }

    @Test
    public void loaderRejectsTruncatedAndTrailingBytes() {
        byte[] valid = smallAsset();
        for (int size = 0; size < valid.length; size++) rejects(java.util.Arrays.copyOf(valid, size));
        rejects(java.util.Arrays.copyOf(valid, valid.length + 1));
    }

    @Test
    public void loaderRejectsInvalidCountsBeforeAllocation() {
        for (int count : new int[] {-1, 0, 65537, Integer.MAX_VALUE}) {
            byte[] b = smallAsset();
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(0, count);
            rejects(b);
        }
        for (int count : new int[] {-1, 0, 1, 2, 4, 400002, Integer.MAX_VALUE}) {
            byte[] b = smallAsset();
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(4, count);
            rejects(b);
        }
        rejects(new byte[3_000_001]);
    }

    @Test
    public void loaderRejectsInvalidAttributesAndIndices() {
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (int offset = 8; offset < 32; offset += 4) {
                byte[] b = smallAsset();
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putFloat(offset, value);
                rejects(b);
            }
        }
        byte[] zeroNormal = smallAsset();
        ByteBuffer.wrap(zeroNormal).order(ByteOrder.LITTLE_ENDIAN).putFloat(24, 0);
        rejects(zeroNormal);
        byte[] badIndex = smallAsset();
        ByteBuffer.wrap(badIndex).order(ByteOrder.LITTLE_ENDIAN).putShort(32, (short) 65535);
        rejects(badIndex);
    }

    @Test
    public void boxedInStartCreatesTheTeapotEvenWithDefaultElbows() {
        PipeSim sim = new PipeSim(1, 1, 1, new Random(1975), new PipeSim.Params());
        List<PipeSim.Piece> pieces = new ArrayList<>();
        sim.drain(pieces);
        assertEquals(1, pieces.size());
        PipeSim.Piece p = pieces.get(0);
        assertEquals(PipeSim.Type.TEAPOT, p.type);
        assertEquals(0f, p.x, 0f);
        assertEquals(0f, p.y, 0f);
        assertEquals(0f, p.z, 0f);
    }

    @Test
    public void sharedTeapotsDoNotConsumeThePipeBatch() {
        float[] teapot;
        try (FileInputStream input = new FileInputStream("src/main/assets/teapot.bin")) {
            teapot = Meshes.teapot(input);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        int run = Meshes.cylinder(8, false).length / 6;
        int ball = Meshes.sphere(8, 8).length / 6;
        int elbow = Meshes.torusQuarter(PipeSim.RADIUS, PipeSim.RADIUS, 4, 8).length / 6;
        PipeSim.Params params = new PipeSim.Params();
        params.joints = PipeSim.JointMode.MIXED;
        params.teapotChance = 1f;
        int teapots = 0;
        for (int seed = 0; seed < 20; seed++) {
            PipeSim sim = new PipeSim(15, 8, 15, new Random(seed), params);
            List<PipeSim.Piece> pieces = new ArrayList<>();
            sim.drain(pieces);
            int ticks = 0;
            while (!sim.isOver()) {
                sim.tick();
                sim.drain(pieces);
                assertTrue(++ticks < 10000);
            }
            int vertices = 0, pots = 0;
            for (PipeSim.Piece p : pieces) {
                if (p.type == PipeSim.Type.TEAPOT) pots++;
                else vertices += p.type == PipeSim.Type.RUN ? run : p.type == PipeSim.Type.BALL ? ball : elbow;
            }
            assertTrue(vertices <= PipesRenderer.sceneVertexCapacity(1800, run, Math.max(ball, elbow)));
            assertTrue(pots <= 1800);
            teapots += pots;
        }
        assertTrue(teapots > 18);
        assertEquals(37296, teapot.length / 6);
        assertEquals(2.5f / 7f, Meshes.TEAPOT_SCALE, 1e-7f);
    }

    @Test
    public void everyTransformedTeapotVertexClearsTheCameraNearPlane() throws IOException {
        float[] mesh;
        try (FileInputStream input = new FileInputStream("src/main/assets/teapot.bin")) {
            mesh = Meshes.teapot(input);
        }
        for (int[] screen : new int[][] {{1920, 1080}, {1024, 768}, {1080, 1920}}) {
            int[] lattice = PipeSim.latticeFor(screen[0], screen[1]);
            float hx = (lattice[0] - 1) / 2f, hz = (lattice[2] - 1) / 2f;
            for (int i = 0; i < mesh.length; i += 6) {
                // Maximizing across all yaw angles gives hypot(x,z); corner signs cover every lattice node.
                double reach = Math.hypot(hx + Math.abs(mesh[i] * Meshes.TEAPOT_SCALE),
                        hz + Math.abs(mesh[i + 2] * Meshes.TEAPOT_SCALE));
                assertTrue(PipesRenderer.CAMERA_DISTANCE - reach > PipesRenderer.NEAR_PLANE + .2f);
            }
        }
    }
}
