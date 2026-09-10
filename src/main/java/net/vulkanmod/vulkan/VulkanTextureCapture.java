package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.MinecraftInstance;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanTextureCapture {
    public static ByteBuffer captureFramebuffer(int width, int height) {
        try {
            GL.createCapabilities();

            int pixelCount = width * height;
            ByteBuffer buffer = BufferUtils.createByteBuffer(pixelCount * 4);

            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

            return buffer;
        } catch (Exception e) {
            VulkanMod.LOGGER.warn("Failed to capture framebuffer: {}", e.getMessage());
            return null;
        }
    }

    public static int getViewportWidth() {
        try {
            GL.createCapabilities();
            IntBuffer viewport = MemoryUtil.memAllocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int width = viewport.get(2);
            MemoryUtil.memFree(viewport);
            return width;
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getViewportHeight() {
        try {
            GL.createCapabilities();
            IntBuffer viewport = MemoryUtil.memAllocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int height = viewport.get(3);
            MemoryUtil.memFree(viewport);
            return height;
        } catch (Exception e) {
            return 0;
        }
    }
}
