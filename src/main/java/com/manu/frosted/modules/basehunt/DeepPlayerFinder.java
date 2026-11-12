package com.manu.frosted.modules.basehunt;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;

import java.util.BitSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Detects underground player activity from light packets.
 *
 * When a section below the surface arrives with an initialised block-light
 * array that is entirely zero, something has carved out space there. Natural
 * terrain below ground is either not sent or carries light; a fresh all-zero
 * section means a tunnel, a room or a mine.
 *
 * This is packet observation only. Nothing is sent, no movement is touched.
 */
public class DeepPlayerFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Only flag chunks within this many chunks of you.")
        .defaultValue(12)
        .min(1)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> maxFlags = sgGeneral.add(new IntSetting.Builder()
        .name("max-flags")
        .description("Keep at most this many flagged chunks in memory.")
        .defaultValue(2048)
        .min(64)
        .sliderRange(64, 8192)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat message when a chunk is flagged.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sound = sgGeneral.add(new BoolSetting.Builder()
        .name("sound")
        .defaultValue(false)
        .build()
    );

    private final Set<Long> flagged = ConcurrentHashMap.newKeySet();
    private final Queue<Long> pending = new ConcurrentLinkedQueue<>();

    public DeepPlayerFinder() {
        super(FrostedAddon.BASEHUNT, "deep-player-finder", "Flags chunks with zeroed underground light — someone dug there.");
    }

    @Override
    public void onActivate() {
        flagged.clear();
        pending.clear();
    }

    @Override
    public void onDeactivate() {
        flagged.clear();
        pending.clear();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(flagged.size());
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof LightUpdateS2CPacket packet)) return;
        if (mc.world == null || mc.player == null) return;

        int cx = packet.getChunkX();
        int cz = packet.getChunkZ();

        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int radius = scanRadius.get();
        if (Math.abs(cx - pcx) > radius || Math.abs(cz - pcz) > radius) return;

        long key = ChunkPos.toLong(cx, cz);
        if (flagged.contains(key)) return;

        LightData data = packet.getData();
        BitSet inited = data.getInitedBlock();
        List<byte[]> nibbles = data.getBlockNibbles();
        int bottomSection = mc.world.getBottomY() >> 4;

        int index = 0;
        for (int bit = inited.nextSetBit(0); bit >= 0; bit = inited.nextSetBit(bit + 1)) {
            byte[] array = index < nibbles.size() ? nibbles.get(index) : null;
            index++;

            int sectionY = (bottomSection + bit) << 4;
            if (sectionY >= 16) continue;
            if (array == null || array.length == 0) continue;

            if (isAllZero(array)) {
                if (flagged.size() >= maxFlags.get()) return;
                flagged.add(key);
                pending.add(key);
                return;
            }
        }
    }

    private static boolean isAllZero(byte[] array) {
        for (byte b : array) {
            if (b != 0) return false;
        }
        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Long key;
        while ((key = pending.poll()) != null) {
            int x = (ChunkPos.getPackedX(key) << 4) + 8;
            int z = (ChunkPos.getPackedZ(key) << 4) + 8;
            if (notify.get()) info("Underground activity near %d, %d", x, z);
            if (sound.get() && mc.player != null) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.7f);
            }
        }
    }
}
