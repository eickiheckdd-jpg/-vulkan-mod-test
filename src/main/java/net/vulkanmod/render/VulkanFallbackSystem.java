package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanInstance;
import net.vulkanmod.vulkan.VulkanRenderer;

public class VulkanFallbackSystem {
    private enum FallbackState {
        NONE,
        SWITCHING_TO_OPENGL,
        OPENGL_ACTIVE
    }

    private static FallbackState state = FallbackState.NONE;
    private static boolean vulkanAvailable = true;
    private static int fallbackAttempts = 0;
    private static final int MAX_FALLBACK_ATTEMPTS = 3;

    public static synchronized boolean initialize() {
        if (!VulkanInstance.isVulkan11Available()) {
            VulkanMod.LOGGER.warn("Vulkan 1.1 not available, falling back to OpenGL");
            return fallbackToOpenGL();
        }

        vulkanAvailable = true;
        return true;
    }

    public static synchronized boolean fallbackToOpenGL() {
        if (fallbackAttempts >= MAX_FALLBACK_ATTEMPTS) {
            VulkanMod.LOGGER.error("Max fallback attempts reached, cannot recover");
            return false;
        }

        VulkanMod.LOGGER.info("Falling back to OpenGL rendering...");
        state = FallbackState.SWITCHING_TO_OPENGL;
        fallbackAttempts++;

        try {
            cleanupVulkanResources();
            state = FallbackState.OPENGL_ACTIVE;
            VulkanMod.LOGGER.info("Successfully fell back to OpenGL");
            return true;
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to fallback to OpenGL: {}", e.getMessage());
            state = FallbackState.NONE;
            return false;
        }
    }

    private static void cleanupVulkanResources() {
        try {
            VulkanRenderer.cleanup();
        } catch (Exception e) {
            VulkanMod.LOGGER.warn("Error during Vulkan cleanup: {}", e.getMessage());
        }
    }

    public static synchronized boolean attemptVulkanRecovery() {
        if (state == FallbackState.OPENGL_ACTIVE) {
            VulkanMod.LOGGER.info("Attempting to recover Vulkan rendering...");
            state = FallbackState.NONE;
            return true;
        }
        return false;
    }

    public static boolean isVulkanAvailable() {
        return vulkanAvailable && state != FallbackState.OPENGL_ACTIVE;
    }

    public static boolean isUsingFallback() {
        return state == FallbackState.OPENGL_ACTIVE;
    }

    public static FallbackState getState() {
        return state;
    }

    public static int getFallbackAttempts() {
        return fallbackAttempts;
    }

    public static void reset() {
        state = FallbackState.NONE;
        fallbackAttempts = 0;
    }

    public static void cleanup() {
        reset();
    }
}
