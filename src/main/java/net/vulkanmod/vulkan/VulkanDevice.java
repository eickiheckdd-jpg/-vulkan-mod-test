package net.vulkanmod.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.PointerBuffer;
import org.lwjgl.vulkan.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class VulkanDevice {
    private static VkDevice device;
    private static VkQueue graphicsQueue;
    private static VkQueue presentQueue;
    private static long commandPool;

    public static void create() {
        try (MemoryStack stack = stackPush()) {
            FloatBuffer pQueuePriorities = stack.floats(1.0f);

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(2, stack);

            VkDeviceQueueCreateInfo graphicsQueueInfo = queueCreateInfos.get(0);
            graphicsQueueInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            graphicsQueueInfo.queueFamilyIndex(VulkanInstance.getGraphicsQueueFamilyIndex());
            graphicsQueueInfo.pQueuePriorities(pQueuePriorities);

            VkDeviceQueueCreateInfo presentQueueInfo = queueCreateInfos.get(1);
            presentQueueInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            presentQueueInfo.queueFamilyIndex(VulkanInstance.getPresentQueueFamilyIndex());
            presentQueueInfo.pQueuePriorities(pQueuePriorities);

            VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);
            deviceFeatures.set(VulkanInstance.getDeviceFeatures());

            VkDeviceCreateInfo.Buffer deviceCreateInfo = VkDeviceCreateInfo.callocStack(stack);
            deviceCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            deviceCreateInfo.pQueueCreateInfos(queueCreateInfos);
            deviceCreateInfo.pEnabledFeatures(deviceFeatures);

            PointerBuffer ppEnabledExtensionNames = stack.pointers(
                stack.UTF8(VK10.VK_KHR_SWAPCHAIN_EXTENSION_NAME)
            );
            deviceCreateInfo.ppEnabledExtensionNames(ppEnabledExtensionNames);

            LongBuffer pDevice = stack.mallocLong(1);
            int result = vkCreateDevice(VulkanInstance.getPhysicalDevice(), deviceCreateInfo, null, pDevice);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device: " + result);
            }
            device = VkDevice.create(pDevice.get(0));

            LongBuffer pQueue = stack.mallocLong(1);
            vkGetDeviceQueue(device, VulkanInstance.getGraphicsQueueFamilyIndex(), 0, pQueue);
            graphicsQueue = VkQueue.create(pQueue.get(0));

            vkGetDeviceQueue(device, VulkanInstance.getPresentQueueFamilyIndex(), 0, pQueue);
            presentQueue = VkQueue.create(pQueue.get(0));

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(VulkanInstance.getGraphicsQueueFamilyIndex());
            poolInfo.flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            result = vkCreateCommandPool(device, poolInfo, null, pCommandPool);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool: " + result);
            }
            commandPool = pCommandPool.get(0);
        }
    }

    public static VkDevice getDevice() {
        return device;
    }

    public static VkQueue getGraphicsQueue() {
        return graphicsQueue;
    }

    public static VkQueue getPresentQueue() {
        return presentQueue;
    }

    public static long getCommandPool() {
        return commandPool;
    }

    public static void cleanup() {
        if (commandPool != VK10.VK_NULL_HANDLE) {
            vkDestroyCommandPool(device, commandPool, null);
        }
        if (device != null) {
            vkDestroyDevice(device, null);
        }
    }
}
