package com.manu.frosted.modules.combat;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Releasing forward for a tick after a hit resets sprint, which changes the
 * knockback you receive. This is the client-side half of a W-tap.
 */
public class WTap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("Percent chance to tap on a hit.")
        .defaultValue(90)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> releaseTicks = sgGeneral.add(new IntSetting.Builder()
        .name("release-ticks")
        .description("Ticks to keep W released.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("players-only")
        .description("Only tap when the target is a player.")
        .defaultValue(true)
        .build()
    );

    private int remaining;
    private boolean heldBefore;
    private boolean released;

    public WTap() {
        super(FrostedAddon.COMBAT, "w-tap", "Releases W after a hit to reset sprint knockback.");
    }

    @Override
    public void onActivate() {
        remaining = 0;
        released = false;
    }

    @Override
    public void onDeactivate() {
        remaining = 0;
        restore();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity target = event.entity;
        if (target == null || mc.player == null) return;
        if (playersOnly.get() && !(target instanceof PlayerEntity)) return;
        if (!Rand.chance(chance.get())) return;

        remaining = releaseTicks.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.options == null || mc.player == null) return;

        if (remaining > 0) {
            if (!released) {
                heldBefore = mc.options.forwardKey.isPressed();
                released = true;
            }
            mc.options.forwardKey.setPressed(false);
            remaining--;
        } else {
            restore();
        }
    }

    /** Gives W back only if we took it and the player was holding it. */
    private void restore() {
        if (!released) return;
        released = false;
        if (mc.options != null && heldBefore) mc.options.forwardKey.setPressed(true);
    }
}
