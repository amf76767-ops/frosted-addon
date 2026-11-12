package com.manu.frosted.util;

/**
 * Shared frosted-glass panel used by the HUD and the region map.
 * Must be called between Renderer2D.COLOR.begin() and render(). Coordinates are framebuffer pixels.
 */
public final class FrostedPanel {
    private FrostedPanel() {}

    public static void draw(double x, double y, double w, double h, double r, double s,
                            int alpha, int accent, boolean shimmer, boolean snow, boolean glow) {
        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        int accRgb = accent & 0xFFFFFF;

        // soft outer glow (three expanding layers)
        if (glow) {
            double pulse = 0.75 + 0.25 * Math.sin(time * 1.6 + x * 0.01);
            for (int i = 3; i >= 1; i--) {
                double g = i * 2.2 * s;
                int a = (int) (alpha * 0.07 * pulse * (4 - i));
                Draw2D.roundRect(x - g, y - g, w + g * 2, h + g * 2, r + g, Draw2D.argb(a, accRgb), Draw2D.argb(a / 2, accRgb));
            }
        }

        // drop shadow
        Draw2D.roundRect(x + 2 * s, y + 3 * s, w, h, r, Draw2D.argb(alpha * 2 / 5, 0x02050C), Draw2D.argb(alpha / 2, 0x02050C));

        // frosted body: icy top fading into deep navy
        Draw2D.roundRect(x, y, w, h, r, Draw2D.argb(alpha, 0x1C365E), Draw2D.argb(alpha, 0x0A1428));

        // glassy highlight on the upper half
        double hl = Math.min(h * 0.45, 14 * s);
        Draw2D.rectV(x + r * 0.6, y + s, w - r * 1.2, hl, Draw2D.argb(alpha / 7, 0xFFFFFF), Draw2D.argb(0, 0xFFFFFF));

        // accent bar on the left edge
        Draw2D.rectV(x, y + r, Math.max(1.5, 2 * s), Math.max(0, h - r * 2), Draw2D.argb(235, accRgb), Draw2D.argb(120, Draw2D.shade(accRgb, -0.3)));

        // thin icy rim along the bottom
        Draw2D.rectH(x + r, y + h - Math.max(1, s), Math.max(0, w - r * 2), Math.max(1, s),
            Draw2D.argb(0, 0x9CD8FF), Draw2D.argb(alpha * 2 / 3, 0x9CD8FF));

        if (shimmer) {
            double span = Math.max(0, w - r * 2);
            double cycle = 3.2;
            double phase = ((time + x * 0.0037) % cycle) / cycle;
            double gw = 26 * s;
            double gx = phase * (span + gw * 2) - gw;
            double x0 = Math.max(0, gx), x1 = Math.min(span, gx + gw);
            if (x1 > x0) {
                double xm = Math.max(x0, Math.min(x1, gx + gw * 0.5));
                double yy = y + s * 0.5;
                double hh = Math.max(1.5, 1.6 * s);
                Draw2D.rectH(x + r + x0, yy, xm - x0, hh, Draw2D.argb(0, 0xFFFFFF), Draw2D.argb(220, 0xFFFFFF));
                Draw2D.rectH(x + r + xm, yy, x1 - xm, hh, Draw2D.argb(220, 0xFFFFFF), Draw2D.argb(0, 0xFFFFFF));
            }
        }

        if (snow) {
            int snowC = Draw2D.argb(240, 0xF6FBFF);
            double start = x + Math.max(r, 3 * s);
            double end = x + w - Math.max(r, 3 * s);
            int idx = 0;
            for (double sx = start; sx < end; sx += 3.5 * s, idx++) {
                int hsh = (idx * 37 + 11) & 7;
                double cw = Math.min((2.5 + (hsh & 1)) * s, end - sx);
                double ch = (1 + (hsh >> 1) % 3) * s * 0.8;
                if (cw <= 0) continue;
                Draw2D.roundRect(sx, y - ch * 0.35, cw, ch, ch * 0.5, snowC, snowC);
            }
        }
    }

    /** Horizontal progress bar with a dark track and a glowing fill. */
    public static void bar(double x, double y, double w, double h, double frac, int rgb) {
        frac = Math.max(0, Math.min(1, frac));
        Draw2D.roundRect(x, y, w, h, h * 0.5, Draw2D.argb(150, 0x050A14), Draw2D.argb(150, 0x050A14));
        if (frac > 0.001) {
            Draw2D.roundRect(x, y, w * frac, h, h * 0.5, Draw2D.argb(255, Draw2D.shade(rgb, 0.25)), Draw2D.argb(255, Draw2D.shade(rgb, -0.2)));
        }
    }
}
