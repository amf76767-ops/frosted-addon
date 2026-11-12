package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.modules.visual.knife.KnifeFinish;
import com.manu.frosted.modules.visual.knife.KnifeRenderer;
import com.manu.frosted.modules.visual.knife.KnifeStyle;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Cosmetic CS-style knife overlay.
 *
 * Animations: idle sway that follows your walking speed, a slash when you attack, an
 * inspect on right click and a spin on the swap-hands key (F by default). The spin key is
 * swallowed before vanilla sees it, so the item is NOT swapped into your offhand.
 */
public class CsKnife extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");
    private final SettingGroup sgMotion = settings.createGroup("Motion");

    private final Setting<KnifeStyle> style = sgGeneral.add(new EnumSetting.Builder<KnifeStyle>()
        .name("style").description("Karambit, Bayonet, Flip, Huntsman, Gut, Butterfly, Stiletto or twin Daggers.")
        .defaultValue(KnifeStyle.Karambit).build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").description("Size relative to the screen height.").defaultValue(1.0).min(0.4).sliderRange(0.4, 2.5).build()
    );

    private final Setting<Integer> offsetX = sgGeneral.add(new IntSetting.Builder()
        .name("offset-x").description("Shift in GUI pixels.").defaultValue(0).sliderRange(-200, 200).build()
    );

    private final Setting<Integer> offsetY = sgGeneral.add(new IntSetting.Builder()
        .name("offset-y").description("Shift in GUI pixels.").defaultValue(0).sliderRange(-200, 200).build()
    );

    private final Setting<Boolean> holdingOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("holding-only")
        .description("Only show the knife while you hold a sword in your main hand.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> showArm = sgGeneral.add(new BoolSetting.Builder()
        .name("show-arm")
        .description("Keep your own arm visible and only swap the sword for the knife. Turn off to hide the whole hand.")
        .defaultValue(true).build()
    );

    private final Setting<KnifeFinish> finish = sgLook.add(new EnumSetting.Builder<KnifeFinish>()
        .name("finish").description("Colour scheme. Custom uses the two colours below.")
        .defaultValue(KnifeFinish.Frost).build()
    );

    private final Setting<SettingColor> bladeColor = sgLook.add(new ColorSetting.Builder()
        .name("blade-color").defaultValue(new SettingColor(220, 235, 250, 255)).build()
    );

    private final Setting<SettingColor> handleColor = sgLook.add(new ColorSetting.Builder()
        .name("handle-color").defaultValue(new SettingColor(40, 45, 55, 255)).build()
    );

    private final Setting<Boolean> shadow = sgLook.add(new BoolSetting.Builder()
        .name("shadow").description("Soft drop shadow under the knife.").defaultValue(true).build()
    );

    private final Setting<Boolean> sparkles = sgLook.add(new BoolSetting.Builder()
        .name("sparkles").description("Twinkling stars along the blade.").defaultValue(true).build()
    );

    private final Setting<Boolean> sway = sgMotion.add(new BoolSetting.Builder()
        .name("idle-sway").description("Gentle motion that grows while you walk.").defaultValue(true).build()
    );

    private final Setting<Boolean> slash = sgMotion.add(new BoolSetting.Builder()
        .name("slash-on-attack").description("Swing the knife when you press attack.").defaultValue(true).build()
    );

    private final Setting<Boolean> inspect = sgMotion.add(new BoolSetting.Builder()
        .name("inspect-on-right-click").defaultValue(true).build()
    );

    private final Setting<Boolean> spinOnSwapKey = sgMotion.add(new BoolSetting.Builder()
        .name("spin-on-swap-key")
        .description("Spin the knife when you press the swap-hands key (F). The item is not swapped into the offhand.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> spinTime = sgMotion.add(new IntSetting.Builder()
        .name("spin-time").description("Length of the spin in milliseconds.")
        .defaultValue(1100).min(400).sliderRange(400, 2500).build()
    );

    private final Setting<Integer> spinTurns = sgMotion.add(new IntSetting.Builder()
        .name("spin-turns").description("Full rotations per spin.")
        .defaultValue(2).min(1).sliderRange(1, 4).build()
    );

    private long inspectStart = -1;
    private long slashStart = -1;
    private long spinStart = -1;
    private boolean lastUse;
    private boolean lastAttack;

    public CsKnife() {
        super(FrostedAddon.VISUAL, "cs-knife", "Cosmetic CS-style knife with slash, inspect and spin animations.");
    }

    @Override
    public void onDeactivate() {
        inspectStart = -1;
        slashStart = -1;
        spinStart = -1;
    }

    /**
     * Runs before vanilla handles input this tick. Draining wasPressed() here means the game
     * never sees the swap-hands press, so nothing moves into the offhand.
     */
    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!spinOnSwapKey.get() || mc.player == null || mc.currentScreen != null) return;
        if (!showing()) return;

        boolean pressed = false;
        while (mc.options.swapHandsKey.wasPressed()) pressed = true;
        if (pressed) spinStart = System.currentTimeMillis();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.currentScreen != null) {
            lastUse = false;
            lastAttack = false;
            return;
        }

        boolean use = mc.options.useKey.isPressed();
        if (inspect.get() && use && !lastUse) inspectStart = System.currentTimeMillis();
        lastUse = use;

        boolean attack = mc.options.attackKey.isPressed();
        if (slash.get() && attack && !lastAttack) slashStart = System.currentTimeMillis();
        lastAttack = attack;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (!showing()) return;

        DrawContext ctx = event.drawContext;
        if (ctx == null) return;

        long now = System.currentTimeMillis();
        double time = (now % 1_000_000L) / 1000.0;

        // GUI-scaled pixels; sizes are relative to the screen height (360 GUI px = 1.0).
        double screenW = mc.getWindow().getScaledWidth();
        double screenH = mc.getWindow().getScaledHeight();
        double k = (screenH / 360.0) * scale.get();

        double tiltAdd = 0;
        double shiftX = 0;
        double shiftY = 0;
        double roll = 1.0;
        double spinDeg = 0;
        double handleFlip = 0;
        double glint = 0;

        if (sway.get()) {
            var vel = mc.player.getVelocity();
            double speed = Math.min(0.3, Math.hypot(vel.x, vel.z));
            double freq = 2.0 + speed * 14.0;
            shiftY += Math.sin(time * freq) * (0.8 + speed * 14.0) * k;
            shiftX += Math.cos(time * freq * 0.5) * (0.5 + speed * 8.0) * k;
            tiltAdd += Math.sin(time * freq * 0.5) * (0.6 + speed * 6.0);
        }

        if (inspectStart > 0) {
            double p = (now - inspectStart) / 1200.0;
            if (p >= 1.0) {
                inspectStart = -1;
            } else {
                double wave = Math.sin(p * Math.PI);
                tiltAdd += wave * 42;
                shiftY -= wave * 20 * k;
                roll *= Math.cos(p * Math.PI * 2.0);
                glint = Math.max(glint, 0.7 * wave);
            }
        }

        if (slashStart > 0) {
            double p = (now - slashStart) / 260.0;
            if (p >= 1.0) {
                slashStart = -1;
            } else {
                double wave = Math.sin(p * Math.PI);
                tiltAdd += wave * 26;
                shiftX -= wave * 30 * k;
                shiftY += wave * 12 * k;
                glint = Math.max(glint, 0.5 * wave);
            }
        }

        if (spinStart > 0) {
            double p = (now - spinStart) / (double) spinTime.get();
            if (p >= 1.0) {
                spinStart = -1;
            } else {
                double eased = p * p * (3.0 - 2.0 * p);
                int turns = spinTurns.get();
                spinDeg = 360.0 * turns * eased;
                roll *= Math.cos(eased * Math.PI * 2.0);
                handleFlip = Math.PI * 2.0 * turns * eased;
                shiftY -= Math.sin(p * Math.PI) * 36 * k;
                glint = 1.0;
            }
        }

        // keep a sliver visible when the knife passes edge-on
        if (Math.abs(roll) < 0.12) roll = roll < 0 ? -0.12 : 0.12;

        KnifeStyle knife = style.get();
        KnifeFinish look = finish.get();
        int blade = rgb(bladeColor.get());
        int handle = rgb(handleColor.get());

        double baseX = screenW * 0.78 + offsetX.get() + shiftX;
        double baseY = screenH * 0.84 + offsetY.get() + shiftY;
        double tilt = knife.tilt + tiltAdd;

        KnifeRenderer.draw(ctx, knife, look, new KnifeRenderer.Pose(
            baseX, baseY, tilt, spinDeg, roll, k, false, time, glint, handleFlip,
            blade, handle, shadow.get(), sparkles.get()));

        if (knife.twin()) {
            KnifeRenderer.draw(ctx, knife, look, new KnifeRenderer.Pose(
                screenW - baseX, baseY, tilt, spinDeg, roll, k, true, time + 0.4, glint, handleFlip,
                blade, handle, shadow.get(), sparkles.get()));
        }
    }

    /** True while the knife is on screen: module on, and a sword in hand when holding-only is set. */
    private boolean showing() {
        return mc.player != null && (!holdingOnly.get() || isSword(mc.player.getMainHandStack()));
    }

    /** Used by the hand mixin: should this first-person item be swapped for the bare arm? */
    public boolean hidesHeldItem(ItemStack stack) {
        if (!isActive() || !showArm.get() || !showing()) return false;
        return !holdingOnly.get() || isSword(stack);
    }

    /** Used by the hand mixin when show-arm is off: hide the whole first-person hand. */
    public boolean hidesWholeHand() {
        return isActive() && !showArm.get() && showing();
    }

    private static int rgb(SettingColor c) {
        return (c.r << 16) | (c.g << 8) | c.b;
    }

    /** Swords only: axes, maces and everything else do not bring the knife up. */
    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().getTranslationKey().contains("sword");
    }
}
