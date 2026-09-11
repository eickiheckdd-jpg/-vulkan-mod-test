package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanPipeline {
    private static long pipeline;
    private static long pipelineLayout;
    private static long terrainPipeline;
    private static long terrainPipelineLayout;
    private static long descriptorSetLayout;
    private static long descriptorPool;
    private static long descriptorSet;
    private static long sampler;

    public static void create() {
        try (MemoryStack stack = stackPush()) {
            createDescriptorSetLayout(stack);
            createDescriptorPool(stack);
            createSampler(stack);
            createDescriptorSet(stack);

            ByteBuffer vertShader = loadShader("shaders/terrain.vert.spv");
            ByteBuffer fragShader = loadShader("shaders/terrain.frag.spv");

            if (vertShader == null || fragShader == null) {
                throw new RuntimeException("Failed to load shaders");
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
            bindingDescription.stride(4 * 8);
            bindingDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            vertexInput.pVertexBindingDescriptions(bindingDescriptions);

            VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.callocStack(3, stack);
            VkVertexInputAttributeDescription posAttribute = attributeDescriptions.get(0);
            posAttribute.binding(0);
            posAttribute.location(0);
            posAttribute.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
            posAttribute.offset(0);

            VkVertexInputAttributeDescription uvAttribute = attributeDescriptions.get(1);
            uvAttribute.binding(0);
            uvAttribute.location(1);
            uvAttribute.format(VK10.VK_FORMAT_R32G32_SFLOAT);
            uvAttribute.offset(3 * 4);

            VkVertexInputAttributeDescription colorAttribute = attributeDescriptions.get(2);
            colorAttribute.binding(0);
            colorAttribute.location(2);
            colorAttribute.format(VK10.VK_FORMAT_R32G32B32A32_SFLOAT);
            colorAttribute.offset(5 * 4);

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
            rasterizer.cullMode(VK10.VK_CULL_MODE_BACK_BIT);
            rasterizer.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE);
            rasterizer.depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
            multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
            multisampling.sampleShadingEnable(false);
            multisampling.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.callocStack(stack);
            depthStencil.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);
            depthStencil.depthTestEnable(true);
            depthStencil.depthWriteEnable(true);
            depthStencil.depthCompareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL);
            depthStencil.depthBoundsTestEnable(false);
            depthStencil.stencilTestEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachments = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
            VkPipelineColorBlendAttachmentState colorBlendAttachment = colorBlendAttachments.get(0);
            colorBlendAttachment.blendEnable(true);
            colorBlendAttachment.srcColorBlendFactor(VK10.VK_BLEND_FACTOR_SRC_ALPHA);
            colorBlendAttachment.dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            colorBlendAttachment.colorBlendOp(VK10.VK_BLEND_OP_ADD);
            colorBlendAttachment.srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE);
            colorBlendAttachment.dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ZERO);
            colorBlendAttachment.alphaBlendOp(VK10.VK_BLEND_OP_ADD);
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

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.callocStack(stack);
            pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            pipelineLayoutInfo.pSetLayouts(stack.longs(descriptorSetLayout));

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.callocStack(1, stack);
            VkPushConstantRange pushConstantRange = pushConstantRanges.get(0);
            pushConstantRange.stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT);
            pushConstantRange.offset(0);
            pushConstantRange.size(64);
            pipelineLayoutInfo.pPushConstantRanges(pushConstantRanges);

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            result = vkCreatePipelineLayout(VulkanDevice.getDevice(), pipelineLayoutInfo, null, pPipelineLayout);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout: " + result);
            }
            pipelineLayout = pPipelineLayout.get(0);

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
                throw new RuntimeException("Failed to create graphics pipeline: " + result);
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

            VulkanMod.LOGGER.info("Graphics pipeline created successfully");
        }
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

    public static void updateTexture(long imageView) {
        if (descriptorSet == NULL || imageView == NULL) return;

        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.callocStack(1, stack);
            VkDescriptorImageInfo descriptorImageInfo = imageInfo.get(0);
            descriptorImageInfo.sampler(sampler);
            descriptorImageInfo.imageView(imageView);
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

    public static long getDescriptorSet() {
        return descriptorSet;
    }

    public static ByteBuffer loadShader(String path) {
        try {
            java.io.InputStream is = VulkanMod.class.getClassLoader().getResourceAsStream(path);
            if (is == null) {
                VulkanMod.LOGGER.error("Shader not found: {}", path);
                return null;
            }
            byte[] bytes = is.readAllBytes();
            is.close();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            return buffer;
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to load shader {}: {}", path, e.getMessage());
            return null;
        }
    }

    public static long getPipeline() {
        return pipeline;
    }

    public static long getPipelineLayout() {
        return pipelineLayout;
    }

    public static void cleanup() {
        if (pipeline != VK10.VK_NULL_HANDLE) {
            vkDestroyPipeline(VulkanDevice.getDevice(), pipeline, null);
        }
        if (pipelineLayout != VK10.VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(VulkanDevice.getDevice(), pipelineLayout, null);
        }
        if (descriptorSetLayout != VK10.VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(VulkanDevice.getDevice(), descriptorSetLayout, null);
        }
        if (descriptorPool != VK10.VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(VulkanDevice.getDevice(), descriptorPool, null);
        }
        if (sampler != VK10.VK_NULL_HANDLE) {
            vkDestroySampler(VulkanDevice.getDevice(), sampler, null);
        }
    }
}
