package com.manu.frosted.util;

import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;

/**
 * Hardware-accelerated 2D drawing helpers built on Meteor's {@link Renderer2D#COLOR}.
 * Uses shared mutable {@link Color} instances to avoid per-vertex heap allocations.
 */
public final class Draw2D {
    private static final Color C0 = new Color();
    private static final Color C1 = new Color();
    private static final Color C2 = new Color();
    private static final Color C3 = new Color();

    private Draw2D() {}

    public static Color setColor(Color out, int argb) {
        out.a = (argb >>> 24) & 0xFF;
        out.r = (argb >>> 16) & 0xFF;
        out.g = (argb >>> 8) & 0xFF;
        out.b = argb & 0xFF;
        return out;
    }

    public static Color color(int argb) {
        return setColor(C0, argb);
    }

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Linear mix of two 0xRRGGBB colours. */
    public static int mix(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Linear mix of two 0xAARRGGBB colours. */
    public static int mixArgb(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int al = (int) Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int r = (int) Math.round(((a >>> 16) & 0xFF) + (((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >>> 8) & 0xFF) + (((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    /** Positive amount lightens toward white, negative darkens toward black. */
    public static int shade(int rgb, double amount) {
        return amount >= 0 ? mix(rgb, 0xFFFFFF, amount) : mix(rgb, 0x000000, -amount);
    }

    /** Emits a solid-coloured arbitrary quad to {@link Renderer2D#COLOR} (must be between begin/render). */
    public static void polyQuad(double x0, double y0, double x1, double y1,
                                double x2, double y2, double x3, double y3, int argb) {
        if ((argb >>> 24) == 0) return;
        MeshBuilder mb = Renderer2D.COLOR.triangles;
        mb.ensureQuadCapacity();
        Color c = setColor(C0, argb);
        int i0 = mb.vec2(x0, y0).color(c).next();
        int i1 = mb.vec2(x1, y1).color(c).next();
        int i2 = mb.vec2(x2, y2).color(c).next();
        int i3 = mb.vec2(x3, y3).color(c).next();
        mb.quad(i0, i1, i2, i3);
    }

    /** Emits a 4-color gradient arbitrary quad to {@link Renderer2D#COLOR} (must be between begin/render). */
    public static void polyQuadGrad(double x0, double y0, int c0,
                                    double x1, double y1, int c1,
                                    double x2, double y2, int c2,
                                    double x3, double y3, int c3) {
        if (((c0 | c1 | c2 | c3) >>> 24) == 0) return;
        MeshBuilder mb = Renderer2D.COLOR.triangles;
        mb.ensureQuadCapacity();
        int i0 = mb.vec2(x0, y0).color(setColor(C0, c0)).next();
        int i1 = mb.vec2(x1, y1).color(setColor(C1, c1)).next();
        int i2 = mb.vec2(x2, y2).color(setColor(C2, c2)).next();
        int i3 = mb.vec2(x3, y3).color(setColor(C3, c3)).next();
        mb.quad(i0, i1, i2, i3);
    }

    /** Emits a 3-color gradient triangle to {@link Renderer2D#COLOR}. */
    public static void triGrad(double x0, double y0, int c0,
                               double x1, double y1, int c1,
                               double x2, double y2, int c2) {
        if (((c0 | c1 | c2) >>> 24) == 0) return;
        MeshBuilder mb = Renderer2D.COLOR.triangles;
        mb.ensureTriCapacity();
        int i0 = mb.vec2(x0, y0).color(setColor(C0, c0)).next();
        int i1 = mb.vec2(x1, y1).color(setColor(C1, c1)).next();
        int i2 = mb.vec2(x2, y2).color(setColor(C2, c2)).next();
        mb.triangle(i0, i1, i2);
    }

    /** Emits a thick anti-aliased line segment (with optional soft glow) as quads. */
    public static void thickLine(double x0, double y0, double x1, double y1, double width, int argb) {
        if ((argb >>> 24) == 0) return;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        if (len < 1e-4) return;
        double nx = -dy / len * (width * 0.5);
        double ny = dx / len * (width * 0.5);
        polyQuad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    /** Emits a thick line with gradient colors from start to end. */
    public static void thickLineGrad(double x0, double y0, int c0,
                                     double x1, double y1, int c1, double width) {
        if (((c0 | c1) >>> 24) == 0) return;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        if (len < 1e-4) return;
        double nx = -dy / len * (width * 0.5);
        double ny = dx / len * (width * 0.5);
        polyQuadGrad(
            x0 + nx, y0 + ny, c0,
            x1 + nx, y1 + ny, c1,
            x1 - nx, y1 - ny, c1,
            x0 - nx, y0 - ny, c0
        );
    }

    /** Emits a 4-point glowing star with a soft diamond halo. */
    public static void star(double cx, double cy, double armLen, double thickness, int coreArgb, int glowArgb) {
        if (glowArgb != 0 && (glowArgb >>> 24) > 0) {
            double gr = armLen * 0.65;
            int trans = glowArgb & 0x00FFFFFF;
            triGrad(cx, cy, glowArgb, cx, cy - gr, trans, cx + gr, cy, trans);
            triGrad(cx, cy, glowArgb, cx + gr, cy, trans, cx, cy + gr, trans);
            triGrad(cx, cy, glowArgb, cx, cy + gr, trans, cx - gr, cy, trans);
            triGrad(cx, cy, glowArgb, cx - gr, cy, trans, cx, cy - gr, trans);
        }
        int tipTrans = coreArgb & 0x00FFFFFF;
        double w = thickness;
        // 4 tapered spikes
        triGrad(cx - w, cy, coreArgb, cx + w, cy, coreArgb, cx, cy - armLen, tipTrans);
        triGrad(cx - w, cy, coreArgb, cx + w, cy, coreArgb, cx, cy + armLen, tipTrans);
        triGrad(cx, cy - w, coreArgb, cx, cy + w, coreArgb, cx - armLen, cy, tipTrans);
        triGrad(cx, cy - w, coreArgb, cx, cy + w, coreArgb, cx + armLen, cy, tipTrans);
    }

    /**
     * Hardware-accelerated polygon fill via triangle fan on {@link Renderer2D#COLOR}.
     * If {@link Renderer2D#COLOR} is not currently building, starts and renders a batch automatically.
     */
    public static void fillPoly(DrawContext ctx, double[] xs, double[] ys, int n, int argb, double ox, double oy) {
        if (n < 3 || (argb >>> 24) == 0) return;
        MeshBuilder mb = Renderer2D.COLOR.triangles;
        boolean ownBatch = !mb.isBuilding();
        if (ownBatch) Renderer2D.COLOR.begin();

        mb.ensureCapacity(n, (n - 2) * 3);
        Color c = setColor(C0, argb);
        int first = mb.vec2(xs[0] + ox, ys[0] + oy).color(c).next();
        int prev = mb.vec2(xs[1] + ox, ys[1] + oy).color(c).next();
        for (int i = 2; i < n; i++) {
            int curr = mb.vec2(xs[i] + ox, ys[i] + oy).color(c).next();
            mb.triangle(first, prev, curr);
            prev = curr;
        }

        if (ownBatch) Renderer2D.COLOR.render();
    }
}
