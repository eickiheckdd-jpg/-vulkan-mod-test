package net.vulkanmod.mixin;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.BuiltBuffer;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanChunkMeshBatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {
    @Inject(method = "end", at = @At("RETURN"))
    private void onEnd(CallbackInfoReturnable<BuiltBuffer> cir) {
        try {
            BuiltBuffer builtBuffer = cir.getReturnValue();
            if (builtBuffer == null) return;

            ByteBuffer vertexBuffer = builtBuffer.vertexBuffer();
            if (vertexBuffer == null || vertexBuffer.remaining() == 0) return;

            int vertexCount = builtBuffer.vertexCount();
            int indexCount = builtBuffer.indexCount();

            if (vertexCount <= 0 || indexCount <= 0) return;

            ByteBuffer vertexData = vertexBuffer.slice();
            VulkanChunkMeshBatcher.addChunkMesh(vertexData, vertexCount, indexCount);
        } catch (Exception e) {
            VulkanMod.LOGGER.warn("Failed to capture chunk mesh: {}", e.getMessage());
        }
    }
}
