package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanVertexCapture {
    private static final Deque<CapturedMesh> meshQueue = new ArrayDeque<>();
    private static long vertexStagingBuffer;
    private static long vertexStagingMemory;
    private static long vertexBuffer;
    private static long vertexBufferMemory;
    private static boolean initialized = false;

    private static class CapturedMesh {
        ByteBuffer vertexData;
        int vertexCount;
        int vertexSize;
    }

    public static synchronized void initialize() {
        if (initialized) return;

        try (MemoryStack stack = stackPush()) {
            createStagingBuffer(stack, 4 * 1024 * 1024); // 4MB staging buffer
            createVertexBuffer(stack, 4 * 1024 * 1024);   // 4MB vertex buffer

            initialized = true;
            VulkanMod.LOGGER.info("Vertex capture initialized");
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to initialize vertex capture: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createStagingBuffer(MemoryStack stack, long size) {
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(size);
        bufferInfo.usage(VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        bufferInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create staging buffer: " + result);
        }
        vertexStagingBuffer = pBuffer.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), vertexStagingBuffer, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pBufferMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pBufferMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate staging buffer memory: " + result);
        }
        vertexStagingMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), vertexStagingBuffer, vertexStagingMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind staging buffer memory: " + result);
        }
    }

    private static void createVertexBuffer(MemoryStack stack, long size) {
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(size);
        bufferInfo.usage(VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        bufferInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create vertex buffer: " + result);
        }
        vertexBuffer = pBuffer.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), vertexBuffer, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pBufferMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pBufferMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate vertex buffer memory: " + result);
        }
        vertexBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), vertexBuffer, vertexBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind vertex buffer memory: " + result);
        }
    }

    public static void captureMesh(ByteBuffer vertexData, int vertexCount, int vertexSize) {
        if (!initialized || vertexData == null || vertexData.remaining() == 0) return;

        CapturedMesh mesh = new CapturedMesh();
        mesh.vertexData = MemoryUtil.memAlloc(vertexData.remaining()).put(vertexData).flip();
        mesh.vertexCount = vertexCount;
        mesh.vertexSize = vertexSize;
        meshQueue.addLast(mesh);

        VulkanMod.LOGGER.debug("Captured mesh: {} vertices, {} bytes", vertexCount, vertexData.remaining());
    }

    public static void uploadAndRender(long commandBuffer, int width, int height) {
        if (!initialized || meshQueue.isEmpty()) return;

        try (MemoryStack stack = stackPush()) {
            long totalSize = 0;
            for (CapturedMesh mesh : meshQueue) {
                totalSize += mesh.vertexData.remaining();
            }

            if (totalSize > 0) {
                // Map staging buffer and copy all vertex data
                PointerBuffer pData = stack.mallocPointer(1);
                int result = vkMapMemory(VulkanDevice.getDevice(), vertexStagingMemory, 0, totalSize, 0, pData);
                if (result == VK_SUCCESS) {
                    long dstPtr = pData.get(0);
                    for (CapturedMesh mesh : meshQueue) {
                        MemoryUtil.memCopy(MemoryUtil.memAddress(mesh.vertexData), dstPtr, mesh.vertexData.remaining());
                        dstPtr += mesh.vertexData.remaining();
                    }
                    vkUnmapMemory(VulkanDevice.getDevice(), vertexStagingMemory);

                    // Copy staging buffer to vertex buffer
                    VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
                    VkBufferCopy copy = copyRegion.get(0);
                    copy.srcOffset(0);
                    copy.dstOffset(0);
                    copy.size(totalSize);

                    VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
                    vkCmdCopyBuffer(cmdBuf, vertexStagingBuffer, vertexBuffer, copyRegion);
                }
            }

            // Render captured meshes
            VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());

            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack);
            viewport.x(0).y(0).width(width).height(height)
                .minDepth(0.0f).maxDepth(1.0f);
            vkCmdSetViewport(cmdBuf, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.callocStack(1, stack);
            scissor.offset(VkOffset2D.callocStack(stack).x(0).y(0))
                .extent(VkExtent2D.callocStack(stack).width(width).height(height));
            vkCmdSetScissor(cmdBuf, 0, scissor);

            vkCmdBindPipeline(cmdBuf, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, VulkanPipeline.getPipeline());

            LongBuffer pVertexBuffers = stack.mallocLong(1);
            pVertexBuffers.put(0, vertexBuffer).flip();
            LongBuffer pOffsets = stack.mallocLong(1);
            pOffsets.put(0, 0).flip();
            vkCmdBindVertexBuffers(cmdBuf, 0, pVertexBuffers, pOffsets);

            long offset = 0;
            for (CapturedMesh mesh : meshQueue) {
                vkCmdDraw(cmdBuf, mesh.vertexCount, 1, (int)(offset / mesh.vertexSize), 0);
                offset += mesh.vertexData.remaining();
            }

            // Clear queue
            for (CapturedMesh mesh : meshQueue) {
                MemoryUtil.memFree(mesh.vertexData);
            }
            meshQueue.clear();
        }
    }

    public static void cleanup() {
        if (vertexBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), vertexBuffer, null);
            vertexBuffer = NULL;
        }
        if (vertexBufferMemory != NULL) {
            vkFreeMemory(VulkanDevice.getDevice(), vertexBufferMemory, null);
            vertexBufferMemory = NULL;
        }
        if (vertexStagingBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), vertexStagingBuffer, null);
            vertexStagingBuffer = NULL;
        }
        if (vertexStagingMemory != NULL) {
            vkFreeMemory(VulkanDevice.getDevice(), vertexStagingMemory, null);
            vertexStagingMemory = NULL;
        }
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
