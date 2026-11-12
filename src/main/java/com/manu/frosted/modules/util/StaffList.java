package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Staff tracker, ported from Anubis: known DonutSMP staff names plus whatever
 * you add, tracked against the tab list. Reports joins and leaves; the on-screen list is the
 * frosted-hud staff panel.
 */
public class StaffList extends Module {
    private static final Set<String> KNOWN = Set.of(
        "0gsummer", "archivepedro", "auzzitech", "bloodspulse", "bobisfound",
        "captainmoose35", "_chaon", "cryptodaveyt", "dough4", "evify", "fallerfly",
        "fluffymaster07", "frenk_btw", "lzouzmp5", "munkerlich", "napooo_",
        "noahvdaa", "owen1212055", "pastagamer08", "showered", "splaterd",
        "u_vv", "w1zox_", "zeef69"
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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
        .defaultValue(40)
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

    private final Set<String> online = new HashSet<>();
    private int timer;

    public StaffList() {
        super(FrostedAddon.UTIL, "staff-list", "Lists online DonutSMP staff. Turn on the staff panel in frosted-hud to see them on screen.");
    }

    @Override
    public void onActivate() {
        online.clear();
        timer = 0;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(online.size());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.getNetworkHandler() == null) return;
        if (--timer > 0) return;
        timer = interval.get();

        Set<String> now = new HashSet<>();
        List<String> displayNames = new ArrayList<>();

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (name == null) continue;
            if (!isStaff(name)) continue;
            now.add(name.toLowerCase(Locale.ROOT));
            displayNames.add(name);
        }

        for (String name : now) {
            if (!online.contains(name)) {
                if (notify.get()) {
                    if (regionAlert.get()) warning("Staff joined: %s", pretty(name));
                    else info("Staff online: %s", pretty(name));
                }
            }
        }

        for (String name : new ArrayList<>(online)) {
            if (!now.contains(name)) {
                if (notify.get()) info("Staff left: %s", pretty(name));
            }
        }

        online.clear();
        online.addAll(now);
    }

    /** Sorted display names of staff currently in the tab list. */
    public List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (String name : online) names.add(pretty(name));
        names.sort(String::compareTo);
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

    private String pretty(String lower) {
        for (String name : KNOWN) {
            if (name.equalsIgnoreCase(lower)) {
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        }
        return lower;
    }
}
