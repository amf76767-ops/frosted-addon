package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Draw2D;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Winter atmosphere with themes: snowfall with wind, twinkling sparkles, a frosted screen edge,
 * and sky effects (tint, stars, aurora, light rays, whiteout streaks).
 *
 * Aurora and stars are placed by direction, so they slide as you turn and are mostly visible
 * when you look up. The sky and cloud colours go through Meteor's own ambience module, since
 * that already owns the sky hooks; its previous values are restored when this is turned off.
 */
public class FrostedAmbience extends Module {
    public enum Theme {
        //      flakes wind  aurora sparkle haze  rays   stars  sky       cloud     tint      tintA
        Tundra(6,   0.30, false, false, 0.00, false, false, 0xBEE1FA, 0xF0F8FF, 0xBEE1FA, 14),
        Blizzard(22, 1.00, false, false, 0.45, false, false, 0x96AABE, 0xC8D2DC, 0xC8D2E1, 34),
        Aurora(4,   0.15, true,  true,  0.00, false, true,  0x122355, 0x3C5A96, 0x0A1946, 40),
        Glacier(5,  0.10, false, true,  0.00, true,  false, 0x78D7FF, 0xE1FAFF, 0x78D7FF, 12),
        Midnight(4, 0.20, false, false, 0.00, false, true,  0x0A0F2D, 0x28325A, 0x050A28, 46);

        final int flakes;
        final double wind;
        final boolean aurora;
        final boolean sparkles;
        final double haze;
        final boolean rays;
        final boolean stars;
        final int sky;
        final int cloud;
        final int tint;
        final int tintAlpha;

        Theme(int flakes, double wind, boolean aurora, boolean sparkles, double haze, boolean rays,
              boolean stars, int sky, int cloud, int tint, int tintAlpha) {
            this.flakes = flakes;
            this.wind = wind;
            this.aurora = aurora;
            this.sparkles = sparkles;
            this.haze = haze;
            this.rays = rays;
            this.stars = stars;
            this.sky = sky;
            this.cloud = cloud;
            this.tint = tint;
            this.tintAlpha = tintAlpha;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSnow = settings.createGroup("Snowfall");
    private final SettingGroup sgScreen = settings.createGroup("Screen");
    private final SettingGroup sgSky = settings.createGroup("Sky");

    private final Setting<Theme> theme = sgGeneral.add(new EnumSetting.Builder<Theme>()
        .name("theme").description("Tundra, Blizzard, Aurora, Glacier or Midnight.")
        .defaultValue(Theme.Tundra).build());

    private final Setting<Integer> intensity = sgGeneral.add(new IntSetting.Builder()
        .name("intensity").description("Scales snowfall and screen effects (5 = the theme's default).")
        .defaultValue(5).min(1).sliderRange(1, 10).build());

    private final Setting<Boolean> snowfall = sgSnow.add(new BoolSetting.Builder()
        .name("snowfall").description("Spawn snowflakes around you.").defaultValue(true).build());

    private final Setting<Boolean> wind = sgSnow.add(new BoolSetting.Builder()
        .name("wind").description("Slowly turning wind with gusts that pushes the snow.").defaultValue(true).build());

    private final Setting<Boolean> sparkles = sgSnow.add(new BoolSetting.Builder()
        .name("sparkles").description("Glittering ice dust on themes that have it.").defaultValue(true).build());

    private final Setting<Double> radius = sgSnow.add(new DoubleSetting.Builder()
        .name("radius").description("Horizontal spread around you.").defaultValue(16.0).min(4.0).sliderRange(4.0, 32.0).build());

    private final Setting<Double> height = sgSnow.add(new DoubleSetting.Builder()
        .name("height").description("How far above you flakes start.").defaultValue(12.0).min(3.0).sliderRange(3.0, 24.0).build());

    private final Setting<Boolean> openSky = sgSnow.add(new BoolSetting.Builder()
        .name("only-open-sky").description("No snow indoors or in caves.").defaultValue(true).build());

    private final Setting<Boolean> overworldOnly = sgSnow.add(new BoolSetting.Builder()
        .name("overworld-only").defaultValue(true).build());

    private final Setting<Boolean> screenEffects = sgScreen.add(new BoolSetting.Builder()
        .name("sky-effects").description("Tint, stars, aurora, light rays and whiteout streaks.").defaultValue(true).build());

    private final Setting<Boolean> vignette = sgScreen.add(new BoolSetting.Builder()
        .name("frost-edge").description("Icy glow around the screen edge.").defaultValue(true).build());

    private final Setting<Integer> strength = sgScreen.add(new IntSetting.Builder()
        .name("strength").defaultValue(35).min(0).sliderRange(0, 100).build());

    private final Setting<Integer> edgeSize = sgScreen.add(new IntSetting.Builder()
        .name("size").description("Edge thickness in percent of the screen height.").defaultValue(14).min(4).sliderRange(4, 30).build());

    private final Setting<Boolean> skyPreset = sgSky.add(new BoolSetting.Builder()
        .name("sky-preset")
        .description("Sky and cloud colour of the theme via Meteor's ambience module (restored when this is turned off).")
        .defaultValue(true).build());

    private final Map<String, Object> skyBackup = new HashMap<>();
    private Module ambience;
    private boolean ambienceWasOff;
    private Theme appliedTheme;

    private double skyFade = 1.0;
    private int ticks;

    public FrostedAmbience() {
        super(FrostedAddon.VISUAL, "frosted-ambience", "Winter themes: snowfall, wind, aurora, stars, ice sparkles and a frosted screen edge.");
    }

    @Override
    public void onActivate() {
        skyFade = 1.0;
        ticks = 0;
        if (skyPreset.get()) applySky();
    }

    @Override
    public void onDeactivate() {
        restoreSky();
    }

    // ----------------------------------------------------------------- tick

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        ticks++;

        Theme t = theme.get();

        if (skyPreset.get() && appliedTheme != t) applySky();

        boolean skyVisible = mc.world.isSkyVisible(mc.player.getBlockPos());
        skyFade += ((skyVisible ? 1.0 : 0.0) - skyFade) * 0.06;

        if (overworldOnly.get() && !"overworld".equals(mc.world.getRegistryKey().getValue().getPath())) return;

        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        // Wind: a slowly turning direction with gusts on top.
        double windX = 0;
        double windZ = 0;
        if (wind.get()) {
            double gust = 0.6 + 0.4 * Math.sin(time * 0.7) + 0.25 * Math.sin(time * 1.9);
            double angle = time * 0.05;
            windX = Math.cos(angle) * t.wind * gust;
            windZ = Math.sin(angle) * t.wind * gust;
        }

        if (snowfall.get()) {
            int n = Math.min(60, (int) Math.round(t.flakes * intensity.get() / 5.0));
            double spread = radius.get() * 2.0;

            for (int i = 0; i < n; i++) {
                // Spawn a bit upwind so the snow drifts across you instead of away from you.
                double x = px + Rand.centered(spread) - windX * 6.0;
                double z = pz + Rand.centered(spread) - windZ * 6.0;
                double y = py + 3.0 + Rand.unit() * height.get();
                if (openSky.get() && !mc.world.isSkyVisible(BlockPos.ofFloored(x, y, z))) continue;

                mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z,
                    Rand.centered(0.05) + windX * 0.12,
                    -0.05 - Rand.unit() * 0.05,
                    Rand.centered(0.05) + windZ * 0.12);
            }
        }

        if (sparkles.get() && t.sparkles && ticks % 3 == 0) {
            double x = px + Rand.centered(18.0);
            double z = pz + Rand.centered(18.0);
            double y = py + Rand.unit() * 5.0;
            mc.world.addParticleClient(ParticleTypes.END_ROD, x, y, z,
                Rand.centered(0.01), 0.005 + Rand.unit() * 0.01, Rand.centered(0.01));
        }
    }

    // --------------------------------------------------------------- screen

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        DrawContext ctx = event.drawContext;
        if (ctx == null) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();
        double time = (now % 1_000_000L) / 1000.0;
        Theme t = theme.get();
        double scale = intensity.get() / 5.0;

        if (screenEffects.get()) {
            double outdoor = skyFade;
            double pitch = mc.player.getPitch();
            double yaw = mc.player.getYaw();
            // degrees on screen: the default 70 degree vertical field of view
            double ppd = h / 70.0;

            // overall tint, softer indoors
            int tintA = (int) (t.tintAlpha * Math.min(1.5, scale) * (0.45 + 0.55 * outdoor));
            if (tintA > 0) ctx.fill(0, 0, w, h, Draw2D.argb(tintA, t.tint));

            double lookUp = clamp01((15.0 - pitch) / 45.0);

            if (t.stars) drawStars(ctx, w, h, time, yaw, pitch, ppd, outdoor * lookUp);
            if (t.aurora) drawAurora(ctx, w, h, time, yaw, pitch, ppd, outdoor * clamp01((25.0 - pitch) / 40.0) * Math.min(1.3, scale));
            if (t.rays) drawRays(ctx, w, h, time, outdoor * Math.min(1.3, scale));
            if (t.haze > 0) drawWhiteout(ctx, w, h, time, t, scale, outdoor);
        }

        if (vignette.get()) drawEdge(ctx, w, h, time);
    }

    private void drawStars(DrawContext ctx, int w, int h, double time, double yaw, double pitch, double ppd, double fade) {
        if (fade <= 0.01) return;
        for (int i = 0; i < 90; i++) {
            double az = hash01(i * 3 + 1) * 360.0;
            double el = 6.0 + hash01(i * 3 + 2) * 70.0;
            double dAz = ((az - yaw + 540.0) % 360.0) - 180.0;
            double x = w / 2.0 + dAz * ppd;
            double y = h / 2.0 - (el - pitch) * ppd;
            if (x < 0 || x >= w || y < 0 || y >= h) continue;

            double twinkle = 0.55 + 0.45 * Math.sin(time * (1.5 + hash01(i * 3) * 2.5) + i);
            int alpha = (int) (200 * fade * twinkle);
            if (alpha <= 8) continue;

            int color = Draw2D.argb(alpha, i % 7 == 0 ? 0xBFE6FF : 0xFFFFFF);
            int px = (int) x;
            int py = (int) y;
            ctx.fill(px, py, px + 1, py + 1, color);
            if (i % 9 == 0 && twinkle > 0.8) {
                int arm = Draw2D.argb(alpha / 2, 0xFFFFFF);
                ctx.fill(px - 2, py, px + 3, py + 1, arm);
                ctx.fill(px, py - 2, px + 1, py + 3, arm);
            }
        }
    }

    private void drawAurora(DrawContext ctx, int w, int h, double time, double yaw, double pitch, double ppd, double fade) {
        if (fade <= 0.01) return;

        int[] colors = {0x50FFAA, 0x5AC8FF, 0xAA78FF};
        double[] elevations = {38.0, 46.0, 32.0};
        int steps = 6;

        for (int band = 0; band < colors.length; band++) {
            double phase = band * 1.7;
            for (int x = 0; x < w; x += 6) {
                double az = yaw + (x - w / 2.0) / ppd;
                double wave = Math.sin(az * 0.09 + time * 0.35 + phase) * 6.0 + Math.sin(az * 0.23 - time * 0.5 + phase) * 2.0;
                double top = h / 2.0 - (elevations[band] + wave - pitch) * ppd;
                double len = (14.0 + 8.0 * Math.sin(az * 0.05 - time * 0.2 + phase)) * ppd;
                if (top + len < 0 || top > h) continue;

                for (int s = 0; s < steps; s++) {
                    double f = (s + 1) / (double) steps;
                    int alpha = (int) (80 * fade * Math.pow(f, 1.6));
                    if (alpha <= 2) continue;
                    int y0 = (int) Math.round(top + len * s / steps);
                    int y1 = (int) Math.round(top + len * (s + 1) / steps);
                    if (y1 <= y0) continue;
                    ctx.fill(x, Math.max(0, y0), Math.min(w, x + 6), Math.min(h, y1), Draw2D.argb(alpha, colors[band]));
                }
            }
        }
    }

    private void drawRays(DrawContext ctx, int w, int h, double time, double fade) {
        if (fade <= 0.01) return;
        for (int i = 0; i < 4; i++) {
            double xTop = w * (0.12 + 0.24 * i) + Math.sin(time * 0.1 + i * 1.3) * 30.0;
            double topW = 34.0 + 22.0 * Math.sin(time * 0.17 + i);
            double drift = h * 0.45;
            double bottomW = topW * 2.2;

            double[] xs = {xTop, xTop + topW, xTop + drift + bottomW, xTop + drift};
            double[] ys = {0, 0, h, h};
            int alpha = (int) ((16 + 10 * Math.sin(time * 0.4 + i * 2.0)) * fade);
            Draw2D.fillPoly(ctx, xs, ys, 4, Draw2D.argb(alpha, 0xDDF6FF), 0, 0);
        }
    }

    private void drawWhiteout(DrawContext ctx, int w, int h, double time, Theme t, double scale, double outdoor) {
        double amount = t.haze * Math.min(1.5, scale) * (0.35 + 0.65 * outdoor);

        int haze = (int) (70 * amount * (0.9 + 0.1 * Math.sin(time * 1.3)));
        if (haze > 0) ctx.fill(0, 0, w, h, Draw2D.argb(haze, 0xFFFFFF));

        // wind-driven streaks, all moving the same way
        int streaks = (int) (70 * amount);
        for (int i = 0; i < streaks; i++) {
            double speed = 90.0 + hash01(i * 5 + 1) * 160.0;
            double x = ((hash01(i * 5) * (w + 120)) + time * speed) % (w + 120) - 60;
            double y = hash01(i * 5 + 2) * h;
            int len = 14 + (int) (hash01(i * 5 + 3) * 40);
            int alpha = (int) ((40 + hash01(i * 5 + 4) * 70) * Math.min(1.0, amount + 0.3));
            ctx.fill((int) x, (int) y, (int) x + len, (int) y + 1, Draw2D.argb(alpha, 0xFFFFFF));
        }
    }

    private void drawEdge(DrawContext ctx, int w, int h, double time) {
        int size = Math.max(6, h * edgeSize.get() / 100);
        int steps = 14;
        double step = size / (double) steps;
        double breathe = 0.92 + 0.08 * Math.sin(time * 1.1);

        for (int i = 0; i < steps; i++) {
            double f = 1.0 - i / (double) steps;
            int alpha = (int) (255 * (strength.get() / 100.0) * f * f * breathe);
            if (alpha <= 0) continue;
            alpha = Math.min(255, alpha);
            int argb = (alpha << 24) | (200 << 16) | (230 << 8) | 255;

            int a0 = (int) Math.round(i * step);
            int a1 = (int) Math.round((i + 1) * step);
            if (a1 <= a0) continue;

            ctx.fill(0, a0, w, a1, argb);
            ctx.fill(0, h - a1, w, h - a0, argb);
            ctx.fill(a0, 0, a1, h, argb);
            ctx.fill(w - a1, 0, w - a0, h, argb);
        }
    }

    private static double hash01(int n) {
        n *= 0x9E3779B1;
        n ^= n >>> 15;
        n *= 0x85EBCA6B;
        n ^= n >>> 13;
        return (n & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ------------------------------------------------------------------ sky

    private void applySky() {
        ambience = Modules.get().get("ambience");
        appliedTheme = theme.get();
        if (ambience == null) {
            warning("Meteor's ambience module was not found, set the sky colour there by hand.");
            return;
        }

        Theme t = theme.get();
        int applied = 0;
        applied += putFirst(new String[] {"overworld-sky-color", "sky-color"}, color(t.sky));
        applied += putFirst(new String[] {"custom-sky-color", "custom-sky"}, Boolean.TRUE);
        applied += putFirst(new String[] {"cloud-color", "clouds-color"}, color(t.cloud));
        applied += putFirst(new String[] {"custom-cloud-color", "custom-clouds"}, Boolean.TRUE);

        if (applied == 0) {
            warning("Could not find the sky settings in Meteor's ambience module, set them there by hand.");
            return;
        }

        if (!ambience.isActive()) {
            ambience.toggle();
            ambienceWasOff = true;
        }
        info("Sky preset '%s' applied (%d of 4 settings).", t.name(), applied);
    }

    private static SettingColor color(int rgb) {
        return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }

    @SuppressWarnings("unchecked")
    private int putFirst(String[] names, Object value) {
        for (String name : names) {
            Setting<?> setting = ambience.settings.get(name);
            if (setting == null) continue;

            Object old = setting.get();
            if (old == null || !value.getClass().isInstance(old)) continue;

            skyBackup.putIfAbsent(name, copy(old));
            ((Setting<Object>) setting).set(value);
            return 1;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private void restoreSky() {
        appliedTheme = null;
        if (ambience == null) return;

        for (Map.Entry<String, Object> entry : skyBackup.entrySet()) {
            Setting<?> setting = ambience.settings.get(entry.getKey());
            if (setting != null) ((Setting<Object>) setting).set(entry.getValue());
        }
        skyBackup.clear();

        if (ambienceWasOff && ambience.isActive()) ambience.toggle();
        ambienceWasOff = false;
        ambience = null;
    }

    private static Object copy(Object value) {
        if (value instanceof SettingColor c) return new SettingColor(c.r, c.g, c.b, c.a);
        return value;
    }
}
