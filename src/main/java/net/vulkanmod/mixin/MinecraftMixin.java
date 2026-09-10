package net.vulkanmod.mixin;

import net.vulkanmod.vulkan.MinecraftInstance;
import net.vulkanmod.vulkan.VulkanRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.glfw.GLFW.*;

@Mixin("net.minecraft.client.Minecraft")
public class MinecraftMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MinecraftInstance.setWindowHandle(Minecraft.getInstance().getWindow().getHandle());
        VulkanMod.LOGGER.info("Window handle captured: 0x{}", Long.toHexString(MinecraftInstance.getWindowHandle()));
    }
}
