package com.manu.frosted.modules.visual.knife;

import com.manu.frosted.util.Draw2D;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a knife as shaded polygons with DrawContext. The blade is built from slices
 * along its profile, so curved and tapered shapes (karambit, bayonet clip point) come
 * out as real outlines instead of a row of dots.
 */
public final class KnifeRenderer {
    private static final int SHADOW = 0x44081028;
    private static final double HINGE_U = -2.5;

    private KnifeRenderer() {}

    /**
     * @param ox,oy        guard position in GUI pixels
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

    private static final class Poly {
        final double[] xs;
        final double[] ys;
        final int argb;

        Poly(double[] xs, double[] ys, int argb) {
            this.xs = xs;
            this.ys = ys;
            this.argb = argb;
        }
    }

    private static final class Xf {
        final double ox, oy, dx, dy, px, py, k, cosS, sinS, pivot, roll;

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
            return ox + (dx * ru + px * rv) * k;
        }

        double y(double u, double v) {
            double lu = u - pivot;
            double lv = v * roll;
            double ru = lu * cosS - lv * sinS + pivot;
            double rv = lu * sinS + lv * cosS;
            return oy + (dy * ru + py * rv) * k;
        }
    }

    public static void draw(DrawContext ctx, KnifeStyle style, KnifeFinish finish, Pose p) {
        Xf xf = new Xf(p, -style.handleLen * 0.5);
        List<Poly> polys = new ArrayList<>(240);

        int handleBase = finish.handle(p.customHandle());
        int accent = finish.accent();

        addBlade(polys, xf, style, finish, p);
        addHandle(polys, xf, style, p, handleBase, accent);

        if (p.shadow()) {
            double off = Math.max(2.0, 3.0 * p.k());
            for (Poly q : polys) Draw2D.fillPoly(ctx, q.xs, q.ys, q.xs.length, SHADOW, off, off * 1.3);
        }
        for (Poly q : polys) Draw2D.fillPoly(ctx, q.xs, q.ys, q.xs.length, q.argb, 0, 0);

        if (p.sparkles()) addSparkles(ctx, xf, style, p);
    }

    // ---------------------------------------------------------------- blade

    private static void addBlade(List<Poly> out, Xf xf, KnifeStyle style, KnifeFinish finish, Pose p) {
        int slices = 38;
        for (int i = 0; i < slices; i++) {
            double t0 = i / (double) slices;
            double t1 = (i + 1) / (double) slices;
            double u0 = t0 * style.bladeLen;
            double u1 = t1 * style.bladeLen;
            double s0 = style.spine.applyAsDouble(t0);
            double s1 = style.spine.applyAsDouble(t1);
            double e0 = style.edge.applyAsDouble(t0);
            double e1 = style.edge.applyAsDouble(t1);

            double tm = (t0 + t1) * 0.5;
            int base = finish.blade(tm, p.time(), p.customBlade());
            base = Draw2D.mix(base, 0xFFFFFF, glintAt(tm, p.time(), p.glint()) * 0.7);

            int top = Draw2D.shade(base, -0.18);
            int low = Draw2D.shade(base, 0.08);
            int rim = Draw2D.shade(base, 0.45);

            double m0 = s0 + (e0 - s0) * 0.45;
            double m1 = s1 + (e1 - s1) * 0.45;

            quad(out, xf, opaque(top), u0, s0, u1, s1, u1, m1, u0, m0);
            quad(out, xf, opaque(low), u0, m0, u1, m1, u1, e1, u0, e0);

            double r0 = e0 - (e0 - s0) * 0.16;
            double r1 = e1 - (e1 - s1) * 0.16;
            quad(out, xf, opaque(rim), u0, r0, u1, r1, u1, e1, u0, e0);

            if (style.has(KnifeFlags.FULLER) && t0 > 0.06 && t1 < 0.82) {
                double f0a = s0 + (e0 - s0) * 0.30;
                double f0b = s0 + (e0 - s0) * 0.38;
                double f1a = s1 + (e1 - s1) * 0.30;
                double f1b = s1 + (e1 - s1) * 0.38;
                quad(out, xf, opaque(Draw2D.shade(base, -0.30)), u0, f0a, u1, f1a, u1, f1b, u0, f0b);
            }
        }
    }

    private static double glintAt(double t, double time, double boost) {
        double speed = 0.5 + 1.8 * boost;
        double pos = (time * speed) % 2.6 - 0.6;
        double g = Math.max(0.0, 1.0 - Math.abs(t - pos) / 0.11);
        return Math.max(g, boost * 0.15);
    }

    // --------------------------------------------------------------- handle

    private static void addHandle(List<Poly> out, Xf xf, KnifeStyle style, Pose p, int base, int accent) {
        if (style.has(KnifeFlags.BUTTERFLY)) {
            double hw = style.handleW * 0.5;
            handleBar(out, xf, base, accent, HINGE_U, style.handleLen, hw * 1.05, hw, 0.0);
            handleBar(out, xf, base, accent, HINGE_U, style.handleLen, -hw * 1.05, hw, p.handleFlip());
        } else if (style.has(KnifeFlags.PUSH)) {
            // push dagger: a cross bar behind the blade and a short stem
            quad(out, xf, opaque(Draw2D.shade(base, 0.10)), -3.0, -14.0, -3.0, 14.0, -9.0, 14.0, -9.0, -14.0);
            quad(out, xf, opaque(accent), -3.0, -14.0, -3.0, 14.0, -4.0, 14.0, -4.0, -14.0);
            handleBar(out, xf, base, accent, -9.0, style.handleLen - 6.0, 0.0, style.handleW * 0.7, 0.0);
        } else {
            handleBar(out, xf, base, accent, HINGE_U, style.handleLen, 0.0, style.handleW, 0.0);
        }

        if (style.guardW > 0 && !style.has(KnifeFlags.PUSH)) {
            double gw = style.guardW;
            quad(out, xf, opaque(Draw2D.shade(base, 0.14)), 0.8, -gw, 0.8, gw, -3.2, gw, -3.2, -gw);
            quad(out, xf, opaque(accent), 1.9, -gw * 0.92, 1.9, gw * 0.92, 0.8, gw * 0.92, 0.8, -gw * 0.92);
        }

        double end = -style.handleLen - 2.5;
        if (style.has(KnifeFlags.RING)) {
            double cu = end - 7.0;
            double outer = 10.5;
            double inner = 6.8;
            int segs = 20;
            for (int i = 0; i < segs; i++) {
                double a0 = i * 2.0 * Math.PI / segs;
                double a1 = (i + 1) * 2.0 * Math.PI / segs;
                int color = opaque(i % 2 == 0 ? base : Draw2D.shade(base, 0.10));
                quad(out, xf, color,
                    cu + outer * Math.cos(a0), outer * Math.sin(a0),
                    cu + outer * Math.cos(a1), outer * Math.sin(a1),
                    cu + inner * Math.cos(a1), inner * Math.sin(a1),
                    cu + inner * Math.cos(a0), inner * Math.sin(a0));
            }
        } else if (!style.has(KnifeFlags.BUTTERFLY) && !style.has(KnifeFlags.PUSH)) {
            double r = style.handleW * 0.9;
            double[] us = new double[8];
            double[] vs = new double[8];
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                us[i] = end - r * 0.35 + r * Math.cos(a) * 0.55;
                vs[i] = r * Math.sin(a);
            }
            poly(out, xf, opaque(Draw2D.shade(base, 0.18)), us, vs);
        }
    }

    /** A grip made of slices; hw is the half width, vc the offset across, rot a rotation around the hinge. */
    private static void handleBar(List<Poly> out, Xf xf, int base, int accent,
                                  double startU, double length, double vc, double hw, double rot) {
        int slices = 10;
        double step = length / slices;
        for (int i = 0; i < slices; i++) {
            double u0 = startU - i * step;
            double u1 = startU - (i + 1) * step;
            double w = hw * (0.9 + 0.1 * Math.sin(Math.PI * (i + 0.5) / slices));
            int body = opaque(i % 2 == 0 ? base : Draw2D.shade(base, 0.08));

            rotQuad(out, xf, body, rot, startU, u0, u1, vc - w, vc + w);
            rotQuad(out, xf, opaque(Draw2D.shade(base, 0.22)), rot, startU, u0, u1, vc - w, vc - w * 0.45);
            rotQuad(out, xf, opaque(accent), rot, startU, u0, u1, vc + w * 0.55, vc + w * 0.82);
        }
    }

    // -------------------------------------------------------------- sparkles

    private static void addSparkles(DrawContext ctx, Xf xf, KnifeStyle style, Pose p) {
        for (int i = 0; i < 3; i++) {
            double t = 0.35 + i * 0.28;
            double a = Math.sin(p.time() * 3.0 + i * 2.1);
            if (a < 0.55) continue;

            double mid = (style.spine.applyAsDouble(t) + style.edge.applyAsDouble(t)) * 0.5;
            int x = (int) Math.round(xf.x(t * style.bladeLen, mid));
            int y = (int) Math.round(xf.y(t * style.bladeLen, mid));
            int alpha = (int) (255 * (a - 0.55) / 0.45);
            int color = Draw2D.argb(alpha, 0xFFFFFF);
            int arm = Math.max(2, (int) (p.k() * 2.5));

            ctx.fill(x - arm, y, x + arm + 1, y + 1, color);
            ctx.fill(x, y - arm, x + 1, y + arm + 1, color);
        }
    }

    // --------------------------------------------------------------- helpers

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static void poly(List<Poly> out, Xf xf, int argb, double[] us, double[] vs) {
        int n = us.length;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = xf.x(us[i], vs[i]);
            ys[i] = xf.y(us[i], vs[i]);
        }
        out.add(new Poly(xs, ys, argb));
    }

    private static void quad(List<Poly> out, Xf xf, int argb,
                             double u1, double v1, double u2, double v2,
                             double u3, double v3, double u4, double v4) {
        poly(out, xf, argb, new double[] {u1, u2, u3, u4}, new double[] {v1, v2, v3, v4});
    }

    /** Rectangle u0..u1 by v0..v1, rotated by rot around the point (hingeU, 0). */
    private static void rotQuad(List<Poly> out, Xf xf, int argb, double rot, double hingeU,
                                double u0, double u1, double v0, double v1) {
        double c = Math.cos(rot);
        double s = Math.sin(rot);
        double[] us = {u0, u1, u1, u0};
        double[] vs = {v0, v0, v1, v1};
        for (int i = 0; i < 4; i++) {
            double du = us[i] - hingeU;
            double dv = vs[i];
            us[i] = hingeU + du * c - dv * s;
            vs[i] = du * s + dv * c;
        }
        poly(out, xf, argb, us, vs);
    }
}
