package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Purely cosmetic jump rings. */
public class JumpCircles extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> maxRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-radius").defaultValue(1.6).min(0.3).sliderRange(0.3, 4.0).build()
    );

    private final Setting<Integer> duration = sgGeneral.add(new IntSetting.Builder()
        .name("duration-ms").defaultValue(600).min(100).sliderRange(100, 2000).build()
    );

    private final Setting<Integer> segments = sgGeneral.add(new IntSetting.Builder()
        .name("segments").defaultValue(28).min(8).sliderRange(8, 64).build()
    );

    private final Setting<Boolean> rim = sgGeneral.add(new BoolSetting.Builder()
        .name("outer-ring").description("Faint ring around the snowflake. Uses the segments setting.").defaultValue(true).build()
    );

    private final Setting<Boolean> grow = sgGeneral.add(new BoolSetting.Builder()
        .name("grow-animation").description("Crystal grows arm first, then branches, then tips.").defaultValue(true).build()
    );

    private final Setting<Boolean> thick = sgGeneral.add(new BoolSetting.Builder()
        .name("thick-arms").description("Draw the main arms as three parallel lines.").defaultValue(true).build()
    );

    private final Setting<Boolean> sparkles = sgGeneral.add(new BoolSetting.Builder()
        .name("tip-sparkles").description("Glinting stars on the arm tips.").defaultValue(true).build()
    );

    private final Setting<Boolean> onlySprint = sgGeneral.add(new BoolSetting.Builder()
        .name("only-sprint").defaultValue(false).build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(120, 200, 255, 210)).build()
    );

    private record Ring(double x, double y, double z, long start) {}

    private final List<Ring> rings = new ArrayList<>();
    private boolean wasOnGround = true;

    public JumpCircles() {
        super(FrostedAddon.VISUAL, "jump-circles", "Draws a cosmetic snowflake when you jump.");
    }

    @Override
    public void onActivate() {
        rings.clear();
        wasOnGround = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean onGround = mc.player.isOnGround();
        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0) {
            if (!onlySprint.get() || mc.player.isSprinting()) {
                rings.add(new Ring(mc.player.getX(), mc.player.getY(), mc.player.getZ(), System.currentTimeMillis()));
            }
        }
        wasOnGround = onGround;

        long now = System.currentTimeMillis();
        rings.removeIf(ring -> now - ring.start > duration.get());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (rings.isEmpty()) return;

        long now = System.currentTimeMillis();
        Color base = color.get();

        for (Ring ring : rings) {
            double p = Math.min(1.0, (now - ring.start) / (double) duration.get());
            double radius = maxRadius.get() * (1.0 - Math.pow(1.0 - p, 3.0)); // ease-out
            if (radius < 0.03) continue;

            double fade = 1.0 - p * p;
            int a = clamp((int) (base.a * fade));

            // Inner half of every arm is paler than the tip, like ice catching light.
            Color tipColor = new Color(base.r, base.g, base.b, a);
            Color coreColor = new Color(lighten(base.r, 0.55), lighten(base.g, 0.55), lighten(base.b, 0.55), a);
            Color twigColor = new Color(lighten(base.r, 0.35), lighten(base.g, 0.35), lighten(base.b, 0.35), clamp((int) (a * 0.85)));
            Color webColor = new Color(lighten(base.r, 0.25), lighten(base.g, 0.25), lighten(base.b, 0.25), clamp((int) (a * 0.40)));
            Color rimColor = new Color(base.r, base.g, base.b, clamp((int) (a * 0.30)));

            // Growth stages: branches and forks appear after the arms are out.
            boolean g = grow.get();
            double twigA = g ? smooth(0.04, 0.32, p) : 1.0;
            double twigB = g ? smooth(0.14, 0.46, p) : 1.0;
            double fork = g ? smooth(0.28, 0.62, p) : 1.0;
            double web = g ? smooth(0.18, 0.55, p) : 1.0;

            double y = ring.y + 0.05 + p * 0.2;           // drifts up a little while it fades
            double spin = Math.toRadians(p * 40.0);
            double lineGap = radius * 0.02;

            double[] tipX = new double[6];
            double[] tipZ = new double[6];
            double[] webX = new double[6];
            double[] webZ = new double[6];

            for (int arm = 0; arm < 6; arm++) {
                double angle = spin + arm * (Math.PI / 3.0);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double px = -sin;
                double pz = cos;

                tipX[arm] = ring.x + cos * radius;
                tipZ[arm] = ring.z + sin * radius;
                webX[arm] = ring.x + cos * radius * 0.55;
                webZ[arm] = ring.z + sin * radius * 0.55;

                // main arm in two tones, optionally three lines wide
                for (int off = thick.get() ? -1 : 0; off <= (thick.get() ? 1 : 0); off++) {
                    double ox = px * lineGap * off;
                    double oz = pz * lineGap * off;
                    line(event, ring.x + ox, y, ring.z + oz, webX[arm] + ox, webZ[arm] + oz, coreColor);
                    line(event, webX[arm] + ox, y, webZ[arm] + oz, tipX[arm] + ox, tipZ[arm] + oz, tipColor);
                }

                // two pairs of side twigs, the inner one longer
                twigs(event, ring.x, y, ring.z, angle, radius * 0.38, radius * 0.27 * twigA, twigColor);
                twigs(event, ring.x, y, ring.z, angle, radius * 0.72, radius * 0.19 * twigB, twigColor);

                // fork at the tip, opening back toward the centre
                double forkLen = radius * 0.14 * fork;
                if (forkLen > 0.005) {
                    for (int side = -1; side <= 1; side += 2) {
                        double fa = angle + side * Math.toRadians(145);
                        line(event, tipX[arm], y, tipZ[arm],
                            tipX[arm] + Math.cos(fa) * forkLen, tipZ[arm] + Math.sin(fa) * forkLen, twigColor);
                    }
                }
            }

            // Hexagonal web that links the arms at 55 % of the radius.
            if (web > 0.02) {
                Color w = new Color(webColor.r, webColor.g, webColor.b, clamp((int) (webColor.a * web)));
                for (int k = 0; k < 6; k++) {
                    int n = (k + 1) % 6;
                    line(event, webX[k], y, webZ[k], webX[n], webZ[n], w);
                }
            }

            // Twinkling stars on the tips.
            if (sparkles.get()) {
                double star = radius * 0.10;
                for (int k = 0; k < 6; k++) {
                    double tw = 0.55 + 0.45 * Math.sin(now * 0.018 + k * 1.7);
                    Color sc = new Color(255, 255, 255, clamp((int) (a * tw)));
                    line(event, tipX[k] - star, y, tipZ[k], tipX[k] + star, tipZ[k], sc);
                    line(event, tipX[k], y, tipZ[k] - star, tipX[k], tipZ[k] + star, sc);
                }
            }

            // Small double hexagon in the middle.
            double hex = radius * 0.14;
            for (int k = 0; k < 6; k++) {
                double a1 = spin + Math.toRadians(30) + k * (Math.PI / 3.0);
                double a2 = a1 + Math.PI / 3.0;
                line(event,
                    ring.x + Math.cos(a1) * hex, y, ring.z + Math.sin(a1) * hex,
                    ring.x + Math.cos(a2) * hex, ring.z + Math.sin(a2) * hex, coreColor);
                line(event,
                    ring.x + Math.cos(a1) * hex * 0.5, y, ring.z + Math.sin(a1) * hex * 0.5,
                    ring.x + Math.cos(a2) * hex * 0.5, ring.z + Math.sin(a2) * hex * 0.5, coreColor);
            }

            if (rim.get()) {
                int n = segments.get();
                double rr = radius * 1.06;
                for (int k = 0; k < n; k++) {
                    double a1 = k * (2 * Math.PI / n);
                    double a2 = (k + 1) * (2 * Math.PI / n);
                    line(event,
                        ring.x + Math.cos(a1) * rr, y, ring.z + Math.sin(a1) * rr,
                        ring.x + Math.cos(a2) * rr, ring.z + Math.sin(a2) * rr, rimColor);
                }
            }
        }
    }

    /** Two twigs leaving the arm at {@code along} blocks from the centre. */
    private void twigs(Render3DEvent event, double cx, double y, double cz, double angle,
                       double along, double length, Color c) {
        if (length < 0.005) return;
        double bx = cx + Math.cos(angle) * along;
        double bz = cz + Math.sin(angle) * along;
        for (int side = -1; side <= 1; side += 2) {
            double ta = angle + side * Math.toRadians(55);
            line(event, bx, y, bz, bx + Math.cos(ta) * length, bz + Math.sin(ta) * length, c);
        }
    }

    private void line(Render3DEvent event, double x1, double y, double z1, double x2, double z2, Color c) {
        event.renderer.line(x1, y, z1, x2, y, z2, c);
    }

    private static double smooth(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int lighten(int channel, double amount) {
        return (int) (channel + (255 - channel) * amount);
    }
}
