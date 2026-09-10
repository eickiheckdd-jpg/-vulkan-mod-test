package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.config.VulkanModConfig;
import net.vulkanmod.render.VulkanChunkMeshBatcher;
import net.vulkanmod.render.VulkanFrustumCuller;
import net.vulkanmod.render.VulkanIndirectDrawSystem;
import net.vulkanmod.render.VulkanMultiThreadedRenderer;
import net.vulkanmod.render.VulkanPerformanceStats;
import net.vulkanmod.render.VulkanTextureStreamer;
import net.vulkanmod.vulkan.VulkanTextureCapture;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanRenderer {
    public enum State {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        ERROR
    }

    private static long window;
    private static State state = State.UNINITIALIZED;
    private static long[] imageAvailableSemaphores;
    private static long[] renderFinishedSemaphores;
    private static long debugMessenger;
    private static int frameIndex = 0;

    public static boolean isVulkanAvailable() {
        return glfwVulkanSupported();
    }

    public static boolean isInitialized() {
        return state == State.READY;
    }

    public static synchronized void initialize() throws Exception {
        if (state != State.UNINITIALIZED) {
            return;
        }

        state = State.INITIALIZING;
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
        VulkanFullscreenQuad.initialize();
        VulkanVertexCapture.initialize();
        VulkanChunkMeshBatcher.initialize();
        VulkanIndirectDrawSystem.initialize();
        VulkanMultiThreadedRenderer.initialize();
        VulkanTextureStreamer.initialize();
        VulkanFrustumCuller.initialize();
        createSyncObjects();
        VulkanCommandBuffer.create();

        state = State.READY;
        VulkanMod.LOGGER.info("VulkanRenderer initialized successfully");
    }

    private static void createSyncObjects() {
        try (MemoryStack stack = stackPush()) {
            int imageCount = VulkanSwapchain.getSwapchainImages().length;
            imageAvailableSemaphores = new long[imageCount];
            renderFinishedSemaphores = new long[imageCount];

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);

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
        if (state != State.READY) {
            return;
        }

        VulkanPerformanceStats.beginFrame();

        try (MemoryStack stack = stackPush()) {
            IntBuffer pImageIndex = stack.ints(0);
            int result = vkAcquireNextImageKHR(
                VulkanDevice.getDevice(),
                VulkanSwapchain.getSwapchain(),
                Long.MAX_VALUE,
                imageAvailableSemaphores[frameIndex],
                VK10.VK_NULL_HANDLE,
                pImageIndex
            );

            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapchain();
                return;
            }
            if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                VulkanMod.LOGGER.error("Failed to acquire swapchain image: {}", result);
                return;
            }

            int imageIndex = pImageIndex.get(0);

            // Capture Minecraft's framebuffer via OpenGL bridge
            int fbWidth = VulkanTextureCapture.getViewportWidth();
            int fbHeight = VulkanTextureCapture.getViewportHeight();
            if (fbWidth > 0 && fbHeight > 0) {
                ByteBuffer pixelData = VulkanTextureCapture.captureFramebuffer(fbWidth, fbHeight);
                if (pixelData != null && VulkanFullscreenQuad.isInitialized()) {
                    VulkanFullscreenQuad.updateTexture(pixelData, fbWidth, fbHeight);
                    if (VulkanPipeline.getDescriptorSet() != NULL && VulkanFullscreenQuad.getCapturedImageView() != NULL) {
                        VulkanPipeline.updateTexture(VulkanFullscreenQuad.getCapturedImageView());
                    }
                    MemoryUtil.memFree(pixelData);
                }
            }

            VulkanCommandBuffer.recordCommandBuffer(imageIndex);

            VulkanTextureStreamer.processTextureQueue();

            VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            LongBuffer pWaitSemaphores = stack.mallocLong(1);
            pWaitSemaphores.put(0, imageAvailableSemaphores[frameIndex]).flip();
            submitInfo.pWaitSemaphores(pWaitSemaphores);
            submitInfo.pWaitDstStageMask(stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
            PointerBuffer pCommandBuffers = stack.mallocPointer(1);
            pCommandBuffers.put(0, VulkanCommandBuffer.getCommandBuffers()[imageIndex]).flip();
            submitInfo.pCommandBuffers(pCommandBuffers);
            LongBuffer pSignalSemaphores = stack.mallocLong(1);
            pSignalSemaphores.put(0, renderFinishedSemaphores[frameIndex]).flip();
            submitInfo.pSignalSemaphores(pSignalSemaphores);

            result = vkQueueSubmit(VulkanDevice.getGraphicsQueue(), submitInfo, VK10.VK_NULL_HANDLE);
            if (result != VK_SUCCESS) {
                VulkanMod.LOGGER.error("Failed to submit draw command buffer: {}", result);
                return;
            }

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.callocStack(stack);
            presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
            LongBuffer pPresentWaitSemaphores = stack.mallocLong(1);
            pPresentWaitSemaphores.put(0, renderFinishedSemaphores[frameIndex]).flip();
            presentInfo.pWaitSemaphores(pPresentWaitSemaphores);
            LongBuffer pSwapchains = stack.mallocLong(1);
            pSwapchains.put(0, VulkanSwapchain.getSwapchain()).flip();
            presentInfo.pSwapchains(pSwapchains);
            presentInfo.pImageIndices(pImageIndex);

            result = vkQueuePresentKHR(VulkanDevice.getPresentQueue(), presentInfo);
            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                recreateSwapchain();
            } else if (result != VK_SUCCESS) {
                VulkanMod.LOGGER.error("Failed to present swapchain image: {}", result);
                return;
            }

            frameIndex = (frameIndex + 1) % imageAvailableSemaphores.length;
        }

        VulkanPerformanceStats.endFrame();
    }

    public static void recreateSwapchain() {
        if (state != State.READY) return;
        VulkanMod.LOGGER.info("Recreating swapchain...");
        cleanupSwapchainResources();
        VulkanSwapchain.create(window);
        VulkanFramebuffer.create();
        VulkanCommandBuffer.create();
    }

    private static void cleanupSwapchainResources() {
        VulkanCommandBuffer.cleanup();
        VulkanFramebuffer.cleanup();
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
        if (state == State.UNINITIALIZED) return;

        cleanupSwapchainResources();

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
        VulkanMultiThreadedRenderer.cleanup();
        VulkanIndirectDrawSystem.cleanup();
        VulkanChunkMeshBatcher.cleanup();
        VulkanVertexCapture.cleanup();
        VulkanFullscreenQuad.cleanup();
        VulkanTextureStreamer.cleanup();
        VulkanFrustumCuller.cleanup();
        VulkanPipeline.cleanup();
        VulkanRenderPass.cleanup();
        VulkanFramebuffer.cleanup();
        VulkanSwapchain.cleanup();
        VulkanDevice.cleanup();
        VulkanInstance.cleanup();

        state = State.UNINITIALIZED;
        VulkanMod.LOGGER.info("VulkanRenderer cleaned up");
    }
}
