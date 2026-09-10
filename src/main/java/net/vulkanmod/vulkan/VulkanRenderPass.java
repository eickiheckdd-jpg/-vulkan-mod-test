package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanRenderPass {
    private static long renderPass;

    public static void create() {
        try (MemoryStack stack = stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(1, stack);
            VkAttachmentDescription colorAttachment = attachments.get(0);
            colorAttachment.format(VulkanSwapchain.getImageFormat());
            colorAttachment.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
            colorAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachment.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorAttachments = VkAttachmentReference.callocStack(1, stack);
            VkAttachmentReference colorAttachmentRef = colorAttachments.get(0);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpasses = VkSubpassDescription.callocStack(1, stack);
            VkSubpassDescription subpass = subpasses.get(0);
            subpass.pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorAttachments);

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.callocStack(1, stack);
            VkSubpassDependency dependency = dependencies.get(0);
            dependency.srcSubpass(VK10.VK_SUBPASS_EXTERNAL);
            dependency.dstSubpass(0);
            dependency.srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.srcAccessMask(0);
            dependency.dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.callocStack(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            createInfo.pAttachments(attachments);
            createInfo.pSubpasses(subpasses);
            createInfo.pDependencies(dependencies);

            LongBuffer pRenderPass = stack.mallocLong(1);
            int result = vkCreateRenderPass(VulkanDevice.getDevice(), createInfo, null, pRenderPass);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render pass: " + result);
            }
            renderPass = pRenderPass.get(0);

            VulkanMod.LOGGER.info("Render pass created successfully");
        }
    }

    public static long getRenderPass() {
        return renderPass;
    }

    public static void cleanup() {
        if (renderPass != VK10.VK_NULL_HANDLE) {
            vkDestroyRenderPass(VulkanDevice.getDevice(), renderPass, null);
        }
    }
}
