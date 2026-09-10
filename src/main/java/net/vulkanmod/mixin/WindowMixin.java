package net.vulkanmod.mixin;

import net.vulkanmod.vulkan.VulkanRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin("net.minecraft.client.Window")
public class WindowMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(CallbackInfo ci) {
        if (VulkanRenderer.isInitialized()) {
            VulkanRenderer.recreateSwapchain();
        }
    }
}
