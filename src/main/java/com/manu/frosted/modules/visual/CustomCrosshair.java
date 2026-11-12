package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Draw2D;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
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
import net.minecraft.entity.LivingEntity;

/**
 * Custom hardware-rendered crosshair drawn at full native screen resolution via {@link Renderer2D#COLOR}.
 * Shapes: Plus, Dot, X, Ring, Snowflake, Diamond.
 */
public class CustomCrosshair extends Module {
    public enum Shape { Plus, Dot, X, Ring, Snowflake, Diamond }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMotion = settings.createGroup("Motion");

    private final Setting<Shape> shape = sgGeneral.add(new EnumSetting.Builder<Shape>()
        .name("shape").defaultValue(Shape.Plus).build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").description("Size multiplier.").defaultValue(1.6).min(0.5).sliderRange(0.5, 4.0).build()
    );

    private final Setting<Integer> thickness = sgGeneral.add(new IntSetting.Builder()
        .name("thickness").description("Line thickness in GUI pixels.").defaultValue(1).min(1).sliderRange(1, 4).build()
    );

    private final Setting<Integer> gap = sgGeneral.add(new IntSetting.Builder()
        .name("gap").description("Empty space in the middle (plus and x).").defaultValue(2).min(0).sliderRange(0, 10).build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(225, 245, 255, 255)).build()
    );

    private final Setting<SettingColor> targetColor = sgGeneral.add(new ColorSetting.Builder()
        .name("target-color").description("Colour when aiming at an entity.").defaultValue(new SettingColor(110, 210, 255, 255)).build()
    );

    private final Setting<Boolean> outline = sgGeneral.add(new BoolSetting.Builder()
        .name("outline").description("Dark outline so it stays readable on snow.").defaultValue(true).build()
    );

    private final Setting<Boolean> centerDot = sgGeneral.add(new BoolSetting.Builder()
        .name("center-dot").description("Small dot in the centre of Plus, X and Ring.").defaultValue(false).build()
    );

    private final Setting<Boolean> dynamic = sgMotion.add(new BoolSetting.Builder()
        .name("dynamic").description("Gap opens while you move and kicks when you attack.").defaultValue(true).build()
    );

    private float curGap;
    private long kickAt = -1;
    private boolean lastAttack;

    public CustomCrosshair() {
        super(FrostedAddon.VISUAL, "custom-crosshair", "Replaces the vanilla crosshair with a crisp hardware-rendered crosshair.");
    }

    @Override
    public void onActivate() {
        curGap = 0;
        kickAt = -1;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;

        double sw = event.screenWidth > 0 ? event.screenWidth : mc.getWindow().getFramebufferWidth();
        double sh = event.screenHeight > 0 ? event.screenHeight : mc.getWindow().getFramebufferHeight();
        double guiScale = Math.max(1.0, sh / Math.max(1.0, mc.getWindow().getScaledHeight()));

        double cx = sw * 0.5;
        double cy = sh * 0.5;
        double th = Math.max(1.5, thickness.get() * guiScale * 0.75);
        double size = Math.max(3.0, 4.0 * scale.get() * guiScale);
        double baseGap = gap.get() * guiScale;

        float targetGap = 0;
        if (dynamic.get()) {
            var vel = mc.player.getVelocity();
            targetGap += (float) Math.min(6.0, Math.hypot(vel.x, vel.z) * 30.0);

            boolean attack = mc.options.attackKey.isPressed();
            if (attack && !lastAttack) kickAt = System.currentTimeMillis();
            lastAttack = attack;

            if (kickAt > 0) {
                double p = (System.currentTimeMillis() - kickAt) / 220.0;
                if (p >= 1.0) kickAt = -1;
                else targetGap += (float) (4.0 * Math.sin((1.0 - p) * Math.PI * 0.5));
            }
        }
        curGap += (targetGap - curGap) * 0.35f;
        double g = baseGap + curGap * guiScale;

        boolean onTarget = mc.targetedEntity instanceof LivingEntity;
        int main = argb(onTarget ? targetColor.get() : color.get());
        int glow = Draw2D.argb(65, main & 0xFFFFFF);
        int dark = 0xC8050A14;
        double outPx = Math.max(1.0, guiScale * 0.65);

        Renderer2D.COLOR.begin();

        // Soft icy halo around the centre
        Draw2D.star(cx, cy, (size + g) * 0.85, th * 0.5, Draw2D.argb(0, main), glow);

        int passes = outline.get() ? 1 : 0;
        for (int pass = passes; pass >= 0; pass--) {
            int c = pass == 1 ? dark : main;
            double e = pass == 1 ? outPx : 0.0;
            switch (shape.get()) {
                case Plus -> plus(cx, cy, size, g, th, e, c);
                case Dot -> dot(cx, cy, th * 1.15, e, c);
                case X -> cross(cx, cy, size, g, th, e, c);
                case Ring -> ring(cx, cy, size + g * 0.45, th, e, c);
                case Snowflake -> snowflake(cx, cy, size + g * 0.35 + guiScale, th, e, c);
                case Diamond -> diamond(cx, cy, size + g * 0.45, th, e, c);
            }
            if (centerDot.get() && shape.get() != Shape.Dot && shape.get() != Shape.Snowflake) {
                dot(cx, cy, th * 0.75, e, c);
            }
        }

        Renderer2D.COLOR.render();
    }

    private static void dot(double cx, double cy, double r, double e, int c) {
        double rad = r + e;
        Draw2D.polyQuad(cx - rad, cy - rad, cx + rad, cy - rad, cx + rad, cy + rad, cx - rad, cy + rad, c);
    }

    private static void plus(double cx, double cy, double size, double g, double th, double e, int c) {
        double half = th * 0.5 + e;
        // Left & right arms
        Draw2D.polyQuad(cx - g - size - e, cy - half, cx - g + e, cy - half, cx - g + e, cy + half, cx - g - size - e, cy + half, c);
        Draw2D.polyQuad(cx + g - e, cy - half, cx + g + size + e, cy - half, cx + g + size + e, cy + half, cx + g - e, cy + half, c);
        // Top & bottom arms
        Draw2D.polyQuad(cx - half, cy - g - size - e, cx + half, cy - g - size - e, cx + half, cy - g + e, cx - half, cy - g + e, c);
        Draw2D.polyQuad(cx - half, cy + g - e, cx + half, cy + g - e, cx + half, cy + g + size + e, cx - half, cy + g + size + e, c);
    }

    private static void cross(double cx, double cy, double size, double g, double th, double e, int c) {
        double w = th + e * 2.0;
        double d0 = Math.max(0.0, g - e * 0.7);
        double d1 = g + size + e * 0.7;
        double inv = 0.70710678;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                Draw2D.thickLine(
                    cx + sx * d0 * inv, cy + sy * d0 * inv,
                    cx + sx * d1 * inv, cy + sy * d1 * inv,
                    w, c
                );
            }
        }
    }

    private static void ring(double cx, double cy, double r, double th, double e, int c) {
        int segs = 28;
        double rIn = Math.max(0.5, r - th * 0.5 - e);
        double rOut = r + th * 0.5 + e;
        for (int i = 0; i < segs; i++) {
            double a0 = i * 2.0 * Math.PI / segs;
            double a1 = (i + 1) * 2.0 * Math.PI / segs;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            Draw2D.polyQuad(
                cx + c0 * rIn, cy + s0 * rIn,
                cx + c1 * rIn, cy + s1 * rIn,
                cx + c1 * rOut, cy + s1 * rOut,
                cx + c0 * rOut, cy + s0 * rOut,
                c
            );
        }
    }

    private static void diamond(double cx, double cy, double r, double th, double e, int c) {
        double rIn = Math.max(0.5, r - th * 0.6 - e);
        double rOut = r + th * 0.6 + e;
        double[] dx = {0, 1, 0, -1};
        double[] dy = {-1, 0, 1, 0};
        for (int i = 0; i < 4; i++) {
            int n = (i + 1) & 3;
            Draw2D.polyQuad(
                cx + dx[i] * rIn, cy + dy[i] * rIn,
                cx + dx[n] * rIn, cy + dy[n] * rIn,
                cx + dx[n] * rOut, cy + dy[n] * rOut,
                cx + dx[i] * rOut, cy + dy[i] * rOut,
                c
            );
        }
    }

    private static void snowflake(double cx, double cy, double r, double th, double e, int c) {
        double w = th + e * 2.0;
        for (int arm = 0; arm < 6; arm++) {
            double a = arm * Math.PI / 3.0;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            Draw2D.thickLine(cx, cy, cx + cos * (r + e), cy + sin * (r + e), w, c);

            double bx = cx + cos * r * 0.58;
            double by = cy + sin * r * 0.58;
            double bLen = r * 0.36 + e;
            for (int side = -1; side <= 1; side += 2) {
                double ba = a + side * Math.toRadians(58);
                Draw2D.thickLine(bx, by, bx + Math.cos(ba) * bLen, by + Math.sin(ba) * bLen, w * 0.85, c);
            }
        }
    }

    private static int argb(SettingColor c) {
        return (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
    }
}
