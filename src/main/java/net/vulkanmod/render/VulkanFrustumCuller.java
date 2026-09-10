package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;

import java.util.*;

public class VulkanFrustumCuller {
    private static final int MAX_CHUNKS = 256;
    private static final float[] frustumPlanes = new float[24];
    private static final float[] projectionMatrix = new float[16];
    private static final float[] viewMatrix = new float[16];
    private static final float[] projViewMatrix = new float[16];
    private static boolean initialized = false;

    private static class ChunkBounds {
        float minX, minY, minZ;
        float maxX, maxY, maxZ;
        boolean visible;
    }

    private static final ChunkBounds[] chunkBounds = new ChunkBounds[MAX_CHUNKS];

    public static synchronized void initialize() {
        if (initialized) return;

        for (int i = 0; i < MAX_CHUNKS; i++) {
            chunkBounds[i] = new ChunkBounds();
        }

        initialized = true;
        VulkanMod.LOGGER.info("Frustum culler initialized");
    }

    public static void updateMatrices(float[] projection, float[] view) {
        if (!initialized) return;

        System.arraycopy(projection, 0, projectionMatrix, 0, 16);
        System.arraycopy(view, 0, viewMatrix, 0, 16);

        multiplyMatrices(projViewMatrix, projectionMatrix, viewMatrix);
        extractFrustumPlanes();
    }

    private static void multiplyMatrices(float[] result, float[] a, float[] b) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                result[row * 4 + col] =
                    a[row * 4 + 0] * b[0 * 4 + col] +
                    a[row * 4 + 1] * b[1 * 4 + col] +
                    a[row * 4 + 2] * b[2 * 4 + col] +
                    a[row * 4 + 3] * b[3 * 4 + col];
            }
        }
    }

    private static void extractFrustumPlanes() {
        float[] m = projViewMatrix;

        frustumPlanes[0] = m[3] + m[0];
        frustumPlanes[1] = m[7] + m[4];
        frustumPlanes[2] = m[11] + m[8];
        frustumPlanes[3] = m[15] + m[12];

        frustumPlanes[4] = m[3] - m[0];
        frustumPlanes[5] = m[7] - m[4];
        frustumPlanes[6] = m[11] - m[8];
        frustumPlanes[7] = m[15] - m[12];

        frustumPlanes[8] = m[3] + m[1];
        frustumPlanes[9] = m[7] + m[5];
        frustumPlanes[10] = m[11] + m[9];
        frustumPlanes[11] = m[15] + m[13];

        frustumPlanes[12] = m[3] - m[1];
        frustumPlanes[13] = m[7] - m[5];
        frustumPlanes[14] = m[11] - m[9];
        frustumPlanes[15] = m[15] - m[13];

        frustumPlanes[16] = m[3] + m[2];
        frustumPlanes[17] = m[7] + m[6];
        frustumPlanes[18] = m[11] + m[10];
        frustumPlanes[19] = m[15] + m[14];

        frustumPlanes[20] = m[3] - m[2];
        frustumPlanes[21] = m[7] - m[6];
        frustumPlanes[22] = m[11] - m[10];
        frustumPlanes[23] = m[15] - m[14];

        normalizePlanes();
    }

    private static void normalizePlanes() {
        for (int i = 0; i < 6; i++) {
            float length = (float) Math.sqrt(
                frustumPlanes[i * 4] * frustumPlanes[i * 4] +
                frustumPlanes[i * 4 + 1] * frustumPlanes[i * 4 + 1] +
                frustumPlanes[i * 4 + 2] * frustumPlanes[i * 4 + 2]
            );

            if (length > 0.001f) {
                frustumPlanes[i * 4] /= length;
                frustumPlanes[i * 4 + 1] /= length;
                frustumPlanes[i * 4 + 2] /= length;
                frustumPlanes[i * 4 + 3] /= length;
            }
        }
    }

    public static void updateChunkBounds(int chunkId, float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ) {
        if (!initialized || chunkId < 0 || chunkId >= MAX_CHUNKS) return;

        ChunkBounds bounds = chunkBounds[chunkId];
        bounds.minX = minX;
        bounds.minY = minY;
        bounds.minZ = minZ;
        bounds.maxX = maxX;
        bounds.maxY = maxY;
        bounds.maxZ = maxZ;
        bounds.visible = testAABB(bounds);
    }

    private static boolean testAABB(ChunkBounds bounds) {
        for (int i = 0; i < 6; i++) {
            int planeOffset = i * 4;
            float planeX = frustumPlanes[planeOffset];
            float planeY = frustumPlanes[planeOffset + 1];
            float planeZ = frustumPlanes[planeOffset + 2];
            float planeD = frustumPlanes[planeOffset + 3];

            float positiveVertexX = bounds.maxX;
            float positiveVertexY = bounds.maxY;
            float positiveVertexZ = bounds.maxZ;

            if (planeX < 0) positiveVertexX = bounds.minX;
            if (planeY < 0) positiveVertexY = bounds.minY;
            if (planeZ < 0) positiveVertexZ = bounds.minZ;

            if (planeX * positiveVertexX + planeY * positiveVertexY +
                planeZ * positiveVertexZ + planeD < 0) {
                return false;
            }
        }

        return true;
    }

    public static boolean isChunkVisible(int chunkId) {
        if (!initialized || chunkId < 0 || chunkId >= MAX_CHUNKS) return false;
        return chunkBounds[chunkId].visible;
    }

    public static int getVisibleChunkCount() {
        if (!initialized) return 0;

        int count = 0;
        for (int i = 0; i < MAX_CHUNKS; i++) {
            if (chunkBounds[i].visible) count++;
        }
        return count;
    }

    public static void cleanup() {
        Arrays.fill(chunkBounds, null);
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
