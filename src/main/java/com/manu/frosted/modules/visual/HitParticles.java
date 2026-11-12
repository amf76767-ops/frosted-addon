package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Cosmetic particle burst on hit. */
public class HitParticles extends Module {
    public enum HitSound { Off, Glass, Amethyst, Chime }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> count = sgGeneral.add(new IntSetting.Builder()
        .name("count").defaultValue(14).min(1).sliderRange(1, 40).build()
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

    public HitParticles() {
        super(FrostedAddon.VISUAL, "hit-particles", "Spawns particles when you hit an entity.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity target = event.entity;
        if (!(target instanceof LivingEntity living) || mc.world == null) return;

        double x = living.getX();
        double y = living.getBodyY(0.5);
        double z = living.getZ();
        int n = count.get();

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
            int spokes = 20;
            for (int i = 0; i < spokes; i++) {
                double a = i * 2.0 * Math.PI / spokes;
                double dx = Math.cos(a);
                double dz = Math.sin(a);
                mc.world.addParticleClient(ParticleTypes.SNOWFLAKE,
                    x + dx * 0.3, y - 0.2, z + dz * 0.3, dx * 0.16, 0.02, dz * 0.16);
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
}
