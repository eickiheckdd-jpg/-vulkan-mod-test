package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanDevice;
import net.vulkanmod.vulkan.VulkanPipeline;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanEntityRenderer {
    private static final int MAX_ENTITIES = 1024;
    private static final int MAX_VERTICES_PER_ENTITY = 1024;
    private static final int VERTEX_SIZE = 24; // pos(12) + color(4) + uv(4) + normal(4) = 24

    private static long vertexBuffer;
    private static long vertexBufferMemory;
    private static long stagingBuffer;
    private static long stagingBufferMemory;
    private static long pipeline;
    private static boolean initialized = false;

    private static class EntityMesh {
        ByteBuffer vertexData;
        int vertexCount;
        float x, y, z;
        float rotationX, rotationY, rotationZ;
        float scaleX, scaleY, scaleZ;
    }

    private static final Deque<EntityMesh> entityQueue = new ArrayDeque<>();

    public static synchronized void initialize() {
        if (initialized) return;

        try (MemoryStack stack = stackPush()) {
            createVertexBuffer(stack);
            createStagingBuffer(stack);
            createPipeline(stack);

            initialized = true;
            VulkanMod.LOGGER.info("Entity renderer initialized");
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to initialize entity renderer: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createVertexBuffer(MemoryStack stack) {
        long bufferSize = (long) MAX_ENTITIES * MAX_VERTICES_PER_ENTITY * VERTEX_SIZE;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create entity vertex buffer: " + result);
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
            throw new RuntimeException("Failed to allocate entity vertex buffer memory: " + result);
        }
        vertexBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), vertexBuffer, vertexBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind entity vertex buffer memory: " + result);
        }
    }

    private static void createStagingBuffer(MemoryStack stack) {
        long bufferSize = (long) MAX_ENTITIES * MAX_VERTICES_PER_ENTITY * VERTEX_SIZE;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create entity staging buffer: " + result);
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
            throw new RuntimeException("Failed to allocate entity staging buffer memory: " + result);
        }
        stagingBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), stagingBuffer, stagingBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind entity staging buffer memory: " + result);
        }
    }

    private static void createPipeline(MemoryStack stack) {
        VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, stack);
        VkPipelineShaderStageCreateInfo vertStage = shaderStages.get(0);
        vertStage.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
        vertStage.stage(VK_SHADER_STAGE_VERTEX_BIT);
        vertStage.module(NULL);
        vertStage.pName(stack.UTF8("main"));

        VkPipelineShaderStageCreateInfo fragStage = shaderStages.get(1);
        fragStage.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
        fragStage.stage(VK_SHADER_STAGE_FRAGMENT_BIT);
        fragStage.module(NULL);
        fragStage.pName(stack.UTF8("main"));

        VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.callocStack(stack);
        vertexInput.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

        VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.callocStack(stack);
        inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
        inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        inputAssembly.primitiveRestartEnable(false);

        VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.callocStack(stack);
        viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
        viewportState.viewportCount(1);
        viewportState.scissorCount(1);

        VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.callocStack(stack);
        rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
        rasterizer.depthClampEnable(false);
        rasterizer.rasterizerDiscardEnable(false);
        rasterizer.polygonMode(VK_POLYGON_MODE_FILL);
        rasterizer.lineWidth(1.0f);
        rasterizer.cullMode(VK_CULL_MODE_NONE);
        rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE);
        rasterizer.depthBiasEnable(false);

        VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
        multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
        multisampling.sampleShadingEnable(false);
        multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

        VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.callocStack(stack);
        depthStencil.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);
        depthStencil.depthTestEnable(true);
        depthStencil.depthWriteEnable(true);
        depthStencil.depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
        depthStencil.depthBoundsTestEnable(false);
        depthStencil.stencilTestEnable(false);

        VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachments = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
        VkPipelineColorBlendAttachmentState colorBlendAttachment = colorBlendAttachments.get(0);
        colorBlendAttachment.blendEnable(true);
        colorBlendAttachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
        colorBlendAttachment.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
        colorBlendAttachment.colorBlendOp(VK_BLEND_OP_ADD);
        colorBlendAttachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
        colorBlendAttachment.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
        colorBlendAttachment.alphaBlendOp(VK_BLEND_OP_ADD);
        colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);

        VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.callocStack(stack);
        colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
        colorBlending.logicOpEnable(false);
        colorBlending.logicOp(VK_LOGIC_OP_COPY);
        colorBlending.pAttachments(colorBlendAttachments);

        VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.callocStack(stack);
        dynamicState.sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
        IntBuffer pDynamicStates = stack.ints(
            VK_DYNAMIC_STATE_VIEWPORT,
            VK_DYNAMIC_STATE_SCISSOR
        );
        dynamicState.pDynamicStates(pDynamicStates);

        VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.callocStack(stack);
        pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
        pipelineLayoutInfo.pSetLayouts(stack.longs(0));

        LongBuffer pPipelineLayout = stack.mallocLong(1);
        int result = vkCreatePipelineLayout(VulkanDevice.getDevice(), pipelineLayoutInfo, null, pPipelineLayout);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create entity pipeline layout: " + result);
        }

        VkGraphicsPipelineCreateInfo.Buffer pipelineInfoBuffer = VkGraphicsPipelineCreateInfo.callocStack(1, stack);
        VkGraphicsPipelineCreateInfo pipelineInfo = pipelineInfoBuffer.get(0);
        pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
        pipelineInfo.stageCount(2);
        pipelineInfo.pStages(shaderStages);
        pipelineInfo.pVertexInputState(vertexInput);
        pipelineInfo.pInputAssemblyState(inputAssembly);
        pipelineInfo.pViewportState(viewportState);
        pipelineInfo.pRasterizationState(rasterizer);
        pipelineInfo.pMultisampleState(multisampling);
        pipelineInfo.pDepthStencilState(depthStencil);
        pipelineInfo.pColorBlendState(colorBlending);
        pipelineInfo.pDynamicState(dynamicState);
        pipelineInfo.layout(pPipelineLayout.get(0));
        pipelineInfo.renderPass(VK_NULL_HANDLE);
        pipelineInfo.subpass(0);
        pipelineInfo.basePipelineHandle(NULL);
        pipelineInfo.basePipelineIndex(-1);

        LongBuffer pPipeline = stack.mallocLong(1);
        result = vkCreateGraphicsPipelines(VulkanDevice.getDevice(), NULL, pipelineInfoBuffer, null, pPipeline);
        if (result == VK_SUCCESS) {
            pipeline = pPipeline.get(0);
        }

        vkDestroyPipelineLayout(VulkanDevice.getDevice(), pPipelineLayout.get(0), null);
    }

    public static void addEntity(ByteBuffer vertexData, int vertexCount,
                                  float x, float y, float z,
                                  float rotationX, float rotationY, float rotationZ,
                                  float scaleX, float scaleY, float scaleZ) {
        if (!initialized || vertexData == null || vertexData.remaining() == 0) return;

        EntityMesh mesh = new EntityMesh();
        mesh.vertexData = MemoryUtil.memAlloc(vertexData.remaining()).put(vertexData).flip();
        mesh.vertexCount = vertexCount;
        mesh.x = x;
        mesh.y = y;
        mesh.z = z;
        mesh.rotationX = rotationX;
        mesh.rotationY = rotationY;
        mesh.rotationZ = rotationZ;
        mesh.scaleX = scaleX;
        mesh.scaleY = scaleY;
        mesh.scaleZ = scaleZ;
        entityQueue.addLast(mesh);
    }

    public static void uploadAndRender(long commandBuffer) {
        if (!initialized || entityQueue.isEmpty()) return;

        try (MemoryStack stack = stackPush()) {
            long totalSize = 0;
            for (EntityMesh mesh : entityQueue) {
                totalSize += mesh.vertexData.remaining();
            }

            if (totalSize > 0) {
                PointerBuffer pData = stack.mallocPointer(1);
                int result = vkMapMemory(VulkanDevice.getDevice(), stagingBufferMemory, 0, totalSize, 0, pData);
                if (result == VK_SUCCESS) {
                    long dstPtr = pData.get(0);
                    for (EntityMesh mesh : entityQueue) {
                        MemoryUtil.memCopy(MemoryUtil.memAddress(mesh.vertexData), dstPtr, mesh.vertexData.remaining());
                        dstPtr += mesh.vertexData.remaining();
                    }
                    vkUnmapMemory(VulkanDevice.getDevice(), stagingBufferMemory);
                }

                VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack);
                VkBufferCopy copy = copyRegion.get(0);
                copy.srcOffset(0);
                copy.dstOffset(0);
                copy.size(totalSize);

                VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
                vkCmdCopyBuffer(cmdBuf, stagingBuffer, vertexBuffer, copyRegion);
            }

            VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
            vkCmdBindPipeline(cmdBuf, VK_PIPELINE_BIND_POINT_GRAPHICS, VulkanPipeline.getPipeline());
            vkCmdBindVertexBuffers(cmdBuf, 0, stack.longs(vertexBuffer), stack.longs(0));

            vkCmdPushConstants(cmdBuf, VulkanPipeline.getPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, stack.floats(
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
            ).flip());

            long offset = 0;
            for (EntityMesh mesh : entityQueue) {
                // Apply model matrix transformation
                // For now, just draw the mesh
                vkCmdDraw(cmdBuf, mesh.vertexCount, 1, (int)(offset / VERTEX_SIZE), 0);
                offset += mesh.vertexData.remaining();
            }

            for (EntityMesh mesh : entityQueue) {
                MemoryUtil.memFree(mesh.vertexData);
            }
            entityQueue.clear();
        }
    }

    public static void cleanup() {
        for (EntityMesh mesh : entityQueue) {
            MemoryUtil.memFree(mesh.vertexData);
        }
        entityQueue.clear();

        if (pipeline != NULL) {
            vkDestroyPipeline(VulkanDevice.getDevice(), pipeline, null);
            pipeline = NULL;
        }
        if (stagingBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), stagingBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), stagingBufferMemory, null);
            stagingBuffer = NULL;
            stagingBufferMemory = NULL;
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
