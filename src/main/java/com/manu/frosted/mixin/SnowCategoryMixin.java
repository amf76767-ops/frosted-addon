package com.manu.frosted.mixin;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frosts the category headers (every window whose title starts with "Frosted").
 *
 * Runs at the tail of WWindow.render, after the header child has been drawn, so
 * the effects sit on top of it. Everything is time based, so the header is
 * alive: snow drifts down, sparkles twinkle and a glint sweeps along the lower
 * edge. If anything goes wrong once, the effect switches itself off instead of
 * spamming the log every frame.
 */
@Mixin(WWindow.class)
public abstract class SnowCategoryMixin {
    @Shadow protected String title;

    private static boolean frosted$failed;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void frosted$snowHeader(GuiRenderer renderer, double mouseX, double mouseY, double delta,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (frosted$failed || title == null || !title.startsWith("Frosted")) return;

        try {
            WWidget self = (WWidget) (Object) this;
            frosted$paint(renderer, self.x, self.y, self.width, frosted$headerHeight(self), title.hashCode());
            frosted$frame(renderer, self.x, self.y, self.width, self.height, title.hashCode());
        } catch (Throwable t) {
            frosted$failed = true;
        }
    }

    private static void frosted$paint(GuiRenderer renderer, double hx, double hy, double hw, double hh, int seed) {
        if (hw < 8 || hh < 6) return;
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;

        // Frozen gradient, four bands from light ice to deep blue.
        int bands = 4;
        double bandH = hh / bands;
        for (int b = 0; b < bands; b++) {
            double f = b / (double) (bands - 1);
            renderer.quad(hx, hy + b * bandH, hw, bandH + 0.5,
                new Color(frosted$lerp(100, 44, f), frosted$lerp(180, 100, f), frosted$lerp(250, 184, f), frosted$lerp(150, 175, f)));
        }
        renderer.quad(hx, hy + hh - 2, hw, 2, new Color(130, 205, 255, 220));

        // Snow caps along the top edge.
        for (double sx = hx + 2; sx < hx + hw - 2; sx += 4) {
            int r = (int) ((seed * 31 + (long) sx * 19) & 0xFF);
            double capW = Math.min(3 + (r >> 4) % 3, hx + hw - 2 - sx);
            double capH = 1 + r % 3;
            if (capW <= 0) continue;
            renderer.quad(sx, hy, capW, capH, new Color(248, 252, 255, 235));
        }

        // Drifting snowflakes: fall through the header and fade at both ends.
        int flakes = (int) Math.max(4, Math.min(12, hw / 24));
        for (int i = 0; i < flakes; i++) {
            long h = (seed * 0x9E3779B1L + i * 0x85EBCA6BL) & 0x7FFFFFFFL;
            double baseX = hx + 3 + (h % Math.max(1L, (long) (hw - 6)));
            double speed = 5 + ((h >> 8) % 7);
            double phase = ((h >> 16) % 100) / 100.0;
            double fall = (t * speed / Math.max(hh, 1.0) + phase) % 1.0;

            double fx = baseX + Math.sin(t * 1.3 + i) * 2.5;
            fx = Math.max(hx + 1, Math.min(hx + hw - 3, fx));
            double fy = hy + fall * (hh - 2);
            int alpha = 40 + (int) (190 * Math.sin(Math.PI * fall));
            double size = ((h >> 24) % 3 == 0) ? 2 : 1;
            renderer.quad(fx, fy, size, size, new Color(255, 255, 255, Math.max(0, Math.min(255, alpha))));
        }

        // Twinkling sparkles with a small cross when they peak.
        for (int i = 0; i < 5; i++) {
            long h = (seed * 17L + i * 89L) & 0xFFL;
            double fx = hx + 3 + h % Math.max(1, (int) hw - 6);
            double fy = hy + 3 + (h >> 3) % Math.max(1, (int) hh - 5);
            int alpha = (int) (110 + 110 * Math.sin(t * 2.5 + i * 1.7));
            alpha = Math.max(0, Math.min(255, alpha));
            renderer.quad(fx, fy, 1, 1, new Color(255, 255, 255, alpha));
            if (alpha > 190) {
                renderer.quad(fx - 1, fy, 3, 1, new Color(220, 240, 255, alpha / 2));
                renderer.quad(fx, fy - 1, 1, 3, new Color(220, 240, 255, alpha / 2));
            }
        }

        // Glint sweeping along the lower edge.
        double cycle = hw + 60;
        double gx = hx - 30 + (t * 45) % cycle;
        int[] glint = {40, 100, 40};
        for (int k = 0; k < glint.length; k++) {
            double x0 = Math.max(hx, gx + k * 10);
            double x1 = Math.min(hx + hw, gx + k * 10 + 10);
            if (x1 > x0) renderer.quad(x0, hy + hh - 2, x1 - x0, 2, new Color(255, 255, 255, glint[k]));
        }

        // Icicles hanging off the bottom edge.
        for (double sx = hx + 5; sx < hx + hw - 5; sx += 11) {
            int r = (int) ((seed * 7 + (long) sx * 13) & 0xFF);
            double len = 1 + r % 4;
            renderer.quad(sx, hy + hh, 1, len, new Color(190, 230, 255, 200));
        }
    }

    /** Icy outline, a soft inner glow and a drift of snow along the bottom edge. */
    private static void frosted$frame(GuiRenderer renderer, double x, double y, double w, double h, int seed) {
        if (w < 24 || h < 24) return;
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        double pulse = 0.65 + 0.35 * Math.sin(t * 1.4);

        Color edge = new Color(150, 215, 255, (int) (120 * pulse));
        renderer.quad(x, y, 1, h, edge);
        renderer.quad(x + w - 1, y, 1, h, edge);
        renderer.quad(x, y + h - 1, w, 1, edge);

        Color glow = new Color(150, 215, 255, (int) (30 * pulse));
        renderer.quad(x + 1, y + h - 4, w - 2, 3, glow);

        // snow drift: low bumps of varying height
        for (double sx = x + 2; sx < x + w - 3; sx += 3) {
            int r = (int) ((seed * 13 + (long) (sx - x) * 29) & 0xFF);
            double bh = 1 + r % 3;
            double bw = Math.min(2 + (r >> 5) % 2, x + w - 2 - sx);
            if (bw <= 0) continue;
            renderer.quad(sx, y + h - 1 - bh, bw, bh, new Color(248, 252, 255, 215));
        }

        // twinkling corners
        double tw = 0.5 + 0.5 * Math.sin(t * 2.2 + seed);
        Color star = new Color(255, 255, 255, (int) (200 * tw));
        renderer.quad(x - 1, y + h - 1, 3, 1, star);
        renderer.quad(x, y + h - 2, 1, 3, star);
        renderer.quad(x + w - 2, y + h - 1, 3, 1, star);
        renderer.quad(x + w - 1, y + h - 2, 1, 3, star);
    }

    private static int frosted$lerp(int a, int b, double f) {
        return (int) Math.round(a + (b - a) * f);
    }

    private static java.lang.reflect.Field frosted$headerField;
    private static boolean frosted$headerFieldTried;

    private static double frosted$headerHeight(WWidget window) {
        if (!frosted$headerFieldTried) {
            frosted$headerFieldTried = true;
            try {
                frosted$headerField = WWindow.class.getDeclaredField("header");
                frosted$headerField.setAccessible(true);
            } catch (Throwable ignored) {
                frosted$headerField = null;
            }
        }

        if (frosted$headerField != null) {
            try {
                Object header = frosted$headerField.get(window);
                if (header instanceof WWidget widget) {
                    return widget.height;
                }
            } catch (Throwable ignored) {
            }
        }

        return window.theme.textHeight() + 8;
    }
}
