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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

/**
 * 3D volumetric CS-style knife with synchronized first-person Minecraft arm animations.
 *
 * Both the 3D knife and the player's first-person MC arm sway, slash on attack,
 * lift & twist on right-click inspect, and toss/spin in 3D when pressing the swap-hands key (F).
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
        .description("Keep your own arm visible and animate it along with the knife. Turn off to hide the whole hand.")
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
        .name("slash-on-attack").description("Swing the knife and arm when you press attack.").defaultValue(true).build()
    );

    private final Setting<Boolean> inspect = sgMotion.add(new BoolSetting.Builder()
        .name("inspect-on-right-click").defaultValue(true).build()
    );

    private final Setting<Boolean> spinOnSwapKey = sgMotion.add(new BoolSetting.Builder()
        .name("spin-on-swap-key")
        .description("Spin the knife and arm when you press the swap-hands key (F). The item is not swapped into the offhand.")
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

    // Live animation state shared between the 3D arm renderer and the 3D knife renderer
    private double animShiftXNorm;
    private double animShiftYNorm;
    private double animTiltDeg;
    private double animSpinDeg;
    private double animRollRad;
    private double animPitchRad;
    private double animSlashWave;
    private double animInspectWave;
    private double animSpinWave;

    public CsKnife() {
        super(FrostedAddon.VISUAL, "cs-knife", "3D CS-style knife with synchronized MC arm slash, inspect and spin animations.");
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

    /** Updates the shared 3D animation state for the current frame. */
    private void updateAnimationState(long now) {
        long nowNs = System.nanoTime();
        double dt = Math.min(0.1, Math.max(0.0, (nowNs - lastFrameNs) / 1_000_000_000.0));
        if (dt > 0.0005) {
            lastFrameNs = nowNs;
            if (sway.get() && mc.player != null) {
                var vel = mc.player.getVelocity();
                double rawSpeed = Math.min(0.32, Math.hypot(vel.x, vel.z));
                double alpha = 1.0 - Math.exp(-dt * 12.0);
                smoothSpeed += (rawSpeed - smoothSpeed) * alpha;
                walkPhase += dt * (2.2 + smoothSpeed * 18.0);

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
            }
        }

        double shiftX = 0;
        double shiftY = 0;
        double tiltAdd = 0;
        // Base 3D perspective angle so the spine and 3D bevels are always visible
        double rollRad = 0.36;
        double pitchRad = 0.16;
        double spinDeg = 0;

        if (sway.get()) {
            shiftY += Math.sin(walkPhase) * (0.9 + smoothSpeed * 15.0) + turnY;
            shiftX += Math.cos(walkPhase * 0.5) * (0.6 + smoothSpeed * 9.0) + turnX;
            tiltAdd += Math.sin(walkPhase * 0.5) * (0.7 + smoothSpeed * 6.5) + turnX * 0.35;
            rollRad += Math.sin(walkPhase * 0.5) * 0.08 + turnY * 0.015;
            pitchRad += Math.cos(walkPhase) * 0.05 - turnX * 0.012;
        }

        double inspectWave = 0;
        if (inspectStart > 0) {
            double p = (now - inspectStart) / 1250.0;
            if (p >= 1.0) {
                inspectStart = -1;
            } else {
                inspectWave = Math.sin(p * Math.PI);
                tiltAdd += inspectWave * 40.0;
                shiftY -= inspectWave * 22.0;
                shiftX -= inspectWave * 12.0;
                // Full 360-degree 3D roll so both sides + spine of the blade revolve in 3D
                rollRad += p * Math.PI * 2.0;
                pitchRad += Math.sin(p * Math.PI * 2.0) * 0.32;
            }
        }

        double slashWave = 0;
        if (slashStart > 0) {
            double p = (now - slashStart) / 260.0;
            if (p >= 1.0) {
                slashStart = -1;
            } else {
                slashWave = Math.sin(Math.pow(p, 0.72) * Math.PI);
                tiltAdd += slashWave * 30.0;
                shiftX -= slashWave * 34.0;
                shiftY += slashWave * 14.0;
                rollRad += slashWave * 0.55;
                pitchRad -= slashWave * 0.38;
            }
        }

        double spinWave = 0;
        if (spinStart > 0) {
            double p = (now - spinStart) / (double) spinTime.get();
            if (p >= 1.0) {
                spinStart = -1;
            } else {
                double eased = p * p * (3.0 - 2.0 * p);
                int turns = spinTurns.get();
                spinWave = Math.sin(p * Math.PI);
                spinDeg = 360.0 * turns * eased;
                rollRad += eased * Math.PI * 2.0;
                pitchRad += Math.sin(eased * Math.PI * 2.0 * turns) * 0.35;
                shiftY -= spinWave * 34.0;
            }
        }

        animShiftXNorm = shiftX;
        animShiftYNorm = shiftY;
        animTiltDeg = tiltAdd;
        animSpinDeg = spinDeg;
        animRollRad = rollRad;
        animPitchRad = pitchRad;
        animSlashWave = slashWave;
        animInspectWave = inspectWave;
        animSpinWave = spinWave;
    }

    /**
     * Transforms the first-person 3D Minecraft arm in camera space so it holds the knife
     * and moves with idle sway, slash, inspect, and spin!
     */
    public void applyArmTransform(MatrixStack matrices, Arm arm) {
        updateAnimationState(System.currentTimeMillis());

        float side = (arm == Arm.RIGHT) ? 1.0f : -1.0f;

        // Convert GUI-unit shifts into 3D camera-space offsets
        float ox = (float) ((offsetX.get() * 0.0035 + animShiftXNorm * 0.0045) * side);
        float oy = (float) (-offsetY.get() * 0.0035 - animShiftYNorm * 0.0045);
        float oz = (float) (-animSlashWave * 0.22 - animInspectWave * 0.14 - animSpinWave * 0.16);

        // Move arm slightly up and inward so the hand grips the 3D knife handle
        matrices.translate(side * -0.08f + ox, 0.14f + oy, -0.06f + oz);

        // Pivot around the forearm/wrist in camera space
        float pivX = side * 0.52f;
        float pivY = -0.46f;
        float pivZ = -0.68f;
        matrices.translate(pivX, pivY, pivZ);

        // 1. In-plane spin & tilt (matches the knife's spinDeg and tiltAdd!)
        float zRot = (float) (side * (animTiltDeg * 0.75 + animSpinDeg));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(zRot));

        // 2. 3D wrist roll & inspect twist
        float yRot = (float) (side * ((animRollRad - 0.36) * 42.0 - animSlashWave * 28.0));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yRot));

        // 3. 3D pitch lunge on slash / lift on inspect & spin
        float xRot = (float) (animInspectWave * 26.0 - animSlashWave * 34.0 + Math.sin(Math.toRadians(animSpinDeg)) * 22.0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(xRot));

        matrices.translate(-pivX, -pivY, -pivZ);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (!showing()) return;

        DrawContext ctx = event.drawContext;
        long now = System.currentTimeMillis();
        double time = (now % 1_000_000L) / 1000.0;

        updateAnimationState(now);

        double screenW = event.screenWidth > 0 ? event.screenWidth : mc.getWindow().getFramebufferWidth();
        double screenH = event.screenHeight > 0 ? event.screenHeight : mc.getWindow().getFramebufferHeight();
        double guiScale = Math.max(1.0, screenH / Math.max(1.0, mc.getWindow().getScaledHeight()));
        double k = (screenH / 360.0) * scale.get();

        double shiftX = animShiftXNorm * k;
        double shiftY = animShiftYNorm * k;
        double glint = Math.max(animSlashWave * 0.65, Math.max(animInspectWave * 0.75, animSpinWave > 0 ? 1.0 : 0.0));
        double handleFlip = Math.toRadians(animSpinDeg);

        KnifeStyle knife = style.get();
        KnifeFinish look = finish.get();
        int blade = rgb(bladeColor.get());
        int handle = rgb(handleColor.get());

        double baseX = screenW * 0.78 + offsetX.get() * guiScale + shiftX;
        double baseY = screenH * 0.84 + offsetY.get() * guiScale + shiftY;
        double tilt = knife.tilt + animTiltDeg;

        Renderer2D.COLOR.begin();

        if (animSlashWave > 0.08) {
            drawSlashArc(baseX, baseY, knife.bladeLen * k, animSlashWave, look.accent(), false);
            if (knife.twin()) {
                drawSlashArc(screenW - baseX, baseY, knife.bladeLen * k, animSlashWave, look.accent(), true);
            }
        }

        KnifeRenderer.draw(ctx, knife, look, new KnifeRenderer.Pose(
            baseX, baseY, tilt, animSpinDeg, animRollRad, animPitchRad,
            k, false, time, glint, handleFlip,
            blade, handle, shadow.get(), sparkles.get()));

        if (knife.twin()) {
            KnifeRenderer.draw(ctx, knife, look, new KnifeRenderer.Pose(
                screenW - baseX, baseY, tilt, animSpinDeg, animRollRad, animPitchRad,
                k, true, time + 0.4, glint, handleFlip,
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

    public boolean showing() {
        return mc.player != null && (!holdingOnly.get() || isSword(mc.player.getMainHandStack()));
    }

    public boolean isTwin() {
        return style.get().twin();
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
