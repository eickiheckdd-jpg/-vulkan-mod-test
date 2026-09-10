package net.vulkanmod.render;

import net.vulkanmod.vulkan.VulkanDevice;
import net.vulkanmod.vulkan.VulkanPipeline;
import net.vulkanmod.vulkan.VulkanSwapchain;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanRenderEventBus {
    private static final int MAX_VERTEX_BUFFERS = 16;

    private static long[] vertexBuffers;
    private static long[] vertexBufferMemories;
    private static long[] indexBuffers;
    private static long[] indexBufferMemories;
    private static boolean initialized = false;

    private static class RenderCommand {
        int type;
        long vertexBuffer;
        long indexBuffer;
        int vertexCount;
        int indexCount;
        int firstVertex;
        int firstIndex;
        float[] mvpMatrix;
        int topology;
        int pipelineIndex;
    }

    private static final RenderCommand[] renderCommands = new RenderCommand[MAX_VERTEX_BUFFERS];

    public static synchronized void initialize() {
        if (initialized) return;

        for (int i = 0; i < MAX_VERTEX_BUFFERS; i++) {
            renderCommands[i] = new RenderCommand();
        }

        createBufferResources();

        initialized = true;
    }

    private static void createBufferResources() {
        try (MemoryStack stack = stackPush()) {
            int imageCount = VulkanSwapchain.getSwapchainImages().length;
            vertexBuffers = new long[imageCount];
            vertexBufferMemories = new long[imageCount];
            indexBuffers = new long[imageCount];
            indexBufferMemories = new long[imageCount];

            for (int i = 0; i < imageCount; i++) {
                VkDevice device = VulkanDevice.getDevice();

                VkBufferCreateInfo vertexBufferInfo = VkBufferCreateInfo.callocStack(stack);
                vertexBufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
                vertexBufferInfo.size(16 * 1024 * 1024);
                vertexBufferInfo.usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
                vertexBufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

                LongBuffer pVertexBuffer = stack.mallocLong(1);
                int result = vkCreateBuffer(device, vertexBufferInfo, null, pVertexBuffer);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create render event vertex buffer: " + result);
                }
                vertexBuffers[i] = pVertexBuffer.get(0);

                VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
                vkGetBufferMemoryRequirements(device, vertexBuffers[i], memRequirements);

                int memoryTypeIndex = VulkanDevice.findMemoryType(
                    memRequirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                );

                VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
                allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
                allocInfo.allocationSize(memRequirements.size());
                allocInfo.memoryTypeIndex(memoryTypeIndex);

                LongBuffer pVertexMemory = stack.mallocLong(1);
                result = vkAllocateMemory(device, allocInfo, null, pVertexMemory);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to allocate vertex buffer memory: " + result);
                }
                vertexBufferMemories[i] = pVertexMemory.get(0);

                result = vkBindBufferMemory(device, vertexBuffers[i], vertexBufferMemories[i], 0);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to bind vertex buffer memory: " + result);
                }

                VkBufferCreateInfo indexBufferInfo = VkBufferCreateInfo.callocStack(stack);
                indexBufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
                indexBufferInfo.size(4 * 1024 * 1024);
                indexBufferInfo.usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
                indexBufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

                LongBuffer pIndexBuffer = stack.mallocLong(1);
                result = vkCreateBuffer(device, indexBufferInfo, null, pIndexBuffer);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create render event index buffer: " + result);
                }
                indexBuffers[i] = pIndexBuffer.get(0);

                vkGetBufferMemoryRequirements(device, indexBuffers[i], memRequirements);

                int indexMemoryTypeIndex = VulkanDevice.findMemoryType(
                    memRequirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                );

                VkMemoryAllocateInfo indexAllocInfo = VkMemoryAllocateInfo.callocStack(stack);
                indexAllocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
                indexAllocInfo.allocationSize(memRequirements.size());
                indexAllocInfo.memoryTypeIndex(indexMemoryTypeIndex);

                LongBuffer pIndexMemory = stack.mallocLong(1);
                result = vkAllocateMemory(device, indexAllocInfo, null, pIndexMemory);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to allocate index buffer memory: " + result);
                }
                indexBufferMemories[i] = pIndexMemory.get(0);

                result = vkBindBufferMemory(device, indexBuffers[i], indexBufferMemories[i], 0);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to bind index buffer memory: " + result);
                }
            }
        }
    }

    public static void submitVertexData(int imageIndex, ByteBuffer vertexData, int vertexCount, int vertexStride) {
        if (!initialized || vertexData == null || vertexData.remaining() == 0) return;

        try (MemoryStack stack = stackPush()) {
            long dataSize = vertexData.remaining();
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(VulkanDevice.getDevice(), vertexBufferMemories[imageIndex], 0, dataSize, 0, pData);
            if (result == VK_SUCCESS) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(vertexData), pData.get(0), dataSize);
                vkUnmapMemory(VulkanDevice.getDevice(), vertexBufferMemories[imageIndex]);
            }
        }
    }

    public static void submitIndexData(int imageIndex, ByteBuffer indexData) {
        if (!initialized || indexData == null || indexData.remaining() == 0) return;

        try (MemoryStack stack = stackPush()) {
            long dataSize = indexData.remaining();
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(VulkanDevice.getDevice(), indexBufferMemories[imageIndex], 0, dataSize, 0, pData);
            if (result == VK_SUCCESS) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(indexData), pData.get(0), dataSize);
                vkUnmapMemory(VulkanDevice.getDevice(), indexBufferMemories[imageIndex]);
            }
        }
    }

    public static void renderMeshes(VkCommandBuffer vkCommandBuffer, int imageIndex) {
        if (!initialized) return;

        VkDevice device = VulkanDevice.getDevice();
        long pipelineLayout = VulkanPipeline.getPipelineLayout();
        long pipeline = VulkanPipeline.getPipeline();

        try (MemoryStack stack = stackPush()) {
            for (int i = 0; i < MAX_VERTEX_BUFFERS; i++) {
                RenderCommand cmd = renderCommands[i];
                if (cmd.vertexCount <= 0) continue;

                vkCmdBindPipeline(vkCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

                if (cmd.mvpMatrix != null && cmd.mvpMatrix.length == 16) {
                    FloatBuffer mvpBuffer = stack.floats(cmd.mvpMatrix);
                    vkCmdPushConstants(vkCommandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, mvpBuffer);
                }

                LongBuffer pVertexBuffers = stack.mallocLong(1);
                pVertexBuffers.put(0, vertexBuffers[imageIndex]).flip();
                LongBuffer pOffsets = stack.mallocLong(1);
                pOffsets.put(0, 0L).flip();
                vkCmdBindVertexBuffers(vkCommandBuffer, 0, pVertexBuffers, pOffsets);

                if (cmd.indexBuffer != NULL && cmd.indexCount > 0) {
                    vkCmdBindIndexBuffer(vkCommandBuffer, cmd.indexBuffer, 0, VK_INDEX_TYPE_UINT16);
                    vkCmdDrawIndexed(vkCommandBuffer, cmd.indexCount, 1, 0, 0, 0);
                } else {
                    vkCmdDraw(vkCommandBuffer, cmd.vertexCount, 1, 0, 0);
                }
            }
        }
    }

    public static void cleanup() {
        VkDevice device = VulkanDevice.getDevice();
        if (vertexBuffers != null) {
            for (int i = 0; i < vertexBuffers.length; i++) {
                if (vertexBuffers[i] != NULL) {
                    vkDestroyBuffer(device, vertexBuffers[i], null);
                    vkFreeMemory(device, vertexBufferMemories[i], null);
                }
                if (indexBuffers[i] != NULL) {
                    vkDestroyBuffer(device, indexBuffers[i], null);
                    vkFreeMemory(device, indexBufferMemories[i], null);
                }
            }
        }
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
