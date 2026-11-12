package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.sound.SoundEvents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Records where you died, in the current dimension, and appends it to a file.
 * On an anarchy economy server that is the difference between losing the set
 * and walking straight back to it.
 */
public class DeathCoords extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat")
        .description("Print the death position locally.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sound = sgGeneral.add(new BoolSetting.Builder()
        .name("sound")
        .description("Play a ping on death.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveToFile = sgGeneral.add(new BoolSetting.Builder()
        .name("save-to-file")
        .description("Append deaths to a file in the run directory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> fileName = sgGeneral.add(new StringSetting.Builder()
        .name("file-name")
        .description("Relative to the run directory.")
        .defaultValue("frosted-deaths.txt")
        .build()
    );

    private boolean dead;
    private double lastX;
    private double lastY;
    private double lastZ;

    public DeathCoords() {
        super(FrostedAddon.UTIL, "death-coords", "Logs the position where you died.");
    }

    @Override
    public void onActivate() {
        dead = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (!dead) {
            lastX = mc.player.getX();
            lastY = mc.player.getY();
            lastZ = mc.player.getZ();
        }

        boolean nowDead = mc.player.getHealth() <= 0.0f;
        if (nowDead && !dead) {
            dead = true;
            record((int) Math.floor(lastX), (int) Math.floor(lastY), (int) Math.floor(lastZ));
        } else if (!nowDead && dead) {
            dead = false;
        }
    }

    private void record(int x, int y, int z) {
        String dimension = mc.world.getRegistryKey().getValue().toString();

        if (chat.get()) info("Died at %d, %d, %d (%s)", x, y, z, dimension);
        if (sound.get()) {
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.6f);
        }
        if (saveToFile.get()) {
            try {
                Path path = mc.runDirectory.toPath().resolve(fileName.get());
                String entry = Instant.now() + "\t" + dimension + "\t" + x + ", " + y + ", " + z
                    + System.lineSeparator();
                Files.writeString(path, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                error("Write failed: %s", e.getMessage());
            }
        }
    }
}
