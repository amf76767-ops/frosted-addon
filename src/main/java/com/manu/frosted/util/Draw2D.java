package com.manu.frosted.util;

import net.minecraft.client.gui.DrawContext;

/** Small 2D helpers on top of DrawContext.fill (GUI-scaled pixels). */
public final class Draw2D {
    private static final double[] HITS = new double[16];

    private Draw2D() {}

    /**
     * Scanline fill of a polygon. Works for convex and concave shapes, drawn as
     * one-pixel-high spans. (ox, oy) shifts the whole polygon, handy for shadows.
     */
    public static void fillPoly(DrawContext ctx, double[] xs, double[] ys, int n, int argb, double ox, double oy) {
        if (n < 3 || (argb >>> 24) == 0) return;

        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }

        int y0 = (int) Math.floor(minY + oy);
        int y1 = (int) Math.ceil(maxY + oy);

        for (int y = y0; y < y1; y++) {
            double yc = y + 0.5 - oy;
            int hits = 0;

            for (int i = 0, j = n - 1; i < n; j = i++) {
                double yi = ys[i];
                double yj = ys[j];
                if ((yi <= yc && yj > yc) || (yj <= yc && yi > yc)) {
                    if (hits >= HITS.length) break;
                    HITS[hits++] = xs[i] + (yc - yi) / (yj - yi) * (xs[j] - xs[i]);
                }
            }

            for (int a = 1; a < hits; a++) {
                double v = HITS[a];
                int b = a - 1;
                while (b >= 0 && HITS[b] > v) {
                    HITS[b + 1] = HITS[b];
                    b--;
                }
                HITS[b + 1] = v;
            }

            for (int a = 0; a + 1 < hits; a += 2) {
                int x0 = (int) Math.round(HITS[a] + ox);
                int x1 = (int) Math.round(HITS[a + 1] + ox);
                if (x1 > x0) ctx.fill(x0, y, x1, y + 1, argb);
            }
        }
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

    /** Positive amount lightens toward white, negative darkens toward black. */
    public static int shade(int rgb, double amount) {
        return amount >= 0 ? mix(rgb, 0xFFFFFF, amount) : mix(rgb, 0x000000, -amount);
    }
}
