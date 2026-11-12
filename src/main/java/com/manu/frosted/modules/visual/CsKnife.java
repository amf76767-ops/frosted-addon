package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.modules.visual.knife.KnifeFinish;
import com.manu.frosted.modules.visual.knife.KnifeRenderer;
import com.manu.frosted.modules.visual.knife.KnifeStyle;
import com.manu.frosted.util.Draw2D;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
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
 * Cosmetic CS-style knife overlay rendered at full screen resolution via {@link Renderer2D#COLOR}.
 *
 * Animations: smooth idle sway with camera-turn inertia, slash with an icy trail when you attack,
 * inspect on right click and spin on the swap-hands key (F by default).
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
        .name("idle-sway").description("Gentle motion that grows while you walk and turn.").defaultValue(true).build()
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

    private double smoothSpeed;
    private double walkPhase;
    private long lastFrameNs;
    private float prevYaw;
    private float prevPitch;
    private double turnX;
    private double turnY;

    public CsKnife() {
        super(FrostedAddon.VISUAL, "cs-knife", "Cosmetic CS-style knife with slash, inspect and spin animations.");
    }

    @Override
    public void onActivate() {
        lastFrameNs = System.nanoTime();
        smoothSpeed = 0;
        turnX = 0;
        turnY = 0;
        if (mc.player != null) {
            prevYaw = mc.player.getYaw();
            prevPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onDeactivate() {
        inspectStart = -1;
        slashStart = -1;
        spinStart = -1;
    }

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
        long now = System.currentTimeMillis();
        double time = (now % 1_000_000L) / 1000.0;

        long nowNs = System.nanoTime();
        double dt = Math.min(0.1, Math.max(0.001, (nowNs - lastFrameNs) / 1_000_000_000.0));
        lastFrameNs = nowNs;

        // Render in native screen pixels so the knife is crisp at every resolution.
        double screenW = event.screenWidth > 0 ? event.screenWidth : mc.getWindow().getFramebufferWidth();
        double screenH = event.screenHeight > 0 ? event.screenHeight : mc.getWindow().getFramebufferHeight();
        double guiScale = Math.max(1.0, screenH / Math.max(1.0, mc.getWindow().getScaledHeight()));
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
            double rawSpeed = Math.min(0.32, Math.hypot(vel.x, vel.z));
            double alpha = 1.0 - Math.exp(-dt * 12.0);
            smoothSpeed += (rawSpeed - smoothSpeed) * alpha;
            walkPhase += dt * (2.2 + smoothSpeed * 18.0);

            shiftY += Math.sin(walkPhase) * (0.9 + smoothSpeed * 15.0) * k;
            shiftX += Math.cos(walkPhase * 0.5) * (0.6 + smoothSpeed * 9.0) * k;
            tiltAdd += Math.sin(walkPhase * 0.5) * (0.7 + smoothSpeed * 6.5);

            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            double dYaw = ((yaw - prevYaw + 540.0) % 360.0) - 180.0;
            double dPitch = pitch - prevPitch;
            prevYaw = yaw;
            prevPitch = pitch;

            turnX = Math.max(-22.0, Math.min(22.0, turnX - dYaw * 0.42));
            turnY = Math.max(-16.0, Math.min(16.0, turnY - dPitch * 0.35));
            double decay = Math.exp(-dt * 10.0);
            turnX *= decay;
            turnY *= decay;

            shiftX += turnX * k;
            shiftY += turnY * k;
            tiltAdd += turnX * 0.35;
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
                glint = Math.max(glint, 0.75 * wave);
            }
        }

        double slashWave = 0;
        if (slashStart > 0) {
            double p = (now - slashStart) / 250.0;
            if (p >= 1.0) {
                slashStart = -1;
            } else {
                // Fast snap-out and smooth recovery
                slashWave = Math.sin(Math.pow(p, 0.72) * Math.PI);
                tiltAdd += slashWave * 28;
                shiftX -= slashWave * 32 * k;
                shiftY += slashWave * 13 * k;
                glint = Math.max(glint, 0.65 * slashWave);
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

        double baseX = screenW * 0.78 + offsetX.get() * guiScale + shiftX;
        double baseY = screenH * 0.84 + offsetY.get() * guiScale + shiftY;
        double tilt = knife.tilt + tiltAdd;

        Renderer2D.COLOR.begin();

        if (slashWave > 0.08) {
            drawSlashArc(baseX, baseY, knife.bladeLen * k, slashWave, look.accent(), false);
            if (knife.twin()) {
                drawSlashArc(screenW - baseX, baseY, knife.bladeLen * k, slashWave, look.accent(), true);
            }
        }

        KnifeRenderer.draw(ctx, knife, look, new KnifeRenderer.Pose(
            baseX, baseY, tilt, spinDeg, roll, k, false, time, glint, handleFlip,
            blade, handle, shadow.get(), sparkles.get()));

        if (knife.twin()) {
            KnifeRenderer.draw(ctx, knife, look, new KnifeRenderer.Pose(
                screenW - baseX, baseY, tilt, spinDeg, roll, k, true, time + 0.4, glint, handleFlip,
                blade, handle, shadow.get(), sparkles.get()));
        }

        Renderer2D.COLOR.render();
    }

    private static void drawSlashArc(double ox, double oy, double radius, double wave, int accent, boolean mirror) {
        int segs = 14;
        double dir = mirror ? 1.0 : -1.0;
        double startA = Math.toRadians(15);
        double endA = Math.toRadians(78);
        for (int i = 0; i < segs; i++) {
            double f0 = i / (double) segs;
            double f1 = (i + 1) / (double) segs;
            double a0 = startA + (endA - startA) * f0;
            double a1 = startA + (endA - startA) * f1;
            double w0 = Math.sin(f0 * Math.PI) * radius * 0.11 * wave;
            double w1 = Math.sin(f1 * Math.PI) * radius * 0.11 * wave;
            int alpha0 = (int) Math.round(145 * wave * Math.sin(f0 * Math.PI));
            int alpha1 = (int) Math.round(145 * wave * Math.sin(f1 * Math.PI));
            int c0 = Draw2D.argb(alpha0, Draw2D.mix(accent, 0xFFFFFF, f0 * 0.6));
            int c1 = Draw2D.argb(alpha1, Draw2D.mix(accent, 0xFFFFFF, f1 * 0.6));

            double x0In = ox + dir * Math.cos(a0) * (radius - w0);
            double y0In = oy - Math.sin(a0) * (radius - w0);
            double x0Out = ox + dir * Math.cos(a0) * (radius + w0);
            double y0Out = oy - Math.sin(a0) * (radius + w0);

            double x1In = ox + dir * Math.cos(a1) * (radius - w1);
            double y1In = oy - Math.sin(a1) * (radius - w1);
            double x1Out = ox + dir * Math.cos(a1) * (radius + w1);
            double y1Out = oy - Math.sin(a1) * (radius + w1);

            Draw2D.polyQuadGrad(x0In, y0In, c0, x1In, y1In, c1, x1Out, y1Out, c1, x0Out, y0Out, c0);
        }
    }

    private boolean showing() {
        return mc.player != null && (!holdingOnly.get() || isSword(mc.player.getMainHandStack()));
    }

    public boolean hidesHeldItem(ItemStack stack) {
        if (!isActive() || !showArm.get() || !showing()) return false;
        return !holdingOnly.get() || isSword(stack);
    }

    public boolean hidesWholeHand() {
        return isActive() && !showArm.get() && showing();
    }

    private static int rgb(SettingColor c) {
        return (c.r << 16) | (c.g << 8) | c.b;
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().getTranslationKey().contains("sword");
    }
}
