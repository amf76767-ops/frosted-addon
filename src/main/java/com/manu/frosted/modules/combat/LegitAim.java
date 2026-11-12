package com.manu.frosted.modules.combat;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Aim assist: turns the camera toward the best target every tick. It never
 * attacks — no click, no packet, no swing.
 */
public class LegitAim extends Module {
    public enum Priority { Distance, Crosshair }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> aimRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("aim-range")
        .description("Maximum distance to track.")
        .defaultValue(4.5)
        .min(1.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Only track targets within this half-angle of your view.")
        .defaultValue(60.0)
        .min(5.0)
        .sliderRange(5.0, 180.0)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Maximum degrees per tick.")
        .defaultValue(12.0)
        .min(1.0)
        .sliderRange(1.0, 45.0)
        .build()
    );

    private final Setting<Double> ease = sgGeneral.add(new DoubleSetting.Builder()
        .name("ease")
        .description("Fraction of the remaining angle covered per tick, capped by rotation-speed. 1 = as fast as the cap allows, lower = softer approach.")
        .defaultValue(1.0)
        .min(0.05)
        .sliderRange(0.05, 1.0)
        .build()
    );

    private final Setting<Priority> priority = sgGeneral.add(new EnumSetting.Builder<Priority>()
        .name("priority")
        .description("Distance picks the closest target, Crosshair picks the one nearest your view.")
        .defaultValue(Priority.Crosshair)
        .build()
    );

    private final Setting<Boolean> pauseWhileMining = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-mining")
        .description("Do not steer while you are breaking a block or using an item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("players-only")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> visibleOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("visible-only")
        .description("Skip targets behind blocks.")
        .defaultValue(false)
        .build()
    );

    public LegitAim() {
        super(FrostedAddon.COMBAT, "aim-assist", "Turns toward targets; never attacks.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (pauseWhileMining.get()) {
            if (mc.player.isUsingItem()) return;
            if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) return;
        }

        Entity target = findTarget();
        if (target != null) turnToward(target);
    }

    private Entity findTarget() {
        double range = aimRange.get();
        double rangeSq = range * range;
        double maxFov = fov.get();

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        boolean byCrosshair = priority.get() == Priority.Crosshair;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (entity == mc.player || entity.isSpectator()) continue;
            if (playersOnly.get() && !(entity instanceof PlayerEntity)) continue;

            double distanceSq = mc.player.squaredDistanceTo(entity);
            if (distanceSq > rangeSq) continue;
            double angle = angleTo(entity);
            if (angle > maxFov) continue;
            if (visibleOnly.get() && !mc.player.canSee(entity)) continue;

            double score = byCrosshair ? angle : distanceSq;
            if (score >= bestScore) continue;

            best = entity;
            bestScore = score;
        }
        return best;
    }

    private double angleTo(Entity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        return Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
    }

    private void turnToward(Entity target) {
        double dx = target.getX() - mc.player.getX();
        double dy = target.getEyeY() - mc.player.getEyeY();
        double dz = target.getZ() - mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = targetPitch - mc.player.getPitch();
        float speed = rotationSpeed.get().floatValue();
        float easing = ease.get().floatValue();

        float yawStep = MathHelper.clamp(yawDiff * easing, -speed, speed);
        float pitchStep = MathHelper.clamp(pitchDiff * easing, -speed, speed);

        mc.player.setYaw(mc.player.getYaw() + yawStep);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + pitchStep, -90.0f, 90.0f));
    }
}
