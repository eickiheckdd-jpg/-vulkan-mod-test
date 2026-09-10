package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanDevice;
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

public class VulkanRenderStateManager {
    private static final int MAX_PIPELINES = 64;
    private static final int MAX_RENDER_STATES = 256;

    private static long[] pipelineCache;
    private static long pipelineLayout;
    private static long descriptorSetLayout;
    private static long sampler;
    private static boolean initialized = false;

    private static class PipelineKey {
        int topology;
        int cullMode;
        int blendMode;
        int depthTest;
        int depthWrite;
        long shaderHash;
    }

    private static class RenderState {
        int topology;
        int cullMode;
        int blendMode;
        int depthTest;
        int depthWrite;
        long pipeline;
        boolean dirty;
    }

    private static final Deque<RenderState> stateStack = new ArrayDeque<>();
    private static RenderState currentState;

    public static synchronized void initialize() {
        if (initialized) return;

        try (MemoryStack stack = stackPush()) {
            pipelineCache = new long[MAX_PIPELINES];
            createPipelineLayout(stack);
            createDescriptorSetLayout(stack);
            createSampler(stack);
            createDefaultPipelines(stack);

            currentState = new RenderState();
            currentState.dirty = true;

            initialized = true;
            VulkanMod.LOGGER.info("Render state manager initialized");
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to initialize render state manager: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createPipelineLayout(MemoryStack stack) {
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.callocStack(stack);
        layoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
        layoutInfo.pSetLayouts(stack.longs(0));

        LongBuffer pLayout = stack.mallocLong(1);
        int result = vkCreatePipelineLayout(VulkanDevice.getDevice(), layoutInfo, null, pLayout);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create pipeline layout: " + result);
        }
        pipelineLayout = pLayout.get(0);
    }

    private static void createDescriptorSetLayout(MemoryStack stack) {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.callocStack(1, stack);
        VkDescriptorSetLayoutBinding binding = bindings.get(0);
        binding.binding(0);
        binding.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        binding.descriptorCount(1);
        binding.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        binding.pImmutableSamplers(null);

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

    private static void createSampler(MemoryStack stack) {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.callocStack(stack);
        samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
        samplerInfo.magFilter(VK_FILTER_LINEAR);
        samplerInfo.minFilter(VK_FILTER_LINEAR);
        samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);
        samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT);
        samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT);
        samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT);
        samplerInfo.mipLodBias(0.0f);
        samplerInfo.anisotropyEnable(false);
        samplerInfo.maxAnisotropy(1.0f);
        samplerInfo.compareEnable(false);
        samplerInfo.compareOp(VK_COMPARE_OP_ALWAYS);
        samplerInfo.minLod(0.0f);
        samplerInfo.maxLod(1.0f);
        samplerInfo.borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK);
        samplerInfo.unnormalizedCoordinates(false);

        LongBuffer pSampler = stack.mallocLong(1);
        int result = vkCreateSampler(VulkanDevice.getDevice(), samplerInfo, null, pSampler);
        if (result == VK_SUCCESS) {
            sampler = pSampler.get(0);
        }
    }

    private static void createDefaultPipelines(MemoryStack stack) {
        // Opaque pipeline
        createPipeline(stack, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, VK_CULL_MODE_BACK_BIT, 0, true, true);
        // Alpha tested pipeline
        createPipeline(stack, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, VK_CULL_MODE_BACK_BIT, 1, true, true);
        // Translucent pipeline
        createPipeline(stack, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, VK_CULL_MODE_NONE, 2, true, false);
    }

    private static void createPipeline(MemoryStack stack, int topology, int cullMode, int blendMode,
                                       boolean depthTest, boolean depthWrite) {
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
        inputAssembly.topology(topology);
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
        rasterizer.cullMode(cullMode);
        rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE);
        rasterizer.depthBiasEnable(false);

        VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
        multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
        multisampling.sampleShadingEnable(false);
        multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

        VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.callocStack(stack);
        depthStencil.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);
        depthStencil.depthTestEnable(depthTest);
        depthStencil.depthWriteEnable(depthWrite);
        depthStencil.depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
        depthStencil.depthBoundsTestEnable(false);
        depthStencil.stencilTestEnable(false);

        VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachments = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
        VkPipelineColorBlendAttachmentState colorBlendAttachment = colorBlendAttachments.get(0);

        if (blendMode == 0) {
            colorBlendAttachment.blendEnable(false);
            colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
        } else if (blendMode == 1) {
            colorBlendAttachment.blendEnable(true);
            colorBlendAttachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
            colorBlendAttachment.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            colorBlendAttachment.colorBlendOp(VK_BLEND_OP_ADD);
            colorBlendAttachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
            colorBlendAttachment.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
            colorBlendAttachment.alphaBlendOp(VK_BLEND_OP_ADD);
            colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
        } else {
            colorBlendAttachment.blendEnable(true);
            colorBlendAttachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
            colorBlendAttachment.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            colorBlendAttachment.colorBlendOp(VK_BLEND_OP_ADD);
            colorBlendAttachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
            colorBlendAttachment.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
            colorBlendAttachment.alphaBlendOp(VK_BLEND_OP_ADD);
            colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
        }

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
        pipelineInfo.renderPass(VK_NULL_HANDLE);
        pipelineInfo.subpass(0);
        pipelineInfo.basePipelineHandle(NULL);
        pipelineInfo.basePipelineIndex(-1);

        LongBuffer pPipeline = stack.mallocLong(1);
        int result = vkCreateGraphicsPipelines(VulkanDevice.getDevice(), NULL, pipelineInfoBuffer, null, pPipeline);
        if (result == VK_SUCCESS) {
            int index = (topology * 16 + cullMode * 4 + blendMode) % MAX_PIPELINES;
            pipelineCache[index] = pPipeline.get(0);
        }
    }

    public static void setTopology(int topology) {
        if (currentState.topology != topology) {
            currentState.topology = topology;
            currentState.dirty = true;
        }
    }

    public static void setCullMode(int cullMode) {
        if (currentState.cullMode != cullMode) {
            currentState.cullMode = cullMode;
            currentState.dirty = true;
        }
    }

    public static void setBlendMode(int blendMode) {
        if (currentState.blendMode != blendMode) {
            currentState.blendMode = blendMode;
            currentState.dirty = true;
        }
    }

    public static void setDepthTest(boolean enabled) {
        if (currentState.depthTest != (enabled ? 1 : 0)) {
            currentState.depthTest = enabled ? 1 : 0;
            currentState.dirty = true;
        }
    }

    public static void setDepthWrite(boolean enabled) {
        if (currentState.depthWrite != (enabled ? 1 : 0)) {
            currentState.depthWrite = enabled ? 1 : 0;
            currentState.dirty = true;
        }
    }

    public static void applyState(long commandBuffer) {
        if (!currentState.dirty) return;

        int index = (currentState.topology * 16 + currentState.cullMode * 4 + currentState.blendMode) % MAX_PIPELINES;
        long pipeline = pipelineCache[index];
        if (pipeline != NULL) {
            VkCommandBuffer cmdBuf = new VkCommandBuffer(commandBuffer, VulkanDevice.getDevice());
            vkCmdBindPipeline(cmdBuf, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        }
        currentState.dirty = false;
    }

    public static void pushState() {
        stateStack.push(currentState);
        currentState = new RenderState();
        currentState.dirty = true;
    }

    public static void popState() {
        if (!stateStack.isEmpty()) {
            currentState = stateStack.pop();
            currentState.dirty = true;
        }
    }

    public static long getPipelineLayout() {
        return pipelineLayout;
    }

    public static long getDescriptorSetLayout() {
        return descriptorSetLayout;
    }

    public static long getSampler() {
        return sampler;
    }

    public static void cleanup() {
        for (int i = 0; i < MAX_PIPELINES; i++) {
            if (pipelineCache[i] != NULL) {
                vkDestroyPipeline(VulkanDevice.getDevice(), pipelineCache[i], null);
                pipelineCache[i] = NULL;
            }
        }
        if (pipelineLayout != NULL) {
            vkDestroyPipelineLayout(VulkanDevice.getDevice(), pipelineLayout, null);
            pipelineLayout = NULL;
        }
        if (descriptorSetLayout != NULL) {
            vkDestroyDescriptorSetLayout(VulkanDevice.getDevice(), descriptorSetLayout, null);
            descriptorSetLayout = NULL;
        }
        if (sampler != NULL) {
            vkDestroySampler(VulkanDevice.getDevice(), sampler, null);
            sampler = NULL;
        }
        stateStack.clear();
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
