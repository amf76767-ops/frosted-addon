package com.manu.frosted.mixin;

import com.manu.frosted.util.AimUtil;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The crosshair is updated twice per frame path: once in MinecraftClient.tick
 * and once in GameRenderer.renderWorld. Meteor's freecam rewrites it in both,
 * so the player-view raycast has to run after both as well.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;updateCrosshairTarget(F)V",
            shift = At.Shift.AFTER
        )
    )
    private void frosted$retarget(CallbackInfo ci) {
        if (Modules.get().isActive(Freecam.class)) {
            AimUtil.retarget();
        }
    }
}
