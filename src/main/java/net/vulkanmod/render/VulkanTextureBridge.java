package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanDevice;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanTextureBridge {
    private static final Map<Integer, Long> textureMap = new HashMap<>();
    private static boolean initialized = false;
    private static long stagingBuffer;
    private static long stagingBufferMemory;

    public static synchronized void initialize() {
        if (initialized) return;

        createStagingBuffer();

        initialized = true;
        VulkanMod.LOGGER.info("Texture bridge initialized");
    }

    private static void createStagingBuffer() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanDevice.getDevice();

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            bufferInfo.size(16 * 1024 * 1024);
            bufferInfo.usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            int result = vkCreateBuffer(device, bufferInfo, null, pBuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create texture staging buffer: " + result);
            }
            stagingBuffer = pBuffer.get(0);

            VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, memRequirements);

            int memoryTypeIndex = VulkanDevice.findMemoryType(
                memRequirements.memoryTypeBits(),
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            allocInfo.allocationSize(memRequirements.size());
            allocInfo.memoryTypeIndex(memoryTypeIndex);

            LongBuffer pBufferMemory = stack.mallocLong(1);
            result = vkAllocateMemory(device, allocInfo, null, pBufferMemory);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate texture staging memory: " + result);
            }
            stagingBufferMemory = pBufferMemory.get(0);

            result = vkBindBufferMemory(device, stagingBuffer, stagingBufferMemory, 0);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to bind texture staging memory: " + result);
            }
        }
    }

    public static void registerTexture(int textureId, long imageView) {
        textureMap.put(textureId, imageView);
    }

    public static long getTextureImageView(int textureId) {
        return textureMap.getOrDefault(textureId, NULL);
    }

    public static void uploadTextureData(int textureId, ByteBuffer pixelData, int width, int height) {
        if (pixelData == null || pixelData.remaining() == 0) return;

        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(VulkanDevice.getDevice(), stagingBufferMemory, 0, pixelData.remaining(), 0, pData);
            if (result == VK_SUCCESS) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(pixelData), pData.get(0), pixelData.remaining());
                vkUnmapMemory(VulkanDevice.getDevice(), stagingBufferMemory);
            }
        }
    }

    public static int getTextureCount() {
        return textureMap.size();
    }

    public static void cleanup() {
        textureMap.clear();
        if (stagingBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), stagingBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), stagingBufferMemory, null);
            stagingBuffer = NULL;
            stagingBufferMemory = NULL;
        }
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
