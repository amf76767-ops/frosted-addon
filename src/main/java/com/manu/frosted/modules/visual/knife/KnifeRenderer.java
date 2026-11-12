package com.manu.frosted.modules.visual.knife;

import com.manu.frosted.util.Draw2D;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import net.minecraft.client.gui.DrawContext;

/**
 * Hardware-accelerated CS knife renderer using {@link Renderer2D#COLOR}.
 *
 * Emits smooth per-vertex shaded triangles and quads directly to the GPU mesh buffer
 * with zero per-frame heap allocations and zero CPU scanline rasterization.
 */
public final class KnifeRenderer {
    private static final int SHADOW_CORE = 0x52060C1E;
    private static final double HINGE_U = -2.5;

    private KnifeRenderer() {}

    /**
     * @param ox,oy        guard position in screen pixels
     * @param tilt         degrees above the horizontal
     * @param spinDeg      extra in-plane rotation around the middle of the handle
     * @param roll         cosine of the roll angle, scales the knife across its width (negative flips it)
     * @param k            pixels per knife unit
     * @param handleFlip   radians of the butterfly's loose handle half
     * @param glint        0..1, how fast and bright the shine sweeps
     */
    public record Pose(double ox, double oy, double tilt, double spinDeg, double roll, double k,
                       boolean mirror, double time, double glint, double handleFlip,
                       int customBlade, int customHandle, boolean shadow, boolean sparkles) {}

    private static final class Xf {
        final double ox, oy, dx, dy, px, py, k, cosS, sinS, pivot, roll;
        double sx, sy;

        Xf(Pose p, double pivotU) {
            double rad = Math.toRadians(p.tilt());
            this.dx = (p.mirror() ? 1.0 : -1.0) * Math.cos(rad);
            this.dy = -Math.sin(rad);
            this.px = -dy;
            this.py = dx;
            this.ox = p.ox();
            this.oy = p.oy();
            this.k = p.k();
            double s = Math.toRadians(p.spinDeg());
            this.cosS = Math.cos(s);
            this.sinS = Math.sin(s);
            this.pivot = pivotU;
            this.roll = p.roll();
        }

        double x(double u, double v) {
            double lu = u - pivot;
            double lv = v * roll;
            double ru = lu * cosS - lv * sinS + pivot;
            double rv = lu * sinS + lv * cosS;
            return ox + (dx * ru + px * rv) * k + sx;
        }

        double y(double u, double v) {
            double lu = u - pivot;
            double lv = v * roll;
            double ru = lu * cosS - lv * sinS + pivot;
            double rv = lu * sinS + lv * cosS;
            return oy + (dy * ru + py * rv) * k + sy;
        }
    }

    public static void draw(DrawContext ctx, KnifeStyle style, KnifeFinish finish, Pose p) {
        boolean ownBatch = !Renderer2D.COLOR.triangles.isBuilding();
        if (ownBatch) Renderer2D.COLOR.begin();

        Xf xf = new Xf(p, -style.handleLen * 0.5);
        int handleBase = finish.handle(p.customHandle());
        int accent = finish.accent();

        if (p.shadow()) {
            double off = Math.max(2.5, 3.2 * p.k());
            xf.sx = off;
            xf.sy = off * 1.35;
            emitShadow(xf, style, p);
            xf.sx = 0;
            xf.sy = 0;
        }

        emitBlade(xf, style, finish, p, accent);
        emitHandle(xf, style, p, handleBase, accent);

        if (p.sparkles()) {
            emitSparkles(xf, style, finish, p);
        }

        if (ownBatch) Renderer2D.COLOR.render();
    }

    // ---------------------------------------------------------------- shadow

    private static void emitShadow(Xf xf, KnifeStyle style, Pose p) {
        int slices = 18;
        for (int i = 0; i < slices; i++) {
            double t0 = i / (double) slices;
            double t1 = (i + 1) / (double) slices;
            double u0 = t0 * style.bladeLen;
            double u1 = t1 * style.bladeLen;
            double s0 = style.spine.applyAsDouble(t0);
            double s1 = style.spine.applyAsDouble(t1);
            double e0 = style.edge.applyAsDouble(t0);
            double e1 = style.edge.applyAsDouble(t1);
            quad(xf, SHADOW_CORE, u0, s0, u1, s1, u1, e1, u0, e0);
        }

        if (style.has(KnifeFlags.BUTTERFLY)) {
            double hw = style.handleW * 0.5;
            rotQuad(xf, SHADOW_CORE, 0.0, HINGE_U, HINGE_U, HINGE_U - style.handleLen, hw * 0.05, hw * 2.05);
            rotQuad(xf, SHADOW_CORE, p.handleFlip(), HINGE_U, HINGE_U, HINGE_U - style.handleLen, -hw * 2.05, -hw * 0.05);
        } else {
            double hw = style.handleW;
            quad(xf, SHADOW_CORE, HINGE_U, -hw, HINGE_U - style.handleLen, -hw, HINGE_U - style.handleLen, hw, HINGE_U, hw);
        }
    }

    // ---------------------------------------------------------------- blade

    private static void emitBlade(Xf xf, KnifeStyle style, KnifeFinish finish, Pose p, int accent) {
        int slices = 32;
        int glowEdge = Draw2D.argb(42, accent);
        int glowOuter = Draw2D.argb(0, accent);

        for (int i = 0; i < slices; i++) {
            double t0 = i / (double) slices;
            double t1 = (i + 1) / (double) slices;
            double u0 = t0 * style.bladeLen;
            double u1 = t1 * style.bladeLen;
            double s0 = style.spine.applyAsDouble(t0);
            double s1 = style.spine.applyAsDouble(t1);
            double e0 = style.edge.applyAsDouble(t0);
            double e1 = style.edge.applyAsDouble(t1);

            int c0 = bladeColorAt(finish, t0, p);
            int c1 = bladeColorAt(finish, t1, p);

            // 4 profile zones across the blade: spine bevel -> upper flat -> hollow grind -> razor edge
            int spine0 = opaque(Draw2D.shade(c0, -0.34));
            int spine1 = opaque(Draw2D.shade(c1, -0.34));
            int flat0 = opaque(Draw2D.shade(c0, -0.12));
            int flat1 = opaque(Draw2D.shade(c1, -0.12));
            int ridge0 = opaque(Draw2D.shade(c0, 0.18));
            int ridge1 = opaque(Draw2D.shade(c1, 0.18));
            int grind0 = opaque(Draw2D.shade(c0, 0.04));
            int grind1 = opaque(Draw2D.shade(c1, 0.04));
            int edge0 = opaque(Draw2D.shade(c0, 0.56));
            int edge1 = opaque(Draw2D.shade(c1, 0.56));

            double sp0 = s0 + (e0 - s0) * 0.14;
            double sp1 = s1 + (e1 - s1) * 0.14;
            double m0 = s0 + (e0 - s0) * 0.46;
            double m1 = s1 + (e1 - s1) * 0.46;
            double r0 = e0 - (e0 - s0) * 0.18;
            double r1 = e1 - (e1 - s1) * 0.18;

            // Soft icy aura extending slightly past the cutting edge
            double aura0 = e0 + (e0 - s0) * 0.16;
            double aura1 = e1 + (e1 - s1) * 0.16;
            quadGrad(xf, u0, e0, glowEdge, u1, e1, glowEdge, u1, aura1, glowOuter, u0, aura0, glowOuter);

            // Spine bevel
            quadGrad(xf, u0, s0, spine0, u1, s1, spine1, u1, sp1, flat1, u0, sp0, flat0);
            // Upper flat to center ridge
            quadGrad(xf, u0, sp0, flat0, u1, sp1, flat1, u1, m1, ridge1, u0, m0, ridge0);
            // Center ridge to bevel line
            quadGrad(xf, u0, m0, ridge0, u1, m1, ridge1, u1, r1, grind1, u0, r0, grind0);
            // Razor cutting edge
            quadGrad(xf, u0, r0, grind0, u1, r1, grind1, u1, e1, edge1, u0, e0, edge0);

            // Recessed fuller groove with metallic accent core
            if (style.has(KnifeFlags.FULLER) && t0 > 0.06 && t1 < 0.80) {
                double f0a = s0 + (e0 - s0) * 0.28;
                double f0b = s0 + (e0 - s0) * 0.37;
                double f1a = s1 + (e1 - s1) * 0.28;
                double f1b = s1 + (e1 - s1) * 0.37;
                int fullerDark0 = opaque(Draw2D.shade(c0, -0.45));
                int fullerDark1 = opaque(Draw2D.shade(c1, -0.45));
                int fullerGlow0 = opaque(Draw2D.mix(Draw2D.shade(c0, -0.22), accent, 0.35));
                int fullerGlow1 = opaque(Draw2D.mix(Draw2D.shade(c1, -0.22), accent, 0.35));
                quadGrad(xf, u0, f0a, fullerDark0, u1, f1a, fullerDark1, u1, f1b, fullerGlow1, u0, f0b, fullerGlow0);
            }
        }
    }

    private static int bladeColorAt(KnifeFinish finish, double t, Pose p) {
        int base = finish.blade(t, p.time(), p.customBlade());
        double glint = glintAt(t, p.time(), p.glint());
        return Draw2D.mix(base, 0xFFFFFF, glint * 0.78);
    }

    private static double glintAt(double t, double time, double boost) {
        double speed = 0.55 + 1.9 * boost;
        double pos = (time * speed) % 2.6 - 0.6;
        double primary = Math.max(0.0, 1.0 - Math.abs(t - pos) / 0.13);
        double secondary = Math.max(0.0, 1.0 - Math.abs(t - (pos - 0.22)) / 0.07) * 0.45;
        return Math.max(Math.max(primary * primary, secondary), boost * 0.16);
    }

    // --------------------------------------------------------------- handle

    private static void emitHandle(Xf xf, KnifeStyle style, Pose p, int base, int accent) {
        if (style.has(KnifeFlags.BUTTERFLY)) {
            double hw = style.handleW * 0.5;
            handleBar(xf, base, accent, HINGE_U, style.handleLen, hw * 1.08, hw, 0.0);
            handleBar(xf, base, accent, HINGE_U, style.handleLen, -hw * 1.08, hw, p.handleFlip());
        } else if (style.has(KnifeFlags.PUSH)) {
            int top = opaque(Draw2D.shade(base, 0.18));
            int bot = opaque(Draw2D.shade(base, -0.18));
            quadGrad(xf, -2.8, -14.5, top, -2.8, 14.5, bot, -9.2, 14.5, bot, -9.2, -14.5, top);
            quad(xf, opaque(accent), -2.8, -14.0, -2.8, 14.0, -4.1, 14.0, -4.1, -14.0);
            handleBar(xf, base, accent, -9.2, style.handleLen - 6.0, 0.0, style.handleW * 0.72, 0.0);
        } else {
            handleBar(xf, base, accent, HINGE_U, style.handleLen, 0.0, style.handleW, 0.0);
        }

        if (style.guardW > 0 && !style.has(KnifeFlags.PUSH)) {
            double gw = style.guardW;
            int gTop = opaque(Draw2D.shade(base, 0.28));
            int gBot = opaque(Draw2D.shade(base, -0.15));
            quadGrad(xf, 1.0, -gw, gTop, 1.0, gw, gBot, -3.4, gw, gBot, -3.4, -gw, gTop);
            quad(xf, opaque(accent), 2.1, -gw * 0.90, 2.1, gw * 0.90, 0.9, gw * 0.90, 0.9, -gw * 0.90);
        }

        double end = -style.handleLen - 2.5;
        if (style.has(KnifeFlags.RING)) {
            double cu = end - 7.0;
            double outer = 10.6;
            double mid = 8.6;
            double inner = 6.7;
            int segs = 24;
            int cOuter = opaque(Draw2D.shade(base, -0.15));
            int cMid = opaque(Draw2D.mix(Draw2D.shade(base, 0.25), accent, 0.35));
            int cInner = opaque(Draw2D.shade(base, -0.25));
            for (int i = 0; i < segs; i++) {
                double a0 = i * 2.0 * Math.PI / segs;
                double a1 = (i + 1) * 2.0 * Math.PI / segs;
                double c0 = Math.cos(a0), s0 = Math.sin(a0);
                double c1 = Math.cos(a1), s1 = Math.sin(a1);
                quadGrad(xf,
                    cu + outer * c0, outer * s0, cOuter,
                    cu + outer * c1, outer * s1, cOuter,
                    cu + mid * c1, mid * s1, cMid,
                    cu + mid * c0, mid * s0, cMid);
                quadGrad(xf,
                    cu + mid * c0, mid * s0, cMid,
                    cu + mid * c1, mid * s1, cMid,
                    cu + inner * c1, inner * s1, cInner,
                    cu + inner * c0, inner * s0, cInner);
            }
        } else if (!style.has(KnifeFlags.BUTTERFLY) && !style.has(KnifeFlags.PUSH)) {
            double r = style.handleW * 0.92;
            double cu = end - r * 0.35;
            int pommelCore = opaque(Draw2D.shade(base, 0.24));
            int pommelRim = opaque(Draw2D.shade(base, -0.15));
            int segs = 10;
            for (int i = 0; i < segs; i++) {
                double a0 = i * 2.0 * Math.PI / segs;
                double a1 = (i + 1) * 2.0 * Math.PI / segs;
                Draw2D.triGrad(
                    xf.x(cu, 0), xf.y(cu, 0), pommelCore,
                    xf.x(cu + r * Math.cos(a0) * 0.58, r * Math.sin(a0)),
                    xf.y(cu + r * Math.cos(a0) * 0.58, r * Math.sin(a0)), pommelRim,
                    xf.x(cu + r * Math.cos(a1) * 0.58, r * Math.sin(a1)),
                    xf.y(cu + r * Math.cos(a1) * 0.58, r * Math.sin(a1)), pommelRim
                );
            }
        }
    }

    private static void handleBar(Xf xf, int base, int accent,
                                  double startU, double length, double vc, double hw, double rot) {
        int slices = 10;
        double step = length / slices;
        int hi = opaque(Draw2D.shade(base, 0.24));
        int lo = opaque(Draw2D.shade(base, -0.22));
        int acc = opaque(accent);

        for (int i = 0; i < slices; i++) {
            double u0 = startU - i * step;
            double u1 = startU - (i + 1) * step;
            double w0 = hw * (0.9 + 0.12 * Math.sin(Math.PI * i / slices));
            double w1 = hw * (0.9 + 0.12 * Math.sin(Math.PI * (i + 1) / slices));
            int mid = opaque(i % 2 == 0 ? base : Draw2D.shade(base, 0.09));

            rotQuadTapered(xf, hi, mid, rot, startU, u0, u1, vc - w0, vc, vc - w1, vc);
            rotQuadTapered(xf, mid, lo, rot, startU, u0, u1, vc, vc + w0, vc, vc + w1);
            rotQuadTapered(xf, acc, acc, rot, startU, u0, u1, vc + w0 * 0.52, vc + w0 * 0.78, vc + w1 * 0.52, vc + w1 * 0.78);
        }
    }

    // -------------------------------------------------------------- sparkles

    private static void emitSparkles(Xf xf, KnifeStyle style, KnifeFinish finish, Pose p) {
        int accent = finish.accent();
        for (int i = 0; i < 4; i++) {
            double t = 0.22 + i * 0.24;
            double wave = Math.sin(p.time() * 3.2 + i * 1.9);
            if (wave < 0.35) continue;

            double intensity = (wave - 0.35) / 0.65;
            double edgeV = style.edge.applyAsDouble(t);
            double spineV = style.spine.applyAsDouble(t);
            double v = spineV + (edgeV - spineV) * 0.72;
            double x = xf.x(t * style.bladeLen, v);
            double y = xf.y(t * style.bladeLen, v);

            int coreAlpha = (int) Math.round(255 * intensity);
            int glowAlpha = (int) Math.round(135 * intensity);
            double arm = Math.max(3.5, p.k() * (3.2 + 2.4 * intensity));
            double thick = Math.max(1.0, p.k() * 0.85);
            Draw2D.star(x, y, arm, thick, Draw2D.argb(coreAlpha, 0xFFFFFF), Draw2D.argb(glowAlpha, accent));
        }
    }

    // --------------------------------------------------------------- helpers

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static void quad(Xf xf, int argb,
                             double u1, double v1, double u2, double v2,
                             double u3, double v3, double u4, double v4) {
        Draw2D.polyQuad(
            xf.x(u1, v1), xf.y(u1, v1),
            xf.x(u2, v2), xf.y(u2, v2),
            xf.x(u3, v3), xf.y(u3, v3),
            xf.x(u4, v4), xf.y(u4, v4),
            argb
        );
    }

    private static void quadGrad(Xf xf,
                                 double u1, double v1, int c1,
                                 double u2, double v2, int c2,
                                 double u3, double v3, int c3,
                                 double u4, double v4, int c4) {
        Draw2D.polyQuadGrad(
            xf.x(u1, v1), xf.y(u1, v1), c1,
            xf.x(u2, v2), xf.y(u2, v2), c2,
            xf.x(u3, v3), xf.y(u3, v3), c3,
            xf.x(u4, v4), xf.y(u4, v4), c4
        );
    }

    private static void rotQuad(Xf xf, int argb, double rot, double hingeU,
                                double u0, double u1, double v0, double v1) {
        rotQuadTapered(xf, argb, argb, rot, hingeU, u0, u1, v0, v1, v0, v1);
    }

    private static void rotQuadTapered(Xf xf, int cTop, int cBot, double rot, double hingeU,
                                       double u0, double u1, double v0a, double v0b, double v1a, double v1b) {
        double c = Math.cos(rot);
        double s = Math.sin(rot);

        double du0 = u0 - hingeU;
        double du1 = u1 - hingeU;

        double ru0a = hingeU + du0 * c - v0a * s;
        double rv0a = du0 * s + v0a * c;
        double ru1a = hingeU + du1 * c - v1a * s;
        double rv1a = du1 * s + v1a * c;
        double ru1b = hingeU + du1 * c - v1b * s;
        double rv1b = du1 * s + v1b * c;
        double ru0b = hingeU + du0 * c - v0b * s;
        double rv0b = du0 * s + v0b * c;

        quadGrad(xf, ru0a, rv0a, cTop, ru1a, rv1a, cTop, ru1b, rv1b, cBot, ru0b, rv0b, cBot);
    }
}
