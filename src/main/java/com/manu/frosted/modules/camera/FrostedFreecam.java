package com.manu.frosted.modules.camera;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;

/**
 * Enables Meteor's Freecam and leaves the aiming fix to MinecraftClientMixin,
 * which re-raycasts from the player's real view after every crosshair update.
 *
 * You can also bind Meteor's freecam directly; the fix applies whenever
 * Meteor's Freecam is active, not only when this module is.
 */
public class FrostedFreecam extends Module {
    private boolean delegated;

    public FrostedFreecam() {
        super(FrostedAddon.BASEHUNT, "freecam-plus", "Meteor freecam with player-view targeting.");
    }

    @Override
    public void onActivate() {
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && !freecam.isActive()) {
            freecam.enable();
            delegated = true;
        }
    }

    @Override
    public void onDeactivate() {
        if (!delegated) return;
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam != null && freecam.isActive()) freecam.disable();
        delegated = false;
    }
}
