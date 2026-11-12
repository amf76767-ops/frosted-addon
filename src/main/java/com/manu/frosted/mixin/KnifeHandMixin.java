package com.manu.frosted.mixin;

import com.manu.frosted.modules.visual.CsKnife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person hand handling for the 3D CS knife.
 *
 * Swaps the held sword for the player's bare 3D arm and synchronizes the 3D arm's
 * MatrixStack with the CS knife's idle sway, slash, inspect, and spin animations.
 * When twin Daggers are selected, also renders the second arm in sync.
 */
@Mixin(HeldItemRenderer.class)
public abstract class KnifeHandMixin {
    @Shadow
    private void renderArmHoldingItem(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                      int light, float equipProgress, float swingProgress, Arm arm) {}

    @Unique
    private boolean frosted$renderingTwinArm;

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack frosted$bareArm(ItemStack item) {
        CsKnife knife = Modules.get().get(CsKnife.class);
        return knife != null && knife.hidesHeldItem(item) ? ItemStack.EMPTY : item;
    }

    @ModifyVariable(method = "renderArmHoldingItem", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
    private float frosted$zeroEquipProgress(float equipProgress) {
        CsKnife knife = Modules.get().get(CsKnife.class);
        return knife != null && knife.isActive() && knife.showing() ? 0.0f : equipProgress;
    }

    @ModifyVariable(method = "renderArmHoldingItem", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 0)
    private float frosted$zeroSwingProgress(float swingProgress) {
        CsKnife knife = Modules.get().get(CsKnife.class);
        return knife != null && knife.isActive() && knife.showing() ? 0.0f : swingProgress;
    }

    @Inject(method = "renderArmHoldingItem", at = @At("HEAD"), require = 0)
    private void frosted$beforeRenderArm(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                         int light, float equipProgress, float swingProgress, Arm arm,
                                         CallbackInfo ci) {
        CsKnife knife = Modules.get().get(CsKnife.class);
        if (knife != null && knife.isActive() && knife.showing()) {
            matrices.push();
            knife.applyArmTransform(matrices, arm);
        }
    }

    @Inject(method = "renderArmHoldingItem", at = @At("RETURN"), require = 0)
    private void frosted$afterRenderArm(MatrixStack matrices, OrderedRenderCommandQueue queue,
                                        int light, float equipProgress, float swingProgress, Arm arm,
                                        CallbackInfo ci) {
        CsKnife knife = Modules.get().get(CsKnife.class);
        if (knife != null && knife.isActive() && knife.showing()) {
            matrices.pop();

            if (!frosted$renderingTwinArm && knife.isTwin()) {
                frosted$renderingTwinArm = true;
                try {
                    matrices.push();
                    renderArmHoldingItem(matrices, queue, light, 0.0f, 0.0f, arm.getOpposite());
                    matrices.pop();
                } finally {
                    frosted$renderingTwinArm = false;
                }
            }
        }
    }

    @Inject(
        method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void frosted$hideWholeHand(float tickDelta, MatrixStack matrices,
                                       OrderedRenderCommandQueue queue,
                                       ClientPlayerEntity player, int light,
                                       CallbackInfo ci) {
        CsKnife knife = Modules.get().get(CsKnife.class);
        if (knife != null && knife.hidesWholeHand()) {
            ci.cancel();
        }
    }
}
