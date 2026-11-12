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
 * Uses shared mutable Color instances to avoid per-frame allocations.
 */
@Mixin(WWindow.class)
public abstract class SnowCategoryMixin {
    @Shadow protected String title;

    private static boolean frosted$failed;
    private static final Color C1 = new Color();
    private static final Color C2 = new Color();

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

        // Smooth top-to-bottom frozen ice gradient in a single quad
        C1.set(105, 188, 255, 145);
        C2.set(38, 92, 178, 175);
        renderer.quad(hx, hy, hw, hh, C1, C1, C2, C2);

        C1.set(140, 215, 255, 225);
        renderer.quad(hx, hy + hh - 2, hw, 2, C1);

        // Snow caps along the top edge
        C1.set(248, 252, 255, 235);
        for (double sx = hx + 2; sx < hx + hw - 2; sx += 4) {
            int r = (int) ((seed * 31 + (long) sx * 19) & 0xFF);
            double capW = Math.min(3 + (r >> 4) % 3, hx + hw - 2 - sx);
            double capH = 1 + r % 3;
            if (capW <= 0) continue;
            renderer.quad(sx, hy, capW, capH, C1);
        }

        // Drifting snowflakes
        int flakes = (int) Math.max(4, Math.min(10, hw / 26));
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
            C1.set(255, 255, 255, Math.max(0, Math.min(255, alpha)));
            renderer.quad(fx, fy, size, size, C1);
        }

        // Twinkling sparkles
        for (int i = 0; i < 4; i++) {
            long h = (seed * 17L + i * 89L) & 0xFFL;
            double fx = hx + 3 + h % Math.max(1, (int) hw - 6);
            double fy = hy + 3 + (h >> 3) % Math.max(1, (int) hh - 5);
            int alpha = (int) (110 + 110 * Math.sin(t * 2.5 + i * 1.7));
            alpha = Math.max(0, Math.min(255, alpha));
            C1.set(255, 255, 255, alpha);
            renderer.quad(fx, fy, 1, 1, C1);
            if (alpha > 190) {
                C2.set(220, 240, 255, alpha / 2);
                renderer.quad(fx - 1, fy, 3, 1, C2);
                renderer.quad(fx, fy - 1, 1, 3, C2);
            }
        }

        // Smooth glint sweeping along the lower edge
        double cycle = hw + 60;
        double gx = hx - 30 + (t * 48) % cycle;
        int[] glint = {45, 130, 45};
        for (int k = 0; k < glint.length; k++) {
            double x0 = Math.max(hx, gx + k * 10);
            double x1 = Math.min(hx + hw, gx + k * 10 + 10);
            if (x1 > x0) {
                C1.set(255, 255, 255, glint[k]);
                renderer.quad(x0, hy + hh - 2, x1 - x0, 2, C1);
            }
        }

        // Icicles hanging off the bottom edge
        C1.set(190, 230, 255, 200);
        for (double sx = hx + 5; sx < hx + hw - 5; sx += 11) {
            int r = (int) ((seed * 7 + (long) sx * 13) & 0xFF);
            double len = 1 + r % 4;
            renderer.quad(sx, hy + hh, 1, len, C1);
        }
    }

    private static void frosted$frame(GuiRenderer renderer, double x, double y, double w, double h, int seed) {
        if (w < 24 || h < 24) return;
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        double pulse = 0.65 + 0.35 * Math.sin(t * 1.4);

        C1.set(150, 215, 255, (int) (130 * pulse));
        renderer.quad(x, y, 1, h, C1);
        renderer.quad(x + w - 1, y, 1, h, C1);
        renderer.quad(x, y + h - 1, w, 1, C1);

        C1.set(150, 215, 255, 0);
        C2.set(150, 215, 255, (int) (38 * pulse));
        renderer.quad(x + 1, y + h - 5, w - 2, 4, C1, C1, C2, C2);

        C1.set(248, 252, 255, 215);
        for (double sx = x + 2; sx < x + w - 3; sx += 4) {
            int r = (int) ((seed * 13 + (long) (sx - x) * 29) & 0xFF);
            double bh = 1 + r % 3;
            double bw = Math.min(2 + (r >> 5) % 2, x + w - 2 - sx);
            if (bw <= 0) continue;
            renderer.quad(sx, y + h - 1 - bh, bw, bh, C1);
        }

        double tw = 0.5 + 0.5 * Math.sin(t * 2.2 + seed);
        C1.set(255, 255, 255, (int) (210 * tw));
        renderer.quad(x - 1, y + h - 1, 3, 1, C1);
        renderer.quad(x, y + h - 2, 1, 3, C1);
        renderer.quad(x + w - 2, y + h - 1, 3, 1, C1);
        renderer.quad(x + w - 1, y + h - 2, 1, 3, C1);
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
