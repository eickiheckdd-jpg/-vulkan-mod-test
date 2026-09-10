package net.vulkanmod.mixin;

import net.minecraft.client.render.BufferBuilder;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.VulkanVertexCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {
    @Inject(method = "build", at = @At("RETURN"))
    private void onBuild(CallbackInfoReturnable<ByteBuffer> cir) {
        ByteBuffer buffer = cir.getReturnValue();
        if (buffer != null && buffer.remaining() > 0) {
            VulkanMod.LOGGER.debug("BufferBuilder built: {} bytes", buffer.remaining());
            
            // Capture vertex data for direct Vulkan rendering
            try {
                BufferBuilder builder = (BufferBuilder) (Object) this;
                int vertexSize = builder.getVertexFormat().getVertexSize();
                int vertexCount = buffer.remaining() / vertexSize;
                
                if (vertexCount > 0) {
                    VulkanVertexCapture.captureMesh(buffer, vertexCount, vertexSize);
                }
            } catch (Exception e) {
                VulkanMod.LOGGER.warn("Failed to capture vertex data: {}", e.getMessage());
            }
        }
    }
}
