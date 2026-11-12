package com.manu.frosted.mixin;

import com.manu.frosted.modules.visual.CsKnife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person hand handling for the CS knife.
 *
 * Default (show-arm on): the sword is replaced by an empty stack right before the hand is drawn,
 * so vanilla renders just your arm and the knife overlay takes the place of the sword.
 * show-arm off: the whole hand is skipped, like before.
 */
@Mixin(HeldItemRenderer.class)
public abstract class KnifeHandMixin {
    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack frosted$bareArm(ItemStack item) {
        CsKnife knife = Modules.get().get(CsKnife.class);
        return knife != null && knife.hidesHeldItem(item) ? ItemStack.EMPTY : item;
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
