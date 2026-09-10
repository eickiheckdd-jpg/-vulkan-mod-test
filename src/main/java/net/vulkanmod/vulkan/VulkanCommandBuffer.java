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

public class VulkanCommandBuffer {
    private static long[] commandBuffers;
    private static long fence;

    public static void create() {
        try (MemoryStack stack = stackPush()) {
            int imageCount = VulkanSwapchain.getSwapchainImages().length;
            commandBuffers = new long[imageCount];

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(VulkanDevice.getCommandPool());
            allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(imageCount);

            PointerBuffer pCommandBuffers = stack.mallocPointer(imageCount);
            int result = vkAllocateCommandBuffers(VulkanDevice.getDevice(), allocInfo, pCommandBuffers);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers: " + result);
            }
            for (int i = 0; i < imageCount; i++) {
                commandBuffers[i] = pCommandBuffers.get(i);
            }

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

            PointerBuffer pFence = stack.mallocPointer(1);
            result = vkCreateFence(VulkanDevice.getDevice(), fenceInfo, null, pFence);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create fence: " + result);
            }
            fence = pFence.get(0);

            VulkanMod.LOGGER.info("Command buffers allocated (count={})", imageCount);
        }
    }

    public static void recordCommandBuffer(int imageIndex) {
        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer commandBuffer = VkCommandBuffer.create(commandBuffers[imageIndex], VulkanDevice.getDevice());

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(0);

            int result = vkBeginCommandBuffer(commandBuffer, beginInfo);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer: " + result);
            }

            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.callocStack(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
            renderPassInfo.renderPass(VulkanRenderPass.getRenderPass());
            renderPassInfo.framebuffer(VulkanFramebuffer.getFramebuffers()[imageIndex]);
            renderPassInfo.renderArea(VkRect2D.callocStack(stack).offset(VkOffset2D.callocStack(stack).x(0).y(0))
                .extent(VkExtent2D.callocStack(stack).width(VulkanSwapchain.getWidth()).height(VulkanSwapchain.getHeight())));
            VkClearValue.Buffer clearValues = VkClearValue.callocStack(1, stack);
            VkClearValue clearValue = clearValues.get(0);
            FloatBuffer clearColor = stack.floats(0.1f, 0.1f, 0.1f, 1.0f);
            clearValue.color(clearColor);
            renderPassInfo.pClearValues(clearValues);

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);

            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack);
            viewport.x(0).y(0).width(VulkanSwapchain.getWidth()).height(VulkanSwapchain.getHeight())
                .minDepth(0.0f).maxDepth(1.0f);
            vkCmdSetViewport(commandBuffer, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.callocStack(1, stack);
            scissor.offset(VkOffset2D.callocStack(stack).x(0).y(0))
                .extent(VkExtent2D.callocStack(stack).width(VulkanSwapchain.getWidth()).height(VulkanSwapchain.getHeight()));
            vkCmdSetScissor(commandBuffer, 0, scissor);

            vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, VulkanPipeline.getPipeline());
            vkCmdDraw(commandBuffer, 3, 1, 0, 0);

            vkCmdEndRenderPass(commandBuffer);

            result = vkEndCommandBuffer(commandBuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to end command buffer: " + result);
            }
        }
    }

    public static long[] getCommandBuffers() {
        return commandBuffers;
    }

    public static long getFence() {
        return fence;
    }

    public static void cleanup() {
        if (commandBuffers != null) {
            PointerBuffer pCommandBuffers = MemoryUtil.memAllocPointer(commandBuffers.length);
            for (int i = 0; i < commandBuffers.length; i++) {
                pCommandBuffers.put(i, commandBuffers[i]);
            }
            pCommandBuffers.flip();
            vkFreeCommandBuffers(VulkanDevice.getDevice(), VulkanDevice.getCommandPool(), pCommandBuffers);
            MemoryUtil.memFree(pCommandBuffers);
            commandBuffers = null;
        }
        if (fence != VK10.VK_NULL_HANDLE) {
            vkDestroyFence(VulkanDevice.getDevice(), fence, null);
            fence = VK10.VK_NULL_HANDLE;
        }
    }
}
