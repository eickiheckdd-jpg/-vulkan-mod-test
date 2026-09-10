package net.vulkanmod.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanChunkMeshBatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderChunkLayer", at = @At("HEAD"))
    private void onRenderChunkLayer(CallbackInfo ci) {
        VulkanChunkMeshBatcher.processQueue();
    }

    @Inject(method = "rebuildChunk", at = @At("TAIL"))
    private void onRebuildChunk(CallbackInfo ci) {
        if (VulkanChunkMeshBatcher.isInitialized()) {
            VulkanChunkMeshBatcher.processQueue();
        }
    }
}
