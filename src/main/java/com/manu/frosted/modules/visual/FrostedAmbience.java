package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Draw2D;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Winter atmosphere with themes: snowfall with wind, twinkling sparkles, a frosted screen edge
 * with corner ice crystals, and smooth hardware-shaded sky effects (tint, stars, aurora curtains,
 * volumetric light rays, whiteout wind streaks).
 */
public class FrostedAmbience extends Module {
    public enum Theme {
        //      flakes wind  aurora sparkle haze  rays   stars  sky       cloud     tint      tintA
        Tundra(6,   0.30, false, true,  0.00, false, false, 0xBEE1FA, 0xF0F8FF, 0xBEE1FA, 14),
        Blizzard(18, 1.00, false, false, 0.42, false, false, 0x96AABE, 0xC8D2DC, 0xC8D2E1, 30),
        Aurora(5,   0.15, true,  true,  0.00, false, true,  0x122355, 0x3C5A96, 0x0A1946, 36),
        Glacier(5,  0.12, false, true,  0.00, true,  false, 0x78D7FF, 0xE1FAFF, 0x78D7FF, 14),
        Midnight(4, 0.20, true,  true,  0.00, false, true,  0x0A0F2D, 0x28325A, 0x050A28, 42);

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
        .name("frost-edge").description("Icy glow and corner frost crystals around the screen edge.").defaultValue(true).build());

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

        double windX = 0;
        double windZ = 0;
        if (wind.get()) {
            double gust = 0.6 + 0.4 * Math.sin(time * 0.7) + 0.25 * Math.sin(time * 1.9);
            double angle = time * 0.05;
            windX = Math.cos(angle) * t.wind * gust;
            windZ = Math.sin(angle) * t.wind * gust;
        }

        if (snowfall.get() && (!openSky.get() || skyFade > 0.05)) {
            int n = Math.min(36, (int) Math.round(t.flakes * intensity.get() / 5.0));
            double spread = radius.get() * 2.0;

            for (int i = 0; i < n; i++) {
                double x = px + Rand.centered(spread) - windX * 6.0;
                double z = pz + Rand.centered(spread) - windZ * 6.0;
                double y = py + 2.5 + Rand.unit() * height.get();
                if (openSky.get() && !mc.world.isSkyVisible(BlockPos.ofFloored(x, y, z))) continue;

                mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z,
                    Rand.centered(0.04) + windX * 0.12,
                    -0.045 - Rand.unit() * 0.045,
                    Rand.centered(0.04) + windZ * 0.12);
            }
        }

        if (sparkles.get() && t.sparkles && ticks % 2 == 0) {
            double x = px + Rand.centered(16.0);
            double z = pz + Rand.centered(16.0);
            double y = py + 0.3 + Rand.unit() * 4.5;
            mc.world.addParticleClient(ParticleTypes.END_ROD, x, y, z,
                Rand.centered(0.012), 0.006 + Rand.unit() * 0.012, Rand.centered(0.012));
        }
    }

    // --------------------------------------------------------------- screen

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        double w = event.screenWidth > 0 ? event.screenWidth : mc.getWindow().getFramebufferWidth();
        double h = event.screenHeight > 0 ? event.screenHeight : mc.getWindow().getFramebufferHeight();
        long now = System.currentTimeMillis();
        double time = (now % 1_000_000L) / 1000.0;
        Theme t = theme.get();
        double scale = intensity.get() / 5.0;

        Renderer2D.COLOR.begin();

        if (screenEffects.get()) {
            double outdoor = skyFade;
            double pitch = mc.player.getPitch();
            double yaw = mc.player.getYaw();
            double ppd = h / 70.0;

            int tintA = (int) (t.tintAlpha * Math.min(1.5, scale) * (0.40 + 0.60 * outdoor));
            if (tintA > 0) {
                int topTint = Draw2D.argb(tintA, t.tint);
                int botTint = Draw2D.argb(tintA / 3, Draw2D.shade(t.tint, 0.2));
                Draw2D.polyQuadGrad(0, 0, topTint, w, 0, topTint, w, h, botTint, 0, h, botTint);
            }

            double lookUp = clamp01((18.0 - pitch) / 45.0);

            if (t.stars) drawStars(w, h, time, yaw, pitch, ppd, outdoor * lookUp);
            if (t.aurora) drawAurora(w, h, time, yaw, pitch, ppd, outdoor * clamp01((28.0 - pitch) / 42.0) * Math.min(1.3, scale));
            if (t.rays) drawRays(w, h, time, outdoor * Math.min(1.3, scale));
            if (t.haze > 0) drawWhiteout(w, h, time, t, scale, outdoor);
        }

        if (vignette.get()) drawEdge(w, h, time);

        Renderer2D.COLOR.render();
    }

    private void drawStars(double w, double h, double time, double yaw, double pitch, double ppd, double fade) {
        if (fade <= 0.01) return;
        double dotSize = Math.max(1.5, h / 420.0);

        for (int i = 0; i < 85; i++) {
            double az = hash01(i * 3 + 1) * 360.0;
            double el = 6.0 + hash01(i * 3 + 2) * 70.0;
            double dAz = ((az - yaw + 540.0) % 360.0) - 180.0;
            double x = w * 0.5 + dAz * ppd;
            double y = h * 0.5 - (el - pitch) * ppd;
            if (x < 0 || x >= w || y < 0 || y >= h) continue;

            double twinkle = 0.50 + 0.50 * Math.sin(time * (1.6 + hash01(i * 3) * 2.6) + i);
            int alpha = (int) (220 * fade * twinkle);
            if (alpha <= 10) continue;

            int rgb = (i % 5 == 0) ? 0xA8E0FF : (i % 7 == 0 ? 0xD8C8FF : 0xFFFFFF);
            if (i % 8 == 0 && twinkle > 0.65) {
                Draw2D.star(x, y, dotSize * 3.4, dotSize * 0.7, Draw2D.argb(alpha, 0xFFFFFF), Draw2D.argb(alpha / 2, rgb));
            } else {
                int c = Draw2D.argb(alpha, rgb);
                Draw2D.polyQuad(x - dotSize * 0.5, y - dotSize * 0.5, x + dotSize * 0.5, y - dotSize * 0.5,
                    x + dotSize * 0.5, y + dotSize * 0.5, x - dotSize * 0.5, y + dotSize * 0.5, c);
            }
        }
    }

    private void drawAurora(double w, double h, double time, double yaw, double pitch, double ppd, double fade) {
        if (fade <= 0.01) return;

        int[] topColors = {0x9868FF, 0x48C8FF, 0x60FFB4};
        int[] botColors = {0x42FFAA, 0x58FFEE, 0x78B4FF};
        double[] elevations = {40.0, 47.0, 33.0};
        int cols = 42;
        double stepX = w / cols;

        for (int band = 0; band < topColors.length; band++) {
            double phase = band * 1.85;
            double prevX = 0;
            double prevTop = 0;
            double prevMid = 0;
            double prevBot = 0;
            int prevMidC = 0;
            int prevGlowC = 0;

            for (int c = 0; c <= cols; c++) {
                double x = c * stepX;
                double az = yaw + (x - w * 0.5) / ppd;
                double wave = Math.sin(az * 0.085 + time * 0.38 + phase) * 6.2
                    + Math.sin(az * 0.22 - time * 0.52 + phase) * 2.2;
                double top = h * 0.5 - (elevations[band] + wave - pitch) * ppd;
                double len = (15.0 + 8.5 * Math.sin(az * 0.055 - time * 0.24 + phase)) * ppd;
                double mid = top + len * 0.68;
                double bot = top + len;

                double shimmer = 0.65 + 0.35 * Math.sin(az * 0.16 + time * 0.9 + band);
                int alphaMid = (int) Math.round(78 * fade * shimmer);
                int alphaGlow = (int) Math.round(96 * fade * shimmer);
                int midC = Draw2D.argb(alphaMid, topColors[band]);
                int glowC = Draw2D.argb(alphaGlow, botColors[band]);
                int transTop = Draw2D.argb(0, topColors[band]);
                int transBot = Draw2D.argb(0, botColors[band]);

                if (c > 0 && !(Math.max(prevBot, bot) < 0 || Math.min(prevTop, top) > h)) {
                    // Upper curtain: fades in from transparent top to glowing middle
                    Draw2D.polyQuadGrad(prevX, prevTop, transTop, x, top, transTop, x, mid, midC, prevX, prevMid, prevMidC);
                    // Lower curtain core: intense aurora ribbon near the bottom fold
                    Draw2D.polyQuadGrad(prevX, prevMid, prevMidC, x, mid, midC, x, bot, glowC, prevX, prevBot, prevGlowC);
                    // Soft bottom fringe fadeout
                    Draw2D.polyQuadGrad(prevX, prevBot, prevGlowC, x, bot, glowC, x, bot + len * 0.22, transBot, prevX, prevBot + len * 0.22, transBot);
                }

                prevX = x;
                prevTop = top;
                prevMid = mid;
                prevBot = bot;
                prevMidC = midC;
                prevGlowC = glowC;
            }
        }
    }

    private void drawRays(double w, double h, double time, double fade) {
        if (fade <= 0.01) return;
        for (int i = 0; i < 5; i++) {
            double xTop = w * (0.10 + 0.20 * i) + Math.sin(time * 0.12 + i * 1.4) * (w * 0.03);
            double topW = (w * 0.032) + (w * 0.018) * Math.sin(time * 0.19 + i);
            double drift = h * 0.42;
            double bottomW = topW * 2.4;

            int topAlpha = (int) Math.round((24 + 12 * Math.sin(time * 0.45 + i * 1.9)) * fade);
            int cTop = Draw2D.argb(topAlpha, 0xDDF6FF);
            int cBot = Draw2D.argb(0, 0x96D8FF);

            Draw2D.polyQuadGrad(
                xTop, 0, cTop,
                xTop + topW, 0, cTop,
                xTop + drift + bottomW, h, cBot,
                xTop + drift, h, cBot
            );
        }
    }

    private void drawWhiteout(double w, double h, double time, Theme t, double scale, double outdoor) {
        double amount = t.haze * Math.min(1.5, scale) * (0.35 + 0.65 * outdoor);

        int haze = (int) (62 * amount * (0.9 + 0.1 * Math.sin(time * 1.3)));
        if (haze > 0) {
            int c = Draw2D.argb(haze, 0xEEF6FF);
            Draw2D.polyQuad(0, 0, w, 0, w, h, 0, h, c);
        }

        int streaks = (int) (55 * amount);
        double thick = Math.max(1.2, h / 480.0);
        for (int i = 0; i < streaks; i++) {
            double speed = (w * 0.22) + hash01(i * 5 + 1) * (w * 0.35);
            double span = w + 160;
            double x = ((hash01(i * 5) * span) + time * speed) % span - 80;
            double y = hash01(i * 5 + 2) * h;
            double len = (w * 0.025) + hash01(i * 5 + 3) * (w * 0.065);
            double dy = len * 0.12;
            int alpha = (int) ((45 + hash01(i * 5 + 4) * 75) * Math.min(1.0, amount + 0.3));
            Draw2D.thickLineGrad(x, y, Draw2D.argb(0, 0xFFFFFF), x + len, y + dy, Draw2D.argb(alpha, 0xFFFFFF), thick);
        }
    }

    private void drawEdge(double w, double h, double time) {
        double size = Math.max(12.0, h * edgeSize.get() / 100.0);
        double breathe = 0.90 + 0.10 * Math.sin(time * 1.15);
        int outerAlpha = Math.min(255, (int) Math.round(215 * (strength.get() / 100.0) * breathe));
        if (outerAlpha <= 0) return;

        int edgeColor = Draw2D.argb(outerAlpha, 0xBCE4FF);
        int midColor = Draw2D.argb(outerAlpha / 3, 0x8CC8FF);
        int innerTrans = Draw2D.argb(0, 0x8CC8FF);

        // Top & Bottom smooth two-stage frost gradients
        Draw2D.polyQuadGrad(0, 0, edgeColor, w, 0, edgeColor, w, size * 0.45, midColor, 0, size * 0.45, midColor);
        Draw2D.polyQuadGrad(0, size * 0.45, midColor, w, size * 0.45, midColor, w, size, innerTrans, 0, size, innerTrans);

        Draw2D.polyQuadGrad(0, h - size, innerTrans, w, h - size, innerTrans, w, h - size * 0.45, midColor, 0, h - size * 0.45, midColor);
        Draw2D.polyQuadGrad(0, h - size * 0.45, midColor, w, h - size * 0.45, midColor, w, h, edgeColor, 0, h, edgeColor);

        // Left & Right smooth two-stage frost gradients
        Draw2D.polyQuadGrad(0, 0, edgeColor, size * 0.45, 0, midColor, size * 0.45, h, midColor, 0, h, edgeColor);
        Draw2D.polyQuadGrad(size * 0.45, 0, midColor, size, 0, innerTrans, size, h, innerTrans, size * 0.45, h, midColor);

        Draw2D.polyQuadGrad(w - size, 0, innerTrans, w - size * 0.45, 0, midColor, w - size * 0.45, h, midColor, w - size, h, innerTrans);
        Draw2D.polyQuadGrad(w - size * 0.45, 0, midColor, w, 0, edgeColor, w, h, edgeColor, w - size * 0.45, h, midColor);

        // Delicate frost needles in the 4 corners
        int crystalBase = Draw2D.argb(Math.min(255, (int) (outerAlpha * 0.85)), 0xEAF8FF);
        int crystalTip = Draw2D.argb(0, 0xFFFFFF);
        double needleLen = size * 1.15;
        double needleW = Math.max(2.0, size * 0.09);

        cornerNeedles(0, 0, 1, 1, needleLen, needleW, crystalBase, crystalTip);
        cornerNeedles(w, 0, -1, 1, needleLen, needleW, crystalBase, crystalTip);
        cornerNeedles(0, h, 1, -1, needleLen, needleW, crystalBase, crystalTip);
        cornerNeedles(w, h, -1, -1, needleLen, needleW, crystalBase, crystalTip);
    }

    private static void cornerNeedles(double cx, double cy, double sx, double sy,
                                      double len, double width, int baseC, int tipC) {
        double[] angles = {0.22, 0.52, 0.78, 1.08, 1.35};
        double[] scales = {0.85, 0.65, 1.0, 0.65, 0.85};
        for (int i = 0; i < angles.length; i++) {
            double a = angles[i];
            double l = len * scales[i];
            double dx = Math.cos(a) * sx;
            double dy = Math.sin(a) * sy;
            double px = -dy * width;
            double py = dx * width;
            Draw2D.triGrad(
                cx + px, cy + py, baseC,
                cx - px, cy - py, baseC,
                cx + dx * l, cy + dy * l, tipC
            );
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
