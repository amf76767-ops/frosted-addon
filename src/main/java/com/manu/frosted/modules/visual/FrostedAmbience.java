package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Draw2D;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Winter atmosphere with themes: snowfall with wind, twinkling sparkles, a frosted screen edge,
 * and a true 3D depth-tested sky dome (3D stars, high-altitude shooting stars and 3D aurora curtains
 * that sit in the actual sky behind blocks and terrain).
 */
public class FrostedAmbience extends Module {
    public enum Theme {
        //      flakes wind  aurora sparkle haze  stars  sky       cloud     tint      tintA
        Tundra(6,   0.30, false, true,  0.00, false, 0xBEE1FA, 0xF0F8FF, 0xBEE1FA, 10),
        Blizzard(18, 1.00, false, false, 0.28, false, 0x96AABE, 0xC8D2DC, 0xC8D2E1, 22),
        Aurora(5,   0.15, true,  true,  0.00, true,  0x122355, 0x3C5A96, 0x0A1946, 24),
        Glacier(5,  0.12, true,  true,  0.00, false, 0x78D7FF, 0xE1FAFF, 0x78D7FF, 10),
        Midnight(4, 0.20, true,  true,  0.00, true,  0x0A0F2D, 0x28325A, 0x050A28, 30);

        final int flakes;
        final double wind;
        final boolean aurora;
        final boolean sparkles;
        final double haze;
        final boolean stars;
        final int sky;
        final int cloud;
        final int tint;
        final int tintAlpha;

        Theme(int flakes, double wind, boolean aurora, boolean sparkles, double haze,
              boolean stars, int sky, int cloud, int tint, int tintAlpha) {
            this.flakes = flakes;
            this.wind = wind;
            this.aurora = aurora;
            this.sparkles = sparkles;
            this.haze = haze;
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
        .name("intensity").description("Scales snowfall and sky effects (5 = the theme's default).")
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
        .name("sky-effects").description("3D aurora curtains, 3D stars and shooting stars in the sky dome.").defaultValue(true).build());

    private final Setting<Boolean> shootingStars = sgScreen.add(new BoolSetting.Builder()
        .name("shooting-stars").description("Occasional shooting stars high up in the 3D sky dome.").defaultValue(true).build());

    private final Setting<Boolean> vignette = sgScreen.add(new BoolSetting.Builder()
        .name("frost-edge").description("Icy glow and corner frost crystals around the screen edge.").defaultValue(true).build());

    private final Setting<Integer> strength = sgScreen.add(new IntSetting.Builder()
        .name("strength").defaultValue(30).min(0).sliderRange(0, 100).build());

    private final Setting<Integer> edgeSize = sgScreen.add(new IntSetting.Builder()
        .name("size").description("Edge thickness in percent of the screen height.").defaultValue(12).min(4).sliderRange(4, 30).build());

    private final Setting<Boolean> skyPreset = sgSky.add(new BoolSetting.Builder()
        .name("sky-preset")
        .description("Sky and cloud colour of the theme via Meteor's ambience module (restored when this is turned off).")
        .defaultValue(true).build());

    private final Map<String, Object> skyBackup = new HashMap<>();
    private Module ambience;
    private boolean ambienceWasOff;
    private Theme appliedTheme;

    private final Color c0 = new Color();
    private final Color c1 = new Color();
    private final Color c2 = new Color();
    private final Color c3 = new Color();

    private double skyFade = 1.0;
    private int ticks;

    public FrostedAmbience() {
        super(FrostedAddon.VISUAL, "frosted-ambience", "Winter themes: snowfall, wind, 3D aurora, 3D sky stars and a frosted screen edge.");
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
        if (skyPreset.get()) {
            if (appliedTheme != t || ambience == null || !ambience.isActive()) applySky();
        } else if (appliedTheme != null) {
            restoreSky();
        }

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

    // --------------------------------------------------------------- 3D sky dome

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !screenEffects.get()) return;
        if (overworldOnly.get() && !"overworld".equals(mc.world.getRegistryKey().getValue().getPath())) return;
        if (skyFade <= 0.02) return;

        Renderer3D r = event.depthRenderer != null ? event.depthRenderer : event.renderer;
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        double cx = cam.x;
        double cy = cam.y;
        double cz = cam.z;

        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        Theme t = theme.get();
        double scale = Math.min(1.4, intensity.get() / 5.0);
        double fade = skyFade * scale;

        if (t.stars) {
            drawSkyStars3D(r, cx, cy, cz, time, fade);
            if (shootingStars.get()) {
                drawShootingStars3D(r, cx, cy, cz, time, fade);
            }
        }
        if (t.aurora) {
            drawAurora3D(r, cx, cy, cz, time, fade);
        }
    }

    private void drawSkyStars3D(Renderer3D r, double cx, double cy, double cz, double time, double fade) {
        double domeR = 135.0;
        for (int i = 0; i < 95; i++) {
            double az = hash01(i * 3 + 1) * Math.PI * 2.0;
            double el = Math.toRadians(14.0 + hash01(i * 3 + 2) * 72.0);
            double cosEl = Math.cos(el);
            double sx = cx + Math.cos(az) * cosEl * domeR;
            double sy = cy + Math.sin(el) * domeR;
            double sz = cz + Math.sin(az) * cosEl * domeR;

            double twinkle = 0.45 + 0.55 * Math.sin(time * (1.5 + hash01(i * 3) * 2.4) + i);
            int alpha = Math.min(255, (int) (220 * fade * twinkle));
            if (alpha <= 12) continue;

            int rgb = (i % 5 == 0) ? 0xA8E0FF : (i % 7 == 0 ? 0xD8C8FF : 0xFFFFFF);
            c0.set((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);

            double size = (i % 8 == 0 ? 0.95 : 0.55) * (0.8 + 0.4 * twinkle);
            r.line(sx - size, sy, sz, sx + size, sy, sz, c0);
            r.line(sx, sy - size, sz, sx, sy + size, sz, c0);
            r.line(sx, sy, sz - size, sx, sy, sz + size, c0);
        }
    }

    /** High-altitude shooting stars that streak across the 3D sky dome overhead (behind blocks). */
    private void drawShootingStars3D(Renderer3D r, double cx, double cy, double cz, double time, double fade) {
        double domeR = 128.0;
        for (int s = 0; s < 2; s++) {
            double period = 6.5 + s * 4.2;
            double cycle = Math.floor(time / period);
            double phase = (time % period) / 1.15; // active for 1.15s per cycle
            if (phase > 1.0) continue;

            int seed = (int) (cycle * 31 + s * 97);
            double startAz = hash01(seed + 1) * Math.PI * 2.0;
            double startEl = Math.toRadians(38.0 + hash01(seed + 2) * 38.0);
            double dirAz = startAz + (hash01(seed + 3) - 0.5) * 0.9;
            double endEl = startEl - Math.toRadians(16.0 + hash01(seed + 4) * 12.0);

            double headT = phase;
            double tailT = Math.max(0.0, phase - 0.28);

            double azH = startAz + (dirAz - startAz) * headT;
            double elH = startEl + (endEl - startEl) * headT;
            double azT = startAz + (dirAz - startAz) * tailT;
            double elT = startEl + (endEl - startEl) * tailT;

            double hx = cx + Math.cos(azH) * Math.cos(elH) * domeR;
            double hy = cy + Math.sin(elH) * domeR;
            double hz = cz + Math.sin(azH) * Math.cos(elH) * domeR;

            double tx = cx + Math.cos(azT) * Math.cos(elT) * domeR;
            double ty = cy + Math.sin(elT) * domeR;
            double tz = cz + Math.sin(azT) * Math.cos(elT) * domeR;

            double env = Math.sin(phase * Math.PI);
            int headA = Math.min(255, (int) (245 * fade * env));
            if (headA <= 8) continue;

            c0.set(255, 255, 255, headA);
            c1.set(140, 215, 255, 0);
            r.line(hx, hy, hz, tx, ty, tz, c0, c1);

            // Bright star head
            double hs = 0.85 * env;
            r.line(hx - hs, hy, hz, hx + hs, hy, hz, c0);
            r.line(hx, hy - hs, hz, hx, hy + hs, hz, c0);
        }
    }

    /** Waving 3D aurora borealis curtains high up in the sky dome. */
    private void drawAurora3D(Renderer3D r, double cx, double cy, double cz, double time, double fade) {
        int[] topRgb = {0x9868FF, 0x48C8FF, 0x58FFB4};
        int[] botRgb = {0x42FFAA, 0x58FFEE, 0x78B4FF};
        int segs = 48;

        for (int band = 0; band < 3; band++) {
            double baseR = 95.0 + band * 18.0;
            double baseY = cy + 52.0 + band * 10.0;
            double curtainH = 26.0 + band * 4.0;
            double phase = band * 2.1;

            int tr = (topRgb[band] >> 16) & 0xFF, tg = (topRgb[band] >> 8) & 0xFF, tb = topRgb[band] & 0xFF;
            int br = (botRgb[band] >> 16) & 0xFF, bg = (botRgb[band] >> 8) & 0xFF, bb = botRgb[band] & 0xFF;

            double prevX = 0, prevZ = 0, prevY = 0, prevH = 0;
            int prevA = 0;

            for (int i = 0; i <= segs; i++) {
                double a = (i / (double) segs) * Math.PI * 2.0;
                double waveR = baseR
                    + Math.sin(a * 4.0 + time * 0.35 + phase) * 12.0
                    + Math.cos(a * 7.0 - time * 0.48 + phase) * 5.5;
                double x = cx + Math.cos(a) * waveR;
                double z = cz + Math.sin(a) * waveR;
                double y = baseY + Math.sin(a * 3.0 - time * 0.3 + phase) * 5.0;
                double h = curtainH * (0.75 + 0.25 * Math.sin(a * 5.0 + time * 0.4 + phase));

                double shimmer = 0.55 + 0.45 * Math.sin(a * 6.0 + time * 0.8 + band);
                int alpha = Math.min(255, (int) (72 * fade * shimmer));

                if (i > 0) {
                    double midY0 = prevY + prevH * 0.38;
                    double midY1 = y + h * 0.38;

                    // Lower intense glow fold
                    c0.set(br, bg, bb, 0);
                    c1.set(br, bg, bb, prevA);
                    c2.set(br, bg, bb, alpha);
                    r.quad(
                        prevX, prevY, prevZ,
                        x, y, z,
                        x, midY1, z,
                        prevX, midY0, prevZ,
                        c0, c0, c2, c1
                    );

                    // Upper curtain fading into the night sky
                    c3.set(tr, tg, tb, 0);
                    r.quad(
                        prevX, midY0, prevZ,
                        x, midY1, z,
                        x, y + h, z,
                        prevX, prevY + prevH, prevZ,
                        c1, c2, c3, c3
                    );
                }

                prevX = x;
                prevZ = z;
                prevY = y;
                prevH = h;
                prevA = alpha;
            }
        }
    }

    // --------------------------------------------------------------- 2D screen edge

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        double w = Draw2D.screenW();
        double h = Draw2D.screenH();
        long now = System.currentTimeMillis();
        double time = (now % 1_000_000L) / 1000.0;
        Theme t = theme.get();
        double scale = intensity.get() / 5.0;

        Renderer2D.COLOR.begin();

        if (screenEffects.get()) {
            int tintA = (int) (t.tintAlpha * Math.min(1.4, scale) * (0.35 + 0.65 * skyFade));
            if (tintA > 0) {
                int topTint = Draw2D.argb(tintA, t.tint);
                int botTint = Draw2D.argb(tintA / 4, Draw2D.shade(t.tint, 0.2));
                Draw2D.polyQuadGrad(0, 0, topTint, w, 0, topTint, w, h, botTint, 0, h, botTint);
            }
        }

        if (vignette.get()) drawEdge(w, h, time);

        Renderer2D.COLOR.render();
    }

    private void drawEdge(double w, double h, double time) {
        double size = Math.max(12.0, h * edgeSize.get() / 100.0);
        double breathe = 0.90 + 0.10 * Math.sin(time * 1.15);
        int outerAlpha = Math.min(255, (int) Math.round(210 * (strength.get() / 100.0) * breathe));
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
