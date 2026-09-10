package net.vulkanmod.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;

public class VulkanMatrixExtractor {
    private static Matrix4f projectionMatrix = new Matrix4f();
    private static Matrix4f modelViewMatrix = new Matrix4f();
    private static Matrix4f mvpMatrix = new Matrix4f();
    private static float[] projectionArray = new float[16];
    private static float[] modelViewArray = new float[16];
    private static float[] mvpArray = new float[16];

    public static void extractMatrices() {
        try {
        } catch (Exception e) {
            projectionMatrix.identity();
            modelViewMatrix.identity();
            mvpMatrix.identity();
        }
    }

    public static float[] getProjectionMatrix() {
        return projectionArray;
    }

    public static float[] getModelViewMatrix() {
        return modelViewArray;
    }

    public static float[] getMVPMatrix() {
        return mvpArray;
    }

    public static Matrix4f getProjection() {
        return projectionMatrix;
    }

    public static Matrix4f getModelView() {
        return modelViewMatrix;
    }

    public static Matrix4f getMVP() {
        return mvpMatrix;
    }
}
