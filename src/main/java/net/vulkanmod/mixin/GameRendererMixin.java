package net.vulkanmod.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.vulkanmod.config.VulkanModConfig;
import net.vulkanmod.vulkan.VulkanRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!VulkanModConfig.enableVulkanRenderer) {
            return;
        }

        if (!VulkanRenderer.isInitialized()) {
            try {
                VulkanRenderer.initialize();
            } catch (Exception e) {
                VulkanMod.LOGGER.error("Failed to initialize Vulkan renderer: {}", e.getMessage());
                return;
            }
        }

        if (VulkanRenderer.isInitialized()) {
            try {
                VulkanRenderer.render();
            } catch (Exception e) {
                VulkanMod.LOGGER.error("Vulkan render error: {}", e.getMessage());
            }
        }
    }
}
