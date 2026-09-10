package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
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
        VulkanModConfig.load();

        if (VulkanModConfig.enableVulkanRenderer) {
            LOGGER.info("Vulkan renderer is ENABLED in config. It will initialize on first frame.");
        } else {
            LOGGER.info("Vulkan renderer is DISABLED in config. Minecraft will use the default renderer.");
        }

        LOGGER.info("VulkanMod initialized successfully!");
    }
}
