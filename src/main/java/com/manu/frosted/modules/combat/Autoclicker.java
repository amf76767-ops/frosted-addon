package com.manu.frosted.modules.combat;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;


/**
 * Clicks for you at a configurable rate. Left click goes through the vanilla
 * interaction manager so block breaking and entity attacks stay consistent;
 * right click uses the item-use path.
 */
public class Autoclicker extends Module {
    public enum Button { Left, Right, Both }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Button> button = sgGeneral.add(new EnumSetting.Builder<Button>()
        .name("button")
        .description("Which mouse button to click.")
        .defaultValue(Button.Left)
        .build()
    );

    private final Setting<Integer> cps = sgGeneral.add(new IntSetting.Builder()
        .name("cps")
        .description("Clicks per second.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> jitter = sgGeneral.add(new IntSetting.Builder()
        .name("jitter")
        .description("Random extra delay in ticks, 0 = perfectly regular.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("Percent chance to click when the timer fires.")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> onlyHolding = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-holding")
        .description("Only click while you physically hold the bound mouse button.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> respectCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("respect-cooldown")
        .description("Left click waits for the vanilla attack cooldown.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing the hand on left click.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> validTargetOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("valid-target-only")
        .description("Left click only when the crosshair is on a real block or entity, never air or fluid.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public Autoclicker() {
        super(FrostedAddon.COMBAT, "autoclicker", "Clicks at a fixed rate with optional jitter.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (--timer > 0) return;

        Button mode = button.get();
        boolean left = mode == Button.Left || mode == Button.Both;
        boolean right = mode == Button.Right || mode == Button.Both;

        if (onlyHolding.get()) {
            left &= mc.options.attackKey.isPressed();
            right &= mc.options.useKey.isPressed();
        }

        if (!left && !right) {
            timer = 1;
            return;
        }

        if (!Rand.chance(chance.get())) {
            timer = 1;
            return;
        }

        if (left && (!respectCooldown.get() || mc.player.getAttackCooldownProgress(0.5f) >= 1.0f)) {
            clickLeft();
        }
        if (right) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }

        int base = Math.max(1, 20 / cps.get());
        timer = base + Rand.upTo(jitter.get());
    }

    private void clickLeft() {
        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            Entity target = hit.getEntity();
            mc.interactionManager.attackEntity(mc.player, target);
        } else if (mc.crosshairTarget instanceof BlockHitResult hit) {
            if (validTargetOnly.get() && !isBreakable(hit.getBlockPos())) return;
            mc.interactionManager.attackBlock(hit.getBlockPos(), hit.getSide());
        } else if (validTargetOnly.get()) {
            return;
        }

        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isBreakable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
