package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;

/**
 * DonutSMP region map. The server splits the world into 9x9 cells of 50,000
 * blocks centred on 0,0; each cell belongs to a region type. The HUD draws the
 * grid, highlights your current cell and puts a marker at your exact position
 * inside it.
 *
 * Region table and colours match the server's own region layout.
 */
public class RegionMap extends Module {
    public enum Placement { TopLeft, TopRight, BottomLeft, BottomRight }

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
        0x9FCE63, 0x00A663, 0x4FADEA, 0x2F6EBA, 0xF5C242, 0xFC8803
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Placement> placement = sgGeneral.add(new EnumSetting.Builder<Placement>()
        .name("placement")
        .defaultValue(Placement.TopRight)
        .build()
    );

    private final Setting<Integer> cellSize = sgGeneral.add(new IntSetting.Builder()
        .name("cell-size")
        .description("Pixels per region cell.")
        .defaultValue(10)
        .min(4)
        .sliderRange(4, 20)
        .build()
    );

    private final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .description("Region fill opacity, 0-255.")
        .defaultValue(160)
        .min(10)
        .sliderRange(10, 255)
        .build()
    );

    private final Setting<Boolean> showLegend = sgGeneral.add(new BoolSetting.Builder()
        .name("legend")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showNumbers = sgGeneral.add(new BoolSetting.Builder()
        .name("region-ids")
        .description("Draw the region ID in each cell.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showCoordinates = sgGeneral.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Draw your coordinates under the map.")
        .defaultValue(true)
        .build()
    );

    public RegionMap() {
        super(FrostedAddon.UTIL, "region-map", "DonutSMP 9x9 region grid with your position.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        DrawContext ctx = event.drawContext;
        int cs = cellSize.get();
        int grid = cs * 9;
        int pad = 4;
        int legendHeight = showLegend.get() ? 14 : 0;
        int panelW = grid + pad * 2;
        int panelH = grid + pad * 2 + legendHeight;

        int x = switch (placement.get()) {
            case TopLeft, BottomLeft -> 6;
            case TopRight, BottomRight -> event.screenWidth - panelW - 6;
        };
        int y = switch (placement.get()) {
            case TopLeft, TopRight -> 6;
            case BottomLeft, BottomRight -> event.screenHeight - panelH - 6;
        };

        ctx.fill(x, y, x + panelW, y + panelH, 0xB0000000);

        int cellX = cell(mc.player.getX());
        int cellZ = cell(mc.player.getZ());
        int alpha = opacity.get();

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                int legend = REGIONS[index][1];
                int color = (alpha << 24) | COLORS[Math.min(legend, COLORS.length - 1)];

                int cx = x + pad + col * cs;
                int cy = y + pad + row * cs;
                ctx.fill(cx + 1, cy + 1, cx + cs - 1, cy + cs - 1, color);

                if (showNumbers.get()) {
                    String label = Integer.toString(REGIONS[index][0]);
                    ctx.drawText(mc.textRenderer, label, cx + 1, cy + 1, 0xFF000000, false);
                }
            }
        }

        if (cellX >= 0 && cellX < 9 && cellZ >= 0 && cellZ < 9) {
            int cx = x + pad + cellX * cs;
            int cy = y + pad + cellZ * cs;
            ctx.fill(cx, cy, cx + cs, cy + 2, 0xFFFFFFFF);
            ctx.fill(cx, cy + cs - 2, cx + cs, cy + cs, 0xFFFFFFFF);
            ctx.fill(cx, cy, cx + 2, cy + cs, 0xFFFFFFFF);
            ctx.fill(cx + cs - 2, cy, cx + cs, cy + cs, 0xFFFFFFFF);

            double fx = fraction(mc.player.getX());
            double fz = fraction(mc.player.getZ());
            int px = (int) (cx + fx * cs);
            int pz = (int) (cy + fz * cs);
            ctx.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFFFFFFFF);

            double yaw = Math.toRadians(mc.player.getYaw());
            int dx = (int) Math.round(-Math.sin(yaw) * 4);
            int dz = (int) Math.round(Math.cos(yaw) * 4);
            ctx.fill(px + dx - 1, pz + dz - 1, px + dx + 1, pz + dz + 1, 0xFF9CC8FF);
        }

        if (showLegend.get()) {
            int lx = x + pad;
            int ly = y + pad + grid + 3;
            for (int i = 0; i < LEGEND.length; i++) {
                int lcx = lx + i * (panelW - pad * 2) / LEGEND.length;
                ctx.fill(lcx, ly, lcx + 5, ly + 5, 0xFF000000 | COLORS[i]);
                ctx.drawText(mc.textRenderer, LEGEND[i], lcx + 7, ly - 1, 0xFFDDDDDD, false);
            }
        }

        if (showCoordinates.get()) {
            String text = (int) mc.player.getX() + ", " + (int) mc.player.getZ();
            ctx.drawText(mc.textRenderer, text, x + pad, y + pad + grid + 14, 0xFF9CC8FF, false);
        }
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
