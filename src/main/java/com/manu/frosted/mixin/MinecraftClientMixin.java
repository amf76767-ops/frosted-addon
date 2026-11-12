package com.manu.frosted.mixin;

import com.manu.frosted.modules.combat.Backtrack;
import com.manu.frosted.util.AimUtil;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(
        method = "tick",
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
        Backtrack backtrack = Modules.get().get(Backtrack.class);
        if (backtrack != null && backtrack.isActive()) {
            backtrack.updateCrosshairRetarget();
        }
    }
}
