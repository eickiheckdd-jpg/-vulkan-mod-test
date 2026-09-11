package net.vulkanmod.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanChunkMeshBatcher;
import net.vulkanmod.render.VulkanSectionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {
        VulkanSectionTracker.updateFromCamera();
        VulkanChunkMeshBatcher.processQueue();
    }
}
