package net.vulkanmod.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class VulkanInstance {
    public static final String[] REQUIRED_INSTANCE_EXTENSIONS = {
        VK10.VK_KHR_SURFACE_EXTENSION_NAME,
        VK10.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
    };

    public static final String[] REQUIRED_DEVICE_EXTENSIONS = {
        VK10.VK_KHR_SWAPCHAIN_EXTENSION_NAME
    };

    private static VkInstance instance;
    private static long physicalDevice;
    private static int graphicsQueueFamilyIndex = -1;
    private static int presentQueueFamilyIndex = -1;
    private static VkPhysicalDeviceProperties deviceProperties;
    private static VkPhysicalDeviceFeatures deviceFeatures;
    private static boolean vulkan11Available;

    public static void create() throws Exception {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pExtensionCount = stack.ints(0);
            vkEnumerateInstanceExtensionProperties((String[]) null, pExtensionCount, null);
            if (pExtensionCount.get(0) == 0) {
                throw new RuntimeException("No Vulkan extensions supported");
            }

            VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(pExtensionCount.get(0), stack);
            vkEnumerateInstanceExtensionProperties((String[]) null, pExtensionCount, extensions);

            Set<String> availableExtensions = new HashSet<>();
            for (int i = 0; i < extensions.capacity(); i++) {
                availableExtensions.add(extensions.get(i).extensionNameString());
            }

            for (String required : REQUIRED_INSTANCE_EXTENSIONS) {
                if (!availableExtensions.contains(required)) {
                    throw new RuntimeException("Missing required instance extension: " + required);
                }
            }

            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack);
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8("Minecraft"));
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.pEngineName(stack.UTF8("VulkanMod"));
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.apiVersion(VK11.VK_MAKE_VERSION(1, 1, 0));

            VkInstanceCreateInfo.Buffer createInfo = VkInstanceCreateInfo.callocStack(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);

            LongBuffer pInstance = stack.mallocLong(1);
            int result = vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan instance: " + result);
            }
            instance = new VkInstance(pInstance.get(0), createInfo);
        }
    }

    public static void selectPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pDeviceCount = stack.ints(0);
            vkEnumeratePhysicalDevices(instance, pDeviceCount, null);
            if (pDeviceCount.get(0) == 0) {
                throw new RuntimeException("No Vulkan physical devices found");
            }

            LongBuffer pDevices = stack.mallocLong(pDeviceCount.get(0));
            vkEnumeratePhysicalDevices(instance, pDeviceCount, pDevices);

            physicalDevice = pDevices.get(0);
            if (physicalDevice == VK10.VK_NULL_HANDLE) {
                throw new RuntimeException("Failed to select physical device");
            }

            deviceProperties = VkPhysicalDeviceProperties.callocStack(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties);

            deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);
            vkGetPhysicalDeviceFeatures(physicalDevice, deviceFeatures);

            vulkan11Available = deviceProperties.apiVersion() >= VK11.VK_MAKE_VERSION(1, 1, 0);

            IntBuffer pQueueFamilyCount = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, null);
            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(pQueueFamilyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, queueFamilies);

            for (int i = 0; i < queueFamilies.capacity(); i++) {
                VkQueueFamilyProperties props = queueFamilies.get(i);
                if ((props.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsQueueFamilyIndex = i;
                }
                if ((props.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    presentQueueFamilyIndex = i;
                    break;
                }
            }

            if (graphicsQueueFamilyIndex < 0) {
                throw new RuntimeException("No graphics queue family found");
            }
            if (presentQueueFamilyIndex < 0) {
                presentQueueFamilyIndex = graphicsQueueFamilyIndex;
            }
        }
    }

    public static VkInstance getInstance() {
        return instance;
    }

    public static long getPhysicalDevice() {
        return physicalDevice;
    }

    public static int getGraphicsQueueFamilyIndex() {
        return graphicsQueueFamilyIndex;
    }

    public static int getPresentQueueFamilyIndex() {
        return presentQueueFamilyIndex;
    }

    public static VkPhysicalDeviceProperties getDeviceProperties() {
        return deviceProperties;
    }

    public static VkPhysicalDeviceFeatures getDeviceFeatures() {
        return deviceFeatures;
    }

    public static boolean isVulkan11Available() {
        return vulkan11Available;
    }

    public static void cleanup() {
        if (instance != null) {
            vkDestroyInstance(instance, null);
            instance = null;
        }
    }
}
