package com.manu.frosted.modules.basehunt;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers storage blocks and spawners while their chunk is loaded and keeps drawing
 * them as a "ghost" once the chunk is gone, so a base you flew past stays on your map.
 *
 * It does its own scan of the loaded chunks, it does not read Meteor's ESP modules:
 * those draw only what is currently loaded and expose no list to borrow.
 */
public class GhostEsp extends Module {
    public enum Kind {
        Chest(255, 175, 45),
        Barrel(210, 150, 90),
        EnderChest(175, 95, 255),
        Shulker(255, 120, 215),
        Spawner(255, 70, 70),
        Furnace(190, 195, 205),
        Hopper(140, 150, 170),
        Dispenser(160, 170, 185);

        final int r, g, b;

        Kind(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private final SettingGroup sgKinds = settings.createGroup("Remember");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> chests = sgKinds.add(new BoolSetting.Builder()
        .name("chests").description("Chests and trapped chests.").defaultValue(true).build());
    private final Setting<Boolean> barrels = sgKinds.add(new BoolSetting.Builder()
        .name("barrels").defaultValue(true).build());
    private final Setting<Boolean> enderChests = sgKinds.add(new BoolSetting.Builder()
        .name("ender-chests").defaultValue(true).build());
    private final Setting<Boolean> shulkers = sgKinds.add(new BoolSetting.Builder()
        .name("shulkers").defaultValue(true).build());
    private final Setting<Boolean> spawners = sgKinds.add(new BoolSetting.Builder()
        .name("spawners").defaultValue(true).build());
    private final Setting<Boolean> furnaces = sgKinds.add(new BoolSetting.Builder()
        .name("furnaces").description("Furnaces, blast furnaces and smokers.").defaultValue(false).build());
    private final Setting<Boolean> hoppers = sgKinds.add(new BoolSetting.Builder()
        .name("hoppers").defaultValue(false).build());
    private final Setting<Boolean> dispensers = sgKinds.add(new BoolSetting.Builder()
        .name("dispensers").description("Dispensers and droppers.").defaultValue(false).build());

    private final Setting<Integer> rememberRange = sgGeneral.add(new IntSetting.Builder()
        .name("remember-range")
        .description("Only blocks closer than this when scanned are remembered, a stand-in for 'things you have seen'.")
        .defaultValue(160).min(16).sliderRange(16, 512).build());

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance").description("Ghosts further away than this are not drawn.")
        .defaultValue(400).min(32).sliderRange(32, 1024).build());

    private final Setting<Integer> maxEntries = sgGeneral.add(new IntSetting.Builder()
        .name("max-entries").description("Memory cap. New finds are ignored when it is full.")
        .defaultValue(20000).min(100).sliderRange(100, 50000).build());

    private final Setting<Boolean> persist = sgGeneral.add(new BoolSetting.Builder()
        .name("save-to-disk").description("Keep the memory per server in .minecraft/frosted.")
        .defaultValue(true).build());

    private final Setting<Boolean> forgetOnDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("forget-on-disable").description("Wipe the memory (and its file) when the module is turned off.")
        .defaultValue(false).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<Integer> fillAlpha = sgRender.add(new IntSetting.Builder()
        .name("fill-alpha").defaultValue(35).min(0).sliderRange(0, 255).build());

    private final Setting<Integer> lineAlpha = sgRender.add(new IntSetting.Builder()
        .name("line-alpha").defaultValue(210).min(0).sliderRange(0, 255).build());

    private final Setting<Boolean> pulse = sgRender.add(new BoolSetting.Builder()
        .name("pulse").description("Slow breathing so ghosts are easy to tell from real boxes.")
        .defaultValue(true).build());

    private final Setting<Boolean> drawLoaded = sgRender.add(new BoolSetting.Builder()
        .name("draw-loaded").description("Also draw remembered blocks whose chunk is loaded right now.")
        .defaultValue(false).build());

    /** dimension id -> packed block position -> kind */
    private final Map<String, Map<Long, Kind>> memory = new HashMap<>();
    private final Set<Long> pending = new HashSet<>();

    private String loadedKey;
    private boolean dirty;
    private int timer;

    public GhostEsp() {
        super(FrostedAddon.BASEHUNT, "ghost-esp", "Remembers storage and spawners you have seen and keeps showing them after the chunk unloads.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        if (persist.get() && dirty) save();
        if (forgetOnDisable.get()) {
            memory.clear();
            dirty = false;
            deleteFile();
        }
        pending.clear();
        loadedKey = null;
        if (!forgetOnDisable.get()) memory.clear();
    }

    @Override
    public String getInfoString() {
        return countAll() + " saved";
    }

    // ------------------------------------------------------------ scanning

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        ChunkPos pos = event.chunk().getPos();
        pending.add(ChunkPos.toLong(pos.x, pos.z));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            if (loadedKey != null) {
                if (persist.get() && dirty) save();
                memory.clear();
                pending.clear();
                loadedKey = null;
            }
            return;
        }

        String key = serverKey();
        if (!key.equals(loadedKey)) {
            if (loadedKey != null && persist.get() && dirty) save();
            memory.clear();
            pending.clear();
            loadedKey = key;
            dirty = false;
            if (persist.get()) load();
        }

        Map<Long, Kind> map = memory.computeIfAbsent(dimension(), k -> new HashMap<>());

        if (!pending.isEmpty()) {
            for (Long packed : pending) {
                int cx = ChunkPos.getPackedX(packed);
                int cz = ChunkPos.getPackedZ(packed);
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                if (chunk != null) scanChunk(chunk, map);
            }
            pending.clear();
        }

        timer++;
        if (timer % 20 == 0) refresh(map);
        if (timer % 6000 == 0 && persist.get() && dirty) save();
    }

    /** Drops entries that are gone from loaded chunks, then picks up anything new nearby. */
    private void refresh(Map<Long, Kind> map) {
        Iterator<Map.Entry<Long, Kind>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Kind> entry = it.next();
            BlockPos pos = BlockPos.fromLong(entry.getKey());
            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (classify(mc.world.getBlockState(pos).getBlock()) != entry.getValue()) {
                it.remove();
                dirty = true;
            }
        }

        int radius = (int) Math.ceil(rememberRange.get() / 16.0);
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                if (chunk != null) scanChunk(chunk, map);
            }
        }
    }

    private void scanChunk(WorldChunk chunk, Map<Long, Kind> map) {
        double rangeSq = (double) rememberRange.get() * rememberRange.get();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockPos pos = blockEntity.getPos();
            Kind kind = classify(blockEntity.getCachedState().getBlock());
            if (kind == null || !enabled(kind)) continue;
            if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) continue;

            long key = pos.asLong();
            if (map.get(key) == kind) continue;
            if (countAll() >= maxEntries.get()) return;
            map.put(key, kind);
            dirty = true;
        }
    }

    private static Kind classify(Block block) {
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) return Kind.Chest;
        if (block == Blocks.BARREL) return Kind.Barrel;
        if (block == Blocks.ENDER_CHEST) return Kind.EnderChest;
        if (block instanceof ShulkerBoxBlock) return Kind.Shulker;
        if (block == Blocks.SPAWNER) return Kind.Spawner;
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER) return Kind.Furnace;
        if (block == Blocks.HOPPER) return Kind.Hopper;
        if (block == Blocks.DISPENSER || block == Blocks.DROPPER) return Kind.Dispenser;
        return null;
    }

    private boolean enabled(Kind kind) {
        return switch (kind) {
            case Chest -> chests.get();
            case Barrel -> barrels.get();
            case EnderChest -> enderChests.get();
            case Shulker -> shulkers.get();
            case Spawner -> spawners.get();
            case Furnace -> furnaces.get();
            case Hopper -> hoppers.get();
            case Dispenser -> dispensers.get();
        };
    }

    private int countAll() {
        int total = 0;
        for (Map<Long, Kind> map : memory.values()) total += map.size();
        return total;
    }

    // ------------------------------------------------------------ rendering

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        Map<Long, Kind> map = memory.get(dimension());
        if (map == null || map.isEmpty()) return;

        double breathe = pulse.get() ? 0.8 + 0.2 * Math.sin(System.currentTimeMillis() / 350.0) : 1.0;
        int fa = clamp((int) (fillAlpha.get() * breathe));
        int la = clamp((int) (lineAlpha.get() * breathe));

        Kind[] kinds = Kind.values();
        Color[] sides = new Color[kinds.length];
        Color[] lines = new Color[kinds.length];
        for (Kind kind : kinds) {
            // Pull every colour a bit toward ice so ghosts look different from live ESP boxes.
            int r = mix(kind.r, 210), g = mix(kind.g, 235), b = mix(kind.b, 255);
            sides[kind.ordinal()] = new Color(r, g, b, fa);
            lines[kind.ordinal()] = new Color(r, g, b, la);
        }

        double maxSq = (double) maxDistance.get() * maxDistance.get();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        ShapeMode mode = shapeMode.get();
        boolean loadedToo = drawLoaded.get();

        for (Map.Entry<Long, Kind> entry : map.entrySet()) {
            Kind kind = entry.getValue();
            if (!enabled(kind)) continue;

            BlockPos pos = BlockPos.fromLong(entry.getKey());
            double dx = pos.getX() + 0.5 - px;
            double dy = pos.getY() + 0.5 - py;
            double dz = pos.getZ() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > maxSq) continue;
            if (!loadedToo && mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;

            event.renderer.box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                sides[kind.ordinal()], lines[kind.ordinal()], mode, 0);
        }
    }

    private static int mix(int channel, int target) {
        return (int) Math.round(channel + (target - channel) * 0.25);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ---------------------------------------------------------- persistence

    private String dimension() {
        return mc.world.getRegistryKey().getValue().toString();
    }

    private String serverKey() {
        ServerInfo info = mc.getCurrentServerEntry();
        String raw = info != null ? info.address : "singleplayer";
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Path file() {
        return mc.runDirectory.toPath().resolve("frosted").resolve("ghost-" + loadedKey + ".txt");
    }

    private void save() {
        if (loadedKey == null) return;
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Map<Long, Kind>> dim : memory.entrySet()) {
                for (Map.Entry<Long, Kind> entry : dim.getValue().entrySet()) {
                    BlockPos pos = BlockPos.fromLong(entry.getKey());
                    out.append(dim.getKey()).append('\t')
                        .append(pos.getX()).append('\t').append(pos.getY()).append('\t').append(pos.getZ()).append('\t')
                        .append(entry.getValue().name()).append('\n');
                }
            }
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            error("Save failed: %s", e.getMessage());
        }
    }

    private void load() {
        try {
            Path path = file();
            if (!Files.exists(path)) return;
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.split("\t");
                if (parts.length != 5) continue;
                try {
                    Kind kind = Kind.valueOf(parts[4]);
                    long key = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])).asLong();
                    memory.computeIfAbsent(parts[0], k -> new HashMap<>()).put(key, kind);
                } catch (IllegalArgumentException ignored) {
                    // bad line, skip it
                }
            }
        } catch (IOException e) {
            error("Load failed: %s", e.getMessage());
        }
    }

    private void deleteFile() {
        if (loadedKey == null) return;
        try {
            Files.deleteIfExists(file());
        } catch (IOException ignored) {
        }
    }
}
