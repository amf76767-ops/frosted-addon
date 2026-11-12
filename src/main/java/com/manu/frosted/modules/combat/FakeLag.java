package com.manu.frosted.modules.combat;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chokes outgoing movement packets in timed bursts (with optional random jitter)
 * so other players see you lagging/teleporting in steps, and draws a 3D Frosted
 * Ghost-Box at your current server-side position.
 */
public class FakeLag extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chokeMs = sgGeneral.add(new IntSetting.Builder()
        .name("choke-ms")
        .description("Base duration in milliseconds to hold movement packets before bursting.")
        .defaultValue(320)
        .min(50)
        .sliderRange(50, 1500)
        .build()
    );

    private final Setting<Integer> jitterMs = sgGeneral.add(new IntSetting.Builder()
        .name("jitter-ms")
        .description("Random extra lag variance added to each burst to mimic unstable ping.")
        .defaultValue(90)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Boolean> flushOnAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("flush-on-attack")
        .description("Immediately flushes queued movement packets when you attack an entity so hits register cleanly.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lagVehicles = sgGeneral.add(new BoolSetting.Builder()
        .name("lag-vehicles")
        .description("Also choke VehicleMoveC2SPacket when riding boats or horses.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderServerGhost = sgRender.add(new BoolSetting.Builder()
        .name("render-server-pos")
        .description("Draws a 3D Frosted Ghost-Box where the server and other players currently see you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> ghostColor = sgRender.add(new ColorSetting.Builder()
        .name("ghost-color")
        .defaultValue(new SettingColor(140, 215, 255, 180))
        .build()
    );

    private final List<Packet<?>> queue = new ArrayList<>();
    private boolean flushing;
    private long lastFlushTime;
    private long currentInterval;

    private double serverX;
    private double serverY;
    private double serverZ;
    private boolean hasServerPos;

    private final Color cFill = new Color();
    private final Color cLine = new Color();

    public FakeLag() {
        super(FrostedAddon.COMBAT, "fake-lag", "Holds movement packets in bursts so you appear to lag to other players.");
    }

    @Override
    public void onActivate() {
        synchronized (queue) {
            queue.clear();
        }
        flushing = false;
        lastFlushTime = System.currentTimeMillis();
        rollNextInterval();

        if (mc.player != null) {
            serverX = mc.player.getX();
            serverY = mc.player.getY();
            serverZ = mc.player.getZ();
            hasServerPos = true;
        } else {
            hasServerPos = false;
        }
    }

    @Override
    public void onDeactivate() {
        flushQueue();
        hasServerPos = false;
    }

    @Override
    public String getInfoString() {
        synchronized (queue) {
            return queue.size() + " pkts";
        }
    }

    private void rollNextInterval() {
        int base = chokeMs.get();
        int jit = jitterMs.get();
        int extra = jit > 0 ? ThreadLocalRandom.current().nextInt(-jit / 2, jit + 1) : 0;
        currentInterval = Math.max(40, base + extra);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) return;

        long now = System.currentTimeMillis();
        if (now - lastFlushTime >= currentInterval) {
            flushQueue();
            lastFlushTime = now;
            rollNextInterval();
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!Utils.canUpdate() || flushing) return;

        Packet<?> packet = event.packet;

        if (flushOnAttack.get() && packet instanceof PlayerInteractEntityC2SPacket) {
            flushQueue();
            lastFlushTime = System.currentTimeMillis();
            rollNextInterval();
            return;
        }

        boolean isPlayerMove = packet instanceof PlayerMoveC2SPacket;
        boolean isVehicleMove = lagVehicles.get() && packet instanceof VehicleMoveC2SPacket;

        if (!isPlayerMove && !isVehicleMove) return;

        event.cancel();
        synchronized (queue) {
            queue.add(packet);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        synchronized (queue) {
            queue.clear();
        }
        hasServerPos = false;
    }

    private void flushQueue() {
        if (mc.player == null || mc.player.networkHandler == null) {
            synchronized (queue) {
                queue.clear();
            }
            return;
        }

        flushing = true;
        try {
            synchronized (queue) {
                for (Packet<?> pkt : queue) {
                    mc.player.networkHandler.sendPacket(pkt);
                    if (pkt instanceof PlayerMoveC2SPacket move && move.changesPosition()) {
                        serverX = move.getX(serverX);
                        serverY = move.getY(serverY);
                        serverZ = move.getZ(serverZ);
                        hasServerPos = true;
                    }
                }
                queue.clear();
            }
            if (mc.player != null) {
                serverX = mc.player.getX();
                serverY = mc.player.getY();
                serverZ = mc.player.getZ();
                hasServerPos = true;
            }
        } finally {
            flushing = false;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderServerGhost.get() || !hasServerPos || mc.player == null) return;

        double dx = mc.player.getX() - serverX;
        double dy = mc.player.getY() - serverY;
        double dz = mc.player.getZ() - serverZ;
        if (dx * dx + dy * dy + dz * dz < 0.02) return;

        SettingColor col = ghostColor.get();
        double pulse = 0.8 + 0.2 * Math.sin(System.currentTimeMillis() * 0.01);
        cFill.set(col.r, col.g, col.b, Math.max(0, Math.min(255, (int) (col.a * 0.22 * pulse))));
        cLine.set(col.r, col.g, col.b, Math.max(0, Math.min(255, (int) (col.a * pulse))));

        double hw = mc.player.getWidth() * 0.5;
        double h = mc.player.getHeight();

        event.renderer.box(
            serverX - hw, serverY, serverZ - hw,
            serverX + hw, serverY + h, serverZ + hw,
            cFill, cLine, shapeMode.get(), 0
        );

        double curX = mc.player.lastRenderX + (mc.player.getX() - mc.player.lastRenderX) * event.tickDelta;
        double curY = mc.player.lastRenderY + (mc.player.getY() - mc.player.lastRenderY) * event.tickDelta;
        double curZ = mc.player.lastRenderZ + (mc.player.getZ() - mc.player.lastRenderZ) * event.tickDelta;
        event.renderer.line(serverX, serverY + 0.1, serverZ, curX, curY + 0.1, curZ, cLine);
    }
}
