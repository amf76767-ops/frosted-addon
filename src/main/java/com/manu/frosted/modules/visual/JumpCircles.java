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
import java.util.List;

/**
 * 3D crystalline snowflake & shockwave ring when you jump.
 * Combines glowing translucent ice facets with crisp geometric crystal branches.
 */
public class JumpCircles extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> maxRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-radius").defaultValue(1.6).min(0.3).sliderRange(0.3, 4.0).build()
    );

    private final Setting<Integer> duration = sgGeneral.add(new IntSetting.Builder()
        .name("duration-ms").defaultValue(750).min(100).sliderRange(100, 2000).build()
    );

    private final Setting<Integer> segments = sgGeneral.add(new IntSetting.Builder()
        .name("segments").defaultValue(32).min(8).sliderRange(8, 64).build()
    );

    private final Setting<Boolean> rim = sgGeneral.add(new BoolSetting.Builder()
        .name("outer-ring").description("Glowing shockwave ring around the snowflake.").defaultValue(true).build()
    );

    private final Setting<Boolean> grow = sgGeneral.add(new BoolSetting.Builder()
        .name("grow-animation").description("Crystal grows arm first, then branches, then tips.").defaultValue(true).build()
    );

    private final Setting<Boolean> thick = sgGeneral.add(new BoolSetting.Builder()
        .name("thick-arms").description("Draw the main arms with glowing ice facets.").defaultValue(true).build()
    );

    private final Setting<Boolean> sparkles = sgGeneral.add(new BoolSetting.Builder()
        .name("tip-sparkles").description("Glinting 3D stars on the arm tips.").defaultValue(true).build()
    );

    private final Setting<Boolean> onlySprint = sgGeneral.add(new BoolSetting.Builder()
        .name("only-sprint").defaultValue(false).build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(120, 200, 255, 220)).build()
    );

    private record Ring(double x, double y, double z, long start) {}

    private final List<Ring> rings = new ArrayList<>();
    private boolean wasOnGround = true;

    private final Color cTip = new Color();
    private final Color cCore = new Color();
    private final Color cTwig = new Color();
    private final Color cWeb = new Color();
    private final Color cRim = new Color();
    private final Color cFacet = new Color();
    private final Color cFacetOut = new Color();
    private final Color cStar = new Color();

    public JumpCircles() {
        super(FrostedAddon.VISUAL, "jump-circles", "Draws a cosmetic 3D ice crystal snowflake when you jump.");
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
                if (rings.size() >= 12) rings.remove(0);
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
        SettingColor base = color.get();

        double[] tipX = new double[6];
        double[] tipZ = new double[6];
        double[] webX = new double[6];
        double[] webZ = new double[6];

        for (Ring ring : rings) {
            double p = Math.min(1.0, (now - ring.start) / (double) duration.get());
            double radius = maxRadius.get() * (1.0 - Math.pow(1.0 - p, 3.2));
            if (radius < 0.03) continue;

            double fade = (1.0 - p) * (1.0 - p * 0.5);
            int a = clamp((int) (base.a * fade));
            if (a <= 2) continue;

            cTip.set(base.r, base.g, base.b, a);
            cCore.set(lighten(base.r, 0.65), lighten(base.g, 0.65), lighten(base.b, 0.65), a);
            cTwig.set(lighten(base.r, 0.40), lighten(base.g, 0.40), lighten(base.b, 0.40), clamp((int) (a * 0.88)));
            cWeb.set(lighten(base.r, 0.30), lighten(base.g, 0.30), lighten(base.b, 0.30), clamp((int) (a * 0.48)));
            cRim.set(base.r, base.g, base.b, clamp((int) (a * 0.42)));
            cFacet.set(lighten(base.r, 0.35), lighten(base.g, 0.35), lighten(base.b, 0.35), clamp((int) (a * 0.28)));
            cFacetOut.set(base.r, base.g, base.b, 0);

            boolean g = grow.get();
            double twigA = g ? smooth(0.04, 0.32, p) : 1.0;
            double twigB = g ? smooth(0.14, 0.46, p) : 1.0;
            double fork = g ? smooth(0.26, 0.60, p) : 1.0;
            double web = g ? smooth(0.16, 0.52, p) : 1.0;

            double y = ring.y + 0.04 + p * 0.18;
            double spin = Math.toRadians(p * 42.0);
            double armHalfW = radius * 0.075;

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

                // Translucent diamond facet along each arm
                if (thick.get()) {
                    double midX = ring.x + cos * radius * 0.48;
                    double midZ = ring.z + sin * radius * 0.48;
                    double leftX = midX + px * armHalfW;
                    double leftZ = midZ + pz * armHalfW;
                    double rightX = midX - px * armHalfW;
                    double rightZ = midZ - pz * armHalfW;

                    event.renderer.quad(
                        ring.x, y, ring.z,
                        leftX, y, leftZ,
                        tipX[arm], y, tipZ[arm],
                        rightX, y, rightZ,
                        cCore, cFacet, cTip, cFacet
                    );
                    event.renderer.line(ring.x, y, ring.z, leftX, y, leftZ, cTwig);
                    event.renderer.line(leftX, y, leftZ, tipX[arm], y, tipZ[arm], cTip);
                    event.renderer.line(ring.x, y, ring.z, rightX, y, rightZ, cTwig);
                    event.renderer.line(rightX, y, rightZ, tipX[arm], y, tipZ[arm], cTip);
                }

                // Bright central spine along the arm
                event.renderer.line(ring.x, y, ring.z, webX[arm], y, webZ[arm], cCore, cTip);
                event.renderer.line(webX[arm], y, webZ[arm], tipX[arm], y, tipZ[arm], cTip, cCore);

                // Two pairs of crystalline side branches
                twigs(event, ring.x, y, ring.z, angle, radius * 0.36, radius * 0.28 * twigA, cTwig);
                twigs(event, ring.x, y, ring.z, angle, radius * 0.70, radius * 0.20 * twigB, cTwig);

                // Diamond spearhead at the tip
                double forkLen = radius * 0.15 * fork;
                if (forkLen > 0.005) {
                    for (int side = -1; side <= 1; side += 2) {
                        double fa = angle + side * Math.toRadians(142);
                        event.renderer.line(tipX[arm], y, tipZ[arm],
                            tipX[arm] + Math.cos(fa) * forkLen, y, tipZ[arm] + Math.sin(fa) * forkLen, cCore, cTwig);
                    }
                }
            }

            // Hexagonal web linking the arms at 55 % radius + translucent inner hex star
            if (web > 0.02) {
                cWeb.a = clamp((int) (a * 0.50 * web));
                for (int k = 0; k < 6; k++) {
                    int n = (k + 1) % 6;
                    event.renderer.line(webX[k], y, webZ[k], webX[n], y, webZ[n], cWeb);
                }
            }

            // 3D glinting stars on the tips (horizontal + vertical diamond cross)
            if (sparkles.get()) {
                double star = radius * 0.11;
                for (int k = 0; k < 6; k++) {
                    double tw = 0.55 + 0.45 * Math.sin(now * 0.018 + k * 1.7);
                    cStar.set(255, 255, 255, clamp((int) (a * tw)));
                    event.renderer.line(tipX[k] - star, y, tipZ[k], tipX[k] + star, y, tipZ[k], cStar);
                    event.renderer.line(tipX[k], y, tipZ[k] - star, tipX[k], y, tipZ[k] + star, cStar);
                    event.renderer.line(tipX[k], y - star * 0.8, tipZ[k], tipX[k], y + star * 0.8, tipZ[k], cStar);
                }
            }

            // Inner glowing hexagonal core
            double hex = radius * 0.16;
            for (int k = 0; k < 6; k++) {
                double a1 = spin + Math.toRadians(30) + k * (Math.PI / 3.0);
                double a2 = a1 + Math.PI / 3.0;
                double x1 = ring.x + Math.cos(a1) * hex;
                double z1 = ring.z + Math.sin(a1) * hex;
                double x2 = ring.x + Math.cos(a2) * hex;
                double z2 = ring.z + Math.sin(a2) * hex;
                event.renderer.line(x1, y, z1, x2, y, z2, cCore);
            }

            // Outer shockwave ring with soft translucent glow ribbon
            if (rim.get()) {
                int n = segments.get();
                double rr = radius * 1.05;
                double rrIn = radius * 0.96;
                for (int k = 0; k < n; k++) {
                    double a1 = k * (2 * Math.PI / n);
                    double a2 = (k + 1) * (2 * Math.PI / n);
                    double c1 = Math.cos(a1), s1 = Math.sin(a1);
                    double c2 = Math.cos(a2), s2 = Math.sin(a2);

                    double ox1 = ring.x + c1 * rr, oz1 = ring.z + s1 * rr;
                    double ox2 = ring.x + c2 * rr, oz2 = ring.z + s2 * rr;
                    double ix1 = ring.x + c1 * rrIn, iz1 = ring.z + s1 * rrIn;
                    double ix2 = ring.x + c2 * rrIn, iz2 = ring.z + s2 * rrIn;

                    event.renderer.line(ox1, y, oz1, ox2, y, oz2, cRim);
                    event.renderer.quad(
                        ox1, y, oz1,
                        ox2, y, oz2,
                        ix2, y, iz2,
                        ix1, y, iz1,
                        cFacet, cFacet, cFacetOut, cFacetOut
                    );
                }
            }
        }
    }

    private void twigs(Render3DEvent event, double cx, double y, double cz, double angle,
                       double along, double length, Color c) {
        if (length < 0.005) return;
        double bx = cx + Math.cos(angle) * along;
        double bz = cz + Math.sin(angle) * along;
        for (int side = -1; side <= 1; side += 2) {
            double ta = angle + side * Math.toRadians(55);
            event.renderer.line(bx, y, bz, bx + Math.cos(ta) * length, y, bz + Math.sin(ta) * length, cCore, c);
        }
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
