package com.manu.frosted.modules.hud;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.modules.util.StaffList;
import com.manu.frosted.util.Draw2D;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Frosted HUD: independent frosted panels (coords, session, ping, armour, staff, watermark).
 *
 * Every panel is draggable. Open chat, hold left click on a panel, drag it
 * anywhere — the position is stored in the panel's own settings and survives
 * restarts. All panel backgrounds are batched in a single GPU draw call.
 */
public class FrostedHud extends Module {
    private final SettingGroup sgLook = settings.getDefaultGroup();
    private final SettingGroup sgPanels = settings.createGroup("Panels");

    private final Setting<Integer> corner = sgLook.add(new IntSetting.Builder()
        .name("corner-radius")
        .description("Rounded corner size in pixels.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 12)
        .build()
    );

    private final Setting<Integer> padding = sgLook.add(new IntSetting.Builder()
        .name("padding")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 12)
        .build()
    );

    private final Setting<Integer> gap = sgLook.add(new IntSetting.Builder()
        .name("line-gap")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Integer> bgAlpha = sgLook.add(new IntSetting.Builder()
        .name("background-alpha")
        .defaultValue(225)
        .min(0)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Boolean> snowCap = sgLook.add(new BoolSetting.Builder()
        .name("snow-cap")
        .description("Draw snow along the top edge of every panel.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shimmer = sgLook.add(new BoolSetting.Builder()
        .name("shimmer")
        .description("A glint that slides along the top line of every panel.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> accentColor = sgLook.add(new ColorSetting.Builder()
        .name("accent")
        .defaultValue(new SettingColor(110, 180, 255, 255))
        .build()
    );

    private final Setting<String> watermarkText = sgPanels.add(new StringSetting.Builder()
        .name("watermark-text")
        .defaultValue("Frosted")
        .build()
    );

    private final Setting<Boolean> armorNames = sgPanels.add(new BoolSetting.Builder()
        .name("armor-names").defaultValue(false).build()
    );

    private final Panel coords = new Panel("coords", sgPanels, true, 6, 6);
    private final Panel session = new Panel("session", sgPanels, true, 6, 44);
    private final Panel ping = new Panel("ping", sgPanels, true, 6, 66);
    private final Panel armor = new Panel("armor", sgPanels, true, 6, 88);
    private final Panel staff = new Panel("staff", sgPanels, true, 6, 150);
    private final Panel watermark = new Panel("watermark", sgPanels, true, 140, 6);

    private final List<Panel> panels = List.of(coords, session, ping, armor, staff, watermark);
    private final Color tmpTextColor = new Color();

    private long start;
    private Panel dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public FrostedHud() {
        super(FrostedAddon.UTIL, "frosted-hud", "Frosted, draggable coords, session, ping, armour, staff and watermark panels.");
    }

    @Override
    public void onActivate() {
        start = System.currentTimeMillis();
        dragging = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (!(mc.currentScreen instanceof ChatScreen)) {
            dragging = null;
            return;
        }

        double mx = mc.mouse.getX();
        double my = mc.mouse.getY();
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (!down) {
            dragging = null;
            return;
        }

        if (dragging == null) {
            for (Panel panel : panels) {
                if (!panel.enabled.get() || !panel.boundsValid) continue;
                if (mx >= panel.boundsX && mx <= panel.boundsX + panel.boundsW
                    && my >= panel.boundsY && my <= panel.boundsY + panel.boundsH) {
                    dragging = panel;
                    dragOffsetX = mx - panel.boundsX;
                    dragOffsetY = my - panel.boundsY;
                    break;
                }
            }
        }

        if (dragging != null) {
            dragging.x.set(Math.max(0, (int) Math.round(mx - dragOffsetX)));
            dragging.y.set(Math.max(0, (int) Math.round(my - dragOffsetY)));
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        int pad = padding.get();
        int lineGap = gap.get();
        TextRenderer text = TextRenderer.get();
        double textH = text.getHeight();
        int lineHeight = (int) Math.ceil(textH) + lineGap;

        int alpha = bgAlpha.get();
        SettingColor acc = accentColor.get();
        int accentArgb = Draw2D.argb(acc.a, (acc.r << 16) | (acc.g << 8) | acc.b);

        List<List<Line>> allLines = List.of(
            coordsLines(),
            sessionLines(),
            pingLines(),
            armorLines(),
            staffLines(),
            watermarkLines()
        );

        // 1. Measure all active panels in one TextRenderer pass
        text.begin(1.0, false, true);
        for (int p = 0; p < panels.size(); p++) {
            Panel panel = panels.get(p);
            List<Line> lines = allLines.get(p);
            if (!panel.enabled.get() || lines.isEmpty()) {
                panel.boundsValid = false;
                continue;
            }
            double width = 0;
            for (Line line : lines) width = Math.max(width, text.getWidth(line.text));
            panel.boundsX = panel.x.get();
            panel.boundsY = panel.y.get();
            panel.boundsW = width + pad * 2;
            panel.boundsH = lines.size() * lineHeight + pad * 2 - (lineHeight - textH);
            panel.boundsValid = true;
        }
        text.end();

        // 2. Draw all panel backgrounds in one single Renderer2D batch
        Renderer2D.COLOR.begin();
        for (Panel panel : panels) {
            if (!panel.boundsValid) continue;
            frostedBody(panel.boundsX, panel.boundsY, panel.boundsW, panel.boundsH, corner.get(), alpha, accentArgb);
        }
        Renderer2D.COLOR.render();

        // 3. Render all panel text in one single TextRenderer batch
        text.begin(1.0, false, true);
        for (int p = 0; p < panels.size(); p++) {
            Panel panel = panels.get(p);
            if (!panel.boundsValid) continue;
            List<Line> lines = allLines.get(p);
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                Draw2D.setColor(tmpTextColor, line.color);
                text.render(line.text, panel.boundsX + pad, panel.boundsY + pad + i * lineHeight, tmpTextColor, true);
            }
        }
        text.end();
    }

    private void frostedBody(double x, double y, double w, double h, int radius, int alpha, int accentArgb) {
        int r = Math.max(0, Math.min(radius, (int) (Math.min(w, h) / 2)));

        // Soft drop shadow
        int shadowArgb = Draw2D.argb(Math.min(110, alpha / 2), 0x040814);
        Draw2D.polyQuad(x + r + 2, y + 3, x + w - r + 2, y + 3, x + w - r + 2, y + h + 3, x + r + 2, y + h + 3, shadowArgb);

        // Top and bottom rounded caps + central hardware vertical gradient body
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double inset = r - Math.sqrt(Math.max(0, r * r - dy * dy));
            double tTop = h <= 1 ? 0 : row / (h - 1);
            int cTop = bodyColor(tTop, alpha);
            Draw2D.polyQuad(x + inset, y + row, x + w - inset, y + row, x + w - inset, y + row + 1, x + inset, y + row + 1, cTop);

            double botRow = h - r + row;
            double dyBot = row + 0.5;
            double insetBot = r - Math.sqrt(Math.max(0, r * r - dyBot * dyBot));
            double tBot = h <= 1 ? 1 : botRow / (h - 1);
            int cBot = bodyColor(tBot, alpha);
            Draw2D.polyQuad(x + insetBot, y + botRow, x + w - insetBot, y + botRow, x + w - insetBot, y + botRow + 1, x + insetBot, y + botRow + 1, cBot);
        }

        double midH = Math.max(0, h - r * 2);
        if (midH > 0) {
            int cTop = bodyColor(r / Math.max(1.0, h - 1), alpha);
            int cBot = bodyColor((h - r) / Math.max(1.0, h - 1), alpha);
            Draw2D.polyQuadGrad(x, y + r, cTop, x + w, y + r, cTop, x + w, y + r + midH, cBot, x, y + r + midH, cBot);
        }

        // Subtle icy rim along left, right, and bottom
        int rimArgb = Draw2D.argb(Math.min(180, alpha * 2 / 3), 0x8CD2FF);
        Draw2D.polyQuad(x + r, y + h - 1, x + w - r, y + h - 1, x + w - r, y + h, x + r, y + h, rimArgb);
        // Accent bar below top edge
        Draw2D.polyQuad(x + r, y + 2, x + w - r, y + 2, x + w - r, y + 3, x + r, y + 3, accentArgb);

        if (shimmer.get()) {
            double span = Math.max(0, w - r * 2);
            long cycle = 3000L;
            double phase = ((System.currentTimeMillis() + (long) (x * 7)) % cycle) / (double) cycle;
            double gx = phase * (span + 36) - 18;
            double x0 = Math.max(0, gx);
            double x1 = Math.min(span, gx + 18);
            if (x1 > x0) {
                int whitePeak = Draw2D.argb(230, 0xFFFFFF);
                int whiteEdge = Draw2D.argb(0, 0xFFFFFF);
                double xm = (x0 + x1) * 0.5;
                Draw2D.polyQuadGrad(x + r + x0, y + 1.5, whiteEdge, x + r + xm, y + 1.5, whitePeak, x + r + xm, y + 3.5, whitePeak, x + r + x0, y + 3.5, whiteEdge);
                Draw2D.polyQuadGrad(x + r + xm, y + 1.5, whitePeak, x + r + x1, y + 1.5, whiteEdge, x + r + x1, y + 3.5, whiteEdge, x + r + xm, y + 3.5, whitePeak);
            }
        }

        if (snowCap.get()) {
            int snow = Draw2D.argb(238, 0xF8FCFF);
            double start = x + Math.max(r, 2);
            double end = x + w - Math.max(r, 2);
            int index = 0;
            for (double sx = start; sx < end; sx += 3, index++) {
                int hash = (index * 37 + 11) & 7;
                double capW = Math.min(2 + (hash & 1), end - sx);
                double capH = 1 + (hash >> 1) % 2;
                if (capW <= 0) continue;
                Draw2D.polyQuad(sx, y, sx + capW, y, sx + capW, y + capH, sx, y + capH, snow);
            }
        }
    }

    private static int bodyColor(double t, int alpha) {
        int cr = (int) Math.round(24 + (8 - 24) * t);
        int cg = (int) Math.round(46 + (15 - 46) * t);
        int cb = (int) Math.round(82 + (32 - 82) * t);
        return Draw2D.argb(alpha, (cr << 16) | (cg << 8) | cb);
    }

    private List<Line> coordsLines() {
        List<Line> lines = new ArrayList<>();
        if (!coords.enabled.get()) return lines;
        lines.add(new Line(String.format(Locale.ROOT, "%.1f  %.1f  %.1f",
            mc.player.getX(), mc.player.getY(), mc.player.getZ()), 0xFFE8F4FF));
        lines.add(new Line(mc.world.getRegistryKey().getValue().getPath() + " · " + facing(mc.player.getYaw()), 0xFF9CC8FF));
        return lines;
    }

    private List<Line> sessionLines() {
        if (!session.enabled.get()) return List.of();
        long seconds = (System.currentTimeMillis() - start) / 1000L;
        return List.of(new Line(String.format("Session %02d:%02d:%02d",
            seconds / 3600, (seconds % 3600) / 60, seconds % 60), 0xFFCDE6FF));
    }

    private List<Line> pingLines() {
        if (!ping.enabled.get() || mc.getNetworkHandler() == null) return List.of();
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int latency = entry != null ? entry.getLatency() : 0;
        int color = latency < 80 ? 0xFF9BE89B : latency < 160 ? 0xFFF5C542 : 0xFFFF5A5A;
        return List.of(new Line(latency + " ms", color));
    }

    private List<Line> armorLines() {
        List<Line> lines = new ArrayList<>();
        if (!armor.enabled.get()) return lines;

        for (int i = 3; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(36 + i);
            if (stack.isEmpty()) continue;

            int max = stack.getMaxDamage();
            String text;
            int color;
            if (max > 0) {
                int percent = (int) Math.round((max - stack.getDamage()) * 100.0 / max);
                text = percent + "%";
                color = percent > 50 ? 0xFF9BE89B : percent > 20 ? 0xFFF5C542 : 0xFFFF5A5A;
            } else {
                text = "—";
                color = 0xFFDDDDDD;
            }
            lines.add(new Line(armorNames.get() ? stack.getName().getString() + " " + text : text, color));
        }
        return lines;
    }

    private List<Line> staffLines() {
        if (!staff.enabled.get()) return List.of();
        StaffList module = Modules.get().get(StaffList.class);
        if (module == null || !module.isActive()) return List.of();

        List<String> names = module.onlineNames();
        if (names.isEmpty()) return List.of();

        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Staff " + names.size(), 0xFFC8E1FF));
        for (String name : names) lines.add(new Line(name, 0xFFFFDC78));
        return lines;
    }

    private List<Line> watermarkLines() {
        if (!watermark.enabled.get()) return List.of();
        String value = watermarkText.get();
        if (value == null || value.isEmpty()) return List.of();
        return List.of(new Line(value, 0xFFDCF0FF));
    }

    private String facing(float yaw) {
        Direction direction = Direction.fromHorizontalDegrees(yaw);
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            default -> "?";
        };
    }

    private record Line(String text, int color) {}

    private static final class Panel {
        final Setting<Boolean> enabled;
        final Setting<Integer> x;
        final Setting<Integer> y;

        boolean boundsValid;
        double boundsX, boundsY, boundsW, boundsH;

        Panel(String name, SettingGroup group, boolean on, int x, int y) {
            enabled = group.add(new BoolSetting.Builder().name(name).defaultValue(on).build());
            this.x = group.add(new IntSetting.Builder().name(name + "-x").defaultValue(x).sliderRange(0, 1920).build());
            this.y = group.add(new IntSetting.Builder().name(name + "-y").defaultValue(y).sliderRange(0, 1080).build());
        }
    }
}
