package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class VulkanFramebuffer {
    private static long[] framebuffers;

    public static void create() {
        try (MemoryStack stack = stackPush()) {
            int imageCount = VulkanSwapchain.getSwapchainImages().length;
            framebuffers = new long[imageCount];
            LongBuffer pFramebuffer = stack.mallocLong(1);

            for (int i = 0; i < imageCount; i++) {
                LongBuffer attachments = stack.mallocLong(1);
                attachments.put(0, VulkanSwapchain.getImageViews()[i]);

                VkFramebufferCreateInfo.Buffer createInfo = VkFramebufferCreateInfo.callocStack(stack);
                createInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
                createInfo.renderPass(VulkanRenderPass.getRenderPass());
                createInfo.pAttachments(attachments);
                createInfo.width(VulkanSwapchain.getWidth());
                createInfo.height(VulkanSwapchain.getHeight());
                createInfo.layers(1);

                int result = vkCreateFramebuffer(VulkanDevice.getDevice(), createInfo, null, pFramebuffer);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer: " + result);
                }
                framebuffers[i] = pFramebuffer.get(0);
            }

            VulkanMod.LOGGER.info("Created {} framebuffer(s)", framebuffers.length);
        }
    }

    public static long[] getFramebuffers() {
        return framebuffers;
    }
}
