package com.manu.frosted.modules.camera;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.orbit.EventHandler;

/**
 * FreeLook Plus: drives Meteor's FreeLook, adds through-walls camera clipping bypass,
 * and exposes FreeLook settings directly here.
 */
public class FrostedFreelook extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Camera ignores block clipping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> togglePerspective = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-perspective")
        .description("Automatically switch to third-person perspective when enabled.")
        .defaultValue(true)
        .onChanged(v -> syncSettings())
        .build()
    );

    private final Setting<Double> sensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("camera-sensitivity")
        .description("Rotation speed of the camera.")
        .defaultValue(8.0)
        .min(1.0)
        .sliderRange(1.0, 10.0)
        .onChanged(v -> syncSettings())
        .build()
    );

    private boolean delegated;

    public FrostedFreelook() {
        super(FrostedAddon.BASEHUNT, "freelook-plus", "Meteor free look with through-walls camera and built-in controls.");
    }

    @Override
    public void onActivate() {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null) {
            syncSettings();
            if (!freeLook.isActive()) {
                freeLook.enable();
                delegated = true;
            }
        }
    }

    @Override
    public void onDeactivate() {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) {
            freeLook.disable();
        }
        delegated = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook == null) return;
        if (!freeLook.isActive()) {
            if (!delegated) {
                syncSettings();
                freeLook.enable();
                delegated = true;
            } else {
                delegated = false;
                disable();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void syncSettings() {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook == null) return;
        setSetting(freeLook, "toggle-perspective", togglePerspective.get());
        setSetting(freeLook, "camera-sensitivity", sensitivity.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> void setSetting(Module m, String name, T val) {
        Setting<?> s = m.settings.get(name);
        if (s != null) {
            try {
                ((Setting<T>) s).set(val);
            } catch (Throwable ignored) {}
        }
    }

    public boolean throughWalls() {
        return throughWalls.get();
    }
}
