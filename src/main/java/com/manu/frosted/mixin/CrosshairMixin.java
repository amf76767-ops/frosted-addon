package com.manu.frosted.mixin;

import com.manu.frosted.modules.visual.CustomCrosshair;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class CrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void frosted$hideVanillaCrosshair(CallbackInfo ci) {
        CustomCrosshair crosshair = Modules.get().get(CustomCrosshair.class);
        if (crosshair != null && crosshair.isActive()) {
            ci.cancel();
        }
    }
}
