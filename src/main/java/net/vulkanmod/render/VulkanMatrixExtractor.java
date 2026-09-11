package net.vulkanmod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;

public class VulkanMatrixExtractor {
    private static Matrix4f projectionMatrix = new Matrix4f();
    private static Matrix4f viewMatrix = new Matrix4f();
    private static Matrix4f mvpMatrix = new Matrix4f();
    private static float[] projectionArray = new float[16];
    private static float[] viewArray = new float[16];
    private static float[] mvpArray = new float[16];
    private static boolean initialized = false;

    public static void extractMatrices() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.gameRenderer == null) return;

        try {
            GameRenderer gameRenderer = mc.gameRenderer;
            Camera camera = mc.gameRenderer.getCamera();
            if (camera == null) return;

            float partialTick = 1.0f;
            projectionMatrix.set(gameRenderer.getBasicProjectionMatrix(partialTick));

            viewMatrix.identity();
            viewMatrix.rotate(camera.getPitch(), 1.0f, 0.0f, 0.0f);
            viewMatrix.rotate(camera.getYaw(), 0.0f, 1.0f, 0.0f);

            double camX = camera.getCameraPos().x;
            double camY = camera.getCameraPos().y;
            double camZ = camera.getCameraPos().z;
            viewMatrix.translate((float) -camX, (float) -camY, (float) -camZ);

            mvpMatrix.identity();
            mvpMatrix.set(projectionMatrix);
            mvpMatrix.mul(viewMatrix);

            projectionMatrix.get(projectionArray);
            viewMatrix.get(viewArray);
            mvpMatrix.get(mvpArray);
            initialized = true;
        } catch (Exception e) {
            projectionMatrix.identity();
            viewMatrix.identity();
            mvpMatrix.identity();
            initialized = false;
        }
    }

    public static float[] getProjectionMatrix() {
        return projectionArray;
    }

    public static float[] getViewMatrix() {
        return viewArray;
    }

    public static float[] getMVPMatrix() {
        return mvpArray;
    }

    public static Matrix4f getProjection() {
        return projectionMatrix;
    }

    public static Matrix4f getView() {
        return viewMatrix;
    }

    public static Matrix4f getMVP() {
        return mvpMatrix;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
