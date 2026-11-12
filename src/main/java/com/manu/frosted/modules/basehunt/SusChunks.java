package com.manu.frosted.modules.basehunt;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.FrostedChunkRenderer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of Corz's SusChunkFinderV2.
 *
 * Scores chunks by growth that only advances on random ticks. Unlike a raw
 * block count, each witness must pass a shape test: a single vine block proves
 * nothing, a seven-block vine run does. Flags are then merged into zones so a
 * base reads as one site instead of forty chunks.
 */
public class SusChunks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetect = settings.createGroup("Detect");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("1 = flags a lot, 10 = only the obvious sites. Threshold = sensitivity x 5.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> scanBudget = sgGeneral.add(new IntSetting.Builder()
        .name("scan-budget")
        .description("Chunks scanned per tick.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Boolean> soundPing = sgGeneral.add(new BoolSetting.Builder()
        .name("sound")
        .description("Play a ping when a new zone appears.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatNotify = sgGeneral.add(new BoolSetting.Builder()
        .name("chat")
        .description("Announce new zones in chat with coordinates and distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> amethyst = sgDetect.add(new BoolSetting.Builder()
        .name("amethyst")
        .description("Budding amethyst, clusters and buds. The strongest signal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> kelp = sgDetect.add(new BoolSetting.Builder()
        .name("kelp")
        .description("Tall kelp columns only.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bamboo = sgDetect.add(new BoolSetting.Builder()
        .name("bamboo")
        .description("Max-height bamboo only.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> berries = sgDetect.add(new BoolSetting.Builder()
        .name("berries")
        .description("Sweet berry bushes at max growth.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vines = sgDetect.add(new BoolSetting.Builder()
        .name("vines")
        .description("Vines grown into long runs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dripstone = sgDetect.add(new BoolSetting.Builder()
        .name("dripstone")
        .description("Dripstone spikes longer than natural generation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderY = sgRender.add(new IntSetting.Builder()
        .name("render-y")
        .description("Height of the flat chunk highlights.")
        .defaultValue(60)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .description("Fill opacity, 0-255.")
        .defaultValue(90)
        .min(0)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline and zone beam color.")
        .defaultValue(new SettingColor(255, 60, 200, 255))
        .build()
    );

    private static final int AMETHYST_WEIGHT = 6;
    private static final int OTHER_WEIGHT = 2;
    private static final int AMETHYST_CAP = 16;
    private static final int OTHER_CAP = 6;
    private static final int ZONE_RADIUS = 3;

    private final Long2ObjectMap<ChunkScore> scores = new Long2ObjectOpenHashMap<>();
    private final LongSet queued = new LongOpenHashSet();
    private final Queue<Long> scanQueue = new ConcurrentLinkedQueue<>();
    private final LongSet announced = new LongOpenHashSet();

    private List<Long> flagged = List.of();
    private List<Zone> zones = List.of();
    private boolean zonesDirty;

    public SusChunks() {
        super(FrostedAddon.BASEHUNT, "sus-chunks", "Flags chunks with growth that proves long load time.");
    }

    @Override
    public void onActivate() {
        scores.clear();
        queued.clear();
        scanQueue.clear();
        announced.clear();
        flagged = List.of();
        zones = List.of();

        if (mc.player != null) {
            int pcx = mc.player.getBlockX() >> 4;
            int pcz = mc.player.getBlockZ() >> 4;
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) enqueue(pcx + dx, pcz + dz);
            }
        }
    }

    @Override
    public void onDeactivate() {
        scores.clear();
        queued.clear();
        scanQueue.clear();
        flagged = List.of();
        zones = List.of();
    }

    @Override
    public String getInfoString() {
        return zones.size() + " zones";
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        ChunkPos pos = event.chunk().getPos();
        enqueue(pos.x, pos.z);
    }

    private void enqueue(int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        if (queued.add(key)) scanQueue.add(key);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;

        int budget = scanBudget.get();
        while (budget-- > 0) {
            Long key = scanQueue.poll();
            if (key == null) break;
            queued.remove(key);

            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            WorldChunk chunk = mc.world.getChunk(cx, cz);
            if (chunk == null) continue;

            ChunkScore score = scan(chunk, key);
            if (score == null) scores.remove(key);
            else scores.put(key, score);
            zonesDirty = true;
        }

        if (zonesDirty) {
            rebuild();
            zonesDirty = false;
        }
    }

    private int minScore() {
        return sensitivity.get() * 5;
    }

    // --------------------------------------------------------------- scanning

    private ChunkScore scan(WorldChunk chunk, long key) {
        if (!amethyst.get() && !kelp.get() && !bamboo.get() && !berries.get() && !vines.get() && !dripstone.get()) {
            return null;
        }

        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();

        int amethystCount = 0;
        int kelpCount = 0;
        int bambooCount = 0;
        int berryCount = 0;
        int vineCount = 0;
        int dripstoneCount = 0;

        double total = 0;
        double weightedX = 0;
        double weightedZ = 0;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(state -> classify(state.getBlock()) != 0)) continue;

            int sectionY = bottomY + (i << 4);

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        int kind = classify(state.getBlock());
                        if (kind == 0) continue;

                        int worldY = sectionY + y;
                        double weight = 0;

                        switch (kind) {
                            case 1 -> {
                                if (amethystCount < AMETHYST_CAP) {
                                    amethystCount++;
                                    weight = AMETHYST_WEIGHT;
                                }
                            }
                            case 2 -> {
                                if (kelpCount < OTHER_CAP && isTallKelp(sections, bottomY, x, worldY, z, state)) {
                                    kelpCount++;
                                    weight = OTHER_WEIGHT;
                                }
                            }
                            case 3 -> {
                                if (bambooCount < OTHER_CAP && isTallBamboo(sections, bottomY, x, worldY, z)) {
                                    bambooCount++;
                                    weight = OTHER_WEIGHT;
                                }
                            }
                            case 4 -> {
                                if (berryCount < OTHER_CAP && state.get(Properties.AGE_3) == 3) {
                                    berryCount++;
                                    weight = OTHER_WEIGHT;
                                }
                            }
                            case 5 -> {
                                if (vineCount < OTHER_CAP && isLongVine(sections, bottomY, x, worldY, z)) {
                                    vineCount++;
                                    weight = OTHER_WEIGHT;
                                }
                            }
                            case 6 -> {
                                if (dripstoneCount < OTHER_CAP && isLongDripstone(sections, bottomY, x, worldY, z)) {
                                    dripstoneCount++;
                                    weight = OTHER_WEIGHT;
                                }
                            }
                        }

                        if (weight > 0) {
                            total += weight;
                            weightedX += ((ChunkPos.getPackedX(key) << 4) + x + 0.5) * weight;
                            weightedZ += ((ChunkPos.getPackedZ(key) << 4) + z + 0.5) * weight;
                        }
                    }
                }
            }
        }

        double score = amethystCount * AMETHYST_WEIGHT
            + (kelpCount + bambooCount + berryCount + vineCount + dripstoneCount) * OTHER_WEIGHT;

        if (score < minScore()) return null;
        return new ChunkScore(key, score, total, weightedX, weightedZ);
    }

    private int classify(Block block) {
        if (amethyst.get() && isAmethyst(block)) return 1;
        if (kelp.get() && (block == Blocks.KELP || block == Blocks.KELP_PLANT)) return 2;
        if (bamboo.get() && block == Blocks.BAMBOO) return 3;
        if (berries.get() && block == Blocks.SWEET_BERRY_BUSH) return 4;
        if (vines.get() && block == Blocks.VINE) return 5;
        if (dripstone.get() && block == Blocks.POINTED_DRIPSTONE) return 6;
        return 0;
    }

    private static boolean isAmethyst(Block block) {
        return block == Blocks.BUDDING_AMETHYST
            || block == Blocks.AMETHYST_CLUSTER
            || block == Blocks.LARGE_AMETHYST_BUD
            || block == Blocks.MEDIUM_AMETHYST_BUD
            || block == Blocks.SMALL_AMETHYST_BUD;
    }

    private static Block blockAt(ChunkSection[] sections, int bottomY, int x, int y, int z) {
        int index = (y - bottomY) >> 4;
        if (index < 0 || index >= sections.length) return Blocks.AIR;
        ChunkSection section = sections[index];
        if (section == null || section.isEmpty()) return Blocks.AIR;
        return section.getBlockState(x, y & 15, z).getBlock();
    }

    private static int runLength(ChunkSection[] sections, int bottomY, int x, int y, int z, int step, Block a, Block b) {
        int count;
        for (count = 1; count < 40; count++) {
            y += step;
            Block block = blockAt(sections, bottomY, x, y, z);
            if (block != a && block != b) break;
        }
        return count;
    }

    private static boolean isTallKelp(ChunkSection[] sections, int bottomY, int x, int y, int z, BlockState state) {
        Block above = blockAt(sections, bottomY, x, y + 1, z);
        if (above == Blocks.KELP || above == Blocks.KELP_PLANT) return false;

        int run = runLength(sections, bottomY, x, y, z, -1, Blocks.KELP, Blocks.KELP_PLANT);
        boolean fullAge = state.isOf(Blocks.KELP) && state.contains(Properties.AGE_25) && state.get(Properties.AGE_25) == 25;
        return (fullAge && run >= 8) || run >= 14;
    }

    private static boolean isTallBamboo(ChunkSection[] sections, int bottomY, int x, int y, int z) {
        return blockAt(sections, bottomY, x, y + 1, z) != Blocks.BAMBOO
            && runLength(sections, bottomY, x, y, z, 1, Blocks.BAMBOO, Blocks.BAMBOO) >= 12;
    }

    private static boolean isLongVine(ChunkSection[] sections, int bottomY, int x, int y, int z) {
        return blockAt(sections, bottomY, x, y + 1, z) != Blocks.VINE
            && runLength(sections, bottomY, x, y, z, -1, Blocks.VINE, Blocks.VINE) >= 7;
    }

    private static boolean isLongDripstone(ChunkSection[] sections, int bottomY, int x, int y, int z) {
        return blockAt(sections, bottomY, x, y + 1, z) != Blocks.POINTED_DRIPSTONE
            && blockAt(sections, bottomY, x, y - 1, z) == Blocks.POINTED_DRIPSTONE
            && runLength(sections, bottomY, x, y, z, -1, Blocks.POINTED_DRIPSTONE, Blocks.POINTED_DRIPSTONE) >= 5;
    }

    // ------------------------------------------------------------------ zones

    private void rebuild() {
        List<Long> flags = new ArrayList<>();
        for (Map.Entry<Long, ChunkScore> entry : scores.entrySet()) {
            if (entry.getValue().score >= minScore()) flags.add(entry.getKey());
        }
        flagged = List.copyOf(flags);
        zones = cluster(flags);
        announce();
    }

    private List<Zone> cluster(List<Long> flags) {
        Map<Long, Double> weight = new HashMap<>();
        for (Long key : flags) weight.put(key, scores.get((long) key).score);

        Set<Long> seen = new HashSet<>();
        List<Zone> result = new ArrayList<>();

        for (Long start : flags) {
            if (!seen.add(start)) continue;

            List<Long> members = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                long current = queue.poll();
                members.add(current);

                int cx = ChunkPos.getPackedX(current);
                int cz = ChunkPos.getPackedZ(current);

                for (int dx = -ZONE_RADIUS; dx <= ZONE_RADIUS; dx++) {
                    for (int dz = -ZONE_RADIUS; dz <= ZONE_RADIUS; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        long neighbour = ChunkPos.toLong(cx + dx, cz + dz);
                        if (weight.containsKey(neighbour) && seen.add(neighbour)) queue.add(neighbour);
                    }
                }
            }

            result.add(buildZone(members, weight));
        }

        result.sort(Comparator.comparingDouble((Zone zone) -> zone.score).reversed());
        return result;
    }

    private Zone buildZone(List<Long> members, Map<Long, Double> weight) {
        double score = 0;
        double weightTotal = 0;
        double weightedX = 0;
        double weightedZ = 0;
        double fallbackX = 0;
        double fallbackZ = 0;

        for (Long key : members) {
            double w = weight.getOrDefault(key, 0.0);
            score += w;

            ChunkScore chunk = scores.get((long) key);
            if (chunk != null && chunk.totalWeight > 0) {
                weightTotal += chunk.totalWeight;
                weightedX += chunk.weightedX;
                weightedZ += chunk.weightedZ;
            }

            fallbackX += ((ChunkPos.getPackedX(key) << 4) + 8) * w;
            fallbackZ += ((ChunkPos.getPackedZ(key) << 4) + 8) * w;
        }

        double centerX = weightTotal > 0 ? weightedX / weightTotal : (score > 0 ? fallbackX / score : 0);
        double centerZ = weightTotal > 0 ? weightedZ / weightTotal : (score > 0 ? fallbackZ / score : 0);
        return new Zone(Set.copyOf(members), centerX, centerZ, score);
    }

    private void announce() {
        if (mc.player == null) return;

        for (Zone zone : zones) {
            boolean fresh = true;
            for (Long key : zone.chunks) {
                if (announced.contains(key)) {
                    fresh = false;
                    break;
                }
            }
            announced.addAll(zone.chunks);
            if (!fresh) continue;

            int x = (int) Math.round(zone.centerX);
            int z = (int) Math.round(zone.centerZ);
            int distance = (int) Math.hypot(x - mc.player.getX(), z - mc.player.getZ());

            if (chatNotify.get()) info("Sus zone · %dm · %d, %d", distance, x, z);
            if (soundPing.get()) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.1f);
            }
        }
    }

    // ----------------------------------------------------------------- render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || flagged.isEmpty()) return;

        int y = renderY.get();
        int alpha = opacity.get();
        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        SettingColor col = lineColor.get();

        for (Long key : flagged) {
            double x = ChunkPos.getPackedX(key) << 4;
            double z = ChunkPos.getPackedZ(key) << 4;
            FrostedChunkRenderer.drawChunk(event.renderer, x, y, z, col, alpha, time, false);
        }

        for (Zone zone : zones) {
            FrostedChunkRenderer.drawBeacon(event.renderer, zone.centerX, y, zone.centerZ, col, time, 11.0);
        }
    }

    private record ChunkScore(long key, double score, double totalWeight, double weightedX, double weightedZ) {}

    private record Zone(Set<Long> chunks, double centerX, double centerZ, double score) {}
}
