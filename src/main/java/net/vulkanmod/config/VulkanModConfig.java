package net.vulkanmod.config;

import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.VulkanMod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class VulkanModConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("vulkanmod.properties");

    public static boolean enableVulkanRenderer = true;
    public static boolean enableDebugMarkers = false;
    public static boolean enableValidationLayers = false;
    public static int renderDistanceScale = 100;

    public static void load() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try {
                props.load(Files.newInputStream(CONFIG_PATH));
                enableVulkanRenderer = Boolean.parseBoolean(props.getProperty("enableVulkanRenderer", "true"));
                enableDebugMarkers = Boolean.parseBoolean(props.getProperty("enableDebugMarkers", "false"));
                enableValidationLayers = Boolean.parseBoolean(props.getProperty("enableValidationLayers", "false"));
                renderDistanceScale = Integer.parseInt(props.getProperty("renderDistanceScale", "100"));
            } catch (IOException e) {
                VulkanMod.LOGGER.warn("Failed to load config: {}", e.getMessage());
            }
        } else {
            saveDefaults(props);
        }

        VulkanMod.LOGGER.info("Config loaded - Vulkan renderer: {}", enableVulkanRenderer);
    }

    private static void saveDefaults(Properties props) {
        props.setProperty("enableVulkanRenderer", String.valueOf(enableVulkanRenderer));
        props.setProperty("enableDebugMarkers", String.valueOf(enableDebugMarkers));
        props.setProperty("enableValidationLayers", String.valueOf(enableValidationLayers));
        props.setProperty("renderDistanceScale", String.valueOf(renderDistanceScale));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            props.store(Files.newOutputStream(CONFIG_PATH), "VulkanMod Configuration");
        } catch (IOException e) {
            VulkanMod.LOGGER.warn("Failed to save config: {}", e.getMessage());
        }
    }
}
