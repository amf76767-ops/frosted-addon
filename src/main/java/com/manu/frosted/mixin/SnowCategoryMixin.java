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
 * Sleek Frosted Glass & Ice Neon styling for all Frosted category windows in ClickGUI.
 * Replaces the old blocky bottom snow pile with a clean glowing ice border,
 * glassy header gradient, and smooth animated aurora shimmer bar.
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
            frosted$paintHeader(renderer, self.x, self.y, self.width, frosted$headerHeight(self), title.hashCode());
            frosted$paintFrame(renderer, self.x, self.y, self.width, self.height);
        } catch (Throwable t) {
            frosted$failed = true;
        }
    }

    private static void frosted$paintHeader(GuiRenderer renderer, double hx, double hy, double hw, double hh, int seed) {
        if (hw < 8 || hh < 6) return;
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;

        // 1. Sleek frosted-glass gradient across the header
        C1.set(85, 175, 255, 110);
        C2.set(22, 65, 138, 135);
        renderer.quad(hx, hy, hw, hh, C1, C1, C2, C2);

        // 2. Crisp specular top glass highlight line
        C1.set(215, 242, 255, 195);
        renderer.quad(hx, hy, hw, 1.2, C1);

        // 3. Glowing ice separator bar at the bottom of the header
        C1.set(110, 200, 255, 210);
        renderer.quad(hx, hy + hh - 1.6, hw, 1.6, C1);

        // 4. Smooth animated shimmer sweeping across the separator bar
        double cycle = hw + 80;
        double gx = hx - 40 + ((t * 52 + (seed & 0xFF)) % cycle);
        double x0 = Math.max(hx, gx);
        double x1 = Math.min(hx + hw, gx + 36);
        if (x1 > x0) {
            double xm = (x0 + x1) * 0.5;
            C1.set(255, 255, 255, 0);
            C2.set(240, 252, 255, 210);
            renderer.quad(x0, hy + hh - 2.0, xm - x0, 2.0, C1, C2, C2, C1);
            renderer.quad(xm, hy + hh - 2.0, x1 - xm, 2.0, C2, C1, C1, C2);
        }
    }

    private static void frosted$paintFrame(GuiRenderer renderer, double x, double y, double w, double h) {
        if (w < 24 || h < 24) return;
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        double pulse = 0.72 + 0.28 * Math.sin(t * 1.6);

        // Clean 1px frosted neon border (no snow piling up at the bottom)
        int borderA = (int) (135 * pulse);
        C1.set(135, 210, 255, borderA);
        C2.set(75, 150, 230, (int) (95 * pulse));

        // Left & right vertical glass edges
        renderer.quad(x, y, 1, h, C1, C1, C2, C2);
        renderer.quad(x + w - 1, y, 1, h, C1, C1, C2, C2);

        // Clean bottom edge line
        renderer.quad(x, y + h - 1, w, 1, C2);
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
