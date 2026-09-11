package net.vulkanmod.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3i;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class VulkanSectionTracker {
    private static final Map<Long, Vector3i> sectionPositions = new ConcurrentHashMap<>();
    private static final ThreadLocal<Vector3i> currentOrigin = ThreadLocal.withInitial(() -> new Vector3i());
    private static int lastSectionX = Integer.MIN_VALUE;
    private static int lastSectionZ = Integer.MIN_VALUE;

    public static void updateFromCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.cameraEntity == null) return;

        double camX = mc.cameraEntity.getX();
        double camZ = mc.cameraEntity.getZ();

        int sectionX = SectionPos.posToSectionCoord(camX);
        int sectionZ = SectionPos.posToSectionCoord(camZ);

        if (sectionX == lastSectionX && sectionZ == lastSectionZ) return;
        lastSectionX = sectionX;
        lastSectionZ = sectionZ;

        sectionPositions.clear();

        int renderDistance = mc.level.getRenderDistance();
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int sx = sectionX + dx;
                int sz = sectionZ + dz;
                long key = SectionPos.asLong(sx, 0, sz);
                Vector3i origin = new Vector3i(sx << 4, 0, sz << 4);
                sectionPositions.put(key, origin);
            }
        }
    }

    public static void setCurrentSection(SectionPos sectionPos) {
        long key = sectionPos.asLong();
        Vector3i origin = sectionPositions.get(key);
        if (origin == null) {
            origin = new Vector3i(sectionPos.minBlockX(), sectionPos.minBlockY(), sectionPos.minBlockZ());
        }
        currentOrigin.set(origin);
    }

    public static Vector3i getCurrentOrigin() {
        return currentOrigin.get();
    }

    public static Vector3i getNearestOrigin(int blockX, int blockY, int blockZ) {
        int sectionX = SectionPos.posToSectionCoord(blockX);
        int sectionY = SectionPos.posToSectionCoord(blockY);
        int sectionZ = SectionPos.posToSectionCoord(blockZ);
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
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
