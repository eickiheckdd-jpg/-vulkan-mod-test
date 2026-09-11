package net.vulkanmod.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.vulkanmod.VulkanMod;
import net.vulkanmod.render.VulkanGUIRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(CallbackInfo ci) {
        if (VulkanGUIRenderer.isInitialized()) {
            VulkanMod.LOGGER.debug("GUI renderer active");
        }
    }
}
