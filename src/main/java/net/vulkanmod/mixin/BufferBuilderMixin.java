package net.vulkanmod.mixin;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.BuiltBuffer;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanChunkMeshBatcher;
import net.vulkanmod.render.VulkanSectionTracker;
import org.lwjgl.system.MemoryUtil;
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

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            for (int i = 0; i < vertexCount; i++) {
                int offset = i * 16; // 4 floats per vertex: pos(3) + uv(1) = 16 bytes
                if (offset + 12 <= vertexData.remaining()) {
                    float x = vertexData.getFloat(offset);
                    float y = vertexData.getFloat(offset + 4);
                    float z = vertexData.getFloat(offset + 8);
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }

            float offsetX = 0, offsetY = 0, offsetZ = 0;
            boolean isLocalSpace = (maxX - minX) <= 20.0f && (maxY - minY) <= 20.0f && (maxZ - minZ) <= 20.0f;

            if (isLocalSpace && minX >= -1.0f && minY >= -1.0f && minZ >= -1.0f) {
                offsetX = ((int) minX / 16) * 16;
                offsetY = ((int) minY / 16) * 16;
                offsetZ = ((int) minZ / 16) * 16;
            }

            VulkanChunkMeshBatcher.addChunkMesh(vertexData, vertexCount, indexCount, offsetX, offsetY, offsetZ);
        } catch (Exception e) {
            VulkanMod.LOGGER.warn("Failed to capture chunk mesh: {}", e.getMessage());
        }
    }
}
