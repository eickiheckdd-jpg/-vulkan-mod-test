package net.vulkanmod.mixin;

import net.minecraft.client.render.BufferBuilder;
import net.vulkanmod.VulkanMod;
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
        }
    }
}
