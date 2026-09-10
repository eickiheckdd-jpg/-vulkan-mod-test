package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanDevice;
import net.vulkanmod.vulkan.VulkanSwapchain;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanIndirectDrawSystem {
    private static long indirectBuffer;
    private static long indirectBufferMemory;
    private static long drawCountBuffer;
    private static long drawCountBufferMemory;
    private static int maxDrawCount = 10000;
    private static boolean initialized = false;

    private static class IndirectDrawCommand {
        int vertexCount;
        int instanceCount;
        int firstVertex;
        int firstInstance;
    }

    public static synchronized void initialize() {
        if (initialized) return;

        try (MemoryStack stack = stackPush()) {
            createIndirectBuffer(stack);
            createDrawCountBuffer(stack);

            initialized = true;
            VulkanMod.LOGGER.info("Indirect draw system initialized with max {} draw calls", maxDrawCount);
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to initialize indirect draw system: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createIndirectBuffer(MemoryStack stack) {
        long bufferSize = maxDrawCount * 4 * 4; // 4 ints per draw command

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create indirect buffer: " + result);
        }
        indirectBuffer = pBuffer.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), indirectBuffer, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pBufferMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pBufferMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate indirect buffer memory: " + result);
        }
        indirectBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), indirectBuffer, indirectBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind indirect buffer memory: " + result);
        }
    }

    private static void createDrawCountBuffer(MemoryStack stack) {
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(4); // 1 int for draw count
        bufferInfo.usage(VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create draw count buffer: " + result);
        }
        drawCountBuffer = pBuffer.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), drawCountBuffer, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pBufferMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pBufferMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate draw count buffer memory: " + result);
        }
        drawCountBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), drawCountBuffer, drawCountBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind draw count buffer memory: " + result);
        }
    }

    public static void submitDrawCommands(List<IndirectDrawCommand> commands) {
        if (!initialized || commands.isEmpty()) return;

        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(VulkanDevice.getDevice(), indirectBufferMemory, 0,
                commands.size() * 16L, 0, pData);
            if (result == VK_SUCCESS) {
                long dataPtr = pData.get(0);
                for (int i = 0; i < commands.size(); i++) {
                    IndirectDrawCommand cmd = commands.get(i);
                    MemoryUtil.memPutInt(dataPtr + i * 16L, cmd.vertexCount);
                    MemoryUtil.memPutInt(dataPtr + i * 16L + 4L, cmd.instanceCount);
                    MemoryUtil.memPutInt(dataPtr + i * 16L + 8L, cmd.firstVertex);
                    MemoryUtil.memPutInt(dataPtr + i * 16L + 12L, cmd.firstInstance);
                }
                vkUnmapMemory(VulkanDevice.getDevice(), indirectBufferMemory);
            }

            // Update draw count
            PointerBuffer pCountData = stack.mallocPointer(1);
            result = vkMapMemory(VulkanDevice.getDevice(), drawCountBufferMemory, 0, 4, 0, pCountData);
            if (result == VK_SUCCESS) {
                MemoryUtil.memPutInt(pCountData.get(0), commands.size());
                vkUnmapMemory(VulkanDevice.getDevice(), drawCountBufferMemory);
            }
        }
    }

    public static void executeIndirectDraw(long commandBuffer) {
        if (!initialized) return;

        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
            vkCmdDrawIndirect(cmdBuf, indirectBuffer, 0, maxDrawCount, 16);
        }
    }

    public static long getIndirectBuffer() {
        return indirectBuffer;
    }

    public static long getDrawCountBuffer() {
        return drawCountBuffer;
    }

    public static int getMaxDrawCount() {
        return maxDrawCount;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void cleanup() {
        if (drawCountBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), drawCountBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), drawCountBufferMemory, null);
            drawCountBuffer = NULL;
            drawCountBufferMemory = NULL;
        }
        if (indirectBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), indirectBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), indirectBufferMemory, null);
            indirectBuffer = NULL;
            indirectBufferMemory = NULL;
        }
        initialized = false;
    }
}
