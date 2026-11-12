package com.manu.frosted.modules.combat;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Presses jump on the tick after taking knockback. A well-timed jump reset
 * cancels part of the knockback; the accuracy setting adds human error so it
 * does not fire on every single hit.
 */
public class AutoJumpReset extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("Percent chance to attempt a reset after a hit.")
        .defaultValue(40)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> accuracy = sgGeneral.add(new IntSetting.Builder()
        .name("accuracy")
        .description("Percent chance the jump lands on the perfect tick.")
        .defaultValue(50)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Minimum ticks after the hit before jumping.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> requireGround = sgGeneral.add(new BoolSetting.Builder()
        .name("require-ground")
        .description("Only jump when on the ground.")
        .defaultValue(true)
        .build()
    );

    private int lastHurtTime;
    private int pending;
    private int jumpTicks;

    public AutoJumpReset() {
        super(FrostedAddon.COMBAT, "auto-jump-reset", "Jumps after taking knockback to reduce it.");
    }

    @Override
    public void onActivate() {
        lastHurtTime = 0;
        pending = 0;
        jumpTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) mc.options.jumpKey.setPressed(false);
        pending = 0;
        jumpTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.options == null || mc.currentScreen != null) return;
        if (mc.player.isUsingItem() || mc.player.hasVehicle()) return;
        if (mc.player.isTouchingWater() || mc.player.isInLava()) return;

        int hurt = mc.player.hurtTime;
        if (hurt > lastHurtTime && hurt > 0) {
            if (Rand.chance(chance.get())) {
                int delay = minDelay.get();
                if (!Rand.chance(accuracy.get())) delay++;
                pending = delay;
            }
        }
        lastHurtTime = hurt;

        if (jumpTicks > 0) {
            jumpTicks--;
            if (jumpTicks == 0) mc.options.jumpKey.setPressed(false);
            return;
        }

        if (pending > 0) {
            if (--pending == 0) {
                if (requireGround.get() && !mc.player.isOnGround()) return;
                mc.options.jumpKey.setPressed(true);
                jumpTicks = 1;
            }
        }
    }
}
