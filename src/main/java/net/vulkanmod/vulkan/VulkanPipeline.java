package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class VulkanPipeline {
    private static long pipeline;
    private static long pipelineLayout;

    public static void create() {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer vertShader = loadShader("shaders/terrain.vert.spv");
            ByteBuffer fragShader = loadShader("shaders/terrain.frag.spv");

            if (vertShader == null || fragShader == null) {
                throw new RuntimeException("Failed to load shaders");
            }

            LongBuffer pVertModule = stack.mallocLong(1);
            VkShaderModuleCreateInfo.Buffer vertInfo = VkShaderModuleCreateInfo.callocStack(stack);
            vertInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            vertInfo.pCode(vertShader);
            int result = vkCreateShaderModule(VulkanDevice.getDevice(), vertInfo, null, pVertModule);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create vertex shader module: " + result);
            }
            long vertModule = pVertModule.get(0);

            LongBuffer pFragModule = stack.mallocLong(1);
            VkShaderModuleCreateInfo.Buffer fragInfo = VkShaderModuleCreateInfo.callocStack(stack);
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
            rasterizer.frontFace(VK10.VK_FRONT_FACE_CLOCKWISE);
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
            pipelineLayoutInfo.pSetLayouts(stack.longs(0));
            pipelineLayoutInfo.pushConstantRangeCount(0);

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            result = vkCreatePipelineLayout(VulkanDevice.getDevice(), pipelineLayoutInfo, null, pPipelineLayout);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout: " + result);
            }
            pipelineLayout = pPipelineLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.callocStack(1, stack);
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
            pipelineInfo.basePipelineHandle(MemoryUtil.NULL);
            pipelineInfo.basePipelineIndex(-1);

            LongBuffer pPipeline = stack.mallocLong(1);
            result = vkCreateGraphicsPipelines(VulkanDevice.getDevice(), VK10.VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
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

    private static ByteBuffer loadShader(String path) {
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
    }
}
