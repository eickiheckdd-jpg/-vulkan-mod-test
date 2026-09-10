package net.vulkanmod.mixin;

import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanTextureBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureManager.class)
public class TextureManagerMixin {
    @Inject(method = "getTexture", at = @At("RETURN"))
    private void onGetTexture(Identifier id, CallbackInfoReturnable<AbstractTexture> cir) {
        AbstractTexture texture = cir.getReturnValue();
        if (texture != null && VulkanTextureBridge.isInitialized()) {
            VulkanTextureBridge.captureAndUploadTexture(texture, id);
        }
    }
}
