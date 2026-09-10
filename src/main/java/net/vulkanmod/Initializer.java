package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.vulkanmod.config.VulkanModConfig;
import net.vulkanmod.vulkan.VulkanRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Initializer implements ClientModInitializer {
    public static final String MOD_ID = "vulkanmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("VulkanMod initializing for Minecraft 1.21.11 (Vulkan 1.1 renderer)");

        if (!VulkanRenderer.isVulkanAvailable()) {
            LOGGER.error("Vulkan is NOT available on this device. VulkanMod requires Vulkan 1.1 support.");
            LOGGER.error("The game will continue with the default OpenGL renderer.");
            return;
        }

        try {
            VulkanRenderer.initialize();
            VulkanModConfig.load();
            LOGGER.info("VulkanMod initialized successfully!");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Vulkan renderer: {}", e.getMessage(), e);
            LOGGER.error("Falling back to OpenGL renderer.");
        }
    }
}
