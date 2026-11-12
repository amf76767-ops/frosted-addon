package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/** Cosmetic particle and 3D ice-crystal burst on hit. */
public class HitParticles extends Module {
    public enum HitSound { Off, Glass, Amethyst, Chime }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> count = sgGeneral.add(new IntSetting.Builder()
        .name("count").defaultValue(12).min(1).sliderRange(1, 35).build()
    );

    private final Setting<Boolean> crystalBurst = sgGeneral.add(new BoolSetting.Builder()
        .name("crystal-burst").description("3D ice shards and shockwave ring on hit.").defaultValue(true).build()
    );

    private final Setting<SettingColor> burstColor = sgGeneral.add(new ColorSetting.Builder()
        .name("burst-color").defaultValue(new SettingColor(130, 210, 255, 225)).build()
    );

    private final Setting<Boolean> crit = sgGeneral.add(new BoolSetting.Builder()
        .name("crit-particles").defaultValue(true).build()
    );

    private final Setting<Boolean> snow = sgGeneral.add(new BoolSetting.Builder()
        .name("snowflake-particles").description("Frosty snowflake burst on hit.").defaultValue(true).build()
    );

    private final Setting<HitSound> hitSound = sgGeneral.add(new EnumSetting.Builder<HitSound>()
        .name("hit-sound").description("A soft icy sound on every hit.").defaultValue(HitSound.Off).build()
    );

    private final Setting<Double> soundVolume = sgGeneral.add(new DoubleSetting.Builder()
        .name("sound-volume").defaultValue(0.6).min(0.1).sliderRange(0.1, 1.5).build()
    );

    private final Setting<Boolean> ring = sgGeneral.add(new BoolSetting.Builder()
        .name("ice-ring").description("A flat ring of snowflakes that expands from the target.").defaultValue(true).build()
    );

    private final Setting<Boolean> glitter = sgGeneral.add(new BoolSetting.Builder()
        .name("glitter").description("White sparks that float up from the hit.").defaultValue(true).build()
    );

    private final Setting<Boolean> enchant = sgGeneral.add(new BoolSetting.Builder()
        .name("enchant-particles").defaultValue(false).build()
    );

    private record Burst(double x, double y, double z, double seed, long start) {}

    private final List<Burst> bursts = new ArrayList<>();
    private final Color cCore = new Color();
    private final Color cOuter = new Color();
    private final Color cFacet = new Color();

    public HitParticles() {
        super(FrostedAddon.VISUAL, "hit-particles", "Spawns frosty particles and 3D ice shards when you hit an entity.");
    }

    @Override
    public void onActivate() {
        bursts.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity target = event.entity;
        if (!(target instanceof LivingEntity living) || mc.world == null) return;

        double x = living.getX();
        double y = living.getBodyY(0.55);
        double z = living.getZ();
        int n = count.get();

        if (crystalBurst.get()) {
            if (bursts.size() >= 10) bursts.remove(0);
            bursts.add(new Burst(x, y, z, Rand.unit() * Math.PI * 2.0, System.currentTimeMillis()));
        }

        SoundEvent sound = switch (hitSound.get()) {
            case Glass -> SoundEvents.BLOCK_GLASS_BREAK;
            case Amethyst -> SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK;
            case Chime -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            case Off -> null;
        };
        if (sound != null && mc.player != null) {
            mc.player.playSound(sound, soundVolume.get().floatValue(), 1.2f + (float) Rand.centered(0.3));
        }

        if (ring.get()) {
            int spokes = 16;
            for (int i = 0; i < spokes; i++) {
                double a = i * 2.0 * Math.PI / spokes;
                double dx = Math.cos(a);
                double dz = Math.sin(a);
                mc.world.addParticleClient(ParticleTypes.SNOWFLAKE,
                    x + dx * 0.3, y - 0.15, z + dz * 0.3, dx * 0.16, 0.02, dz * 0.16);
            }
        }

        if (glitter.get()) {
            for (int i = 0; i < 6; i++) {
                mc.world.addParticleClient(ParticleTypes.END_ROD,
                    x + Rand.centered(0.6), y + Rand.centered(0.8), z + Rand.centered(0.6),
                    Rand.centered(0.04), 0.02 + Rand.unit() * 0.05, Rand.centered(0.04));
            }
        }

        for (int i = 0; i < n; i++) {
            double vx = Rand.centered(0.6);
            double vy = Rand.unit() * 0.5;
            double vz = Rand.centered(0.6);

            if (crit.get()) {
                mc.world.addParticleClient(ParticleTypes.CRIT, x, y, z, vx, vy, vz);
            }
            if (snow.get()) {
                mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z, vx * 0.5, vy * 0.5, vz * 0.5);
            }
            if (enchant.get()) {
                mc.world.addParticleClient(ParticleTypes.ENCHANTED_HIT, x, y, z, vx, vy, vz);
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (bursts.isEmpty() || !crystalBurst.get()) return;

        long now = System.currentTimeMillis();
        bursts.removeIf(b -> now - b.start > 420L);
        if (bursts.isEmpty()) return;

        SettingColor base = burstColor.get();

        for (Burst b : bursts) {
            double p = Math.min(1.0, (now - b.start) / 420.0);
            double ease = 1.0 - Math.pow(1.0 - p, 3.0);
            double fade = (1.0 - p) * (1.0 - p * 0.4);
            int alpha = Math.max(0, Math.min(255, (int) (base.a * fade)));
            if (alpha <= 4) continue;

            cCore.set(255, 255, 255, alpha);
            cOuter.set(base.r, base.g, base.b, alpha);
            cFacet.set(base.r, base.g, base.b, alpha / 3);

            // Expanding 3D diamond ice shards
            int shards = 8;
            for (int i = 0; i < shards; i++) {
                double a = b.seed + i * (2.0 * Math.PI / shards) + p * 0.35;
                double pitch = ((i % 3) - 1) * 0.42;
                double dist = 0.15 + ease * 0.95;
                double len = 0.28 * (1.0 - p * 0.45);
                double width = 0.065 * (1.0 - p * 0.4);

                double dx = Math.cos(a) * Math.cos(pitch);
                double dy = Math.sin(pitch) + (1.0 - p) * 0.15 - p * 0.22;
                double dz = Math.sin(a) * Math.cos(pitch);

                double sx = b.x + dx * dist;
                double sy = b.y + dy * dist;
                double sz = b.z + dz * dist;

                double ex = sx + dx * len;
                double ey = sy + dy * len;
                double ez = sz + dz * len;

                double mx = (sx + ex) * 0.5;
                double my = (sy + ey) * 0.5;
                double mz = (sz + ez) * 0.5;

                double px = -Math.sin(a) * width;
                double pz = Math.cos(a) * width;

                event.renderer.quad(
                    sx, sy, sz,
                    mx + px, my, mz + pz,
                    ex, ey, ez,
                    mx - px, my, mz - pz,
                    cCore, cFacet, cOuter, cFacet
                );
                event.renderer.line(sx, sy, sz, ex, ey, ez, cCore, cOuter);
            }

            // Crisp expanding hexagonal impact ring
            double ringR = 0.20 + ease * 0.85;
            int segs = 18;
            for (int i = 0; i < segs; i++) {
                double a0 = b.seed + i * (2.0 * Math.PI / segs);
                double a1 = b.seed + (i + 1) * (2.0 * Math.PI / segs);
                event.renderer.line(
                    b.x + Math.cos(a0) * ringR, b.y, b.z + Math.sin(a0) * ringR,
                    b.x + Math.cos(a1) * ringR, b.y, b.z + Math.sin(a1) * ringR,
                    cOuter
                );
            }
        }
    }
}
