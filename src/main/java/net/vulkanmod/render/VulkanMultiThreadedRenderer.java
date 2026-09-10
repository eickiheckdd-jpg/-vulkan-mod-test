package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanDevice;
import net.vulkanmod.vulkan.VulkanInstance;
import net.vulkanmod.vulkan.VulkanSwapchain;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanMultiThreadedRenderer {
    private static final BlockingQueue<RenderCommand> commandQueue = new ArrayBlockingQueue<>(1000);
    private static volatile boolean running = false;
    private static Thread renderThread;
    private static long renderThreadFence;
    private static long[] threadCommandBuffers;
    private static int maxThreads = 4;

    private static class RenderCommand {
        int type;
        long buffer;
        int vertexCount;
        int instanceCount;
        int firstVertex;
        int firstInstance;
        long pipeline;
        long pipelineLayout;
        long[] descriptorSets;
        int imageIndex;
    }

    public static synchronized void initialize() {
        if (running) return;

        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(VulkanInstance.getGraphicsQueueFamilyIndex());
            poolInfo.flags(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT | VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pPool = stack.mallocLong(1);
            int result = vkCreateCommandPool(VulkanDevice.getDevice(), poolInfo, null, pPool);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render thread command pool: " + result);
            }

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pFence = stack.mallocLong(1);
            result = vkCreateFence(VulkanDevice.getDevice(), fenceInfo, null, pFence);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render thread fence: " + result);
            }
            renderThreadFence = pFence.get(0);

            int imageCount = VulkanSwapchain.getSwapchainImages().length;
            threadCommandBuffers = new long[imageCount];

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(pPool.get(0));
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_SECONDARY);
            allocInfo.commandBufferCount(imageCount);

            PointerBuffer pCommandBuffers = stack.mallocPointer(imageCount);
            result = vkAllocateCommandBuffers(VulkanDevice.getDevice(), allocInfo, pCommandBuffers);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate thread command buffers: " + result);
            }
            for (int i = 0; i < imageCount; i++) {
                threadCommandBuffers[i] = pCommandBuffers.get(i);
            }

            running = true;
            renderThread = new Thread(VulkanMultiThreadedRenderer::processCommands, "Vulkan-Render-Thread");
            renderThread.setPriority(Thread.MAX_PRIORITY);
            renderThread.start();

            VulkanMod.LOGGER.info("Multi-threaded renderer initialized with {} threads", maxThreads);
        }
    }

    private static void processCommands() {
        while (running) {
            try {
                RenderCommand cmd = commandQueue.poll();
                if (cmd != null) {
                    executeCommand(cmd);
                }
            } catch (Exception e) {
                VulkanMod.LOGGER.error("Render thread error: {}", e.getMessage());
            }
        }
    }

    private static void executeCommand(RenderCommand cmd) {
        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer commandBuffer = new VkCommandBuffer(threadCommandBuffers[cmd.imageIndex], VulkanDevice.getDevice());

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT);

            vkBeginCommandBuffer(commandBuffer, beginInfo);

            if (cmd.pipeline != NULL) {
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
            }

            if (cmd.descriptorSets != null && cmd.descriptorSets.length > 0) {
                LongBuffer pDescriptorSets = stack.longs(cmd.descriptorSets);
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipelineLayout, 0, pDescriptorSets, null);
            }

            vkCmdDraw(commandBuffer, cmd.vertexCount, cmd.instanceCount, cmd.firstVertex, cmd.firstInstance);

            vkEndCommandBuffer(commandBuffer);
        }
    }

    public static void submitCommand(int type, long buffer, int vertexCount, int instanceCount,
                                     int firstVertex, int firstInstance, long pipeline,
                                     long pipelineLayout, long[] descriptorSets, int imageIndex) {
        if (!running) return;

        try {
            RenderCommand cmd = new RenderCommand();
            cmd.type = type;
            cmd.buffer = buffer;
            cmd.vertexCount = vertexCount;
            cmd.instanceCount = instanceCount;
            cmd.firstVertex = firstVertex;
            cmd.firstInstance = firstInstance;
            cmd.pipeline = pipeline;
            cmd.pipelineLayout = pipelineLayout;
            cmd.descriptorSets = descriptorSets;
            cmd.imageIndex = imageIndex;
            commandQueue.put(cmd);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void waitForRenderThread() {
        if (renderThreadFence != NULL) {
            try (MemoryStack stack = stackPush()) {
                vkWaitForFences(VulkanDevice.getDevice(), stack.longs(renderThreadFence), true, Long.MAX_VALUE);
            }
        }
    }

    public static void cleanup() {
        running = false;
        if (renderThread != null) {
            try {
                renderThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (renderThreadFence != NULL) {
            vkDestroyFence(VulkanDevice.getDevice(), renderThreadFence, null);
            renderThreadFence = NULL;
        }
        if (threadCommandBuffers != null) {
            try (MemoryStack stack = stackPush()) {
                PointerBuffer pBuffers = MemoryUtil.memAllocPointer(threadCommandBuffers.length);
                for (long buf : threadCommandBuffers) {
                    pBuffers.put(buf);
                }
                pBuffers.flip();
                vkFreeCommandBuffers(VulkanDevice.getDevice(), VulkanDevice.getCommandPool(), pBuffers);
                MemoryUtil.memFree(pBuffers);
            }
            threadCommandBuffers = null;
        }
    }
}
