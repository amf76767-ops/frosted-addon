package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.modules.hud.FrostedHud;
import com.manu.frosted.util.Draw2D;
import com.manu.frosted.util.FrostedPanel;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Staff tracker for DonutSMP: tracks known and custom staff against the tab list
 * and loaded entities, alerts on joins/leaves with chat + sound, and feeds rich
 * status data (online duration, ping, gamemode, nearby warning) to FrostedHud
 * or its own standalone frosted panel.
 */
public class StaffList extends Module {
    private static final Set<String> KNOWN = Set.of(
        "0gsummer", "archivepedro", "auzzitech", "bloodspulse", "bobisfound",
        "captainmoose35", "_chaon", "cryptodaveyt", "dough4", "evify", "fallerfly",
        "fluffymaster07", "frenk_btw", "lzouzmp5", "munkerlich", "napooo_",
        "noahvdaa", "owen1212055", "pastagamer08", "showered", "splaterd",
        "u_vv", "w1zox_", "zeef69"
    );

    public static final class StaffEntry {
        public final String key;
        public String displayName;
        public final long joinedAt;
        public int latency;
        public GameMode gameMode;
        public boolean inRenderDistance;

        public StaffEntry(String key, String displayName, long joinedAt, int latency, GameMode gameMode, boolean inRenderDistance) {
            this.key = key;
            this.displayName = displayName;
            this.joinedAt = joinedAt;
            this.latency = latency;
            this.gameMode = gameMode;
            this.inRenderDistance = inRenderDistance;
        }

        public String statusRight() {
            long sec = Math.max(0L, (System.currentTimeMillis() - joinedAt) / 1000L);
            String dur = sec < 60 ? sec + "s" : (sec < 3600 ? (sec / 60) + "m " + (sec % 60) + "s" : (sec / 3600) + "h " + ((sec % 3600) / 60) + "m");
            if (inRenderDistance) return "NEARBY \u00B7 " + dur;
            if (gameMode == GameMode.SPECTATOR) return "SPEC \u00B7 " + dur;
            if (latency > 0) return latency + "ms \u00B7 " + dur;
            return dur;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHud = settings.createGroup("Standalone HUD");

    private final Setting<String> extraNames = sgGeneral.add(new StringSetting.Builder()
        .name("extra-names")
        .description("Additional names, comma-separated.")
        .defaultValue("")
        .wide()
        .build()
    );

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval")
        .description("Ticks between tab-list scans.")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 200)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("chat")
        .description("Report staff joins and leaves in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> regionAlert = sgGeneral.add(new BoolSetting.Builder()
        .name("region-alert")
        .description("Extra alert when a staff member joins.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> soundAlert = sgGeneral.add(new BoolSetting.Builder()
        .name("sound-alert")
        .description("Play a chime sound when a staff member joins or enters render distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> standaloneHud = sgHud.add(new BoolSetting.Builder()
        .name("standalone-hud")
        .description("Draw a standalone frosted staff panel when FrostedHud is disabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showWhenEmpty = sgHud.add(new BoolSetting.Builder()
        .name("show-empty")
        .description("Show the standalone panel even when no staff are online.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> hudScale = sgHud.add(new DoubleSetting.Builder()
        .name("scale")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 2.5)
        .build()
    );

    private final Setting<Integer> hudX = sgHud.add(new IntSetting.Builder()
        .name("x")
        .defaultValue(8)
        .sliderRange(0, 3840)
        .build()
    );

    private final Setting<Integer> hudY = sgHud.add(new IntSetting.Builder()
        .name("y")
        .defaultValue(400)
        .sliderRange(0, 2160)
        .build()
    );

    private final Map<String, StaffEntry> online = new HashMap<>();
    private final Color tmp = new Color();
    private int timer;
    private boolean dragging;
    private double dragOffX, dragOffY;
    private double lastW = 120, lastH = 40;

    public StaffList() {
        super(FrostedAddon.UTIL, "staff-list", "Tracks online DonutSMP staff with join duration, ping, nearby detection and frosted HUD.");
    }

    @Override
    public void onActivate() {
        online.clear();
        timer = 0;
        dragging = false;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(online.size());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player != null && mc.currentScreen instanceof ChatScreen) {
            double mx = mc.mouse.getX();
            double my = mc.mouse.getY();
            boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (!down) {
                dragging = false;
            } else {
                double bx = hudX.get(), by = hudY.get();
                if (!dragging && mx >= bx && mx <= bx + lastW && my >= by && my <= by + lastH) {
                    dragging = true;
                    dragOffX = mx - bx;
                    dragOffY = my - by;
                }
                if (dragging) {
                    hudX.set(Math.max(0, (int) Math.round(mx - dragOffX)));
                    hudY.set(Math.max(0, (int) Math.round(my - dragOffY)));
                }
            }
        } else {
            dragging = false;
        }

        if (mc.getNetworkHandler() == null || mc.world == null) return;
        if (--timer > 0) return;
        timer = interval.get();

        Set<String> nearbyNames = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            String n = p.getGameProfile().name();
            if (n != null) nearbyNames.add(n.toLowerCase(Locale.ROOT));
        }

        Map<String, PlayerListEntry> found = new HashMap<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (name == null || !isStaff(name)) continue;
            found.put(name.toLowerCase(Locale.ROOT), entry);
        }

        long nowMs = System.currentTimeMillis();
        for (Map.Entry<String, PlayerListEntry> e : found.entrySet()) {
            String key = e.getKey();
            PlayerListEntry ple = e.getValue();
            String disp = pretty(ple.getProfile().name());
            boolean near = nearbyNames.contains(key);

            StaffEntry existing = online.get(key);
            if (existing == null) {
                online.put(key, new StaffEntry(key, disp, nowMs, ple.getLatency(), ple.getGameMode(), near));
                if (notify.get()) {
                    if (regionAlert.get()) warning("Staff joined: %s (%dms)", disp, ple.getLatency());
                    else info("Staff online: %s", disp);
                }
                if (soundAlert.get() && mc.player != null) {
                    mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.4f);
                }
            } else {
                boolean becameNear = near && !existing.inRenderDistance;
                existing.displayName = disp;
                existing.latency = ple.getLatency();
                existing.gameMode = ple.getGameMode();
                existing.inRenderDistance = near;
                if (becameNear) {
                    if (notify.get()) warning("Staff %s is in your render distance!", disp);
                    if (soundAlert.get() && mc.player != null) {
                        mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.8f);
                    }
                }
            }
        }

        for (String key : new ArrayList<>(online.keySet())) {
            if (!found.containsKey(key)) {
                StaffEntry removed = online.remove(key);
                if (notify.get() && removed != null) info("Staff left: %s", removed.displayName);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || !standaloneHud.get()) return;
        FrostedHud hud = Modules.get().get(FrostedHud.class);
        if (hud != null && hud.isActive() && hud.isStaffPanelEnabled()) return;

        List<StaffEntry> list = onlineStaff();
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        if (list.isEmpty() && !showWhenEmpty.get() && !chatOpen) return;

        double s = hudScale.get() * Math.max(1.0, Draw2D.guiScale() * 0.5);
        double pad = 6 * s;
        double gapX = 12 * s;
        double titleScale = 0.74;

        TextRenderer tr = TextRenderer.get();
        tr.begin(s, false, true);
        double lineH = tr.getHeight(true) + 2 * s;

        String title = list.isEmpty() ? "STAFF TRACKER" : "STAFF \u00B7 " + list.size() + " ONLINE";
        double w = tr.getWidth(title, true) * titleScale + 16 * s;
        if (list.isEmpty()) {
            w = Math.max(w, tr.getWidth("No staff online", true) + gapX + tr.getWidth("CLEAR", true));
        } else {
            for (StaffEntry se : list) {
                double rw = tr.getWidth(se.displayName, true) + gapX + tr.getWidth(se.statusRight(), true) + 8 * s;
                w = Math.max(w, rw);
            }
        }
        tr.end();

        int rows = Math.max(1, list.size());
        double bw = Math.max(w + pad * 2 + 4 * s, 110 * s);
        double bh = pad * 2 + lineH * titleScale + 5 * s + rows * lineH;
        lastW = bw;
        lastH = bh;

        double bx = Math.min(hudX.get(), Math.max(0, Draw2D.screenW() - bw - 4));
        double by = Math.min(hudY.get(), Math.max(0, Draw2D.screenH() - bh - 4));

        int accent = list.isEmpty() ? 0x6EBEFF : 0xFF5A6A;
        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;

        Renderer2D.COLOR.begin();
        FrostedPanel.draw(bx, by, bw, bh, 6 * s, s, 215, dragging ? 0xFFFFFF : accent, true, true, true);

        double cy = by + pad;
        double dotR = 2.3 * s;
        int dotCol = list.isEmpty()
            ? Draw2D.argb(230, 0x7CF0A0)
            : Draw2D.argb((int) (180 + 75 * Math.sin(time * 6.0)), 0xFF4A5A);
        double dy = cy + lineH * titleScale * 0.5 - dotR * 0.2;
        Draw2D.roundRect(bx + pad + 2 * s, dy - dotR, dotR * 2, dotR * 2, dotR, dotCol, dotCol);
        cy += lineH * titleScale + 1 * s;
        Draw2D.rectH(bx + pad, cy, bw - pad * 2, Math.max(1, s * 0.8),
            Draw2D.argb(165, accent), Draw2D.argb(0, accent));
        cy += 4 * s;

        if (!list.isEmpty()) {
            double rowY = cy;
            for (StaffEntry se : list) {
                int statusDot = se.inRenderDistance ? 0xFF3B4E : (se.gameMode == GameMode.SPECTATOR ? 0xFFAA3B : 0xFFD878);
                double r = 1.8 * s;
                Draw2D.roundRect(bx + pad + 3 * s, rowY + lineH * 0.42 - r, r * 2, r * 2, r,
                    Draw2D.argb(240, statusDot), Draw2D.argb(240, statusDot));
                rowY += lineH;
            }
        }
        Renderer2D.COLOR.render();

        // Text pass
        tr.begin(s * titleScale, false, true);
        setColor(Draw2D.shade(accent, 0.38), 255);
        tr.render(title, bx + pad + 9 * s, by + pad, tmp, true);
        tr.end();

        tr.begin(s, false, true);
        double tx = bx + pad + 3 * s;
        if (list.isEmpty()) {
            setColor(0x9CC8FF, 255);
            tr.render("No staff online", tx, cy, tmp, true);
            String right = "CLEAR";
            setColor(0x7CF0A0, 255);
            tr.render(right, bx + bw - pad - tr.getWidth(right, true), cy, tmp, true);
        } else {
            for (StaffEntry se : list) {
                setColor(se.inRenderDistance ? 0xFF6B7A : 0xFFE29A, 255);
                tr.render(se.displayName, tx + 6 * s, cy, tmp, true);
                String right = se.statusRight();
                setColor(se.inRenderDistance ? 0xFF4A5A : 0x9CC8FF, 255);
                tr.render(right, bx + bw - pad - tr.getWidth(right, true), cy, tmp, true);
                cy += lineH;
            }
        }
        tr.end();
    }

    private void setColor(int rgb, int a) {
        tmp.r = (rgb >> 16) & 0xFF;
        tmp.g = (rgb >> 8) & 0xFF;
        tmp.b = rgb & 0xFF;
        tmp.a = a;
    }

    /** Sorted rich entries of staff currently online. */
    public List<StaffEntry> onlineStaff() {
        List<StaffEntry> list = new ArrayList<>(online.values());
        list.sort(Comparator.comparing(a -> a.displayName.toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Sorted display names of staff currently in the tab list. */
    public List<String> onlineNames() {
        List<String> names = new ArrayList<>(online.size());
        for (StaffEntry e : onlineStaff()) names.add(e.displayName);
        return names;
    }

    private boolean isStaff(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (KNOWN.contains(lower)) return true;

        String extra = extraNames.get();
        if (extra == null || extra.isEmpty()) return false;
        for (String part : extra.split(",")) {
            if (part.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String pretty(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        for (String name : KNOWN) {
            if (name.equalsIgnoreCase(raw)) {
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        }
        return raw;
    }
}
