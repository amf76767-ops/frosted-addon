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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Attacks whatever your crosshair is on, respecting the vanilla attack cooldown
 * and a click-per-second ceiling.
 */
public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> cps = sgGeneral.add(new IntSetting.Builder()
        .name("cps")
        .description("Maximum attacks per second.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("Percent chance per attack window.")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("players-only")
        .description("Only attack players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireWeapon = sgGeneral.add(new BoolSetting.Builder()
        .name("require-weapon")
        .description("Only attack while holding a sword, axe or mace.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("cooldown")
        .description("Wait for the vanilla attack cooldown to be full.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-sprint")
        .description("Cancel sprint and wait a tick before attacking. Removes the sprint-attack simulation flag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireGround = sgGeneral.add(new BoolSetting.Builder()
        .name("require-ground")
        .description("Only attack while standing on the ground.")
        .defaultValue(true)
        .build()
    );

    private int cooldownTicks;
    private int sprintWait;

    public TriggerBot() {
        super(FrostedAddon.COMBAT, "trigger-bot", "Attacks the entity under your crosshair.");
    }

    @Override
    public void onActivate() {
        cooldownTicks = 0;
        sprintWait = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (sprintWait > 0) {
            sprintWait--;
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (requireGround.get() && !mc.player.isOnGround()) return;

        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return;
        if (target == mc.player) return;
        if (playersOnly.get() && !(target instanceof PlayerEntity)) return;

        if (requireWeapon.get() && !isWeapon(mc.player.getMainHandStack())) return;
        if (cooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        if (!Rand.chance(chance.get())) return;

        if (stopSprint.get() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            sprintWait = 1;
            return;
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        cooldownTicks = Math.max(1, 20 / cps.get());
    }

    private boolean isWeapon(ItemStack stack) {
        String key = stack.getItem().getTranslationKey();
        return key.contains("sword") || key.contains("axe") || key.contains("mace");
    }
}
