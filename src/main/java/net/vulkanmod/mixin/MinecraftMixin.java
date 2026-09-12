package net.vulkanmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.vulkan.MinecraftInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.glfw.GLFW.*;

@Mixin(MinecraftClient.class)
public class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(RunArgs args, CallbackInfo ci) {
        MinecraftInstance.setWindowHandle(MinecraftClient.getInstance().getWindow().getHandle());
        VulkanMod.LOGGER.info("Window handle captured: 0x{}", Long.toHexString(MinecraftInstance.getWindowHandle()));

    }
}
