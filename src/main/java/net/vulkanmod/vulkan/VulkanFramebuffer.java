package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class VulkanFramebuffer {
    private static long[] framebuffers;
    private static long depthImage;
    private static long depthImageView;
    private static long depthImageMemory;

    public static void create() {
        cleanup();

        try (MemoryStack stack = stackPush()) {
            int imageCount = VulkanSwapchain.getSwapchainImages().length;
            framebuffers = new long[imageCount];

            createDepthResources(stack);

            for (int i = 0; i < imageCount; i++) {
                LongBuffer attachments = stack.mallocLong(2);
                attachments.put(0, VulkanSwapchain.getImageViews()[i]);
                attachments.put(1, depthImageView);
                attachments.flip();

                VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.callocStack(stack);
                createInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
                createInfo.renderPass(VulkanRenderPass.getRenderPass());
                createInfo.pAttachments(attachments);
                createInfo.width(VulkanSwapchain.getWidth());
                createInfo.height(VulkanSwapchain.getHeight());
                createInfo.layers(1);

                LongBuffer pFramebuffer = stack.mallocLong(1);
                int result = vkCreateFramebuffer(VulkanDevice.getDevice(), createInfo, null, pFramebuffer);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer: " + result);
                }
                framebuffers[i] = pFramebuffer.get(0);
            }

            VulkanMod.LOGGER.info("Created {} framebuffer(s) with depth", framebuffers.length);
        }
    }

    private static void createDepthResources(MemoryStack stack) {
        int width = VulkanSwapchain.getWidth();
        int height = VulkanSwapchain.getHeight();
        int depthFormat = VulkanRenderPass.getDepthFormat();

        VkImageCreateInfo imageInfo = VkImageCreateInfo.callocStack(stack);
        imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
        imageInfo.imageType(VK10.VK_IMAGE_TYPE_2D);
        imageInfo.format(depthFormat);
        imageInfo.extent(VkExtent3D.callocStack(stack).width(width).height(height).depth(1));
        imageInfo.mipLevels(1);
        imageInfo.arrayLayers(1);
        imageInfo.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
        imageInfo.tiling(VK10.VK_IMAGE_TILING_OPTIMAL);
        imageInfo.usage(VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        imageInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
        imageInfo.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);

        LongBuffer pImage = stack.mallocLong(1);
        int result = vkCreateImage(VulkanDevice.getDevice(), imageInfo, null, pImage);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create depth image: " + result);
        }
        depthImage = pImage.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.callocStack(stack);
        vkGetImageMemoryRequirements(VulkanDevice.getDevice(), depthImage, memRequirements);

        int memoryTypeIndex = VulkanDevice.findMemoryType(
            memRequirements.memoryTypeBits(),
            VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.callocStack(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(memoryTypeIndex);

        LongBuffer pImageMemory = stack.mallocLong(1);
        result = vkAllocateMemory(VulkanDevice.getDevice(), allocInfo, null, pImageMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate depth image memory: " + result);
        }
        depthImageMemory = pImageMemory.get(0);

        result = vkBindImageMemory(VulkanDevice.getDevice(), depthImage, depthImageMemory, 0);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to bind depth image memory: " + result);
        }

        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.callocStack(stack);
        viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
        viewInfo.image(depthImage);
        viewInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
        viewInfo.format(depthFormat);
        viewInfo.subresourceRange(VkImageSubresourceRange.callocStack(stack)
            .aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1));

        LongBuffer pImageView = stack.mallocLong(1);
        result = vkCreateImageView(VulkanDevice.getDevice(), viewInfo, null, pImageView);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create depth image view: " + result);
        }
        depthImageView = pImageView.get(0);
    }

    public static long[] getFramebuffers() {
        return framebuffers;
    }

    public static long getDepthImageView() {
        return depthImageView;
    }

    public static void cleanup() {
        if (depthImageView != MemoryUtil.NULL) {
            vkDestroyImageView(VulkanDevice.getDevice(), depthImageView, null);
            depthImageView = MemoryUtil.NULL;
        }
        if (depthImage != MemoryUtil.NULL) {
            vkDestroyImage(VulkanDevice.getDevice(), depthImage, null);
            depthImage = MemoryUtil.NULL;
        }
        if (depthImageMemory != MemoryUtil.NULL) {
            vkFreeMemory(VulkanDevice.getDevice(), depthImageMemory, null);
            depthImageMemory = MemoryUtil.NULL;
        }
        if (framebuffers != null) {
            for (long fb : framebuffers) {
                if (fb != MemoryUtil.NULL) {
                    vkDestroyFramebuffer(VulkanDevice.getDevice(), fb, null);
                }
            }
            framebuffers = null;
        }
    }
}
