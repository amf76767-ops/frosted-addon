package com.manu.frosted.modules.basehunt;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.FrostedChunkRenderer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of Corz's ChunkFinderV2 idea: chunks are not scored by growth but by
 * evidence of construction.
 *
 * Agent of the checks:
 *  - deepslate placed above Y 0 (natural deepslate sits below)
 *  - cobbled deepslate above Y 0 (never generates, always carried)
 *  - end stone outside The End
 *  - stripped or lit amethyst geodes
 *  - storage, redstone, pistons, stairs, obsidian clusters
 *  - farm blocks
 */
public class ChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChecks = settings.createGroup("Checks");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("1 = flags a lot, 10 = only strong evidence. Threshold = sensitivity x 4.")
        .defaultValue(5)
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

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat message with the strongest reason per chunk.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> deepslate = sgChecks.add(new BoolSetting.Builder()
        .name("deepslate")
        .description("Upright deepslate above Y 0.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cobbledDeepslate = sgChecks.add(new BoolSetting.Builder()
        .name("cobbled-deepslate")
        .description("Cobbled deepslate above Y 0.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sidewaysDeepslate = sgChecks.add(new BoolSetting.Builder()
        .name("sideways-deepslate")
        .description("Deepslate rotated off the Y axis.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> endStone = sgChecks.add(new BoolSetting.Builder()
        .name("end-stone")
        .description("End stone outside The End.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> amethyst = sgChecks.add(new BoolSetting.Builder()
        .name("amethyst")
        .description("Geodes that look stripped or lit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> playerEvidence = sgChecks.add(new BoolSetting.Builder()
        .name("player-evidence")
        .description("Storage, pistons, obsidian, stairs, cobble lines.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> farms = sgChecks.add(new BoolSetting.Builder()
        .name("farms")
        .description("Farmland, crops, hoppers, composters, beehives.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> renderY = sgRender.add(new IntSetting.Builder()
        .name("render-y")
        .description("Height of the chunk markers.")
        .defaultValue(60)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> deepslateColor = sgRender.add(new ColorSetting.Builder()
        .name("deepslate-color")
        .defaultValue(new SettingColor(255, 215, 0, 200))
        .build()
    );

    private final Setting<SettingColor> playerColor = sgRender.add(new ColorSetting.Builder()
        .name("player-color")
        .defaultValue(new SettingColor(60, 255, 120, 200))
        .build()
    );

    private final Setting<SettingColor> amethystColor = sgRender.add(new ColorSetting.Builder()
        .name("amethyst-color")
        .defaultValue(new SettingColor(190, 90, 255, 200))
        .build()
    );

    private final Setting<SettingColor> farmColor = sgRender.add(new ColorSetting.Builder()
        .name("farm-color")
        .defaultValue(new SettingColor(255, 130, 40, 200))
        .build()
    );

    private static final int DEEPSLATE = 1;
    private static final int PLAYER = 2;
    private static final int AMETHYST = 4;
    private static final int FARM = 8;

    private final Map<Long, Result> results = new HashMap<>();
    private final LongSet queued = new LongOpenHashSet();
    private final Queue<Long> scanQueue = new ConcurrentLinkedQueue<>();

    public ChunkFinder() {
        super(FrostedAddon.BASEHUNT, "chunk-finder", "Flags chunks with construction evidence: misplaced stone, end stone, stripped geodes, farms.");
    }

    @Override
    public void onActivate() {
        results.clear();
        queued.clear();
        scanQueue.clear();

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
        results.clear();
        queued.clear();
        scanQueue.clear();
    }

    @Override
    public String getInfoString() {
        return results.size() + " chunks";
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

            Result result = scan(chunk);
            if (result == null) {
                results.remove(key);
            } else {
                results.put(key, result);
                if (notify.get()) info("Chunk %d, %d — %s", (cx << 4) + 8, (cz << 4) + 8, result.reason);
            }
        }
    }

    private int threshold() {
        return sensitivity.get() * 4;
    }

    private Result scan(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        boolean inEnd = mc.world.getRegistryKey() == World.END;

        int deepslateCount = 0;
        int cobbledCount = 0;
        int sidewaysCount = 0;
        int endStoneCount = 0;
        int obsidianCount = 0;
        int storageCount = 0;
        int pistonCount = 0;
        int stairsCount = 0;
        int budding = 0;
        int clusters = 0;
        int calcite = 0;
        int smoothBasalt = 0;
        int farmBlocks = 0;

        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty()) continue;

            int sectionY = bottomY + (index << 4);
            if (sectionY > 128 || sectionY + 15 < -64) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = sectionY + y;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();
                        if (block == Blocks.AIR || block == Blocks.CAVE_AIR) continue;

                        if (block == Blocks.DEEPSLATE) {
                            if (worldY >= 0) {
                                if (state.contains(Properties.AXIS)) {
                                    if (state.get(Properties.AXIS) == Direction.Axis.Y) deepslateCount++;
                                    else sidewaysCount++;
                                } else {
                                    deepslateCount++;
                                }
                            }
                        } else if (block == Blocks.COBBLED_DEEPSLATE) {
                            if (worldY >= 0) cobbledCount++;
                        } else if (block == Blocks.END_STONE) {
                            if (!inEnd) endStoneCount++;
                        } else if (block == Blocks.OBSIDIAN) {
                            obsidianCount++;
                        } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                            || block == Blocks.BARREL || block == Blocks.SHULKER_BOX
                            || block == Blocks.HOPPER || block == Blocks.FURNACE
                            || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER) {
                            storageCount++;
                        } else if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON
                            || block == Blocks.OBSERVER || block == Blocks.DISPENSER
                            || block == Blocks.DROPPER) {
                            pistonCount++;
                        } else if (block == Blocks.STONE_STAIRS || block == Blocks.COBBLESTONE_STAIRS
                            || block == Blocks.DEEPSLATE_BRICK_STAIRS
                            || block == Blocks.POLISHED_DEEPSLATE_STAIRS) {
                            stairsCount++;
                        } else if (block == Blocks.BUDDING_AMETHYST) {
                            budding++;
                        } else if (block == Blocks.AMETHYST_CLUSTER
                            || block == Blocks.LARGE_AMETHYST_BUD
                            || block == Blocks.MEDIUM_AMETHYST_BUD
                            || block == Blocks.SMALL_AMETHYST_BUD) {
                            clusters++;
                        } else if (block == Blocks.CALCITE) {
                            calcite++;
                        } else if (block == Blocks.SMOOTH_BASALT) {
                            smoothBasalt++;
                        } else if (block == Blocks.FARMLAND || block == Blocks.WHEAT
                            || block == Blocks.CARROTS || block == Blocks.POTATOES
                            || block == Blocks.BEETROOTS || block == Blocks.COMPOSTER
                            || block == Blocks.BEEHIVE || block == Blocks.BEE_NEST) {
                            farmBlocks++;
                        }
                    }
                }
            }
        }

        int score = 0;
        int flags = 0;
        List<String> reasons = new ArrayList<>();

        if (deepslate.get() && deepslateCount >= 4) {
            score += Math.min(deepslateCount / 2, 8);
            flags |= DEEPSLATE;
            reasons.add("deepslate " + deepslateCount);
        }
        if (cobbledDeepslate.get() && cobbledCount >= 3) {
            score += Math.min(cobbledCount, 10);
            flags |= DEEPSLATE;
            reasons.add("cobbled deepslate " + cobbledCount);
        }
        if (sidewaysDeepslate.get() && sidewaysCount >= 2) {
            score += Math.min(sidewaysCount, 6);
            flags |= DEEPSLATE;
            reasons.add("sideways deepslate " + sidewaysCount);
        }
        if (endStone.get() && endStoneCount >= 2) {
            score += Math.min(endStoneCount, 12);
            flags |= DEEPSLATE;
            reasons.add("end stone " + endStoneCount);
        }
        if (amethyst.get() && (budding > 0 || clusters >= 4)) {
            boolean stripped = budding <= 1 && clusters >= 8;
            score += stripped ? 10 : 6;
            flags |= AMETHYST;
            reasons.add(stripped ? "stripped geode" : "geode");
        }
        if (playerEvidence.get()) {
            int p = Math.min(obsidianCount, 6) + Math.min(storageCount, 8)
                + Math.min(pistonCount, 6) + Math.min(stairsCount / 3, 4);
            if (p >= 4) {
                score += p;
                flags |= PLAYER;
                reasons.add("player blocks " + p);
            }
        }
        if (farms.get() && farmBlocks >= 6) {
            score += Math.min(farmBlocks / 2, 10);
            flags |= FARM;
            reasons.add("farm " + farmBlocks);
        }

        if (score < threshold() || flags == 0) return null;

        reasons.sort(Comparator.comparingInt(String::length).reversed());
        return new Result(flags, score, String.join(", ", reasons));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || results.isEmpty()) return;

        int y = renderY.get();
        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        for (Map.Entry<Long, Result> entry : results.entrySet()) {
            long key = entry.getKey();
            double x = ChunkPos.getPackedX(key) << 4;
            double z = ChunkPos.getPackedZ(key) << 4;
            Result result = entry.getValue();

            Color color = colorFor(result.flags);
            FrostedChunkRenderer.drawChunk(event.renderer, x, y, z, color, 105, time, result.score >= threshold() * 2);
        }
    }

    private Color colorFor(int flags) {
        if ((flags & AMETHYST) != 0) return amethystColor.get();
        if ((flags & FARM) != 0) return farmColor.get();
        if ((flags & PLAYER) != 0) return playerColor.get();
        return deepslateColor.get();
    }

    private record Result(int flags, int score, String reason) {}
}
