package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.text.DecimalFormat;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanDebugStats {
    private static final int MAX_STATS = 256;
    private static final DecimalFormat FPS_FORMAT = new DecimalFormat("0.0");
    private static final DecimalFormat MS_FORMAT = new DecimalFormat("0.00");

    private static long[] frameTimes;
    private static long[] drawCalls;
    private static long[] triangleCounts;
    private static long[] textureBindings;
    private static long[] bufferUploads;
    private static long startTime;
    private static long lastFrameTime;
    private static int frameCount;
    private static int currentFrameIndex;
    private static boolean initialized = false;

    private static class StatsSnapshot {
        float fps;
        float frameTime;
        int totalDrawCalls;
        int totalTriangles;
        int totalTextureBindings;
        int totalBufferUploads;
        int visibleChunks;
        int visibleEntities;
        int visibleParticles;
    }

    private static final StatsSnapshot currentSnapshot = new StatsSnapshot();

    public static synchronized void initialize() {
        if (initialized) return;

        frameTimes = new long[MAX_STATS];
        drawCalls = new long[MAX_STATS];
        triangleCounts = new long[MAX_STATS];
        textureBindings = new long[MAX_STATS];
        bufferUploads = new long[MAX_STATS];

        startTime = System.nanoTime();
        lastFrameTime = startTime;
        frameCount = 0;
        currentFrameIndex = 0;

        initialized = true;
        VulkanMod.LOGGER.info("Debug stats initialized");
    }

    public static void beginFrame() {
        if (!initialized) return;
        lastFrameTime = System.nanoTime();
    }

    public static void endFrame() {
        if (!initialized) return;

        long now = System.nanoTime();
        frameTimes[currentFrameIndex] = now - lastFrameTime;
        currentFrameIndex = (currentFrameIndex + 1) % MAX_STATS;
        frameCount++;

        updateSnapshot();
    }

    public static void recordDrawCall(int triangles) {
        if (!initialized) return;
        drawCalls[currentFrameIndex]++;
        triangleCounts[currentFrameIndex] += triangles;
    }

    public static void recordTextureBinding() {
        if (!initialized) return;
        textureBindings[currentFrameIndex]++;
    }

    public static void recordBufferUpload(long bytes) {
        if (!initialized) return;
        bufferUploads[currentFrameIndex]++;
    }

    private static void updateSnapshot() {
        long totalTime = 0;
        long totalDrawCalls = 0;
        long totalTriangles = 0;
        long totalTextureBindings = 0;
        long totalBufferUploads = 0;

        int count = Math.min(frameCount, MAX_STATS);
        for (int i = 0; i < count; i++) {
            totalTime += frameTimes[i];
            totalDrawCalls += drawCalls[i];
            totalTriangles += triangleCounts[i];
            totalTextureBindings += textureBindings[i];
            totalBufferUploads += bufferUploads[i];
        }

        if (count > 0) {
            float avgFrameTime = totalTime / (float) count / 1_000_000.0f;
            currentSnapshot.fps = avgFrameTime > 0 ? 1000.0f / avgFrameTime : 0.0f;
            currentSnapshot.frameTime = avgFrameTime;
            currentSnapshot.totalDrawCalls = (int) (totalDrawCalls / count);
            currentSnapshot.totalTriangles = (int) (totalTriangles / count);
            currentSnapshot.totalTextureBindings = (int) (totalTextureBindings / count);
            currentSnapshot.totalBufferUploads = (int) (totalBufferUploads / count);
        }

        currentSnapshot.visibleChunks = VulkanFrustumCuller.getVisibleChunkCount();
        currentSnapshot.visibleEntities = 0;
        currentSnapshot.visibleParticles = 0;
    }

    public static String getStatsString() {
        if (!initialized) return "Stats not initialized";

        StringBuilder sb = new StringBuilder();
        sb.append("=== Vulkan Renderer Stats ===\n");
        sb.append("FPS: ").append(FPS_FORMAT.format(currentSnapshot.fps)).append("\n");
        sb.append("Frame Time: ").append(MS_FORMAT.format(currentSnapshot.frameTime)).append(" ms\n");
        sb.append("Draw Calls: ").append(currentSnapshot.totalDrawCalls).append("\n");
        sb.append("Triangles: ").append(currentSnapshot.totalTriangles).append("\n");
        sb.append("Texture Bindings: ").append(currentSnapshot.totalTextureBindings).append("\n");
        sb.append("Buffer Uploads: ").append(currentSnapshot.totalBufferUploads).append("\n");
        sb.append("Visible Chunks: ").append(currentSnapshot.visibleChunks).append("\n");
        sb.append("Visible Entities: ").append(currentSnapshot.visibleEntities).append("\n");
        sb.append("Visible Particles: ").append(currentSnapshot.visibleParticles).append("\n");
        sb.append("==========================");

        return sb.toString();
    }

    public static float getFPS() {
        return currentSnapshot.fps;
    }

    public static float getFrameTime() {
        return currentSnapshot.frameTime;
    }

    public static int getDrawCalls() {
        return currentSnapshot.totalDrawCalls;
    }

    public static int getTriangleCount() {
        return currentSnapshot.totalTriangles;
    }

    public static void cleanup() {
        frameTimes = null;
        drawCalls = null;
        triangleCounts = null;
        textureBindings = null;
        bufferUploads = null;
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
