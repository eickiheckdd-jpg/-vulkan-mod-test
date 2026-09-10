package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanFullscreenQuad {
    private static long pipeline;
    private static long pipelineLayout;
    private static long descriptorSetLayout;
    private static long descriptorSet;
    private static long descriptorPool;
    private static long sampler;
    private static long vertexBuffer;
    private static long vertexBufferMemory;
    private static long indexBuffer;
    private static long indexBufferMemory;
    private static long capturedImage;
    private static long capturedImageView;
    private static boolean initialized = false;

    private static final float[] QUAD_VERTICES = {
        // Position    // UV
        -1.0f, -1.0f,  0.0f, 1.0f,
         1.0f, -1.0f,  1.0f, 1.0f,
         1.0f,  1.0f,  1.0f, 0.0f,
        -1.0f,  1.0f,  0.0f, 0.0f
    };

    private static final short[] QUAD_INDICES = {
        0, 1, 2,
        2, 3, 0
    };

    public static synchronized void initialize() {
        if (initialized) return;

        try (MemoryStack stack = stackPush()) {
            createDescriptorPool(stack);
            createDescriptorSetLayout(stack);
            createPipelineLayout(stack);
            createSampler(stack);
            createPipeline(stack);
            createVertexBuffer(stack);
            createIndexBuffer(stack);
            createCapturedTexture(stack, 1, 1);
            createDescriptorSet(stack);

            initialized = true;
            VulkanMod.LOGGER.info("Fullscreen quad initialized");
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to initialize fullscreen quad: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDescriptorPool(MemoryStack stack) {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.callocStack(1, stack);
        VkDescriptorPoolSize poolSize = poolSizes.get(0);
        poolSize.type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        poolSize.descriptorCount(1);

        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack);
        poolInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
        poolInfo.pPoolSizes(poolSizes);
        poolInfo.maxSets(1);

        LongBuffer pPool = stack.mallocLong(1);
        int result = vkCreateDescriptorPool(VulkanDevice.getDevice(), poolInfo, null, pPool);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create descriptor pool: " + result);
        }
        descriptorPool = pPool.get(0);
    }

    private static void createDescriptorSetLayout(MemoryStack stack) {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.callocStack(1, stack);
        VkDescriptorSetLayoutBinding samplerBinding = bindings.get(0);
        samplerBinding.binding(0);
        samplerBinding.descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        samplerBinding.descriptorCount(1);
        samplerBinding.stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        samplerBinding.pImmutableSamplers(null);

        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack);
        layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
        layoutInfo.pBindings(bindings);

        LongBuffer pLayout = stack.mallocLong(1);
        int result = vkCreateDescriptorSetLayout(VulkanDevice.getDevice(), layoutInfo, null, pLayout);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create descriptor set layout: " + result);
        }
        descriptorSetLayout = pLayout.get(0);
    }

    private static void createPipelineLayout(MemoryStack stack) {
        LongBuffer pLayouts = stack.longs(descriptorSetLayout);

        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.callocStack(stack);
        layoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
        layoutInfo.pSetLayouts(pLayouts);

        LongBuffer pPipelineLayout = stack.mallocLong(1);
        int result = vkCreatePipelineLayout(VulkanDevice.getDevice(), layoutInfo, null, pPipelineLayout);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create pipeline layout: " + result);
        }
        pipelineLayout = pPipelineLayout.get(0);
    }

    private static void createPipeline(MemoryStack stack) {
        ByteBuffer vertShader = VulkanPipeline.loadShader("assets/vulkanmod/shaders/fullscreen.vert.spv");
        ByteBuffer fragShader = VulkanPipeline.loadShader("assets/vulkanmod/shaders/fullscreen.frag.spv");

        if (vertShader == null || fragShader == null) {
            throw new RuntimeException("Failed to load fullscreen shaders");
        }

        LongBuffer pVertModule = stack.mallocLong(1);
        VkShaderModuleCreateInfo vertInfo = VkShaderModuleCreateInfo.callocStack(stack);
        vertInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
        vertInfo.pCode(vertShader);
        int result = vkCreateShaderModule(VulkanDevice.getDevice(), vertInfo, null, pVertModule);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create vertex shader module: " + result);
        }
        long vertModule = pVertModule.get(0);

        LongBuffer pFragModule = stack.mallocLong(1);
        VkShaderModuleCreateInfo fragInfo = VkShaderModuleCreateInfo.callocStack(stack);
        fragInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
        fragInfo.pCode(fragShader);
        result = vkCreateShaderModule(VulkanDevice.getDevice(), fragInfo, null, pFragModule);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create fragment shader module: " + result);
        }
        long fragModule = pFragModule.get(0);

        VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, stack);
        VkPipelineShaderStageCreateInfo vertStage = shaderStages.get(0);
        vertStage.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
        vertStage.stage(VK10.VK_SHADER_STAGE_VERTEX_BIT);
        vertStage.module(vertModule);
        vertStage.pName(stack.UTF8("main"));

        VkPipelineShaderStageCreateInfo fragStage = shaderStages.get(1);
        fragStage.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
        fragStage.stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        fragStage.module(fragModule);
        fragStage.pName(stack.UTF8("main"));

        VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.callocStack(stack);
        vertexInput.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

        VkVertexInputBindingDescription.Buffer bindingDescriptions = VkVertexInputBindingDescription.callocStack(1, stack);
        VkVertexInputBindingDescription bindingDescription = bindingDescriptions.get(0);
        bindingDescription.binding(0);
        bindingDescription.stride(4 * 4);
        bindingDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        vertexInput.pVertexBindingDescriptions(bindingDescriptions);

        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.callocStack(2, stack);
        VkVertexInputAttributeDescription posAttribute = attributeDescriptions.get(0);
        posAttribute.binding(0);
        posAttribute.location(0);
        posAttribute.format(VK10.VK_FORMAT_R32G32_SFLOAT);
        posAttribute.offset(0);

        VkVertexInputAttributeDescription uvAttribute = attributeDescriptions.get(1);
        uvAttribute.binding(0);
        uvAttribute.location(1);
        uvAttribute.format(VK10.VK_FORMAT_R32G32_SFLOAT);
        uvAttribute.offset(2 * 4);

        vertexInput.pVertexAttributeDescriptions(attributeDescriptions);

        VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.callocStack(stack);
        inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
        inputAssembly.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        inputAssembly.primitiveRestartEnable(false);

        VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.callocStack(stack);
        viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
        viewportState.viewportCount(1);
        viewportState.scissorCount(1);

        VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.callocStack(stack);
        rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
        rasterizer.depthClampEnable(false);
        rasterizer.rasterizerDiscardEnable(false);
        rasterizer.polygonMode(VK10.VK_POLYGON_MODE_FILL);
        rasterizer.lineWidth(1.0f);
        rasterizer.cullMode(VK10.VK_CULL_MODE_NONE);
        rasterizer.frontFace(VK10.VK_FRONT_FACE_CLOCKWISE);
        rasterizer.depthBiasEnable(false);

        VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
        multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
        multisampling.sampleShadingEnable(false);
        multisampling.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

        VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.callocStack(stack);
        depthStencil.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);
        depthStencil.depthTestEnable(false);
        depthStencil.depthWriteEnable(false);
        depthStencil.depthCompareOp(VK10.VK_COMPARE_OP_ALWAYS);
        depthStencil.depthBoundsTestEnable(false);
        depthStencil.stencilTestEnable(false);

        VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachments = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
        VkPipelineColorBlendAttachmentState colorBlendAttachment = colorBlendAttachments.get(0);
        colorBlendAttachment.blendEnable(false);
        colorBlendAttachment.colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT);

        VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.callocStack(stack);
        colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
        colorBlending.logicOpEnable(false);
        colorBlending.logicOp(VK10.VK_LOGIC_OP_COPY);
        colorBlending.pAttachments(colorBlendAttachments);

        VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.callocStack(stack);
        dynamicState.sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
        IntBuffer pDynamicStates = stack.ints(
            VK10.VK_DYNAMIC_STATE_VIEWPORT,
            VK10.VK_DYNAMIC_STATE_SCISSOR
        );
        dynamicState.pDynamicStates(pDynamicStates);

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
        pipelineInfo.layout(pipelineLayout);
        pipelineInfo.renderPass(VulkanRenderPass.getRenderPass());
        pipelineInfo.subpass(0);
        pipelineInfo.basePipelineHandle(NULL);
        pipelineInfo.basePipelineIndex(-1);

        LongBuffer pPipeline = stack.mallocLong(1);
        result = vkCreateGraphicsPipelines(VulkanDevice.getDevice(), NULL, pipelineInfoBuffer, null, pPipeline);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create fullscreen graphics pipeline: " + result);
        }
        pipeline = pPipeline.get(0);

        vkDestroyShaderModule(VulkanDevice.getDevice(), vertModule, null);
        vkDestroyShaderModule(VulkanDevice.getDevice(), fragModule, null);

        if (vertShader != null) {
            MemoryUtil.memFree(vertShader);
        }
        if (fragShader != null) {
            MemoryUtil.memFree(fragShader);
        }
    }

    private static void createVertexBuffer(MemoryStack stack) {
        FloatBuffer vertexData = stack.floats(QUAD_VERTICES);
        long bufferSize = vertexData.remaining() * 4;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
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
            VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
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

        PointerBuffer pData = stack.mallocPointer(1);
        result = vkMapMemory(VulkanDevice.getDevice(), vertexBufferMemory, 0, bufferSize, 0, pData);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to map vertex buffer memory: " + result);
        }
        long dataPtr = pData.get(0);
        MemoryUtil.memCopy(MemoryUtil.memAddress(vertexData), dataPtr, vertexData.remaining() * 4);
        vkUnmapMemory(VulkanDevice.getDevice(), vertexBufferMemory);
    }

    private static void createIndexBuffer(MemoryStack stack) {
        ShortBuffer indexData = stack.shorts(QUAD_INDICES);
        long bufferSize = indexData.remaining() * 2;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
        bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        bufferInfo.size(bufferSize);
        bufferInfo.usage(VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        bufferInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer pBuffer = stack.mallocLong(1);
        int result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create index buffer: " + result);
        }
        indexBuffer = pBuffer.get(0);

            VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), indexBuffer, memRequirements);

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
            throw new RuntimeException("Failed to allocate index buffer memory: " + result);
        }
        indexBufferMemory = pBufferMemory.get(0);

        result = vkBindBufferMemory(VulkanDevice.getDevice(), indexBuffer, indexBufferMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind index buffer memory: " + result);
        }

        PointerBuffer pData = stack.mallocPointer(1);
        result = vkMapMemory(VulkanDevice.getDevice(), indexBufferMemory, 0, bufferSize, 0, pData);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to map index buffer memory: " + result);
        }
        long dataPtr = pData.get(0);
        MemoryUtil.memCopy(MemoryUtil.memAddress(indexData), dataPtr, indexData.remaining() * 2);
        vkUnmapMemory(VulkanDevice.getDevice(), indexBufferMemory);
    }

    private static void createSampler(MemoryStack stack) {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.callocStack(stack);
        samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
        samplerInfo.magFilter(VK10.VK_FILTER_LINEAR);
        samplerInfo.minFilter(VK10.VK_FILTER_LINEAR);
        samplerInfo.mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR);
        samplerInfo.addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        samplerInfo.addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        samplerInfo.addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        samplerInfo.mipLodBias(0.0f);
        samplerInfo.anisotropyEnable(false);
        samplerInfo.maxAnisotropy(1.0f);
        samplerInfo.compareEnable(false);
        samplerInfo.compareOp(VK10.VK_COMPARE_OP_ALWAYS);
        samplerInfo.minLod(0.0f);
        samplerInfo.maxLod(1.0f);
        samplerInfo.borderColor(VK10.VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK);
        samplerInfo.unnormalizedCoordinates(false);

        LongBuffer pSampler = stack.mallocLong(1);
        int result = vkCreateSampler(VulkanDevice.getDevice(), samplerInfo, null, pSampler);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create sampler: " + result);
        }
        sampler = pSampler.get(0);
    }

    private static void createCapturedTexture(MemoryStack stack, int width, int height) {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.callocStack(stack);
        imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
        imageInfo.imageType(VK10.VK_IMAGE_TYPE_2D);
        imageInfo.format(VK10.VK_FORMAT_R8G8B8A8_UNORM);
        imageInfo.extent(VkExtent3D.callocStack(stack).width(width).height(height).depth(1));
        imageInfo.mipLevels(1);
        imageInfo.arrayLayers(1);
        imageInfo.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
        imageInfo.tiling(VK10.VK_IMAGE_TILING_OPTIMAL);
        imageInfo.usage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT);
        imageInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
        imageInfo.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);

        LongBuffer pImage = stack.mallocLong(1);
        int result = vkCreateImage(VulkanDevice.getDevice(), imageInfo, null, pImage);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create captured texture image: " + result);
        }
        capturedImage = pImage.get(0);

        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.callocStack(stack);
        viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
        viewInfo.image(capturedImage);
        viewInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
        viewInfo.format(VK10.VK_FORMAT_R8G8B8A8_UNORM);
        viewInfo.subresourceRange(VkImageSubresourceRange.callocStack(stack)
            .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1));

        LongBuffer pImageView = stack.mallocLong(1);
        result = vkCreateImageView(VulkanDevice.getDevice(), viewInfo, null, pImageView);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create captured texture image view: " + result);
        }
        capturedImageView = pImageView.get(0);
    }

    private static void createDescriptorSet(MemoryStack stack) {
        LongBuffer pDescriptorSet = stack.mallocLong(1);
        VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.callocStack(stack);
        allocateInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
        allocateInfo.descriptorPool(descriptorPool);
        allocateInfo.pSetLayouts(stack.longs(descriptorSetLayout));

        int result = vkAllocateDescriptorSets(VulkanDevice.getDevice(), allocateInfo, pDescriptorSet);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate descriptor set: " + result);
        }
        descriptorSet = pDescriptorSet.get(0);
    }

    public static void updateTexture(ByteBuffer pixelData, int width, int height) {
        if (capturedImage == NULL || pixelData == null) return;

        try (MemoryStack stack = stackPush()) {
            // Transition image layout to TRANSFER_DST_OPTIMAL
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.callocStack(1, stack);
            VkImageMemoryBarrier barrier = barriers.get(0);
            barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
            barrier.srcAccessMask(0);
            barrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            barrier.newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            barrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            barrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            barrier.image(capturedImage);
            barrier.subresourceRange(VkImageSubresourceRange.callocStack(stack)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1));

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(VulkanDevice.getCommandPool());
            allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(1);

            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            int result = vkAllocateCommandBuffers(VulkanDevice.getDevice(), allocInfo, pCommandBuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffer for texture upload: " + result);
            }
            long commandBuffer = pCommandBuffer.get(0);

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
            vkBeginCommandBuffer(cmdBuf, beginInfo);
            vkCmdPipelineBarrier(cmdBuf, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barriers);

            // Create staging buffer and copy data
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack);
            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            bufferInfo.size(pixelData.remaining());
            bufferInfo.usage(VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            bufferInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pStagingBuffer = stack.mallocLong(1);
            result = vkCreateBuffer(VulkanDevice.getDevice(), bufferInfo, null, pStagingBuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create staging buffer: " + result);
            }
            long stagingBuffer = pStagingBuffer.get(0);

            VkMemoryRequirements stagingMemRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetBufferMemoryRequirements(VulkanDevice.getDevice(), stagingBuffer, stagingMemRequirements);

            int stagingMemoryTypeIndex = VulkanDevice.findMemoryType(
                stagingMemRequirements.memoryTypeBits(),
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );

            VkMemoryAllocateInfo stagingAllocInfo = VkMemoryAllocateInfo.callocStack(stack);
            stagingAllocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            stagingAllocInfo.allocationSize(stagingMemRequirements.size());
            stagingAllocInfo.memoryTypeIndex(stagingMemoryTypeIndex);

            LongBuffer pStagingMemory = stack.mallocLong(1);
            result = vkAllocateMemory(VulkanDevice.getDevice(), stagingAllocInfo, null, pStagingMemory);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate staging memory: " + result);
            }
            long stagingMemory = pStagingMemory.get(0);

            result = vkBindBufferMemory(VulkanDevice.getDevice(), stagingBuffer, stagingMemory, 0);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to bind staging memory: " + result);
            }

            PointerBuffer pData = stack.mallocPointer(1);
            result = vkMapMemory(VulkanDevice.getDevice(), stagingMemory, 0, pixelData.remaining(), 0, pData);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to map staging memory: " + result);
            }
            long dataPtr = pData.get(0);
            MemoryUtil.memCopy(MemoryUtil.memAddress(pixelData), dataPtr, pixelData.remaining());
            vkUnmapMemory(VulkanDevice.getDevice(), stagingMemory);

            VkBufferImageCopy.Buffer copyRegion = VkBufferImageCopy.callocStack(1, stack);
            VkBufferImageCopy copy = copyRegion.get(0);
            copy.bufferOffset(0);
            copy.bufferRowLength(0);
            copy.bufferImageHeight(0);
            copy.imageSubresource(VkImageSubresourceLayers.callocStack(stack)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0)
                .baseArrayLayer(0)
                .layerCount(1));
            copy.imageOffset(VkOffset3D.callocStack(stack).x(0).y(0).z(0));
            copy.imageExtent(VkExtent3D.callocStack(stack).width(width).height(height).depth(1));

            vkCmdCopyBufferToImage(cmdBuf, stagingBuffer, capturedImage, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copyRegion);

            // Transition to SHADER_READ_ONLY_OPTIMAL
            VkImageMemoryBarrier.Buffer barriers2 = VkImageMemoryBarrier.callocStack(1, stack);
            VkImageMemoryBarrier barrier2 = barriers2.get(0);
            barrier2.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
            barrier2.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier2.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);
            barrier2.oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            barrier2.newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            barrier2.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            barrier2.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            barrier2.image(capturedImage);
            barrier2.subresourceRange(VkImageSubresourceRange.callocStack(stack)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1));

            vkCmdPipelineBarrier(cmdBuf, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barriers2);

            vkEndCommandBuffer(cmdBuf);

            VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer));

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(0);

            LongBuffer pFence = stack.mallocLong(1);
            result = vkCreateFence(VulkanDevice.getDevice(), fenceInfo, null, pFence);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create fence: " + result);
            }
            long fence = pFence.get(0);

            result = vkQueueSubmit(VulkanDevice.getGraphicsQueue(), submitInfo, fence);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit texture upload command buffer: " + result);
            }

            vkWaitForFences(VulkanDevice.getDevice(), stack.longs(fence), true, Long.MAX_VALUE);
            vkDestroyFence(VulkanDevice.getDevice(), fence, null);
            vkFreeCommandBuffers(VulkanDevice.getDevice(), VulkanDevice.getCommandPool(), stack.pointers(commandBuffer));
            vkDestroyBuffer(VulkanDevice.getDevice(), stagingBuffer, null);
            vkFreeMemory(VulkanDevice.getDevice(), stagingMemory, null);

            updateDescriptorSet();
        }
    }

    private static void updateDescriptorSet() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.callocStack(1, stack);
            VkDescriptorImageInfo descriptorImageInfo = imageInfo.get(0);
            descriptorImageInfo.sampler(sampler);
            descriptorImageInfo.imageView(capturedImageView);
            descriptorImageInfo.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            VkWriteDescriptorSet.Buffer writeDescriptorSets = VkWriteDescriptorSet.callocStack(1, stack);
            VkWriteDescriptorSet writeDescriptorSet = writeDescriptorSets.get(0);
            writeDescriptorSet.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
            writeDescriptorSet.dstSet(descriptorSet);
            writeDescriptorSet.dstBinding(0);
            writeDescriptorSet.dstArrayElement(0);
            writeDescriptorSet.descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            writeDescriptorSet.pImageInfo(imageInfo);
            writeDescriptorSet.pBufferInfo(null);
            writeDescriptorSet.pTexelBufferView(null);

            vkUpdateDescriptorSets(VulkanDevice.getDevice(), writeDescriptorSets, null);
        }
    }

    public static void render(long commandBuffer, int width, int height) {
        if (!initialized || capturedImage == NULL) return;

        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());

            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack);
            viewport.x(0).y(0).width(width).height(height)
                .minDepth(0.0f).maxDepth(1.0f);
            vkCmdSetViewport(cmdBuf, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.callocStack(1, stack);
            scissor.offset(VkOffset2D.callocStack(stack).x(0).y(0))
                .extent(VkExtent2D.callocStack(stack).width(width).height(height));
            vkCmdSetScissor(cmdBuf, 0, scissor);

            LongBuffer pVertexBuffers = stack.mallocLong(1);
            pVertexBuffers.put(0, vertexBuffer).flip();
            LongBuffer pOffsets = stack.mallocLong(1);
            pOffsets.put(0, 0).flip();
            vkCmdBindVertexBuffers(cmdBuf, 0, pVertexBuffers, pOffsets);
            vkCmdBindIndexBuffer(cmdBuf, indexBuffer, 0, VK10.VK_INDEX_TYPE_UINT16);

            vkCmdBindPipeline(cmdBuf, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            vkCmdBindDescriptorSets(cmdBuf, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, stack.longs(descriptorSet), null);

            vkCmdDrawIndexed(cmdBuf, 6, 1, 0, 0, 0);
        }
    }

    public static void cleanup() {
        if (descriptorPool != NULL) {
            vkDestroyDescriptorPool(VulkanDevice.getDevice(), descriptorPool, null);
            descriptorPool = NULL;
        }
        if (sampler != NULL) {
            vkDestroySampler(VulkanDevice.getDevice(), sampler, null);
            sampler = NULL;
        }
        if (capturedImageView != NULL) {
            vkDestroyImageView(VulkanDevice.getDevice(), capturedImageView, null);
            capturedImageView = NULL;
        }
        if (capturedImage != NULL) {
            vkDestroyImage(VulkanDevice.getDevice(), capturedImage, null);
            capturedImage = NULL;
        }
        if (descriptorSetLayout != NULL) {
            vkDestroyDescriptorSetLayout(VulkanDevice.getDevice(), descriptorSetLayout, null);
            descriptorSetLayout = NULL;
        }
        if (pipeline != NULL) {
            vkDestroyPipeline(VulkanDevice.getDevice(), pipeline, null);
            pipeline = NULL;
        }
        if (pipelineLayout != NULL) {
            vkDestroyPipelineLayout(VulkanDevice.getDevice(), pipelineLayout, null);
            pipelineLayout = NULL;
        }
        if (vertexBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), vertexBuffer, null);
            vertexBuffer = NULL;
        }
        if (vertexBufferMemory != NULL) {
            vkFreeMemory(VulkanDevice.getDevice(), vertexBufferMemory, null);
            vertexBufferMemory = NULL;
        }
        if (indexBuffer != NULL) {
            vkDestroyBuffer(VulkanDevice.getDevice(), indexBuffer, null);
            indexBuffer = NULL;
        }
        if (indexBufferMemory != NULL) {
            vkFreeMemory(VulkanDevice.getDevice(), indexBufferMemory, null);
            indexBufferMemory = NULL;
        }
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
