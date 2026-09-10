package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanRenderer {
    private static long window;
    private static boolean initialized = false;
    private static long[] imageAvailableSemaphores;
    private static long[] renderFinishedSemaphores;
    private static int frameIndex = 0;

    public static boolean isVulkanAvailable() {
        return glfwVulkanSupported();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void initialize() throws Exception {
        if (initialized) return;

        window = MinecraftInstance.getWindowHandle();
        if (window == NULL) {
            throw new RuntimeException("Window handle not available");
        }

        logSystemInfo();

        VulkanInstance.create();
        VulkanInstance.selectPhysicalDevice();
        VulkanDevice.create();
        VulkanSwapchain.create(window);
        VulkanRenderPass.create();
        VulkanFramebuffer.create();
        VulkanPipeline.create();
        createSyncObjects();
        VulkanCommandBuffer.create();

        initialized = true;
        VulkanMod.LOGGER.info("VulkanRenderer initialized successfully");
    }

    private static void createSyncObjects() {
        try (MemoryStack stack = stackPush()) {
            int imageCount = VulkanSwapchain.getSwapchainImages().capacity();
            imageAvailableSemaphores = new long[imageCount];
            renderFinishedSemaphores = new long[imageCount];

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < imageCount; i++) {
                int result = vkCreateSemaphore(VulkanDevice.getDevice(), semaphoreInfo, null, pSemaphore);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create semaphore: " + result);
                imageAvailableSemaphores[i] = pSemaphore.get(0);

                result = vkCreateSemaphore(VulkanDevice.getDevice(), semaphoreInfo, null, pSemaphore);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create semaphore: " + result);
                renderFinishedSemaphores[i] = pSemaphore.get(0);
            }

            VulkanMod.LOGGER.info("Sync objects created ({} pairs)", imageCount);
        }
    }

    public static void render() {
        if (!initialized) return;

        try (MemoryStack stack = stackPush()) {
            LongBuffer pImageIndex = stack.mallocInt(1);
            int result = vkAcquireNextImageKHR(
                VulkanDevice.getDevice(),
                VulkanSwapchain.getSwapchain(),
                Long.MAX_VALUE,
                imageAvailableSemaphores[frameIndex],
                VK10.VK_NULL_HANDLE,
                pImageIndex
            );

            if (result == VK10.VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapchain();
                return;
            }
            if (result != VK10.VK_SUCCESS && result != VK10.VK_SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swapchain image: " + result);
            }

            int imageIndex = pImageIndex.get(0);

            VulkanCommandBuffer.recordCommandBuffer(imageIndex);

            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pWaitSemaphores(stack.longs(imageAvailableSemaphores[frameIndex]));
            submitInfo.pWaitDstStageMask(stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
            submitInfo.pCommandBuffers(stack.longs(VulkanCommandBuffer.getCommandBuffers()[imageIndex]));
            submitInfo.pSignalSemaphores(stack.longs(renderFinishedSemaphores[frameIndex]));

            result = vkQueueSubmit(VulkanDevice.getGraphicsQueue(), submitInfo, VK10.VK_NULL_HANDLE);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit draw command buffer: " + result);
            }

            VkPresentInfoKHR.Buffer presentInfo = VkPresentInfoKHR.callocStack(stack);
            presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
            presentInfo.pWaitSemaphores(stack.longs(renderFinishedSemaphores[frameIndex]));
            presentInfo.pSwapchains(stack.longs(VulkanSwapchain.getSwapchain()));
            presentInfo.pImageIndices(pImageIndex);

            result = vkQueuePresentKHR(VulkanDevice.getPresentQueue(), presentInfo);
            if (result == VK10.VK_ERROR_OUT_OF_DATE_KHR || result == VK10.VK_SUBOPTIMAL_KHR) {
                recreateSwapchain();
            } else if (result != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to present swapchain image: " + result);
            }

            result = vkQueueWaitIdle(VulkanDevice.getPresentQueue());
            if (result != VK_SUCCESS) {
                VulkanMod.LOGGER.warn("vkQueueWaitIdle returned: {}", result);
            }

            frameIndex = (frameIndex + 1) % imageAvailableSemaphores.length;
        }
    }

    private static void cleanupSwapchain() {
        if (VulkanCommandBuffer.getCommandBuffers() != null) {
            vkFreeCommandBuffers(VulkanDevice.getDevice(), VulkanDevice.getCommandPool(),
                VulkanCommandBuffer.getCommandBuffers());
        }
        if (VulkanFramebuffer.getFramebuffers() != null) {
            for (long fb : VulkanFramebuffer.getFramebuffers()) {
                if (fb != MemoryUtil.NULL) {
                    vkDestroyFramebuffer(VulkanDevice.getDevice(), fb, null);
                }
            }
        }
    }

    private static void logSystemInfo() {
        VulkanMod.LOGGER.info("=== Vulkan 1.1 Renderer Diagnostics ===");
        VulkanMod.LOGGER.info("Vulkan supported: {}", glfwVulkanSupported());
        VulkanMod.LOGGER.info("Vulkan API version: {}.{}.{}",
            VK10.VK_API_VERSION_MAJOR(VulkanInstance.getDeviceProperties().apiVersion()),
            VK10.VK_API_VERSION_MINOR(VulkanInstance.getDeviceProperties().apiVersion()),
            VK10.VK_API_VERSION_PATCH(VulkanInstance.getDeviceProperties().apiVersion()));
        VulkanMod.LOGGER.info("Vulkan 1.1 available: {}", VulkanInstance.isVulkan11Available());
        VulkanMod.LOGGER.info("GPU: {}", VulkanInstance.getDeviceProperties().deviceNameString());
        VulkanMod.LOGGER.info("Vendor ID: 0x{}", Integer.toHexString(VulkanInstance.getDeviceProperties().vendorID()));
        VulkanMod.LOGGER.info("Device ID: 0x{}", Integer.toHexString(VulkanInstance.getDeviceProperties().deviceID()));
        VulkanMod.LOGGER.info("Driver version: 0x{}", Integer.toHexString(VulkanInstance.getDeviceProperties().driverVersion()));
        VulkanMod.LOGGER.info("========================================");
    }

    public static void cleanup() {
        if (!initialized) return;

        if (imageAvailableSemaphores != null) {
            for (long sem : imageAvailableSemaphores) {
                if (sem != MemoryUtil.NULL) vkDestroySemaphore(VulkanDevice.getDevice(), sem, null);
            }
        }
        if (renderFinishedSemaphores != null) {
            for (long sem : renderFinishedSemaphores) {
                if (sem != MemoryUtil.NULL) vkDestroySemaphore(VulkanDevice.getDevice(), sem, null);
            }
        }

        VulkanCommandBuffer.cleanup();
        VulkanPipeline.cleanup();
        VulkanRenderPass.cleanup();
        VulkanFramebuffer.cleanup();
        VulkanSwapchain.cleanup();
        VulkanDevice.cleanup();
        VulkanInstance.cleanup();

        initialized = false;
        VulkanMod.LOGGER.info("VulkanRenderer cleaned up");
    }

    public static void recreateSwapchain() {
        if (!initialized) return;
        VulkanMod.LOGGER.info("Recreating swapchain...");
        cleanupSwapchain();
        VulkanSwapchain.create(window);
        VulkanFramebuffer.create();
        VulkanCommandBuffer.create();
    }
}
