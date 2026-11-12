package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Small frosty effects: an ice burst when a totem pops, snow dust under your feet, visible
 * breath outdoors, and glittering trails on ender pearls and arrows. Everything is client side
 * and purely cosmetic.
 */
public class FrostedEffects extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> totemPop = sgGeneral.add(new BoolSetting.Builder()
        .name("totem-pop").description("Ice burst around anyone whose totem pops (including you).").defaultValue(true).build());

    private final Setting<Boolean> frostSteps = sgGeneral.add(new BoolSetting.Builder()
        .name("frost-steps").description("Snow dust kicked up while you run.").defaultValue(true).build());

    private final Setting<Boolean> breath = sgGeneral.add(new BoolSetting.Builder()
        .name("breath").description("Visible breath puffs when you are outdoors.").defaultValue(true).build());

    private final Setting<Boolean> pearlTrails = sgGeneral.add(new BoolSetting.Builder()
        .name("pearl-trails").description("Glittering trail behind ender pearls.").defaultValue(true).build());

    private final Setting<Boolean> arrowTrails = sgGeneral.add(new BoolSetting.Builder()
        .name("arrow-trails").description("Snow trail behind arrows in flight.").defaultValue(true).build());

    /** Packets arrive on the network thread; the particles are spawned from the client tick. */
    private final ConcurrentLinkedQueue<EntityStatusS2CPacket> totemQueue = new ConcurrentLinkedQueue<>();
    private int ticks;

    public FrostedEffects() {
        super(FrostedAddon.VISUAL, "frosted-effects", "Totem-pop ice burst, frost steps, breath and glittering projectile trails.");
    }

    @Override
    public void onActivate() {
        totemQueue.clear();
        ticks = 0;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!totemPop.get()) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return; // 35 = totem of undying
        totemQueue.add(packet);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            totemQueue.clear();
            return;
        }
        ticks++;

        EntityStatusS2CPacket packet;
        while ((packet = totemQueue.poll()) != null) {
            Entity entity = packet.getEntity(mc.world);
            if (entity != null) burst(entity);
        }

        if (frostSteps.get()) steps();
        if (breath.get() && ticks % 50 == 0) breathe();
        if (pearlTrails.get() || arrowTrails.get()) trails();
    }

    private void burst(Entity entity) {
        double x = entity.getX();
        double y = entity.getBodyY(0.5);
        double z = entity.getZ();

        // expanding ring
        int spokes = 28;
        for (int i = 0; i < spokes; i++) {
            double a = i * 2.0 * Math.PI / spokes;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x + dx * 0.4, y - 0.3, z + dz * 0.4, dx * 0.25, 0.03, dz * 0.25);
        }

        // sparks flying out
        for (int i = 0; i < 18; i++) {
            mc.world.addParticleClient(ParticleTypes.END_ROD,
                x + Rand.centered(0.5), y + Rand.centered(0.9), z + Rand.centered(0.5),
                Rand.centered(0.12), 0.05 + Rand.unit() * 0.1, Rand.centered(0.12));
        }

        // a rising spiral
        for (int i = 0; i < 16; i++) {
            double a = i * 0.7;
            mc.world.addParticleClient(ParticleTypes.SNOWFLAKE,
                x + Math.cos(a) * 0.35, y - 0.9 + i * 0.12, z + Math.sin(a) * 0.35, 0.0, 0.06, 0.0);
        }
    }

    private void steps() {
        if (!mc.player.isOnGround()) return;

        var vel = mc.player.getVelocity();
        double speed = Math.hypot(vel.x, vel.z);
        if (speed < 0.06) return;

        boolean sprint = mc.player.isSprinting();
        if (ticks % (sprint ? 1 : 3) != 0) return;

        for (int i = 0; i < (sprint ? 2 : 1); i++) {
            mc.world.addParticleClient(ParticleTypes.SNOWFLAKE,
                mc.player.getX() + Rand.centered(0.5), mc.player.getY() + 0.05, mc.player.getZ() + Rand.centered(0.5),
                Rand.centered(0.02), 0.03 + Rand.unit() * 0.03, Rand.centered(0.02));
        }
    }

    private void breathe() {
        if (!mc.world.isSkyVisible(mc.player.getBlockPos())) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        for (int i = 0; i < 2; i++) {
            mc.world.addParticleClient(ParticleTypes.CLOUD,
                eye.x + look.x * 0.4, eye.y - 0.12, eye.z + look.z * 0.4,
                look.x * 0.04 + Rand.centered(0.01), 0.01, look.z * 0.04 + Rand.centered(0.01));
        }
    }

    private void trails() {
        for (Entity entity : mc.world.getEntities()) {
            EntityType<?> type = entity.getType();
            boolean pearl = pearlTrails.get() && type == EntityType.ENDER_PEARL;
            boolean arrow = arrowTrails.get() && type == EntityType.ARROW;
            if (!pearl && !arrow) continue;
            if (mc.player.squaredDistanceTo(entity) > 64.0 * 64.0) continue;
            if (arrow && entity.getVelocity().lengthSquared() < 0.05) continue; // stuck in a block

            double x = entity.getX();
            double y = entity.getY() + entity.getHeight() * 0.5;
            double z = entity.getZ();

            mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z,
                Rand.centered(0.02), Rand.centered(0.02), Rand.centered(0.02));
            if (pearl) {
                mc.world.addParticleClient(ParticleTypes.END_ROD, x, y, z,
                    Rand.centered(0.02), Rand.centered(0.02), Rand.centered(0.02));
            }
        }
    }
}
