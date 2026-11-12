package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.Rand;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Frosty 3D and particle effects: totem-pop crystal shockwaves, frost steps, visible breath outdoors,
 * player frost ribbon trail, and smooth glowing 3D trails on ender pearls and arrows.
 */
public class FrostedEffects extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> totemPop = sgGeneral.add(new BoolSetting.Builder()
        .name("totem-pop").description("3D ice shockwave and burst around anyone whose totem pops.").defaultValue(true).build());

    private final Setting<Boolean> frostSteps = sgGeneral.add(new BoolSetting.Builder()
        .name("frost-steps").description("Snow dust and glowing trail while you run.").defaultValue(true).build());

    private final Setting<Boolean> breath = sgGeneral.add(new BoolSetting.Builder()
        .name("breath").description("Visible breath puffs when you are outdoors.").defaultValue(true).build());

    private final Setting<Boolean> pearlTrails = sgGeneral.add(new BoolSetting.Builder()
        .name("pearl-trails").description("Glowing 3D ribbon and glittering trail behind ender pearls.").defaultValue(true).build());

    private final Setting<Boolean> arrowTrails = sgGeneral.add(new BoolSetting.Builder()
        .name("arrow-trails").description("Glowing 3D frost trail behind arrows in flight.").defaultValue(true).build());

    private final Setting<SettingColor> trailColor = sgGeneral.add(new ColorSetting.Builder()
        .name("trail-color").defaultValue(new SettingColor(125, 205, 255, 215)).build());

    private record TrailPoint(double x, double y, double z, long time) {}
    private record TotemRing(double x, double y, double z, long start) {}

    private final ConcurrentLinkedQueue<EntityStatusS2CPacket> totemQueue = new ConcurrentLinkedQueue<>();
    private final List<TotemRing> totemRings = new ArrayList<>();
    private final Map<Integer, Deque<TrailPoint>> projectileTrails = new HashMap<>();
    private final Deque<TrailPoint> playerTrail = new ArrayDeque<>();

    private final Color c0 = new Color();
    private final Color c1 = new Color();
    private final Color cSub0 = new Color();
    private final Color cSub1 = new Color();

    private int ticks;

    public FrostedEffects() {
        super(FrostedAddon.VISUAL, "frosted-effects", "Totem-pop ice shockwave, frost steps, breath and glowing 3D projectile trails.");
    }

    @Override
    public void onActivate() {
        totemQueue.clear();
        totemRings.clear();
        projectileTrails.clear();
        playerTrail.clear();
        ticks = 0;
    }

    @Override
    public void onDeactivate() {
        totemQueue.clear();
        totemRings.clear();
        projectileTrails.clear();
        playerTrail.clear();
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
            totemRings.clear();
            projectileTrails.clear();
            playerTrail.clear();
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

        if (totemRings.size() >= 8) totemRings.remove(0);
        totemRings.add(new TotemRing(x, entity.getY(), z, System.currentTimeMillis()));

        int spokes = 22;
        for (int i = 0; i < spokes; i++) {
            double a = i * 2.0 * Math.PI / spokes;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x + dx * 0.4, y - 0.3, z + dz * 0.4, dx * 0.25, 0.03, dz * 0.25);
        }

        for (int i = 0; i < 14; i++) {
            mc.world.addParticleClient(ParticleTypes.END_ROD,
                x + Rand.centered(0.5), y + Rand.centered(0.9), z + Rand.centered(0.5),
                Rand.centered(0.12), 0.05 + Rand.unit() * 0.1, Rand.centered(0.12));
        }

        for (int i = 0; i < 14; i++) {
            double a = i * 0.7;
            mc.world.addParticleClient(ParticleTypes.SNOWFLAKE,
                x + Math.cos(a) * 0.35, y - 0.9 + i * 0.12, z + Math.sin(a) * 0.35, 0.0, 0.06, 0.0);
        }
    }

    private void steps() {
        var vel = mc.player.getVelocity();
        double speed = Math.hypot(vel.x, vel.z);
        long now = System.currentTimeMillis();

        if (speed >= 0.04) {
            playerTrail.addLast(new TrailPoint(mc.player.getX(), mc.player.getY() + 0.06, mc.player.getZ(), now));
            while (playerTrail.size() > 24) playerTrail.removeFirst();
        }
        while (!playerTrail.isEmpty() && now - playerTrail.peekFirst().time > 650L) {
            playerTrail.removeFirst();
        }

        if (!mc.player.isOnGround() || speed < 0.06) return;

        boolean sprint = mc.player.isSprinting();
        if (ticks % (sprint ? 2 : 4) != 0) return;

        mc.world.addParticleClient(ParticleTypes.SNOWFLAKE,
            mc.player.getX() + Rand.centered(0.45), mc.player.getY() + 0.05, mc.player.getZ() + Rand.centered(0.45),
            Rand.centered(0.02), 0.025 + Rand.unit() * 0.025, Rand.centered(0.02));
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
        long now = System.currentTimeMillis();
        for (Entity entity : mc.world.getEntities()) {
            EntityType<?> type = entity.getType();
            boolean pearl = pearlTrails.get() && type == EntityType.ENDER_PEARL;
            boolean arrow = arrowTrails.get() && type == EntityType.ARROW;
            if (!pearl && !arrow) continue;
            if (mc.player.squaredDistanceTo(entity) > 72.0 * 72.0) continue;
            if (arrow && entity.getVelocity().lengthSquared() < 0.05) continue;

            double x = entity.getX();
            double y = entity.getY() + entity.getHeight() * 0.5;
            double z = entity.getZ();

            Deque<TrailPoint> pts = projectileTrails.computeIfAbsent(entity.getId(), k -> new ArrayDeque<>());
            pts.addLast(new TrailPoint(x, y, z, now));
            while (pts.size() > 28) pts.removeFirst();

            if (ticks % 2 == 0) {
                mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z,
                    Rand.centered(0.018), Rand.centered(0.018), Rand.centered(0.018));
                if (pearl) {
                    mc.world.addParticleClient(ParticleTypes.END_ROD, x, y, z,
                        Rand.centered(0.018), Rand.centered(0.018), Rand.centered(0.018));
                }
            }
        }

        Iterator<Map.Entry<Integer, Deque<TrailPoint>>> it = projectileTrails.entrySet().iterator();
        while (it.hasNext()) {
            Deque<TrailPoint> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst().time > 750L) q.removeFirst();
            if (q.isEmpty()) it.remove();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        SettingColor base = trailColor.get();

        // Render 3D Totem-pop double crystal shockwave rings
        if (!totemRings.isEmpty()) {
            totemRings.removeIf(r -> now - r.start > 900L);
            for (TotemRing r : totemRings) {
                double p = Math.min(1.0, (now - r.start) / 900.0);
                double ease = 1.0 - Math.pow(1.0 - p, 3.0);
                double fade = 1.0 - p * p;
                int a = Math.max(0, Math.min(255, (int) (235 * fade)));
                if (a <= 4) continue;

                c0.set(240, 250, 255, a);
                c1.set(base.r, base.g, base.b, a / 2);
                cSub0.set(base.r, base.g, base.b, 0);

                for (int wave = 0; wave < 2; wave++) {
                    double rad = (0.3 + ease * (2.4 - wave * 0.65));
                    double y = r.y + 0.1 + p * (0.6 + wave * 0.45);
                    double ribbonH = 0.28 * (1.0 - p * 0.5);
                    int segs = 28;
                    for (int i = 0; i < segs; i++) {
                        double a0 = i * (2.0 * Math.PI / segs) + p * (wave == 0 ? 1.2 : -1.2);
                        double a1 = (i + 1) * (2.0 * Math.PI / segs) + p * (wave == 0 ? 1.2 : -1.2);
                        double x0 = r.x + Math.cos(a0) * rad, z0 = r.z + Math.sin(a0) * rad;
                        double x1 = r.x + Math.cos(a1) * rad, z1 = r.z + Math.sin(a1) * rad;

                        event.renderer.line(x0, y, z0, x1, y, z1, c0);
                        event.renderer.quad(
                            x0, y, z0,
                            x1, y, z1,
                            x1, y + ribbonH, z1,
                            x0, y + ribbonH, z0,
                            c1, c1, cSub0, cSub0
                        );
                    }
                }
            }
        }

        // Render 3D player frost ribbon trail
        if (frostSteps.get() && playerTrail.size() >= 2) {
            drawRibbon(event, playerTrail, now, 650.0, 0.16, base);
        }

        // Render 3D projectile trails (interpolating tip to live entity position for 144Hz+ smoothness)
        if ((pearlTrails.get() || arrowTrails.get()) && !projectileTrails.isEmpty()) {
            for (Map.Entry<Integer, Deque<TrailPoint>> entry : projectileTrails.entrySet()) {
                Deque<TrailPoint> q = entry.getValue();
                if (q.size() < 2) continue;
                drawRibbon(event, q, now, 750.0, 0.14, base);

                Entity e = mc.world.getEntityById(entry.getKey());
                if (e != null) {
                    TrailPoint last = q.peekLast();
                    double ex = MathHelper.lerp(event.tickDelta, e.lastRenderX, e.getX());
                    double ey = MathHelper.lerp(event.tickDelta, e.lastRenderY, e.getY()) + e.getHeight() * 0.5;
                    double ez = MathHelper.lerp(event.tickDelta, e.lastRenderZ, e.getZ());
                    c0.set(255, 255, 255, base.a);
                    event.renderer.line(last.x, last.y, last.z, ex, ey, ez, c0);
                }
            }
        }
    }

    private void drawRibbon(Render3DEvent event, Deque<TrailPoint> pts, long now, double maxAge, double height, SettingColor base) {
        TrailPoint prev = null;
        for (TrailPoint cur : pts) {
            if (prev != null) {
                double f0 = Math.max(0.0, 1.0 - (now - prev.time) / maxAge);
                double f1 = Math.max(0.0, 1.0 - (now - cur.time) / maxAge);
                int a0 = (int) (base.a * f0 * f0);
                int a1 = (int) (base.a * f1 * f1);
                if (a0 > 2 || a1 > 2) {
                    c0.set(lighten(base.r, f0 * 0.5), lighten(base.g, f0 * 0.5), lighten(base.b, f0 * 0.5), a0);
                    c1.set(lighten(base.r, f1 * 0.5), lighten(base.g, f1 * 0.5), lighten(base.b, f1 * 0.5), a1);
                    cSub0.set(base.r, base.g, base.b, a0 / 4);
                    cSub1.set(base.r, base.g, base.b, a1 / 4);

                    event.renderer.line(prev.x, prev.y, prev.z, cur.x, cur.y, cur.z, c0, c1);
                    event.renderer.quad(
                        prev.x, prev.y, prev.z,
                        cur.x, cur.y, cur.z,
                        cur.x, cur.y + height * f1, cur.z,
                        prev.x, prev.y + height * f0, prev.z,
                        cSub0, cSub1, cSub1, cSub0
                    );
                }
            }
            prev = cur;
        }
    }

    private static int lighten(int channel, double amount) {
        return Math.min(255, (int) (channel + (255 - channel) * amount));
    }
}
