package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanSwapchain {
    private static long surface;
    private static long swapchain;
    private static long[] swapchainImages;
    private static long[] imageViews;
    private static int imageFormat;
    private static int imageColorSpace;
    private static int width;
    private static int height;

    public static void createSurface(long window) {
        if (surface != MemoryUtil.NULL) return;
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            int result = glfwCreateWindowSurface(VulkanInstance.getInstance(), window, null, pSurface);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create window surface: " + result);
            }
            surface = pSurface.get(0);
        }
    }

    public static void create(long window) {
        try (MemoryStack stack = stackPush()) {
            createSurface(window);
            cleanupSwapchain();

            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.callocStack(stack);
            int result = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                VulkanInstance.getPhysicalDevice(), surface, capabilities
            );
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to query surface capabilities: " + result);
            }

            IntBuffer pSurfaceFormatCount = stack.ints(0);
            vkGetPhysicalDeviceSurfaceFormatsKHR(VulkanInstance.getPhysicalDevice(), surface, pSurfaceFormatCount, null);
            if (pSurfaceFormatCount.get(0) == 0) {
                throw new RuntimeException("The Vulkan surface exposes no supported formats");
            }
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(pSurfaceFormatCount.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(VulkanInstance.getPhysicalDevice(), surface, pSurfaceFormatCount, formats);

            imageFormat = formats.get(0).format();
            imageColorSpace = formats.get(0).colorSpace();
            for (int i = 0; i < formats.capacity(); i++) {
                VkSurfaceFormatKHR fmt = formats.get(i);
                if (fmt.format() == VK10.VK_FORMAT_B8G8R8A8_SRGB &&
                    fmt.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    imageFormat = fmt.format();
                    imageColorSpace = fmt.colorSpace();
                    break;
                }
            }

            int desiredImageCount = Math.max(capabilities.minImageCount() + 1, 2);
            if (capabilities.maxImageCount() > 0) {
                desiredImageCount = Math.min(desiredImageCount, capabilities.maxImageCount());
            }

            VkExtent2D extent = capabilities.currentExtent();
            if (extent.width() == 0xFFFFFFFF || extent.height() == 0xFFFFFFFF) {
                IntBuffer framebufferWidth = stack.ints(0);
                IntBuffer framebufferHeight = stack.ints(0);
                GLFW.glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
                extent.width(Math.max(1, framebufferWidth.get(0)));
                extent.height(Math.max(1, framebufferHeight.get(0)));
            }
            width = extent.width();
            height = extent.height();

            int preTransform = (capabilities.supportedTransforms() & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) != 0
                ? VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
                : capabilities.currentTransform();
            int compositeAlpha = chooseCompositeAlpha(capabilities.supportedCompositeAlpha());

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.callocStack(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
            createInfo.surface(surface);
            createInfo.minImageCount(desiredImageCount);
            createInfo.imageFormat(imageFormat);
            createInfo.imageColorSpace(imageColorSpace);
            createInfo.imageExtent(extent);
            createInfo.imageArrayLayers(1);
            createInfo.imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
            createInfo.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            createInfo.preTransform(preTransform);
            createInfo.compositeAlpha(compositeAlpha);
            createInfo.presentMode(VK_PRESENT_MODE_FIFO_KHR);
            createInfo.clipped(true);
            createInfo.oldSwapchain(VK10.VK_NULL_HANDLE);

            LongBuffer pSwapchain = stack.mallocLong(1);
            result = vkCreateSwapchainKHR(VulkanDevice.getDevice(), createInfo, null, pSwapchain);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create swapchain: " + result);
            }
            swapchain = pSwapchain.get(0);

            IntBuffer pImageCount = stack.ints(0);
            vkGetSwapchainImagesKHR(VulkanDevice.getDevice(), swapchain, pImageCount, null);
            swapchainImages = new long[pImageCount.get(0)];
            LongBuffer pImages = stack.mallocLong(pImageCount.get(0));
            vkGetSwapchainImagesKHR(VulkanDevice.getDevice(), swapchain, pImageCount, pImages);
            for (int i = 0; i < swapchainImages.length; i++) {
                swapchainImages[i] = pImages.get(i);
            }

            imageViews = new long[swapchainImages.length];
            for (int i = 0; i < swapchainImages.length; i++) {
                LongBuffer pImageView = stack.mallocLong(1);
                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.callocStack(stack);
                viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
                viewInfo.image(swapchainImages[i]);
                viewInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
                viewInfo.format(imageFormat);
                viewInfo.components(VkComponentMapping.callocStack(stack)
                    .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                    .g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                    .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                    .a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY));
                viewInfo.subresourceRange(VkImageSubresourceRange.callocStack(stack)
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1));

                result = vkCreateImageView(VulkanDevice.getDevice(), viewInfo, null, pImageView);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create image view: " + result);
                }
                imageViews[i] = pImageView.get(0);
            }

            VulkanMod.LOGGER.info("Swapchain created: {}x{}, format={}", width, height, imageFormat);
        }
    }

    public static long getSwapchain() {
        return swapchain;
    }

    public static long[] getSwapchainImages() {
        return swapchainImages;
    }

    public static long[] getImageViews() {
        return imageViews;
    }

    public static int getImageFormat() {
        return imageFormat;
    }

    private static int chooseCompositeAlpha(int supported) {
        int[] candidates = {
            VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
            VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
        };
        for (int candidate : candidates) {
            if ((supported & candidate) != 0) return candidate;
        }
        throw new RuntimeException("The Vulkan surface exposes no supported composite alpha mode");
    }

    public static int getWidth() {
        return width;
    }

    public static int getHeight() {
        return height;
    }

    public static long getSurface() {
        return surface;
    }

    public static void cleanupSwapchain() {
        if (imageViews != null) {
            for (long iv : imageViews) {
                if (iv != MemoryUtil.NULL) {
                    vkDestroyImageView(VulkanDevice.getDevice(), iv, null);
                }
            }
            imageViews = null;
        }
        if (swapchain != MemoryUtil.NULL) {
            vkDestroySwapchainKHR(VulkanDevice.getDevice(), swapchain, null);
            swapchain = MemoryUtil.NULL;
        }
        swapchainImages = null;
        width = 0;
        height = 0;
    }

    public static void cleanup() {
        cleanupSwapchain();
        if (surface != MemoryUtil.NULL) {
            vkDestroySurfaceKHR(VulkanInstance.getInstance(), surface, null);
            surface = MemoryUtil.NULL;
        }
    }
}
