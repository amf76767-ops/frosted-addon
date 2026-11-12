package com.manu.frosted.modules.camera;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;

/**
 * Freecam Plus: drives Meteor's Freecam with player-view targeting (via MinecraftClientMixin
 * and GameRendererMixin) and exposes all important Freecam settings directly here.
 */
public class FrostedFreecam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Your speed while in freecam.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 5.0)
        .onChanged(v -> syncSettings())
        .build()
    );

    private final Setting<Double> scrollSens = sgGeneral.add(new DoubleSetting.Builder()
        .name("scroll-sensitivity")
        .description("Change freecam speed with the scroll wheel (0 to disable).")
        .defaultValue(0.5)
        .min(0.0)
        .sliderRange(0.0, 2.0)
        .onChanged(v -> syncSettings())
        .build()
    );

    private final Setting<Boolean> reloadChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("reload-chunks")
        .description("Disables cave culling in freecam.")
        .defaultValue(true)
        .onChanged(v -> syncSettings())
        .build()
    );

    private final Setting<Boolean> showHands = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hands")
        .description("Render your hands while in freecam.")
        .defaultValue(true)
        .onChanged(v -> syncSettings())
        .build()
    );

    private final Setting<Boolean> staySneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("stay-sneaking")
        .description("Keep sneaking while in freecam.")
        .defaultValue(true)
        .onChanged(v -> syncSettings())
        .build()
    );

    private boolean delegated;

    public FrostedFreecam() {
        super(FrostedAddon.BASEHUNT, "freecam-plus", "Meteor freecam with player-view targeting and built-in controls.");
    }

    @Override
    public void onActivate() {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null) {
            syncSettings();
            if (!freecam.isActive()) {
                freecam.enable();
                delegated = true;
            }
        }
    }

    @Override
    public void onDeactivate() {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && freecam.isActive()) {
            freecam.disable();
        }
        delegated = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam == null) return;
        if (!freecam.isActive()) {
            if (!delegated) {
                syncSettings();
                freecam.enable();
                delegated = true;
            } else {
                // Freecam disabled itself (e.g. damage/log), so disable freecam-plus too
                delegated = false;
                disable();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void syncSettings() {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam == null) return;
        setSetting(freecam, "speed", speed.get());
        setSetting(freecam, "speed-scroll-sensitivity", scrollSens.get());
        setSetting(freecam, "reload-chunks", reloadChunks.get());
        setSetting(freecam, "show-hands", showHands.get());
        setSetting(freecam, "stay-sneaking", staySneaking.get());
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
}
