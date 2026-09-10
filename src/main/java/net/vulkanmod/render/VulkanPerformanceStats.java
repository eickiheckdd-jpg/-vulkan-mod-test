package net.vulkanmod.render;

public class VulkanPerformanceStats {
    private static long lastFrameTime = System.nanoTime();
    private static float fps = 0.0f;
    private static float frameTime = 0.0f;
    private static int frameCount = 0;
    private static float fpsUpdateTimer = 0.0f;
    private static long totalTriangles = 0;
    private static long totalDrawCalls = 0;

    public static synchronized void beginFrame() {
        long now = System.nanoTime();
        frameTime = (now - lastFrameTime) / 1_000_000.0f;
        lastFrameTime = now;
        frameCount++;
        fpsUpdateTimer += frameTime;
        if (fpsUpdateTimer >= 1000.0f) {
            fps = frameCount * 1000.0f / fpsUpdateTimer;
            frameCount = 0;
            fpsUpdateTimer = 0.0f;
        }
        totalTriangles = 0;
        totalDrawCalls = 0;
    }

    public static synchronized void endFrame() {
    }

    public static void addDrawCall(int triangleCount) {
        totalDrawCalls++;
        totalTriangles += triangleCount;
    }

    public static float getFPS() {
        return fps;
    }

    public static float getFrameTime() {
        return frameTime;
    }

    public static long getTotalTriangles() {
        return totalTriangles;
    }

    public static long getTotalDrawCalls() {
        return totalDrawCalls;
    }

    public static void reset() {
        fps = 0.0f;
        frameTime = 0.0f;
        frameCount = 0;
        fpsUpdateTimer = 0.0f;
        totalTriangles = 0;
        totalDrawCalls = 0;
    }
}
