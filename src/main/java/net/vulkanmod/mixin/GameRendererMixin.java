package net.vulkanmod.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.config.VulkanModConfig;
import net.vulkanmod.render.VulkanMatrixExtractor;
import net.vulkanmod.render.VulkanRenderStateTranslator;
import net.vulkanmod.vulkan.VulkanRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
    private void onRenderWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!VulkanModConfig.getConfig().enableVulkanRenderer) return;

        if (!VulkanRenderer.isInitialized()) {
            try {
                VulkanRenderer.initialize();
            } catch (Exception e) {
                VulkanMod.LOGGER.error("Failed to initialize Vulkan renderer: {}", e.getMessage());
                return;
            }
        }

        if (VulkanModConfig.getConfig().enableFrameReplacement) {
            VulkanMatrixExtractor.extractMatrices();
            VulkanRenderer.render();
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!VulkanModConfig.getConfig().enableVulkanRenderer) return;

        VulkanRenderStateTranslator.syncFromOpenGL();
    }
}
