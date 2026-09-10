# VulkanMod - Vulkan 1.1 Renderer for Minecraft 1.21.11

A Vulkan 1.1-based rendering engine replacement for Minecraft Java Edition 1.21.11,
designed for Fabric Loader with specific optimization for low-end Android devices
(OPPO A5x / CPH2727 with Qualcomm Adreno 610 GPU).

## Critical Requirements

- **Minecraft:** 1.21.11
- **Fabric Loader:** 0.18.1
- **Java:** 21
- **Vulkan API:** 1.1.128 ONLY
- **GPU:** Qualcomm Adreno 610
- **Platform:** Android / PojavLauncher
- **RAM:** ~2 GB allocated to Minecraft

## What This Mod Does

Replaces Minecraft's default OpenGL 3.2 renderer with a custom Vulkan 1.1 renderer
that:

1. Uses ONLY Vulkan 1.1 core features (no 1.2 or 1.3 features)
2. Provides runtime diagnostics logging the detected Vulkan version, GPU, and features
3. Falls back gracefully if Vulkan is not available
4. Targets maximum FPS on low-end hardware through:
   - Minimal draw calls
   - Efficient command buffer recording
   - Reduced CPU-GPU synchronization overhead
   - Memory-efficient buffer management

## Vulkan 1.1 Compatibility Audit

### Features NOT Used (Vulkan 1.2+ only)

The following are explicitly NOT used to maintain Vulkan 1.1 compatibility:

- `VkPhysicalDeviceVulkan12Features` / Vulkan 1.2 features
- `VkPhysicalDeviceVulkan13Features` / Vulkan 1.3 features
- Timeline semaphores (`VK_KHR_timeline_semaphore`)
- Descriptor indexing (`VK_EXT_descriptor_indexing`)
- Buffer device address (`VK_KHR_buffer_device_address`)
- `VK_KHR_synchronization2`
- `VK_KHR_spirv_1_4` (requires Vulkan 1.2)
- `VK_KHR_zero_initialize_workgroup_memory`
- Push descriptors (`VK_KHR_push_descriptor`)
- Dynamic rendering (`VK_KHR_dynamic_rendering` - Vulkan 1.3)
- Any 1.2/1.3-specific pipeline or memory features

### Vulkan 1.1 Features Used

- Core Vulkan 1.1 instance creation with `VK_MAKE_VERSION(1, 1, 0)`
- `VkInstanceCreateInfo` with application info
- `VK11.vkGetPhysicalDeviceFeatures2` (available in 1.1 via `VK_KHR_get_physical_device_properties2`)
- `VK_KHR_surface` for window surface creation
- `VK_KHR_swapchain` for framebuffer presentation
- Separate semaphores per frame (no timeline semaphores)
- Traditional render passes (no dynamic rendering)
- Standard descriptor sets (no descriptor indexing)
- Push constants for MVP matrices

### SPIR-V Compatibility

- Target SPIR-V version: 1.3 (supported via `VK_KHR_spirv_1_3` extension in Vulkan 1.1)
- GLSL shaders compiled to SPIR-V using `glslangValidator -V`
- No SPIR-V 1.4+ features (which require Vulkan 1.2)
- No specialization constants requiring 1.2 features

## Build Instructions

### Prerequisites

- OpenJDK 21
- Gradle 8.11+
- Internet access to download dependencies

### Building with Gradle

```bash
# Clone or copy this repository
cd vulkanmod

# Build the mod
./gradlew build

# The JAR will be in build/libs/vulkanmod-1.0.0.jar
```

### Building with javac (fallback)

```bash
# Compile with stubs for external dependencies
bash scripts/build-java.sh
```

### Compiling Shaders

```bash
# Install glslangValidator first, then:
bash scripts/compile-shaders.sh
```

## Installation

1. Install Fabric Loader 0.18.1 for Minecraft 1.21.11
2. Install Fabric API 0.141.1+1.21.11
3. Copy `vulkanmod-1.0.0.jar` to `.minecraft/mods/`
4. Launch Minecraft with the Vulkan renderer

## Runtime Diagnostics

On startup, the mod logs:

```
=== Vulkan 1.1 Renderer Diagnostics ===
Vulkan supported: true
Vulkan API version: 1.1.128
Vulkan 1.1 available: true
GPU: Adreno (TM) 610
Vendor ID: 0x5143
Device ID: 0x...
Driver version: 0x...
========================================
```

If Vulkan 1.1 is not available, the mod logs an error and falls back to OpenGL.

## Files Changed

### Core Renderer Classes
- `src/main/java/net/vulkanmod/vulkan/VulkanInstance.java` - Vulkan instance creation and device selection
- `src/main/java/net/vulkanmod/vulkan/VulkanDevice.java` - Logical device and queue management
- `src/main/java/net/vulkanmod/vulkan/VulkanSwapchain.java` - Swapchain and surface management
- `src/main/java/net/vulkanmod/vulkan/VulkanRenderPass.java` - Render pass creation
- `src/main/java/net/vulkanmod/vulkan/VulkanFramebuffer.java` - Framebuffer management
- `src/main/java/net/vulkanmod/vulkan/VulkanPipeline.java` - Graphics pipeline with shaders
- `src/main/java/net/vulkanmod/vulkan/VulkanCommandBuffer.java` - Command buffer recording
- `src/main/java/net/vulkanmod/vulkan/VulkanRenderer.java` - Main renderer with frame loop
- `src/main/java/net/vulkanmod/vulkan/MinecraftInstance.java` - Window handle management

### Configuration and Entry Point
- `src/main/java/net/vulkanmod/Initializer.java` - Mod entry point
- `src/main/java/net/vulkanmod/config/VulkanModConfig.java` - Configuration management

### Mixins
- `src/main/java/net/vulkanmod/mixin/MinecraftMixin.java` - Captures window handle
- `src/main/java/net/vulkanmod/mixin/GameRendererMixin.java` - Hooks into render loop
- `src/main/java/net/vulkanmod/mixin/WindowMixin.java` - Handles window resize
- `src/main/java/net/vulkanmod/mixin/RenderTargetMixin.java` - Render target integration

### Shaders
- `src/main/resources/assets/vulkanmod/shaders/terrain.vert` - Vertex shader (GLSL)
- `src/main/resources/assets/vulkanmod/shaders/terrain.frag` - Fragment shader (GLSL)

### Build Configuration
- `build.gradle` - Gradle build script
- `gradle.properties` - Project properties
- `settings.gradle` - Gradle settings
- `src/main/resources/fabric.mod.json` - Fabric mod metadata
- `src/main/resources/vulkanmod.mixins.json` - Mixin configuration

## Vulkan 1.1 Only Guarantee

The codebase has been audited to ensure NO Vulkan 1.2 or 1.3 features are used:

- Search for `VK_API_VERSION_1_2` - NOT FOUND
- Search for `VK_API_VERSION_1_3` - NOT FOUND
- Search for `VkPhysicalDeviceVulkan12Features` - NOT FOUND
- Search for `VkPhysicalDeviceVulkan13Features` - NOT FOUND
- Search for `timeline semaphore` - NOT FOUND
- Search for `descriptor_indexing` - NOT FOUND
- Search for `buffer_device_address` - NOT FOUND
- Search for `synchronization2` - NOT FOUND
- Search for `dynamic_rendering` - NOT FOUND
- Search for `spirv_1_4` - NOT FOUND

## Known Limitations

1. **Shader compilation required**: The SPIR-V shader binaries (`.spv`) must be compiled from the GLSL sources using `glslangValidator` before the mod will render anything.

2. **Basic rendering only**: This is a minimal working renderer. Full chunk rendering, entity rendering, particles, and GUI require additional implementation beyond the core Vulkan setup.

3. **Minecraft 1.21.11 mappings**: Mixin targets may need adjustment based on actual obfuscation mappings.

4. **No benchmark results yet**: Performance comparison against Sodium requires actual device testing.

5. **LWJGL 3 dependency**: The mod requires LWJGL 3 with Vulkan bindings, which are provided transitively through Fabric Loader.

## Performance Targets for Adreno 610

- Target: 60 FPS at 720p with 8-12 chunk render distance
- Strategy: Minimize draw calls, reduce CPU overhead, batch geometry
- Memory: Target < 512 MB for renderer resources within 2 GB budget

## License

LGPL-3.0
