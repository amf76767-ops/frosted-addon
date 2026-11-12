package com.manu.frosted.mixin;

import com.manu.frosted.modules.camera.FrostedFreelook;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void frosted$clipToSpace(float desiredDistance, CallbackInfoReturnable<Float> ci) {
        FrostedFreelook freelook = Modules.get().get(FrostedFreelook.class);
        if (freelook != null && freelook.isActive() && freelook.throughWalls()) {
            ci.setReturnValue(desiredDistance);
        }
    }
}
