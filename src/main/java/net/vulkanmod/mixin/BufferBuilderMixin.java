package net.vulkanmod.mixin;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
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

            ByteBuffer vertexBuffer = builtBuffer.getBuffer();
            if (vertexBuffer == null || vertexBuffer.remaining() == 0) return;

            var drawParams = builtBuffer.getDrawParameters();
            int vertexCount = drawParams.vertexCount();
            int indexCount = drawParams.indexCount();

            if (vertexCount <= 0 || indexCount <= 0) return;

            ByteBuffer vertexData = vertexBuffer.slice();

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            int stride = 32;
            for (int i = 0; i < vertexCount && i * stride < vertexData.remaining(); i++) {
                int offset = i * stride;
                if (offset + 12 <= vertexData.remaining()) {
                    float x = vertexData.getFloat(offset);
                    float y = vertexData.getFloat(offset + 4);
                    float z = vertexData.getFloat(offset + 8);
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (z < minZ) minZ = z;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                    if (z > maxZ) maxZ = z;
                }
            }

            float offsetX = 0, offsetY = 0, offsetZ = 0;
            float dx = maxX - minX;
            float dy = maxY - minY;
            float dz = maxZ - minZ;
            boolean isLocalSpace = dx <= 20.0f && dy <= 20.0f && dz <= 20.0f;

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
