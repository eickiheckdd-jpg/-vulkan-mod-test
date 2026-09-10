package net.vulkanmod.mixin;

import net.minecraft.client.particle.ParticleManager;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanParticleRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class ParticleEngineMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(CallbackInfo ci) {
        if (VulkanParticleRenderer.isInitialized()) {
            VulkanParticleRenderer.processQueue();
        }
    }
}
