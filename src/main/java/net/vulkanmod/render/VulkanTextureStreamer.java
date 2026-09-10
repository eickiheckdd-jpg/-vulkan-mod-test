package net.vulkanmod.render;

import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class VulkanTextureStreamer {
    private static final int MAX_TEXTURES = 1024;
    private static final int STREAMING_POOL_SIZE = 64;

    private static long[] textureImages;
    private static long[] textureImageViews;
    private static long[] textureSamplers;
    private static long descriptorPool;
    private static long descriptorSetLayout;
    private static long[] descriptorSets;
    private static boolean[] textureLoaded;
    private static boolean initialized = false;

    private static class TextureRequest {
        int textureId;
        ByteBuffer pixelData;
        int width;
        int height;
    }

    private static final Deque<TextureRequest> textureQueue = new ArrayDeque<>();

    public static synchronized void initialize() {
        if (initialized) return;

        try (MemoryStack stack = stackPush()) {
            textureImages = new long[MAX_TEXTURES];
            textureImageViews = new long[MAX_TEXTURES];
            textureSamplers = new long[MAX_TEXTURES];
            textureLoaded = new boolean[MAX_TEXTURES];

            createDescriptorPool(stack);
            createDescriptorSetLayout(stack);
            createDescriptorSets(stack);
            createDefaultSamplers(stack);

            initialized = true;
            VulkanMod.LOGGER.info("Texture streamer initialized with {} texture slots", MAX_TEXTURES);
        } catch (Exception e) {
            VulkanMod.LOGGER.error("Failed to initialize texture streamer: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDescriptorPool(MemoryStack stack) {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.callocStack(1, stack);
        VkDescriptorPoolSize poolSize = poolSizes.get(0);
        poolSize.type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        poolSize.descriptorCount(MAX_TEXTURES);

        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack);
        poolInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
        poolInfo.pPoolSizes(poolSizes);
        poolInfo.maxSets(MAX_TEXTURES);

        LongBuffer pPool = stack.mallocLong(1);
        int result = vkCreateDescriptorPool(VulkanDevice.getDevice(), poolInfo, null, pPool);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create texture descriptor pool: " + result);
        }
        descriptorPool = pPool.get(0);
    }

    private static void createDescriptorSetLayout(MemoryStack stack) {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.callocStack(1, stack);
        VkDescriptorSetLayoutBinding binding = bindings.get(0);
        binding.binding(0);
        binding.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        binding.descriptorCount(1);
        binding.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        binding.pImmutableSamplers(null);

        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack);
        layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
        layoutInfo.pBindings(bindings);

        LongBuffer pLayout = stack.mallocLong(1);
        int result = vkCreateDescriptorSetLayout(VulkanDevice.getDevice(), layoutInfo, null, pLayout);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create texture descriptor set layout: " + result);
        }
        descriptorSetLayout = pLayout.get(0);
    }

    private static void createDescriptorSets(MemoryStack stack) {
        LongBuffer pLayouts = stack.longs(descriptorSetLayout);
        descriptorSets = new long[MAX_TEXTURES];

        for (int i = 0; i < MAX_TEXTURES; i++) {
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.callocStack(stack);
            allocateInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
            allocateInfo.descriptorPool(descriptorPool);
            allocateInfo.pSetLayouts(pLayouts);

            LongBuffer pDescriptorSet = stack.mallocLong(1);
            int result = vkAllocateDescriptorSets(VulkanDevice.getDevice(), allocateInfo, pDescriptorSet);
            if (result == VK_SUCCESS) {
                descriptorSets[i] = pDescriptorSet.get(0);
            }
        }
    }

    private static void createDefaultSamplers(MemoryStack stack) {
        for (int i = 0; i < MAX_TEXTURES; i++) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.callocStack(stack);
            samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
            samplerInfo.magFilter(VK_FILTER_LINEAR);
            samplerInfo.minFilter(VK_FILTER_LINEAR);
            samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);
            samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.mipLodBias(0.0f);
            samplerInfo.anisotropyEnable(false);
            samplerInfo.maxAnisotropy(1.0f);
            samplerInfo.compareEnable(false);
            samplerInfo.compareOp(VK_COMPARE_OP_ALWAYS);
            samplerInfo.minLod(0.0f);
            samplerInfo.maxLod(1.0f);
            samplerInfo.borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK);
            samplerInfo.unnormalizedCoordinates(false);

            LongBuffer pSampler = stack.mallocLong(1);
            int result = vkCreateSampler(VulkanDevice.getDevice(), samplerInfo, null, pSampler);
            if (result == VK_SUCCESS) {
                textureSamplers[i] = pSampler.get(0);
            }
        }
    }

    public static synchronized int requestTexture(int textureId, ByteBuffer pixelData, int width, int height) {
        if (!initialized) return -1;

        TextureRequest request = new TextureRequest();
        request.textureId = textureId;
        request.pixelData = MemoryUtil.memAlloc(pixelData.remaining()).put(pixelData).flip();
        request.width = width;
        request.height = height;
        textureQueue.addLast(request);

        return textureId % MAX_TEXTURES;
    }

    public static void processTextureQueue() {
        if (!initialized || textureQueue.isEmpty()) return;

        try (MemoryStack stack = stackPush()) {
            while (!textureQueue.isEmpty()) {
                TextureRequest request = textureQueue.pollFirst();
                int index = request.textureId % MAX_TEXTURES;

                if (textureImages[index] != NULL) {
                    cleanupTexture(index);
                }

                createTexture(index, request.pixelData, request.width, request.height);
                MemoryUtil.memFree(request.pixelData);
            }
        }
    }

    private static void createTexture(int index, ByteBuffer pixelData, int width, int height) {
        try (MemoryStack stack = stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.callocStack(stack);
            imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
            imageInfo.imageType(VK_IMAGE_TYPE_2D);
            imageInfo.format(VK_FORMAT_R8G8B8A8_UNORM);
            imageInfo.extent(VkExtent3D.callocStack(stack).width(width).height(height).depth(1));
            imageInfo.mipLevels(1);
            imageInfo.arrayLayers(1);
            imageInfo.samples(VK_SAMPLE_COUNT_1_BIT);
            imageInfo.tiling(VK_IMAGE_TILING_OPTIMAL);
            imageInfo.usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
            imageInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            LongBuffer pImage = stack.mallocLong(1);
            int result = vkCreateImage(VulkanDevice.getDevice(), imageInfo, null, pImage);
            if (result != VK_SUCCESS) {
                VulkanMod.LOGGER.error("Failed to create texture image: {}", result);
                return;
            }
            textureImages[index] = pImage.get(0);

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.callocStack(stack);
            viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            viewInfo.image(textureImages[index]);
            viewInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
            viewInfo.format(VK_FORMAT_R8G8B8A8_UNORM);
            viewInfo.subresourceRange(VkImageSubresourceRange.callocStack(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1));

            LongBuffer pImageView = stack.mallocLong(1);
            result = vkCreateImageView(VulkanDevice.getDevice(), viewInfo, null, pImageView);
            if (result != VK_SUCCESS) {
                VulkanMod.LOGGER.error("Failed to create texture image view: {}", result);
                return;
            }
            textureImageViews[index] = pImageView.get(0);

            updateDescriptorSet(index, textureSamplers[index], textureImageViews[index]);
            textureLoaded[index] = true;
        }
    }

    private static void updateDescriptorSet(int index, long sampler, long imageView) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.callocStack(1, stack);
            VkDescriptorImageInfo descriptorImageInfo = imageInfo.get(0);
            descriptorImageInfo.sampler(sampler);
            descriptorImageInfo.imageView(imageView);
            descriptorImageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            VkWriteDescriptorSet.Buffer writeDescriptorSets = VkWriteDescriptorSet.callocStack(1, stack);
            VkWriteDescriptorSet writeDescriptorSet = writeDescriptorSets.get(0);
            writeDescriptorSet.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
            writeDescriptorSet.dstSet(descriptorSets[index]);
            writeDescriptorSet.dstBinding(0);
            writeDescriptorSet.dstArrayElement(0);
            writeDescriptorSet.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            writeDescriptorSet.pImageInfo(imageInfo);
            writeDescriptorSet.pBufferInfo(null);
            writeDescriptorSet.pTexelBufferView(null);

            vkUpdateDescriptorSets(VulkanDevice.getDevice(), writeDescriptorSets, null);
        }
    }

    private static void cleanupTexture(int index) {
        if (textureImageViews[index] != NULL) {
            vkDestroyImageView(VulkanDevice.getDevice(), textureImageViews[index], null);
            textureImageViews[index] = NULL;
        }
        if (textureImages[index] != NULL) {
            vkDestroyImage(VulkanDevice.getDevice(), textureImages[index], null);
            textureImages[index] = NULL;
        }
        textureLoaded[index] = false;
    }

    public static long getDescriptorSet(int index) {
        if (index >= 0 && index < MAX_TEXTURES) {
            return descriptorSets[index];
        }
        return NULL;
    }

    public static boolean isTextureLoaded(int index) {
        if (index >= 0 && index < MAX_TEXTURES) {
            return textureLoaded[index];
        }
        return false;
    }

    public static void cleanup() {
        for (int i = 0; i < MAX_TEXTURES; i++) {
            cleanupTexture(i);
            if (textureSamplers[i] != NULL) {
                vkDestroySampler(VulkanDevice.getDevice(), textureSamplers[i], null);
                textureSamplers[i] = NULL;
            }
        }

        if (descriptorSetLayout != NULL) {
            vkDestroyDescriptorSetLayout(VulkanDevice.getDevice(), descriptorSetLayout, null);
            descriptorSetLayout = NULL;
        }
        if (descriptorPool != NULL) {
            vkDestroyDescriptorPool(VulkanDevice.getDevice(), descriptorPool, null);
            descriptorPool = NULL;
        }

        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
