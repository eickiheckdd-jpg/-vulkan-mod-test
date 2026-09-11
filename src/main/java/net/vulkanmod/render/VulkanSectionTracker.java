package net.vulkanmod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Vector3i;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class VulkanSectionTracker {
    private static final Map<Long, Vector3i> sectionPositions = new ConcurrentHashMap<>();
    private static final ThreadLocal<Vector3i> currentOrigin = ThreadLocal.withInitial(() -> new Vector3i());
    private static int lastSectionX = Integer.MIN_VALUE;
    private static int lastSectionZ = Integer.MIN_VALUE;

    public static void updateFromCamera() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.getCameraEntity() == null) return;

        double camX = mc.getCameraEntity().getX();
        double camZ = mc.getCameraEntity().getZ();

        int sectionX = ChunkSectionPos.getSectionCoord(camX);
        int sectionZ = ChunkSectionPos.getSectionCoord(camZ);

        if (sectionX == lastSectionX && sectionZ == lastSectionZ) return;
        lastSectionX = sectionX;
        lastSectionZ = sectionZ;

        sectionPositions.clear();

        int renderDistance = mc.options.getViewDistance().getValue();
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int sx = sectionX + dx;
                int sz = sectionZ + dz;
                long key = ChunkSectionPos.asLong(sx, 0, sz);
                Vector3i origin = new Vector3i(sx << 4, 0, sz << 4);
                sectionPositions.put(key, origin);
            }
        }
    }

    public static void setCurrentSection(ChunkSectionPos sectionPos) {
        long key = sectionPos.asLong();
        Vector3i origin = sectionPositions.get(key);
        if (origin == null) {
            origin = new Vector3i(sectionPos.getSectionX() << 4, sectionPos.getSectionY() << 4, sectionPos.getSectionZ() << 4);
        }
        currentOrigin.set(origin);
    }

    public static Vector3i getCurrentOrigin() {
        return currentOrigin.get();
    }

    public static Vector3i getNearestOrigin(int blockX, int blockY, int blockZ) {
        int sectionX = ChunkSectionPos.getSectionCoord(blockX);
        int sectionY = ChunkSectionPos.getSectionCoord(blockY);
        int sectionZ = ChunkSectionPos.getSectionCoord(blockZ);
        long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
        Vector3i origin = sectionPositions.get(key);
        if (origin == null) {
            origin = new Vector3i(sectionX << 4, sectionY << 4, sectionZ << 4);
        }
        return origin;
    }

    public static void clear() {
        sectionPositions.clear();
        currentOrigin.remove();
        lastSectionX = Integer.MIN_VALUE;
        lastSectionZ = Integer.MIN_VALUE;
    }
}
