package com.manu.frosted.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Raycasts from the player's real eyes and real rotation, ignoring whatever the
 * camera is looking at. Used while freecam is active so mining and attacking
 * follow the body, not the detached camera.
 */
public final class AimUtil {
    private AimUtil() {}

    public static void retarget() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        double blockRange = mc.player.getBlockInteractionRange();
        HitResult blockHit = mc.player.raycast(blockRange, 1.0f, false);

        double entityRange = mc.player.getEntityInteractionRange();
        Vec3d end = eye.add(look.multiply(entityRange));
        Box box = mc.player.getBoundingBox().stretch(look.multiply(entityRange)).expand(1.0);

        EntityHitResult entityHit = ProjectileUtil.raycast(
            mc.player, eye, end, box,
            entity -> !entity.isSpectator() && entity.canHit(),
            entityRange * entityRange
        );

        if (entityHit != null) {
            mc.crosshairTarget = entityHit;
            mc.targetedEntity = entityHit.getEntity();
        } else {
            mc.crosshairTarget = blockHit;
            mc.targetedEntity = null;
        }
    }
}
