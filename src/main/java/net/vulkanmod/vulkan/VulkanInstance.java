package net.vulkanmod.vulkan;

import net.vulkanmod.VulkanMod;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanInstance {
    public static final String[] REQUIRED_INSTANCE_EXTENSIONS = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
    };

    public static final String[] REQUIRED_DEVICE_EXTENSIONS = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME
    };

    private static VkInstance instance;
    private static VkPhysicalDevice physicalDevice;
    private static int graphicsQueueFamilyIndex = -1;
    private static int presentQueueFamilyIndex = -1;
    private static VkPhysicalDeviceProperties deviceProperties;
    private static VkPhysicalDeviceFeatures deviceFeatures;
    private static boolean vulkan11Available;
    private static long debugMessenger;

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

            boolean debugUtilsAvailable = availableExtensions.contains(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);

            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack);
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8("Minecraft"));
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.pEngineName(stack.UTF8("VulkanMod"));
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.apiVersion(VK11.VK_MAKE_VERSION(1, 1, 0));

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);

            if (debugUtilsAvailable) {
                VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
                debugCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
                debugCreateInfo.flags(0);
                debugCreateInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                    VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
                debugCreateInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                    VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                    VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
                debugCreateInfo.pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    String message = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData).pMessageString();
                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        VulkanMod.LOGGER.error("[VK VALIDATION] {}", message);
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        VulkanMod.LOGGER.warn("[VK VALIDATION] {}", message);
                    } else {
                        VulkanMod.LOGGER.debug("[VK VALIDATION] {}", message);
                    }
                    return VK10.VK_FALSE;
                });

                createInfo.pNext(debugCreateInfo.address());
                VulkanMod.LOGGER.info("VK_EXT_debug_utils is available. Validation messenger will be created.");
            } else {
                VulkanMod.LOGGER.warn("VK_EXT_debug_utils is NOT available. No Vulkan validation debugging.");
            }

            PointerBuffer pInstance = stack.mallocPointer(1);
            int result = vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan instance: " + result);
            }
            instance = new VkInstance(pInstance.get(0), createInfo);

            if (debugUtilsAvailable) {
                PointerBuffer pMessenger = stack.mallocPointer(1);
                VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
                debugCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
                debugCreateInfo.flags(0);
                debugCreateInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                    VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
                debugCreateInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                    VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                    VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
                debugCreateInfo.pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    String message = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData).pMessageString();
                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        VulkanMod.LOGGER.error("[VK VALIDATION] {}", message);
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        VulkanMod.LOGGER.warn("[VK VALIDATION] {}", message);
                    } else {
                        VulkanMod.LOGGER.debug("[VK VALIDATION] {}", message);
                    }
                    return VK10.VK_FALSE;
                });

                result = vkCreateDebugUtilsMessengerEXT(instance, debugCreateInfo, null, pMessenger);
                if (result == VK_SUCCESS) {
                    debugMessenger = pMessenger.get(0);
                    VulkanMod.LOGGER.info("Vulkan debug messenger created successfully");
                } else {
                    VulkanMod.LOGGER.warn("Failed to create debug messenger: {}", result);
                }
            }
        }
    }

    public static void selectPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pDeviceCount = stack.ints(0);
            vkEnumeratePhysicalDevices(instance, pDeviceCount, null);
            if (pDeviceCount.get(0) == 0) {
                throw new RuntimeException("No Vulkan physical devices found");
            }

            PointerBuffer pDevices = stack.mallocPointer(pDeviceCount.get(0));
            vkEnumeratePhysicalDevices(instance, pDeviceCount, pDevices);

            physicalDevice = new VkPhysicalDevice(pDevices.get(0), instance);
            if (physicalDevice == null || physicalDevice.address() == NULL) {
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

            VulkanMod.LOGGER.info("Selected physical device: {}", deviceProperties.deviceNameString());
            VulkanMod.LOGGER.info("Graphics queue family: {}, Present queue family: {}", graphicsQueueFamilyIndex, presentQueueFamilyIndex);
        }
    }

    public static VkInstance getInstance() {
        return instance;
    }

    public static VkPhysicalDevice getPhysicalDevice() {
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
        if (debugMessenger != NULL) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
            debugMessenger = NULL;
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
            instance = null;
        }
    }
}
