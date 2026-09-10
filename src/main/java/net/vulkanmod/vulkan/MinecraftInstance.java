package net.vulkanmod.vulkan;

import org.lwjgl.glfw.GLFWVk;
import org.lwjgl.system.MemoryUtil;

public class MinecraftInstance {
    private static long windowHandle = MemoryUtil.NULL;

    public static void setWindowHandle(long handle) {
        windowHandle = handle;
    }

    public static long getWindowHandle() {
        return windowHandle;
    }
}
