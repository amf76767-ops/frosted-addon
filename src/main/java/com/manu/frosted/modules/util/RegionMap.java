package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Draw2D;
import com.manu.frosted.util.FrostedPanel;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * DonutSMP 9x9 region radar & map in a frosted-glass HUD panel.
 * Shows all 81 regions with color-coded server zones, optional region IDs,
 * glowing active cell highlight, directional FOV cone + player arrow,
 * and a live region/coordinate header & footer. Drag in chat to reposition.
 */
public class RegionMap extends Module {
    public enum Placement { TopLeft, TopRight, BottomLeft, BottomRight, Custom }

    private static final int[][] REGIONS = {
        {82, 5}, {100, 3}, {101, 3}, {102, 3}, {103, 2}, {104, 2}, {105, 2}, {106, 2}, {91, 2},
        {83, 5}, {44, 3}, {75, 3}, {42, 3}, {41, 2}, {40, 2}, {39, 2}, {38, 2}, {92, 2},
        {84, 5}, {45, 3}, {14, 3}, {13, 3}, {12, 2}, {11, 2}, {10, 2}, {37, 2}, {93, 2},
        {85, 5}, {46, 5}, {74, 5}, {3, 3}, {2, 2}, {1, 2}, {25, 2}, {36, 2}, {94, 2},
        {86, 4}, {47, 4}, {72, 4}, {71, 4}, {5, 2}, {4, 2}, {24, 2}, {35, 2}, {95, 2},
        {87, 4}, {51, 1}, {17, 1}, {9, 0}, {8, 0}, {7, 0}, {23, 0}, {34, 0}, {96, 2},
        {88, 4}, {54, 1}, {18, 1}, {61, 0}, {62, 0}, {21, 0}, {22, 0}, {33, 0}, {97, 0},
        {89, 0}, {26, 1}, {27, 0}, {28, 0}, {29, 0}, {30, 0}, {59, 0}, {32, 0}, {98, 0},
        {90, 0}, {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {99, 0}
    };

    private static final String[] LEGEND = {"EU-C", "EU-W", "NA-E", "NA-W", "Asia", "Oce"};
    private static final int[] COLORS = {
        0x7AD96A, // EU-C (Fresh Emerald Green)
        0x18B87A, // EU-W (Teal Jade)
        0x52B6FF, // NA-E (Ice Sky Blue)
        0x3B78E7, // NA-W (Royal Cobalt)
        0xF8C948, // Asia (Golden Amber)
        0xFF8B28  // Oce  (Sunset Orange)
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Placement> placement = sgGeneral.add(new EnumSetting.Builder<Placement>()
        .name("placement")
        .defaultValue(Placement.TopRight)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall scale of the region map.")
        .defaultValue(1.0)
        .min(0.6)
        .sliderRange(0.6, 2.2)
        .build()
    );

    private final Setting<Integer> cellSize = sgGeneral.add(new IntSetting.Builder()
        .name("cell-size")
        .description("Base pixels per region cell.")
        .defaultValue(18)
        .min(10)
        .sliderRange(10, 32)
        .build()
    );

    private final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .description("Region cell fill opacity, 0-255.")
        .defaultValue(195)
        .min(40)
        .sliderRange(40, 255)
        .build()
    );

    private final Setting<Boolean> showNumbers = sgGeneral.add(new BoolSetting.Builder()
        .name("region-ids")
        .description("Draw the region ID number inside each cell.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showLegend = sgGeneral.add(new BoolSetting.Builder()
        .name("legend")
        .description("Show the region zone legend below the grid.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCoordinates = sgGeneral.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Draw your coordinates and cell border distance in the footer.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> customX = sgGeneral.add(new IntSetting.Builder()
        .name("custom-x")
        .defaultValue(16)
        .sliderRange(0, 3840)
        .visible(() -> placement.get() == Placement.Custom)
        .build()
    );

    private final Setting<Integer> customY = sgGeneral.add(new IntSetting.Builder()
        .name("custom-y")
        .defaultValue(16)
        .sliderRange(0, 2160)
        .visible(() -> placement.get() == Placement.Custom)
        .build()
    );

    private final Color tmp = new Color();
    private final Color c1 = new Color();
    private final Color c2 = new Color();
    private final Color c3 = new Color();

    private boolean dragging;
    private double dragOffX, dragOffY;
    private double lastX, lastY, lastW = 180, lastH = 200;

    public RegionMap() {
        super(FrostedAddon.UTIL, "region-map", "Frosted DonutSMP 9x9 region map with live radar marker, region IDs and zone legend.");
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;
        int cx = cell(mc.player.getX());
        int cz = cell(mc.player.getZ());
        if (cx >= 0 && cx < 9 && cz >= 0 && cz < 9) {
            int[] reg = REGIONS[cz * 9 + cx];
            return "#" + reg[0] + " " + LEGEND[reg[1]];
        }
        return null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !(mc.currentScreen instanceof ChatScreen)) {
            dragging = false;
            return;
        }

        double mx = mc.mouse.getX();
        double my = mc.mouse.getY();
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!down) {
            dragging = false;
            return;
        }

        if (!dragging && mx >= lastX && mx <= lastX + lastW && my >= lastY && my <= lastY + lastH) {
            dragging = true;
            dragOffX = mx - lastX;
            dragOffY = my - lastY;
            placement.set(Placement.Custom);
        }

        if (dragging) {
            customX.set(Math.max(0, (int) Math.round(mx - dragOffX)));
            customY.set(Math.max(0, (int) Math.round(my - dragOffY)));
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        double s = scale.get() * Math.max(1.0, Draw2D.guiScale() * 0.5);
        double cs = cellSize.get() * s;
        double gap = Math.max(1.0, 1.4 * s);
        double grid = cs * 9;
        double pad = 7 * s;

        TextRenderer tr = TextRenderer.get();
        tr.begin(s * 0.78, false, true);
        double headerH = tr.getHeight(true) + 5 * s;
        tr.end();

        double legendH = showLegend.get() ? (14 * s) : 0;
        double coordsH = showCoordinates.get() ? (13 * s) : 0;

        double panelW = grid + pad * 2;
        double panelH = pad * 2 + headerH + grid + legendH + coordsH;

        double sw = Draw2D.screenW();
        double sh = Draw2D.screenH();
        double margin = 10 * s;

        double x = switch (placement.get()) {
            case TopLeft, BottomLeft -> margin;
            case TopRight, BottomRight -> sw - panelW - margin;
            case Custom -> Math.min(customX.get(), Math.max(0, sw - panelW - 4));
        };
        double y = switch (placement.get()) {
            case TopLeft, TopRight -> margin;
            case BottomLeft, BottomRight -> sh - panelH - margin;
            case Custom -> Math.min(customY.get(), Math.max(0, sh - panelH - 4));
        };

        lastX = x;
        lastY = y;
        lastW = panelW;
        lastH = panelH;

        int cellX = cell(mc.player.getX());
        int cellZ = cell(mc.player.getZ());
        boolean inBounds = cellX >= 0 && cellX < 9 && cellZ >= 0 && cellZ < 9;
        int activeId = inBounds ? REGIONS[cellZ * 9 + cellX][0] : -1;
        int activeZone = inBounds ? REGIONS[cellZ * 9 + cellX][1] : -1;
        int accent = inBounds ? COLORS[activeZone] : 0x6EBEFF;

        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;

        // ---- 1. Geometry pass
        Renderer2D.COLOR.begin();
        FrostedPanel.draw(x, y, panelW, panelH, 7 * s, s, 220, dragging ? 0xFFFFFF : accent, true, true, true);

        // Header status dot + divider
        double dotR = 2.4 * s;
        double dotY = y + pad + (headerH - 4 * s) * 0.5;
        Draw2D.roundRect(x + pad + 2 * s, dotY - dotR, dotR * 2, dotR * 2, dotR,
            Draw2D.argb(240, accent), Draw2D.argb(240, accent));
        Draw2D.rectH(x + pad, y + pad + headerH - 3 * s, panelW - pad * 2, Math.max(1.0, s * 0.8),
            Draw2D.argb(175, accent), Draw2D.argb(0, accent));

        double gx = x + pad;
        double gy = y + pad + headerH;

        // Subtle recessed backing behind the 9x9 grid
        Draw2D.roundRect(gx - 2 * s, gy - 2 * s, grid + 4 * s, grid + 4 * s, 4 * s,
            Draw2D.argb(150, 0x060B14), Draw2D.argb(180, 0x04070E));

        int fillAlpha = opacity.get();
        double cellR = Math.max(1.5, 2.5 * s);

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                int zone = REGIONS[index][1];
                int baseRgb = COLORS[Math.min(zone, COLORS.length - 1)];
                boolean isCurrent = (col == cellX && row == cellZ);

                double cx = gx + col * cs + gap * 0.5;
                double cy = gy + row * cs + gap * 0.5;
                double cw = cs - gap;
                double ch = cs - gap;

                int a = isCurrent ? Math.min(255, fillAlpha + 45) : fillAlpha;
                int topCol = Draw2D.argb(a, Draw2D.shade(baseRgb, isCurrent ? 0.28 : 0.12));
                int botCol = Draw2D.argb(a, Draw2D.shade(baseRgb, isCurrent ? -0.05 : -0.22));
                Draw2D.roundRect(cx, cy, cw, ch, cellR, topCol, botCol);

                // Top glossy highlight inside cell
                Draw2D.rect(cx + cellR * 0.6, cy + 0.5 * s, cw - cellR * 1.2, Math.max(1.0, 0.9 * s),
                    Draw2D.argb(isCurrent ? 120 : 55, 0xFFFFFF));
            }
        }

        // Active cell highlight + player radar marker
        if (inBounds) {
            double cx = gx + cellX * cs;
            double cy = gy + cellZ * cs;
            double pulse = 0.5 + 0.5 * Math.sin(time * 4.5);
            int glowA = (int) (170 + 85 * pulse);
            double bw = Math.max(1.2, 1.6 * s);

            // Outer glowing frame around current cell
            int frameCol = Draw2D.argb(glowA, 0xFFFFFF);
            Draw2D.rect(cx, cy, cs, bw, frameCol);
            Draw2D.rect(cx, cy + cs - bw, cs, bw, frameCol);
            Draw2D.rect(cx, cy, bw, cs, frameCol);
            Draw2D.rect(cx + cs - bw, cy, bw, cs, frameCol);

            double fx = fraction(mc.player.getX());
            double fz = fraction(mc.player.getZ());
            double px = cx + fx * cs;
            double pz = cy + fz * cs;

            // Crosshair lines inside the active cell
            int crossCol = Draw2D.argb(70, 0xFFFFFF);
            Draw2D.rect(cx + 1, pz - 0.4 * s, cs - 2, 0.8 * s, crossCol);
            Draw2D.rect(px - 0.4 * s, cy + 1, 0.8 * s, cs - 2, crossCol);

            // Directional FOV cone + player arrow
            double yaw = Math.toRadians(mc.player.getYaw());
            double dirX = -Math.sin(yaw);
            double dirZ = Math.cos(yaw);
            double perpX = -dirZ;
            double perpZ = dirX;

            double coneLen = 6.5 * s;
            double coneHalf = 3.6 * s;
            int coneCenter = Draw2D.argb(95, 0xFFFFFF);
            int coneOuter = Draw2D.argb(0, accent);
            Draw2D.triGrad(
                px, pz, coneCenter,
                px + dirX * coneLen + perpX * coneHalf, pz + dirZ * coneLen + perpZ * coneHalf, coneOuter,
                px + dirX * coneLen - perpX * coneHalf, pz + dirZ * coneLen - perpZ * coneHalf, coneOuter
            );

            // Crisp white directional pointer arrow with dark outline
            double tip = 3.8 * s;
            double back = 2.6 * s;
            double wing = 2.4 * s;
            int arrowOut = Draw2D.argb(230, 0x050A14);
            Draw2D.triGrad(
                px + dirX * (tip + 0.8 * s), pz + dirZ * (tip + 0.8 * s), arrowOut,
                px - dirX * (back + 0.8 * s) + perpX * (wing + 0.8 * s), pz - dirZ * (back + 0.8 * s) + perpZ * (wing + 0.8 * s), arrowOut,
                px - dirX * (back + 0.8 * s) - perpX * (wing + 0.8 * s), pz - dirZ * (back + 0.8 * s) - perpZ * (wing + 0.8 * s), arrowOut
            );
            int arrowTip = Draw2D.argb(255, 0xFFFFFF);
            int arrowWing = Draw2D.argb(255, 0xCFEAFF);
            Draw2D.triGrad(
                px + dirX * tip, pz + dirZ * tip, arrowTip,
                px - dirX * back + perpX * wing, pz - dirZ * back + perpZ * wing, arrowWing,
                px - dirX * back - perpX * wing, pz - dirZ * back - perpZ * wing, arrowWing
            );
        }

        // Legend color badges
        double footerY = gy + grid + 4 * s;
        if (showLegend.get()) {
            double slotW = grid / LEGEND.length;
            double badgeS = 4.5 * s;
            for (int i = 0; i < LEGEND.length; i++) {
                double lx = gx + i * slotW;
                int col = Draw2D.argb(245, COLORS[i]);
                Draw2D.roundRect(lx, footerY + 1.5 * s, badgeS, badgeS, 1.5 * s, col, Draw2D.argb(245, Draw2D.shade(COLORS[i], -0.2)));
            }
        }
        Renderer2D.COLOR.render();

        // ---- 2. Text pass
        // Header title + active region badge
        tr.begin(s * 0.76, false, true);
        setColor(Draw2D.shade(accent, 0.38), 255);
        tr.render("REGION MAP", x + pad + 9 * s, y + pad, tmp, true);

        String badge = inBounds ? ("#" + activeId + " \u00B7 " + LEGEND[activeZone]) : "OUTSIDE";
        setColor(0xEAF6FF, 255);
        tr.render(badge, x + panelW - pad - tr.getWidth(badge, true), y + pad, tmp, true);
        tr.end();

        // Cell region IDs
        if (showNumbers.get()) {
            double numScale = Math.max(0.42, (cellSize.get() / 28.0) * s);
            tr.begin(numScale, false, true);
            double th = tr.getHeight(false);
            for (int row = 0; row < 9; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = row * 9 + col;
                    String label = Integer.toString(REGIONS[index][0]);
                    boolean isCurrent = (col == cellX && row == cellZ);
                    double tw = tr.getWidth(label, false);
                    double tx = gx + col * cs + (cs - tw) * 0.5;
                    double ty = gy + row * cs + (cs - th) * 0.5;
                    setColor(isCurrent ? 0xFFFFFF : 0x0A101A, isCurrent ? 255 : 210);
                    tr.render(label, tx, ty, tmp, isCurrent);
                }
            }
            tr.end();
        }

        // Legend labels
        if (showLegend.get()) {
            tr.begin(s * 0.62, false, true);
            double slotW = grid / LEGEND.length;
            double badgeS = 4.5 * s;
            for (int i = 0; i < LEGEND.length; i++) {
                double lx = gx + i * slotW + badgeS + 2.2 * s;
                setColor(i == activeZone ? 0xFFFFFF : 0xC8DDF2, 255);
                tr.render(LEGEND[i], lx, footerY + 0.5 * s, tmp, true);
            }
            tr.end();
            footerY += legendH - 2 * s;
        }

        // Coordinates & border distance footer
        if (showCoordinates.get()) {
            tr.begin(s * 0.68, false, true);
            int px = (int) Math.round(mc.player.getX());
            int pz = (int) Math.round(mc.player.getZ());
            String coordStr = String.format(Locale.ROOT, "X %d  Z %d", px, pz);
            setColor(0xCFE8FF, 255);
            tr.render(coordStr, gx, footerY + 1 * s, tmp, true);

            if (inBounds) {
                double fx = fraction(mc.player.getX());
                double fz = fraction(mc.player.getZ());
                double distBlocks = Math.min(Math.min(fx, 1.0 - fx), Math.min(fz, 1.0 - fz)) * 50000.0;
                String borderStr = String.format(Locale.ROOT, "%.1fk to border", distBlocks / 1000.0);
                setColor(0x88B4E0, 255);
                tr.render(borderStr, x + panelW - pad - tr.getWidth(borderStr, true), footerY + 1 * s, tmp, true);
            }
            tr.end();
        }
    }

    private void setColor(int rgb, int a) {
        tmp.r = (rgb >> 16) & 0xFF;
        tmp.g = (rgb >> 8) & 0xFF;
        tmp.b = rgb & 0xFF;
        tmp.a = a;
    }

    private static void setC(Color c, int rgb, int a) {
        c.r = (rgb >> 16) & 0xFF;
        c.g = (rgb >> 8) & 0xFF;
        c.b = rgb & 0xFF;
        c.a = a;
    }

    private static int cell(double coord) {
        return (int) Math.floor((coord + 225000.0) / 50000.0);
    }

    private static double fraction(double coord) {
        double f = ((coord + 225000.0) % 50000.0) / 50000.0;
        if (f < 0) f++;
        return f;
    }
}
