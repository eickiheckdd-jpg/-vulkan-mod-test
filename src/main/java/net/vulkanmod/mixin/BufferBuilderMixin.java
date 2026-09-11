package net.vulkanmod.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
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
            VertexFormat format = drawParams.format();

            if (vertexCount <= 0 || indexCount <= 0) return;

            ByteBuffer vertexData = vertexBuffer.slice();
            int stride = format.getVertexSize();

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            int posOffset = getElementOffset(format, VertexFormatElement.POSITION);
            int uvOffset = getElementOffset(format, VertexFormatElement.UV0 != null ? VertexFormatElement.UV0 : VertexFormatElement.UV);
            int colorOffset = getElementOffset(format, VertexFormatElement.COLOR);

            for (int i = 0; i < vertexCount && i * stride < vertexData.remaining(); i++) {
                int off = i * stride;
                if (off + posOffset + 12 <= vertexData.remaining()) {
                    float x = vertexData.getFloat(off + posOffset);
                    float y = vertexData.getFloat(off + posOffset + 4);
                    float z = vertexData.getFloat(off + posOffset + 8);
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

            VulkanChunkMeshBatcher.addChunkMesh(vertexData, vertexCount, indexCount, stride, format, offsetX, offsetY, offsetZ);
        } catch (Exception e) {
            VulkanMod.LOGGER.warn("Failed to capture chunk mesh: {}", e.getMessage());
        }
    }

    private static int getElementOffset(VertexFormat format, VertexFormatElement element) {
        if (element == null) return -1;
        int offset = format.getOffset(element);
        return offset >= 0 ? offset : -1;
    }
}
