package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanDevice;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanChunkMeshBatcher {
    private static final int MAX_CHUNKS = 256;
    private static final int MAX_VERTICES_PER_CHUNK = 65536;
    private static final int VERTEX_SIZE = 16; // pos(12) + uv(4) + light(4) + normal(4) = 24, simplified to 16 for now

    private static long vertexBuffer;
    private static long vertexBufferMemory;
    private static long indexBuffer;
    private static long indexBufferMemory;
    private static long stagingBuffer;
    private static long stagingBufferMemory;
    private static boolean initialized = false;

    private static class ChunkMesh {
        ByteBuffer vertexData;
        int vertexCount;
        int indexCount;
        int firstVertex;
        int firstIndex;
        long descriptorSet;
    }

    private static final Deque<ChunkMesh> meshQueue = new ArrayDeque<>();

    public static synchronized void initialize() {
        if (initialized) return;

        try (MemoryStack stack = stackPush()) {
            createVertexBuffer(stack);
            createIndexBuffer(stack);
            createStagingBuffer(stack);

            initialized = true;
            VulkanMod.LOGGER.info("Chunk mesh batcher initialized");
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to initialize chunk mesh batcher: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createVertexBuffer(MemoryStack stack) {
        long bufferSize = (long) MAX_CHUNKS * MAX_VERTICES_PER_CHUNK * VERTEX_SIZE;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create chunk vertex buffer: " + result);
        }
        vertexBuffer = pBuffer.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), vertexBuffer, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pBufferMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pBufferMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate chunk vertex buffer memory: " + result);
        }
        vertexBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), vertexBuffer, vertexBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind chunk vertex buffer memory: " + result);
        }
    }

    private static void createIndexBuffer(MemoryStack stack) {
        long bufferSize = (long) MAX_CHUNKS * MAX_VERTICES_PER_CHUNK * 6 * 2; // 6 indices per quad, 2 bytes each

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create chunk index buffer: " + result);
        }
        indexBuffer = pBuffer.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), indexBuffer, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pBufferMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pBufferMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate chunk index buffer memory: " + result);
        }
        indexBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), indexBuffer, indexBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind chunk index buffer memory: " + result);
        }
    }

    private static void createStagingBuffer(MemoryStack stack) {
        long bufferSize = (long) MAX_CHUNKS * MAX_VERTICES_PER_CHUNK * VERTEX_SIZE;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create chunk staging buffer: " + result);
        }
        stagingBuffer = pBuffer.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), stagingBuffer, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pBufferMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pBufferMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate chunk staging buffer memory: " + result);
        }
        stagingBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), stagingBuffer, stagingBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind chunk staging buffer memory: " + result);
        }
    }

    public static void addChunkMesh(ByteBuffer vertexData, int vertexCount, int indexCount) {
        if (!initialized || vertexData == null || vertexData.remaining() == 0) return;

        ChunkMesh mesh = new ChunkMesh();
        mesh.vertexData = MemoryUtil.memAlloc(vertexData.remaining()).put(vertexData).flip();
        mesh.vertexCount = vertexCount;
        mesh.indexCount = indexCount;
        meshQueue.addLast(mesh);
    }

    public static void uploadAndRender(long commandBuffer) {
        if (!initialized || meshQueue.isEmpty()) return;

        try (MemoryStack stack = stackPush()) {
            long totalVertexSize = 0;
            long totalIndexSize = 0;
            for (ChunkMesh mesh : meshQueue) {
                totalVertexSize += mesh.vertexData.remaining();
                totalIndexSize += mesh.indexCount * 2L;
            }

            if (totalVertexSize > 0) {
                PointerBuffer pData = stack.mallocPointer(1);
                int result = vkMapMemory(VulkanDevice.getDevice(), stagingBufferMemory, 0, totalVertexSize, 0, pData);
                if (result == VK_SUCCESS) {
                    long dstPtr = pData.get(0);
                    for (ChunkMesh mesh : meshQueue) {
                        MemoryUtil.memCopy(MemoryUtil.memAddress(mesh.vertexData), dstPtr, mesh.vertexData.remaining());
                        dstPtr += mesh.vertexData.remaining();
                    }
                    vkUnmapMemory(VulkanDevice.getDevice(), stagingBufferMemory);
                }

                VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
                VkBufferCopy copy = copyRegion.get(0);
                copy.srcOffset(0);
                copy.dstOffset(0);
                copy.size(totalVertexSize);

                VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
                vkCmdCopyBuffer(cmdBuf, stagingBuffer, vertexBuffer, copyRegion);
            }

            VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
            vkCmdBindVertexBuffers(cmdBuf, 0, stack.longs(vertexBuffer), stack.longs(0));
            vkCmdBindIndexBuffer(cmdBuf, indexBuffer, 0, VK_INDEX_TYPE_UINT16);

            long vertexOffset = 0;
            for (ChunkMesh mesh : meshQueue) {
                vkCmdDrawIndexed(cmdBuf, mesh.indexCount, 1, (int)vertexOffset, 0, 0);
                vertexOffset += mesh.vertexCount;
            }

            for (ChunkMesh mesh : meshQueue) {
                MemoryUtil.memFree(mesh.vertexData);
            }
            meshQueue.clear();
        }
    }

    public static void cleanup() {
        for (ChunkMesh mesh : meshQueue) {
            MemoryUtil.memFree(mesh.vertexData);
        }
        meshQueue.clear();

        if (stagingBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), stagingBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), stagingBufferMemory, null);
            stagingBuffer = NULL;
            stagingBufferMemory = NULL;
        }
        if (indexBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), indexBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), indexBufferMemory, null);
            indexBuffer = NULL;
            indexBufferMemory = NULL;
        }
        if (vertexBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), vertexBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), vertexBufferMemory, null);
            vertexBuffer = NULL;
            vertexBufferMemory = NULL;
        }
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
