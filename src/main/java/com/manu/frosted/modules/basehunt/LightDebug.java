package com.manu.frosted.modules.basehunt;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Light debug, ported from Anubis: scans the blocks around the player and draws
 * merged boxes over lit blocks.
 *
 * Blocks at or above the threshold are collected, then merged per Y layer into
 * rectangles so a lit room reads as a few boxes instead of hundreds of cubes.
 */
public class LightDebug extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("render-radius")
        .description("Block radius around the player.")
        .defaultValue(16)
        .min(4)
        .sliderRange(4, 48)
        .build()
    );

    private final Setting<Integer> minLight = sgGeneral.add(new IntSetting.Builder()
        .name("min-light")
        .description("Mark blocks with block light at or above this level.")
        .defaultValue(7)
        .min(0)
        .sliderRange(0, 15)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ms")
        .defaultValue(750)
        .min(100)
        .sliderRange(100, 5000)
        .build()
    );

    private final Setting<Boolean> denoise = sgGeneral.add(new BoolSetting.Builder()
        .name("denoise")
        .description("Only keep lit blocks with a lit neighbour.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 240, 120, 70))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 245, 170, 220))
        .build()
    );

    private final List<Box> boxes = new ArrayList<>();
    private long lastScan;

    public LightDebug() {
        super(FrostedAddon.BASEHUNT, "light-debug", "Highlights artificial light underground.");
    }

    @Override
    public void onActivate() {
        boxes.clear();
        lastScan = 0;
    }

    @Override
    public void onDeactivate() {
        boxes.clear();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(boxes.size());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScan < cooldown.get()) return;
        lastScan = now;

        boxes.clear();

        int r = radius.get();
        int threshold = minLight.get();
        BlockPos center = mc.player.getBlockPos();

        Map<Integer, Set<Long>> byLayer = new HashMap<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    if (mc.world.getBlockState(pos).isAir()) continue;

                    int light = mc.world.getLightLevel(LightType.BLOCK, pos);
                    if (light < threshold) continue;
                    if (denoise.get() && !hasLitNeighbour(pos, threshold)) continue;

                    byLayer.computeIfAbsent(pos.getY(), k -> new HashSet<>()).add(pack(pos.getX(), pos.getZ()));
                }
            }
        }

        for (Map.Entry<Integer, Set<Long>> entry : byLayer.entrySet()) {
            mergeLayer(entry.getKey(), entry.getValue());
        }
    }

    private void mergeLayer(int y, Set<Long> cells) {
        Set<Long> visited = new HashSet<>();
        List<Long> ordered = new ArrayList<>(cells);
        ordered.sort(Comparator.comparingLong(v -> v));

        for (long cell : ordered) {
            if (visited.contains(cell)) continue;

            int x = unpackX(cell);
            int z = unpackZ(cell);

            int width = 1;
            while (cells.contains(pack(x + width, z)) && !visited.contains(pack(x + width, z))) width++;

            int depth = 1;
            boolean canGrow = true;
            while (canGrow) {
                for (int i = 0; i < width; i++) {
                    long next = pack(x + i, z + depth);
                    if (!cells.contains(next) || visited.contains(next)) {
                        canGrow = false;
                        break;
                    }
                }
                if (canGrow) depth++;
            }

            for (int dz = 0; dz < depth; dz++) {
                for (int dx = 0; dx < width; dx++) {
                    visited.add(pack(x + dx, z + dz));
                }
            }

            boxes.add(new Box(x, y, z, x + width, y + 1, z + depth));
        }
    }

    private boolean hasLitNeighbour(BlockPos pos, int threshold) {
        if (mc.world == null) return false;
        for (var direction : net.minecraft.util.math.Direction.values()) {
            BlockPos neighbour = pos.offset(direction);
            if (mc.world.getBlockState(neighbour).isAir()) continue;
            if (mc.world.getLightLevel(LightType.BLOCK, neighbour) >= threshold) return true;
        }
        return false;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long v) {
        return (int) (v >> 32);
    }

    private static int unpackZ(long v) {
        return (int) v;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (boxes.isEmpty()) return;

        for (Box box : boxes) {
            event.renderer.box(box, color.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }
}
