package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
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

/**
 * Custom crosshair drawn with DrawContext in GUI-scaled pixels, so it is exactly centred at
 * every GUI scale. Shapes: plus, dot, x, ring and a small snowflake. Optionally the gap opens
 * while you move and kicks when you attack.
 */
public class CustomCrosshair extends Module {
    public enum Shape { Plus, Dot, X, Ring, Snowflake }

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
        .name("color").defaultValue(new SettingColor(235, 245, 255, 255)).build()
    );

    private final Setting<Boolean> outline = sgGeneral.add(new BoolSetting.Builder()
        .name("outline").description("Dark outline so it stays readable on snow.").defaultValue(true).build()
    );

    private final Setting<Boolean> dynamic = sgMotion.add(new BoolSetting.Builder()
        .name("dynamic").description("Gap opens while you move and kicks when you attack.").defaultValue(true).build()
    );

    private float curGap;
    private long kickAt = -1;
    private boolean lastAttack;

    public CustomCrosshair() {
        super(FrostedAddon.VISUAL, "custom-crosshair", "Replaces the vanilla crosshair.");
    }

    @Override
    public void onActivate() {
        curGap = 0;
        kickAt = -1;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;
        DrawContext ctx = event.drawContext;
        if (ctx == null) return;

        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int th = thickness.get();
        int size = Math.max(2, (int) Math.round(4 * scale.get()));
        int baseGap = gap.get();

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
        int g = baseGap + Math.round(curGap);

        int main = argb(color.get());
        int dark = 0xC0000000;
        int o = outline.get() ? 1 : 0;

        // outline pass first (slightly larger), then the coloured pass
        for (int pass = o; pass >= 0; pass--) {
            int c = pass == 1 ? dark : main;
            int e = pass; // growth in pixels for the outline
            switch (shape.get()) {
                case Plus -> plus(ctx, cx, cy, size, g, th, e, c);
                case Dot -> {
                    int half = Math.max(1, th);
                    ctx.fill(cx - half - e, cy - half - e, cx + half + e, cy + half + e, c);
                }
                case X -> cross(ctx, cx, cy, size, g, th, e, c);
                case Ring -> ring(ctx, cx, cy, size + 1, th, e, c);
                case Snowflake -> snowflake(ctx, cx, cy, size + 2, th, e, c);
            }
        }
    }

    private static void plus(DrawContext ctx, int cx, int cy, int size, int g, int th, int e, int c) {
        int lo = -(th / 2);
        int hi = lo + th;
        ctx.fill(cx - g - size - e, cy + lo - e, cx - g + e, cy + hi + e, c);
        ctx.fill(cx + g - e, cy + lo - e, cx + g + size + e, cy + hi + e, c);
        ctx.fill(cx + lo - e, cy - g - size - e, cx + hi + e, cy - g + e, c);
        ctx.fill(cx + lo - e, cy + g - e, cx + hi + e, cy + g + size + e, c);
    }

    private static void cross(DrawContext ctx, int cx, int cy, int size, int g, int th, int e, int c) {
        for (int i = 0; i < size; i++) {
            int d = g + i;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    int x = cx + sx * d;
                    int y = cy + sy * d;
                    ctx.fill(x - e, y - e, x + th + e, y + th + e, c);
                }
            }
        }
    }

    private static void ring(DrawContext ctx, int cx, int cy, int r, int th, int e, int c) {
        int steps = Math.max(24, r * 8);
        for (int i = 0; i < steps; i++) {
            double a = i * 2.0 * Math.PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy + (int) Math.round(Math.sin(a) * r);
            ctx.fill(x - e, y - e, x + th + e, y + th + e, c);
        }
    }

    private static void snowflake(DrawContext ctx, int cx, int cy, int r, int th, int e, int c) {
        for (int arm = 0; arm < 6; arm++) {
            double a = arm * Math.PI / 3.0;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            for (int i = 0; i <= r; i++) {
                int x = cx + (int) Math.round(cos * i);
                int y = cy + (int) Math.round(sin * i);
                ctx.fill(x - e, y - e, x + th + e, y + th + e, c);
            }
            // a short branch pair at about 60 % of the arm
            double bx = cos * r * 0.6;
            double by = sin * r * 0.6;
            for (int side = -1; side <= 1; side += 2) {
                double ba = a + side * Math.toRadians(60);
                for (int i = 1; i <= Math.max(1, r / 3); i++) {
                    int x = cx + (int) Math.round(bx + Math.cos(ba) * i);
                    int y = cy + (int) Math.round(by + Math.sin(ba) * i);
                    ctx.fill(x - e, y - e, x + th + e, y + th + e, c);
                }
            }
        }
    }

    private static int argb(SettingColor c) {
        return (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
    }
}
