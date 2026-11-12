package com.manu.frosted.modules.basehunt;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.FrostedChunkRenderer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sound Finder: listens for sounds that only players or farms/redstone make
 * and marks the chunks (plus the exact sound source) with a 3D frosted look.
 */
public class SoundFinder extends Module {
    public enum SoundKind { PLAYER, FARM }

    public static final class ChunkHit {
        public final long chunkKey;
        public SoundKind kind;
        public String lastSound;
        public double lastX, lastY, lastZ;
        public int count;
        public long lastSeenMs;
        public long lastAnnouncedMs;

        public ChunkHit(long chunkKey, SoundKind kind, String lastSound, double x, double y, double z, long now) {
            this.chunkKey = chunkKey;
            this.kind = kind;
            this.lastSound = lastSound;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.count = 1;
            this.lastSeenMs = now;
            this.lastAnnouncedMs = 0L;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filters");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> detectPlayers = sgFilter.add(new BoolSetting.Builder()
        .name("player-sounds")
        .description("Detect sounds made by players (chests, combat, eating, gear, doors, workstations, footsteps).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectFarms = sgFilter.add(new BoolSetting.Builder()
        .name("farm-sounds")
        .description("Detect sounds made by farms and redstone (pistons, dispensers, crafters, bone meal, amethyst, grinders, villagers).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> ignoreSelfRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("ignore-self-radius")
        .description("Ignore sounds closer than this distance (in blocks) to yourself.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderRange(0.0, 16.0)
        .build()
    );

    private final Setting<Integer> minHits = sgGeneral.add(new IntSetting.Builder()
        .name("min-sounds")
        .description("Minimum matching sounds in a chunk before it is marked.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Announce detected farm/player sound chunks in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> soundAlert = sgGeneral.add(new BoolSetting.Builder()
        .name("sound-alert")
        .description("Play a subtle chime when a new chunk is flagged by sound.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useSoundY = sgRender.add(new BoolSetting.Builder()
        .name("use-sound-y")
        .description("Place the frosted chunk marker at the Y level where the sound happened (otherwise uses render-y).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> renderY = sgRender.add(new IntSetting.Builder()
        .name("render-y")
        .description("Fixed Y height for the frosted chunk markers when use-sound-y is off.")
        .defaultValue(60)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Boolean> showExactSource = sgRender.add(new BoolSetting.Builder()
        .name("exact-source-crystal")
        .description("Draw a floating 3D frost crystal at the exact coordinates of the sound.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .description("Frosted fill opacity, 0-255.")
        .defaultValue(110)
        .min(20)
        .sliderRange(20, 255)
        .build()
    );

    private final Setting<SettingColor> playerColor = sgRender.add(new ColorSetting.Builder()
        .name("player-color")
        .description("Frosted color for chunks with player sounds.")
        .defaultValue(new SettingColor(70, 230, 255, 220))
        .build()
    );

    private final Setting<SettingColor> farmColor = sgRender.add(new ColorSetting.Builder()
        .name("farm-color")
        .description("Frosted color for chunks with farm/redstone sounds.")
        .defaultValue(new SettingColor(255, 175, 55, 220))
        .build()
    );

    private final Map<Long, ChunkHit> hits = new ConcurrentHashMap<>();

    public SoundFinder() {
        super(FrostedAddon.BASEHUNT, "sound-finder", "Finds sounds that only farms or players make and marks their chunks with a frosted look.");
    }

    @Override
    public void onActivate() {
        hits.clear();
    }

    @Override
    public void onDeactivate() {
        hits.clear();
    }

    @Override
    public String getInfoString() {
        int marked = 0;
        int req = minHits.get();
        for (ChunkHit h : hits.values()) {
            if (h.count >= req) marked++;
        }
        return marked + " chunks";
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (mc.player == null || mc.world == null) return;
        SoundInstance sound = event.sound;
        if (sound == null) return;

        Identifier id = sound.getId();
        if (id == null) return;
        String path = id.getPath();
        if (path == null || path.isEmpty()) return;

        double sx = sound.getX();
        double sy = sound.getY();
        double sz = sound.getZ();

        // Ignore UI/music/ambient non-positional sounds at (0,0,0)
        if (sx == 0.0 && sy == 0.0 && sz == 0.0) return;

        double selfR = ignoreSelfRadius.get();
        if (selfR > 0 && mc.player.squaredDistanceTo(sx, sy, sz) <= selfR * selfR) return;

        SoundKind kind = classify(path, sound.getCategory());
        if (kind == null) return;
        if (kind == SoundKind.PLAYER && !detectPlayers.get()) return;
        if (kind == SoundKind.FARM && !detectFarms.get()) return;

        int cx = ((int) Math.floor(sx)) >> 4;
        int cz = ((int) Math.floor(sz)) >> 4;
        long key = ChunkPos.toLong(cx, cz);
        long now = System.currentTimeMillis();

        String cleanName = prettySound(path);
        ChunkHit hit = hits.get(key);
        if (hit == null) {
            hit = new ChunkHit(key, kind, cleanName, sx, sy, sz, now);
            hits.put(key, hit);
        } else {
            hit.count++;
            hit.lastSeenMs = now;
            hit.lastX = sx;
            hit.lastY = sy;
            hit.lastZ = sz;
            hit.lastSound = cleanName;
            // Player sound takes priority over farm sound
            if (kind == SoundKind.PLAYER) hit.kind = SoundKind.PLAYER;
        }

        if (hit.count >= minHits.get() && (hit.lastAnnouncedMs == 0L || now - hit.lastAnnouncedMs > 15_000L)) {
            hit.lastAnnouncedMs = now;
            int dist = (int) Math.round(Math.hypot(sx - mc.player.getX(), sz - mc.player.getZ()));
            if (notify.get()) {
                info("%s sound (%s) · %dm · [%d, %d, %d]",
                    hit.kind == SoundKind.PLAYER ? "Player" : "Farm",
                    cleanName, dist, (int) Math.round(sx), (int) Math.round(sy), (int) Math.round(sz));
            }
            if (soundAlert.get() && !path.contains("experience_orb") && !path.contains("note_block")) {
                mc.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.35f);
            }
        }
    }

    private static SoundKind classify(String path, SoundCategory cat) {
        // ---- 1. Explicit Player-only sounds
        if (cat == SoundCategory.PLAYERS
            || path.startsWith("entity.player.")
            || path.startsWith("item.armor.equip")
            || path.startsWith("item.shield.")
            || path.startsWith("item.mace.")
            || path.startsWith("item.trident.")
            || path.startsWith("item.crossbow.")
            || path.startsWith("item.elytra.")
            || path.startsWith("item.totem.")
            || path.startsWith("item.chorus_fruit.")
            || path.startsWith("item.flintandsteel.")
            || path.startsWith("item.spyglass.")
            || path.startsWith("item.bundle.")
            || path.startsWith("item.brush.")
            || path.startsWith("entity.ender_pearl.")
            || path.startsWith("entity.wind_charge.")
            || path.startsWith("entity.fishing_bobber.")
            || path.equals("entity.generic.eat")
            || path.equals("entity.generic.drink")
            || path.equals("entity.item.pickup")
            || path.equals("entity.experience_orb.pickup")
            || path.startsWith("block.chest.")
            || path.startsWith("block.ender_chest.")
            || path.startsWith("block.barrel.")
            || path.startsWith("block.shulker_box.")
            || path.startsWith("block.wooden_door.")
            || path.startsWith("block.iron_door.")
            || path.startsWith("block.copper_door.")
            || path.startsWith("block.wooden_trapdoor.")
            || path.startsWith("block.copper_trapdoor.")
            || path.startsWith("block.fence_gate.")
            || path.startsWith("block.anvil.")
            || path.startsWith("block.grindstone.")
            || path.startsWith("block.smithing_table.")
            || path.startsWith("block.enchantment_table.")
            || path.startsWith("block.brewing_stand.")
            || path.startsWith("block.stonecutter.")
            || path.startsWith("block.Cartography_table.")
            || path.startsWith("block.loom.")
            || path.startsWith("block.bell.")
            || path.startsWith("block.respawn_anchor.")) {
            return SoundKind.PLAYER;
        }

        // ---- 2. Explicit Farm / Redstone / Grinder sounds
        if (path.startsWith("block.piston.")
            || path.startsWith("block.dispenser.")
            || path.startsWith("block.crafter.")
            || path.startsWith("block.comparator.")
            || path.startsWith("block.lever.")
            || path.startsWith("block.stone_button.")
            || path.startsWith("block.wooden_button.")
            || path.startsWith("block.polished_blackstone_button.")
            || path.startsWith("block.stone_pressure_plate.")
            || path.startsWith("block.wooden_pressure_plate.")
            || path.startsWith("block.metal_pressure_plate.")
            || path.startsWith("block.iron_trapdoor.")
            || path.startsWith("block.redstone_torch.")
            || path.startsWith("block.note_block.")
            || path.startsWith("block.tripwire.")
            || path.startsWith("entity.minecart.")
            || path.startsWith("item.bone_meal.")
            || path.startsWith("item.hoe.till")
            || path.startsWith("item.shears.")
            || path.startsWith("entity.sheep.shear")
            || path.startsWith("block.composter.")
            || path.startsWith("block.amethyst_block.")
            || path.startsWith("block.amethyst_cluster.")
            || path.startsWith("block.sweet_berry_bush.")
            || path.startsWith("block.beehive.")
            || path.startsWith("block.crop.")
            || path.startsWith("block.nether_wart.")
            || path.startsWith("block.bamboo.")
            || path.startsWith("block.kelp.")
            || path.startsWith("entity.villager.work_")
            || path.startsWith("entity.iron_golem.hurt")
            || path.startsWith("entity.iron_golem.death")
            || path.startsWith("entity.blaze.hurt")
            || path.startsWith("entity.blaze.death")
            || path.startsWith("entity.guardian.hurt")
            || path.startsWith("entity.zombified_piglin.hurt")
            || path.startsWith("entity.enderman.hurt")
            || path.startsWith("entity.witch.hurt")
            || path.startsWith("entity.magma_cube.squish")
            || path.startsWith("entity.slime.squish")
            || path.startsWith("entity.tnt.")
            || path.equals("entity.generic.explode")) {
            return SoundKind.FARM;
        }

        return null;
    }

    private static String prettySound(String path) {
        String s = path;
        if (s.startsWith("block.")) s = s.substring(6);
        else if (s.startsWith("entity.")) s = s.substring(7);
        else if (s.startsWith("item.")) s = s.substring(5);
        return s.replace('.', ' ').replace('_', ' ');
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || hits.isEmpty()) return;

        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        int req = minHits.get();
        int fillA = opacity.get();
        int fixedY = renderY.get();
        boolean soundY = useSoundY.get();
        boolean exact = showExactSource.get();

        for (ChunkHit hit : hits.values()) {
            if (hit.count < req) continue;

            double cx = ChunkPos.getPackedX(hit.chunkKey) << 4;
            double cz = ChunkPos.getPackedZ(hit.chunkKey) << 4;
            double y = soundY ? Math.floor(hit.lastY) : fixedY;

            Color col = hit.kind == SoundKind.PLAYER ? playerColor.get() : farmColor.get();
            FrostedChunkRenderer.drawChunk(event.renderer, cx, y, cz, col, fillA, time, !exact);

            if (exact) {
                FrostedChunkRenderer.drawBeacon(event.renderer, hit.lastX, hit.lastY, hit.lastZ, col, time, 8.0);
            }
        }
    }
}
