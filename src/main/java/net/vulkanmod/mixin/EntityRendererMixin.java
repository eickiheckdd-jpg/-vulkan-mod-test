package net.vulkanmod.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(CallbackInfo ci) {
        if (VulkanEntityRenderer.isInitialized()) {
            VulkanEntityRenderer.processQueue();
        }
    }
}
