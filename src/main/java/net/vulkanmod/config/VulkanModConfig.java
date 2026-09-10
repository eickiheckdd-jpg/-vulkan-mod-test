package net.vulkanmod.config;

import net.vulkanmod.VulkanMod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VulkanModConfig {
    private static final String CONFIG_FILE = "config/vulkanmod/config.properties";
    private static final Path CONFIG_PATH = Paths.get(CONFIG_FILE);

    public static class Config {
        public boolean enableVulkanRenderer = true;
        public boolean enableMultiThreading = true;
        public boolean enableIndirectDraw = true;
        public boolean enableChunkBatching = true;
        public boolean enableTextureStreaming = true;
        public boolean enableFrustumCulling = true;
        public boolean enableEntityRendering = true;
        public boolean enableParticleRendering = true;
        public boolean enableGUIRendering = true;
        public boolean enableDebugStats = false;
        public boolean enableDebugOverlay = false;
        public int maxChunkMeshes = 256;
        public int maxEntities = 1024;
        public int maxParticles = 8192;
        public int maxDrawCalls = 10000;
        public int renderThreadPriority = Thread.MAX_PRIORITY;
        public float renderScale = 1.0f;
        public int maxFPS = 144;
        public boolean enableVSync = false;
        public int antiAliasing = 0;
    }

    private static Config config;

    public static synchronized Config getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    public static synchronized void loadConfig() {
        config = new Config();

        try {
            if (Files.exists(CONFIG_PATH)) {
                try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;

                        String[] parts = line.split("=", 2);
                        if (parts.length != 2) continue;

                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        switch (key) {
                            case "enableVulkanRenderer" -> config.enableVulkanRenderer = Boolean.parseBoolean(value);
                            case "enableMultiThreading" -> config.enableMultiThreading = Boolean.parseBoolean(value);
                            case "enableIndirectDraw" -> config.enableIndirectDraw = Boolean.parseBoolean(value);
                            case "enableChunkBatching" -> config.enableChunkBatching = Boolean.parseBoolean(value);
                            case "enableTextureStreaming" -> config.enableTextureStreaming = Boolean.parseBoolean(value);
                            case "enableFrustumCulling" -> config.enableFrustumCulling = Boolean.parseBoolean(value);
                            case "enableEntityRendering" -> config.enableEntityRendering = Boolean.parseBoolean(value);
                            case "enableParticleRendering" -> config.enableParticleRendering = Boolean.parseBoolean(value);
                            case "enableGUIRendering" -> config.enableGUIRendering = Boolean.parseBoolean(value);
                            case "enableDebugStats" -> config.enableDebugStats = Boolean.parseBoolean(value);
                            case "enableDebugOverlay" -> config.enableDebugOverlay = Boolean.parseBoolean(value);
                            case "maxChunkMeshes" -> config.maxChunkMeshes = Integer.parseInt(value);
                            case "maxEntities" -> config.maxEntities = Integer.parseInt(value);
                            case "maxParticles" -> config.maxParticles = Integer.parseInt(value);
                            case "maxDrawCalls" -> config.maxDrawCalls = Integer.parseInt(value);
                            case "renderThreadPriority" -> config.renderThreadPriority = Integer.parseInt(value);
                            case "renderScale" -> config.renderScale = Float.parseFloat(value);
                            case "maxFPS" -> config.maxFPS = Integer.parseInt(value);
                            case "enableVSync" -> config.enableVSync = Boolean.parseBoolean(value);
                            case "antiAliasing" -> config.antiAliasing = Integer.parseInt(value);
                        }
                    }
                }
                VulkanMod.LOGGER.info("Configuration loaded from {}", CONFIG_FILE);
            } else {
                saveConfig();
                VulkanMod.LOGGER.info("Default configuration created at {}", CONFIG_FILE);
            }
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to load configuration: {}", e.getMessage());
            config = new Config();
        }
    }

    public static synchronized void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("# VulkanMod Configuration\n");
            sb.append("# Generated automatically\n\n");
            sb.append("enableVulkanRenderer=").append(config.enableVulkanRenderer).append("\n");
            sb.append("enableMultiThreading=").append(config.enableMultiThreading).append("\n");
            sb.append("enableIndirectDraw=").append(config.enableIndirectDraw).append("\n");
            sb.append("enableChunkBatching=").append(config.enableChunkBatching).append("\n");
            sb.append("enableTextureStreaming=").append(config.enableTextureStreaming).append("\n");
            sb.append("enableFrustumCulling=").append(config.enableFrustumCulling).append("\n");
            sb.append("enableEntityRendering=").append(config.enableEntityRendering).append("\n");
            sb.append("enableParticleRendering=").append(config.enableParticleRendering).append("\n");
            sb.append("enableGUIRendering=").append(config.enableGUIRendering).append("\n");
            sb.append("enableDebugStats=").append(config.enableDebugStats).append("\n");
            sb.append("enableDebugOverlay=").append(config.enableDebugOverlay).append("\n");
            sb.append("maxChunkMeshes=").append(config.maxChunkMeshes).append("\n");
            sb.append("maxEntities=").append(config.maxEntities).append("\n");
            sb.append("maxParticles=").append(config.maxParticles).append("\n");
            sb.append("maxDrawCalls=").append(config.maxDrawCalls).append("\n");
            sb.append("renderThreadPriority=").append(config.renderThreadPriority).append("\n");
            sb.append("renderScale=").append(config.renderScale).append("\n");
            sb.append("maxFPS=").append(config.maxFPS).append("\n");
            sb.append("enableVSync=").append(config.enableVSync).append("\n");
            sb.append("antiAliasing=").append(config.antiAliasing).append("\n");

            Files.write(CONFIG_PATH, sb.toString().getBytes());
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to save configuration: {}", e.getMessage());
        }
    }

    public static synchronized void resetConfig() {
        config = new Config();
        saveConfig();
        VulkanMod.LOGGER.info("Configuration reset to defaults");
    }
}
