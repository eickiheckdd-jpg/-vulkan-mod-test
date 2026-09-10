package net.vulkanmod.mixin;

import net.vulkanmod.vulkan.VulkanRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin("net.minecraft.client.renderer.GameRenderer")
public class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        try {
            VulkanRenderer.render();
        } catch (Exception e) {
            if (VulkanMod.LOGGER != null) {
                VulkanMod.LOGGER.error("Vulkan render error: {}", e.getMessage());
            }
        }
    }
}
