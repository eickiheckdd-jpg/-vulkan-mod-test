package net.vulkanmod.render;

import net.vulkanmod.render.VulkanRenderStateManager;

public class VulkanRenderStateTranslator {
    private static int currentTopology = 5;
    private static boolean depthTestEnabled = true;
    private static boolean depthMaskEnabled = true;
    private static boolean blendEnabled = false;

    public static void syncFromOpenGL() {
    }

    public static void applyToVulkan(long commandBuffer) {
        VulkanRenderStateManager.applyState(commandBuffer);
    }

    public static int getVulkanTopology() {
        return currentTopology;
    }

    public static int getBlendMode() {
        return blendEnabled ? 1 : 0;
    }

    public static int getActiveTextureUnit() {
        return 0;
    }

    public static void setActiveTextureUnit(int unit) {
    }
}
