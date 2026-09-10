# Build and Installation Guide for VulkanMod

## Building the Mod

### Option 1: Gradle (Recommended)

This is the standard Fabric mod build method.

```bash
# 1. Ensure you have OpenJDK 21 installed
java -version  # Should show version 21

# 2. Download the project
# (If you cloned from git, you already have it)

# 3. Build
./gradlew build

# 4. The JAR is at: build/libs/vulkanmod-1.0.0.jar
```

### Option 2: Manual Compilation

If Gradle cannot resolve dependencies (e.g., due to network issues):

```bash
# 1. Ensure you have OpenJDK 21
java -version

# 2. Compile stub classes (for external dependencies)
bash scripts/build-java.sh

# 3. The compiled classes are in build/classes/
```

### Compiling Shaders

The SPIR-V shader binaries must be compiled before the mod will render:

```bash
# Install glslangValidator (part of Vulkan SDK or spirv-tools)
# Ubuntu/Debian:
sudo apt install spirv-tools glslang-tools

# Compile shaders
bash scripts/compile-shaders.sh

# This produces:
# src/main/resources/assets/vulkanmod/shaders/terrain.vert.spv
# src/main/resources/assets/vulkanmod/shaders/terrain.frag.spv
```

## Installing on PojavLauncher

### Prerequisites

- PojavLauncher (latest version)
- Minecraft 1.21.11 installed
- Fabric Loader 0.18.1 installed
- Fabric API 0.141.1+1.21.11 installed

### Installation Steps

1. **Copy the JAR:**
   ```bash
   cp build/libs/vulkanmod-1.0.0.jar /path/to/.minecraft/mods/
   ```

2. **Enable Vulkan in PojavLauncher:**
   - Open PojavLauncher settings
   - Go to "Graphics" or "Renderer"
   - Select "Vulkan" (not OpenGL)
   - Ensure Vulkan 1.1 is supported

3. **Launch Minecraft:**
   - Select Minecraft 1.21.11
   - Click "Play"
   - Watch the log for Vulkan initialization messages

### Verifying Vulkan 1.1 is Used

Check the log file (`.minecraft/logs/latest.log`) for:

```
[13:45:22.847] [main/INFO]: Vulkan supported: true
[13:45:22.847] [main/INFO]: Vulkan API version: 1.1.128
[13:45:22.847] [main/INFO]: Vulkan 1.1 available: true
[13:45:22.847] [main/INFO]: GPU: Adreno (TM) 610
```

If you see "Vulkan supported: false", the renderer will fall back to OpenGL.

## Troubleshooting

### "Vulkan is NOT available on this device"

- Ensure your device supports Vulkan 1.1
- Update your GPU drivers
- Check that PojavLauncher is configured for Vulkan

### "Failed to create Vulkan instance"

- Check the log for specific error codes
- Ensure no other mod is using Vulkan
- Restart PojavLauncher

### Shader compilation errors

- Install glslangValidator: `sudo apt install glslang-tools`
- Re-run `bash scripts/compile-shaders.sh`
- Ensure `.spv` files are in `src/main/resources/assets/vulkanmod/shaders/`

### Black screen / no rendering

- The renderer is currently minimal (displays a gray background)
- Full chunk rendering requires additional implementation
- Check for shader compilation errors in the log

## Performance Tips for Adreno 610

1. **Render Distance:** Keep at 8-12 chunks for 60 FPS
2. **Resolution:** Use 720p (1280x720) for best performance
3. **VSync:** Disable in PojavLauncher settings
4. **Memory:** Ensure Java has at least 2 GB (`-Xmx2G`)
5. **Thermal Throttling:** Avoid long sessions in hot environments

## Benchmarking Against Sodium

To compare with Sodium:

1. Install Sodium (same version for MC 1.21.11)
2. Use identical settings:
   - Render distance: 8 chunks
   - Simulation distance: 4 chunks
   - Resolution: 1280x720
   - Graphics: Fast
   - Entity count: Same world
3. Measure:
   - Average FPS (use F3 screen)
   - 1% low FPS (use Spark profiler or similar)
   - Frame time variance

**Note:** This Vulkan renderer currently provides a basic framework.
Sodium will likely outperform it until full chunk rendering is implemented.

## Development

### Project Structure

```
vulkanmod/
├── src/main/java/net/vulkanmod/
│   ├── Initializer.java          # Entry point
│   ├── config/
│   │   └── VulkanModConfig.java  # Configuration
│   ├── mixin/
│   │   ├── MinecraftMixin.java   # Window handle hook
│   │   ├── GameRendererMixin.java # Render loop hook
│   │   ├── WindowMixin.java      # Resize handler
│   │   └── RenderTargetMixin.java # Render target
│   └── vulkan/
│       ├── VulkanInstance.java   # Instance creation
│       ├── VulkanDevice.java     # Device management
│       ├── VulkanSwapchain.java  # Swapchain
│       ├── VulkanRenderPass.java # Render pass
│       ├── VulkanFramebuffer.java # Framebuffer
│       ├── VulkanPipeline.java   # Graphics pipeline
│       ├── VulkanCommandBuffer.java # Command buffers
│       ├── VulkanRenderer.java   # Main renderer
│       └── MinecraftInstance.java # Window handle
├── src/main/resources/
│   ├── fabric.mod.json
│   ├── vulkanmod.mixins.json
│   └── assets/vulkanmod/shaders/
│       ├── terrain.vert          # Vertex shader (GLSL)
│       └── terrain.frag          # Fragment shader (GLSL)
├── build.gradle
├── gradle.properties
└── settings.gradle
```

### Adding New Shaders

1. Write GLSL shader in `src/main/resources/assets/vulkanmod/shaders/`
2. Compile with `glslangValidator -V shader.vert -o shader.vert.spv`
3. Update `VulkanPipeline.java` to load the new shader

### Adding Chunk Rendering

1. Create chunk mesh builder (`ChunkMeshBuilder.java`)
2. Implement vertex buffer management
3. Add chunk render commands to `VulkanCommandBuffer.java`
4. Update `VulkanRenderer.java` to render chunk geometry

## Contributing

This is a proof-of-concept Vulkan 1.1 renderer for Minecraft 1.21.11.
Contributions should maintain Vulkan 1.1 compatibility.

Before submitting:
1. Run the Vulkan 1.1 audit (`VULKAN_1.1_AUDIT.md`)
2. Test on a Vulkan 1.1 device
3. Verify no memory leaks with long play sessions
4. Check GC pressure in render loop
