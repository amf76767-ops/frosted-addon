package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.update.UpdateManager;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Settings for the self-updater. Clicking the module checks for a new version right now
 * (it switches itself back off); the automatic check at startup is controlled below.
 */
public class FrostedUpdater extends Module {
    public static final String DEFAULT_REPO = "amf76767-ops/frosted-addon";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> auto = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-check").description("Look for a new version when the game starts.").defaultValue(true).build()
    );

    private final Setting<String> repo = sgGeneral.add(new StringSetting.Builder()
        .name("repo").description("GitHub repository that publishes the releases, as owner/name.").defaultValue(DEFAULT_REPO).build()
    );

    private final Setting<Boolean> prereleases = sgGeneral.add(new BoolSetting.Builder()
        .name("prereleases").description("Also offer pre-releases.").defaultValue(false).build()
    );

    public FrostedUpdater() {
        super(FrostedAddon.UTIL, "frosted-updater", "Click to check for a new Frosted version. Settings control the automatic check.");
    }

    @Override
    public void onActivate() {
        toggle();
        info("Checking for updates...");
        UpdateManager.INSTANCE.requestCheck();
    }

    public boolean autoCheck() {
        return auto.get();
    }

    public String repo() {
        String value = repo.get();
        if (value == null || value.trim().isEmpty()) {
            repo.set(DEFAULT_REPO);
            return DEFAULT_REPO;
        }
        return value.trim();
    }

    public boolean prereleases() {
        return prereleases.get();
    }
}
