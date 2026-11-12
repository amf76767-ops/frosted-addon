package com.manu.frosted.modules.hud;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.modules.util.StaffList;
import com.manu.frosted.util.Draw2D;
import com.manu.frosted.util.FrostedPanel;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Frosted HUD: draggable frosted-glass panels with headers, bars and an animated watermark.
 * Open chat and drag a panel with the left mouse button; positions are saved in the settings.
 */
public class FrostedHud extends Module {
    private final SettingGroup sgLook = settings.getDefaultGroup();
    private final SettingGroup sgPanels = settings.createGroup("Panels");

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale").description("Overall size of the panels.").defaultValue(1.0).min(0.5).sliderRange(0.5, 2.5).build());

    private final Setting<Integer> corner = sgLook.add(new IntSetting.Builder()
        .name("corner-radius").defaultValue(6).min(0).sliderRange(0, 14).build());

    private final Setting<Integer> bgAlpha = sgLook.add(new IntSetting.Builder()
        .name("background-alpha").defaultValue(215).min(0).sliderRange(0, 255).build());

    private final Setting<Boolean> glow = sgLook.add(new BoolSetting.Builder()
        .name("glow").description("Soft icy glow around every panel.").defaultValue(true).build());

    private final Setting<Boolean> snowCap = sgLook.add(new BoolSetting.Builder()
        .name("snow-cap").defaultValue(true).build());

    private final Setting<Boolean> shimmer = sgLook.add(new BoolSetting.Builder()
        .name("shimmer").defaultValue(true).build());

    private final Setting<Boolean> headers = sgLook.add(new BoolSetting.Builder()
        .name("headers").description("Small titles on top of every panel.").defaultValue(true).build());

    private final Setting<SettingColor> accentColor = sgLook.add(new ColorSetting.Builder()
        .name("accent").defaultValue(new SettingColor(110, 190, 255, 255)).build());

    private final Setting<String> watermarkText = sgPanels.add(new StringSetting.Builder()
        .name("watermark-text").defaultValue("Frosted").build());

    private final Setting<Boolean> armorNames = sgPanels.add(new BoolSetting.Builder()
        .name("armor-names").defaultValue(false).build());

    private final Setting<Boolean> staffShowEmpty = sgPanels.add(new BoolSetting.Builder()
        .name("staff-show-empty").description("Show the staff panel with 'none online' when no staff is there.").defaultValue(true).build());

    private final Panel watermark = new Panel("watermark", sgPanels, true, 8, 8);
    private final Panel coords = new Panel("coords", sgPanels, true, 8, 60);
    private final Panel session = new Panel("session", sgPanels, true, 8, 150);
    private final Panel ping = new Panel("ping", sgPanels, true, 8, 215);
    private final Panel armor = new Panel("armor", sgPanels, true, 8, 270);
    private final Panel staff = new Panel("staff", sgPanels, true, 8, 400);

    private final List<Panel> panels = List.of(watermark, coords, session, ping, armor, staff);
    private final Color tmp = new Color();

    private long start;
    private Panel dragging;
    private double dragOffsetX, dragOffsetY;

    public FrostedHud() {
        super(FrostedAddon.UTIL, "frosted-hud", "Frosted, draggable coords, session, ping, armour, staff and watermark panels.");
    }

    public boolean isStaffPanelEnabled() {
        return staff.enabled.get();
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
            for (Panel p : panels) {
                if (!p.enabled.get() || !p.valid) continue;
                if (mx >= p.bx && mx <= p.bx + p.bw && my >= p.by && my <= p.by + p.bh) {
                    dragging = p;
                    dragOffsetX = mx - p.bx;
                    dragOffsetY = my - p.by;
                    break;
                }
            }
        }

        if (dragging != null) {
            dragging.x.set(Math.max(0, (int) Math.round(mx - dragOffsetX)));
            dragging.y.set(Math.max(0, (int) Math.round(my - dragOffsetY)));
        }
    }

    // ------------------------------------------------------------------ render

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        double s = scale.get() * Math.max(1.0, Draw2D.guiScale() * 0.5);
        TextRenderer text = TextRenderer.get();
        SettingColor ac = accentColor.get();
        int accent = (ac.r << 16) | (ac.g << 8) | ac.b;
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        List<Content> contents = new ArrayList<>(6);
        contents.add(watermarkContent());
        contents.add(coordsContent());
        contents.add(sessionContent());
        contents.add(pingContent());
        contents.add(armorContent());
        contents.add(staffContent(chatOpen));

        double pad = 6 * s;
        double gapX = 10 * s;
        double barH = 3 * s;

        // ---- 1. measure
        text.begin(s, false, true);
        double lineH = text.getHeight(true) + 2 * s;
        double titleScale = 0.72;
        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            Content c = contents.get(i);
            if (!p.enabled.get() || c == null) {
                p.valid = false;
                continue;
            }

            double w = 0;
            if (c.big != null) w = text.getWidth(c.big, true) * 1.0;
            for (Row r : c.rows) {
                double rw = text.getWidth(r.left, true);
                if (r.right != null) rw += gapX + text.getWidth(r.right, true);
                w = Math.max(w, rw);
            }
            if (headers.get() && c.title != null) w = Math.max(w, text.getWidth(c.title, true) * titleScale + 14 * s);

            double h = pad * 2;
            if (headers.get() && c.title != null) h += lineH * titleScale + 4 * s;
            if (c.big != null) h += lineH;
            for (Row r : c.rows) h += lineH + (r.bar >= 0 ? barH + 2 * s : 0);

            p.bw = Math.max(w + pad * 2 + 3 * s, 40 * s);
            p.bh = h;
            p.bx = Math.min(p.x.get(), Math.max(0, Draw2D.screenW() - p.bw - 4));
            p.by = Math.min(p.y.get(), Math.max(0, Draw2D.screenH() - p.bh - 4));
            p.valid = true;
        }
        text.end();

        // ---- 2. backgrounds, headers, bars
        Renderer2D.COLOR.begin();
        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            if (!p.valid) continue;
            Content c = contents.get(i);

            boolean hot = dragging == p;
            FrostedPanel.draw(p.bx, p.by, p.bw, p.bh, corner.get() * s, s, bgAlpha.get(),
                hot ? 0xFFFFFF : accent, shimmer.get(), snowCap.get(), glow.get() || hot);

            double cy = p.by + pad;
            if (headers.get() && c.title != null) {
                // status dot (pulses red for staff alerts)
                double dotR = 2.2 * s;
                int dotC = c.alert ? Draw2D.argb((int) (180 + 75 * Math.sin(time * 6)), 0xFF4A5A) : Draw2D.argb(230, accent);
                double dy = cy + lineH * titleScale * 0.5 - dotR * 0.2;
                Draw2D.roundRect(p.bx + pad + 2 * s, dy - dotR, dotR * 2, dotR * 2, dotR, dotC, dotC);
                cy += lineH * titleScale + 1 * s;
                // separator line
                Draw2D.rectH(p.bx + pad, cy, p.bw - pad * 2, Math.max(1, s * 0.8),
                    Draw2D.argb(160, accent), Draw2D.argb(0, accent));
                cy += 3 * s;
            }
            if (c.big != null) cy += lineH;
            for (Row r : c.rows) {
                cy += lineH;
                if (r.bar >= 0) {
                    FrostedPanel.bar(p.bx + pad + 3 * s, cy - 1 * s, p.bw - pad * 2 - 3 * s, barH, r.bar, r.barColor);
                    cy += barH + 2 * s;
                }
            }
        }
        Renderer2D.COLOR.render();

        // ---- 3. text
        text.begin(s, false, true);
        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            if (!p.valid) continue;
            Content c = contents.get(i);
            double tx = p.bx + pad + 3 * s;
            double cy = p.by + pad;

            if (headers.get() && c.title != null) {
                text.end();
                text.begin(s * titleScale, false, true);
                color(Draw2D.shade(accent, 0.35), 255);
                text.render(c.title, (tx + 6 * s), cy, tmp, true);
                text.end();
                text.begin(s, false, true);
                cy += lineH * titleScale + 4 * s;
            }

            if (c.big != null) {
                // animated icy gradient watermark
                double x = tx;
                for (int k = 0; k < c.big.length(); k++) {
                    String ch = String.valueOf(c.big.charAt(k));
                    double wave = 0.5 + 0.5 * Math.sin(time * 2.4 - k * 0.55);
                    color(Draw2D.mix(accent, 0xFFFFFF, wave * 0.85), 255);
                    text.render(ch, x, cy, tmp, true);
                    x += text.getWidth(ch, true);
                }
                cy += lineH;
            }

            for (Row r : c.rows) {
                color(r.leftColor, 255);
                text.render(r.left, tx, cy, tmp, true);
                if (r.right != null) {
                    double rw = text.getWidth(r.right, true);
                    color(r.rightColor, 255);
                    text.render(r.right, p.bx + p.bw - pad - rw, cy, tmp, true);
                }
                cy += lineH + (r.bar >= 0 ? barH + 2 * s : 0);
            }
        }
        text.end();
    }

    private void color(int rgb, int a) {
        tmp.r = (rgb >> 16) & 0xFF;
        tmp.g = (rgb >> 8) & 0xFF;
        tmp.b = rgb & 0xFF;
        tmp.a = a;
    }

    // ----------------------------------------------------------------- content

    private Content watermarkContent() {
        String v = watermarkText.get();
        if (v == null || v.isEmpty()) return null;
        Content c = new Content(null);
        c.big = v;
        c.rows.add(new Row(mc.getCurrentFps() + " fps", 0x9CC8FF, "1.6.0", 0x6F8FB8));
        return c;
    }

    private Content coordsContent() {
        Content c = new Content("POSITION");
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        boolean nether = "the_nether".equals(mc.world.getRegistryKey().getValue().getPath());
        c.rows.add(new Row("XYZ", 0x7FA8D8, String.format(Locale.ROOT, "%.0f  %.0f  %.0f", x, y, z), 0xEAF6FF));
        double ox = nether ? x * 8 : x / 8, oz = nether ? z * 8 : z / 8;
        c.rows.add(new Row(nether ? "Overworld" : "Nether", 0x7FA8D8, String.format(Locale.ROOT, "%.0f  %.0f", ox, oz), nether ? 0x9BE89B : 0xFF8A7A));
        float yaw = ((mc.player.getYaw() % 360) + 360) % 360;
        c.rows.add(new Row("Facing", 0x7FA8D8, facing(yaw) + "  " + Math.round(yaw) + "\u00B0", 0xCDE6FF));
        return c;
    }

    private Content sessionContent() {
        Content c = new Content("SESSION");
        long sec = (System.currentTimeMillis() - start) / 1000L;
        c.rows.add(new Row("Time", 0x7FA8D8, String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60), 0xEAF6FF));
        String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
        c.rows.add(new Row("Server", 0x7FA8D8, server, 0xCDE6FF));
        return c;
    }

    private Content pingContent() {
        if (mc.getNetworkHandler() == null) return null;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int lat = e != null ? e.getLatency() : 0;
        int col = lat < 80 ? 0x7CF0A0 : lat < 160 ? 0xF5C542 : 0xFF5A5A;
        Content c = new Content("NETWORK");
        Row r = new Row("Ping", 0x7FA8D8, lat + " ms", col);
        r.bar = 1.0 - Math.min(1.0, lat / 300.0);
        r.barColor = col;
        c.rows.add(r);
        return c;
    }

    private Content armorContent() {
        Content c = new Content("ARMOR");
        String[] names = {"Helmet", "Chest", "Legs", "Boots"};
        for (int i = 3; i >= 0; i--) {
            ItemStack st = mc.player.getInventory().getStack(36 + i);
            if (st.isEmpty()) continue;
            String label = armorNames.get() ? st.getName().getString() : names[3 - i];
            int max = st.getMaxDamage();
            if (max > 0) {
                double f = (max - st.getDamage()) / (double) max;
                int pct = (int) Math.round(f * 100);
                int col = pct > 50 ? 0x7CF0A0 : pct > 20 ? 0xF5C542 : 0xFF5A5A;
                Row r = new Row(label, 0xCDE6FF, pct + "%", col);
                r.bar = f;
                r.barColor = col;
                c.rows.add(r);
            } else {
                c.rows.add(new Row(label, 0xCDE6FF, "-", 0xDDDDDD));
            }
        }
        return c.rows.isEmpty() ? null : c;
    }

    private Content staffContent(boolean chatOpen) {
        StaffList module = Modules.get().get(StaffList.class);
        if (module == null || !module.isActive()) {
            if (!chatOpen) return null;
            Content c = new Content("STAFF");
            c.rows.add(new Row("staff-list is off", 0x6F8FB8, null, 0));
            return c;
        }

        List<StaffList.StaffEntry> entries = module.onlineStaff();
        if (entries.isEmpty()) {
            if (!staffShowEmpty.get() && !chatOpen) return null;
            Content c = new Content("STAFF TRACKER");
            c.rows.add(new Row("No staff online", 0x9CC8FF, "CLEAR", 0x7CF0A0));
            return c;
        }

        Content c = new Content("STAFF \u00B7 " + entries.size() + " ONLINE");
        c.alert = true;
        for (StaffList.StaffEntry se : entries) {
            int leftCol = se.inRenderDistance ? 0xFF6B7A : 0xFFE29A;
            int rightCol = se.inRenderDistance ? 0xFF4A5A : 0x9CC8FF;
            c.rows.add(new Row(se.displayName, leftCol, se.statusRight(), rightCol));
        }
        return c;
    }

    private static String facing(float yaw) {
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return dirs[(int) Math.floor(((yaw + 22.5) % 360) / 45.0) & 7];
    }

    // ------------------------------------------------------------------ types

    private static final class Content {
        final String title;
        String big;
        boolean alert;
        final List<Row> rows = new ArrayList<>(6);

        Content(String title) {
            this.title = title;
        }
    }

    private static final class Row {
        final String left;
        final int leftColor;
        final String right;
        final int rightColor;
        double bar = -1;
        int barColor;

        Row(String left, int leftColor, String right, int rightColor) {
            this.left = left;
            this.leftColor = leftColor;
            this.right = right;
            this.rightColor = rightColor;
        }
    }

    private static final class Panel {
        final Setting<Boolean> enabled;
        final Setting<Integer> x;
        final Setting<Integer> y;
        boolean valid;
        double bx, by, bw, bh;

        Panel(String name, SettingGroup group, boolean on, int x, int y) {
            enabled = group.add(new BoolSetting.Builder().name(name).defaultValue(on).build());
            this.x = group.add(new IntSetting.Builder().name(name + "-x").defaultValue(x).sliderRange(0, 3840).build());
            this.y = group.add(new IntSetting.Builder().name(name + "-y").defaultValue(y).sliderRange(0, 2160).build());
        }
    }
}
