package com.manu.frosted.modules.camera;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;

/**
 * Drives Meteor's FreeLook and adds one option: through-walls, which stops the
 * third-person camera from being clipped out of solid blocks.
 */
public class FrostedFreelook extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Camera ignores block clipping.")
        .defaultValue(true)
        .build()
    );

    private boolean delegated;

    public FrostedFreelook() {
        super(FrostedAddon.BASEHUNT, "freelook-plus", "Meteor free look with an optional through-walls camera.");
    }

    @Override
    public void onActivate() {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && !freeLook.isActive()) {
            freeLook.enable();
            delegated = true;
        }
    }

    @Override
    public void onDeactivate() {
        if (!delegated) return;
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) freeLook.disable();
        delegated = false;
    }

    public boolean throughWalls() {
        return throughWalls.get();
    }
}
