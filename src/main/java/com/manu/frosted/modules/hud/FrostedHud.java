package com.manu.frosted.modules.hud;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import com.manu.frosted.modules.util.StaffList;
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
 * restarts.
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
        int lineHeight = (int) Math.ceil(TextRenderer.get().getHeight()) + lineGap;

        int alpha = bgAlpha.get();
        Color accent = new Color(accentColor.get().r, accentColor.get().g, accentColor.get().b, accentColor.get().a);

        drawPanel(coords, coordsLines(), pad, lineHeight, alpha, accent);
        drawPanel(session, sessionLines(), pad, lineHeight, alpha, accent);
        drawPanel(ping, pingLines(), pad, lineHeight, alpha, accent);
        drawPanel(armor, armorLines(), pad, lineHeight, alpha, accent);
        drawPanel(staff, staffLines(), pad, lineHeight, alpha, accent);
        drawPanel(watermark, watermarkLines(), pad, lineHeight, alpha, accent);
    }

    private void drawPanel(Panel panel, List<Line> lines, int pad, int lineHeight,
                           int alpha, Color accent) {
        if (!panel.enabled.get()) {
            panel.boundsValid = false;
            return;
        }
        if (lines.isEmpty()) {
            panel.boundsValid = false;
            return;
        }

        TextRenderer text = TextRenderer.get();
        text.begin(1.0, false, true);
        double width = 0;
        for (Line line : lines) width = Math.max(width, text.getWidth(line.text));
        text.end();

        double panelWidth = width + pad * 2;
        double panelHeight = lines.size() * lineHeight + pad * 2 - (lineHeight - text.getHeight());

        double x = panel.x.get();
        double y = panel.y.get();

        panel.boundsX = x;
        panel.boundsY = y;
        panel.boundsW = panelWidth;
        panel.boundsH = panelHeight;
        panel.boundsValid = true;

        frostedBody(x, y, panelWidth, panelHeight, corner.get(), alpha, accent);

        text.begin(1.0, false, true);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            text.render(line.text, x + pad, y + pad + i * lineHeight, new Color(line.color), true);
        }
        text.end();
    }

    /**
     * Frosted glass body built from one-pixel rows so it works through Renderer2D:
     * vertical ice-blue gradient, rim light at the bottom, accent line and snow on top.
     */
    private void frostedBody(double x, double y, double w, double h, int radius, int alpha, Color accent) {
        int r = Math.max(0, Math.min(radius, (int) (Math.min(w, h) / 2)));
        Renderer2D.COLOR.begin();

        int rows = (int) Math.ceil(h);
        for (int row = 0; row < rows; row++) {
            double inset = 0;
            if (r > 0) {
                if (row < r) {
                    double dy = r - row - 0.5;
                    inset = r - Math.sqrt(Math.max(0, r * r - dy * dy));
                } else if (row >= h - r) {
                    double dy = row - (h - r) + 0.5;
                    inset = r - Math.sqrt(Math.max(0, r * r - dy * dy));
                }
            }

            double rowWidth = w - inset * 2;
            if (rowWidth <= 0) continue;

            double t = h <= 1 ? 0 : row / (h - 1);
            int cr = (int) Math.round(26 + (8 - 26) * t);
            int cg = (int) Math.round(46 + (14 - 46) * t);
            int cb = (int) Math.round(80 + (30 - 80) * t);
            double rowH = Math.min(1, h - row);
            Renderer2D.COLOR.quad(x + inset, y + row, rowWidth, rowH, new Color(cr, cg, cb, alpha));
        }

        // Rim light along the bottom, accent line just below the top edge.
        Renderer2D.COLOR.quad(x + r, y + h - 1, Math.max(0, w - r * 2), 1, new Color(140, 210, 255, Math.min(255, alpha / 2)));
        Renderer2D.COLOR.quad(x + r, y + 2, Math.max(0, w - r * 2), 1, new Color(accent.r, accent.g, accent.b, accent.a));

        if (shimmer.get()) {
            double span = Math.max(0, w - r * 2);
            long cycle = 3200L;
            double phase = ((System.currentTimeMillis() + (long) (x * 7)) % cycle) / (double) cycle;
            double gx = phase * (span + 28) - 14;
            double x0 = Math.max(0, gx);
            double x1 = Math.min(span, gx + 14);
            if (x1 > x0) {
                Renderer2D.COLOR.quad(x + r + x0, y + 2, x1 - x0, 1, new Color(255, 255, 255, 210));
                Renderer2D.COLOR.quad(x + r + Math.max(0, gx - 6), y + 2, Math.max(0, x0 - Math.max(0, gx - 6)), 1, new Color(255, 255, 255, 90));
            }
        }

        // Deterministic snow bumps so the cap never flickers.
        if (snowCap.get()) {
            Color snow = new Color(248, 252, 255, 235);
            double start = x + Math.max(r, 2);
            double end = x + w - Math.max(r, 2);
            int index = 0;
            for (double sx = start; sx < end; sx += 3, index++) {
                int hash = (index * 37 + 11) & 7;
                double capW = Math.min(2 + (hash & 1), end - sx);
                double capH = 1 + (hash >> 1) % 2;
                if (capW <= 0) continue;
                Renderer2D.COLOR.quad(sx, y, capW, capH, snow);
            }
        }

        Renderer2D.COLOR.render();
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
