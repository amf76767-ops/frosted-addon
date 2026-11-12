package com.manu.frosted.modules.combat;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks delayed historical positions of enemy players, renders a 3D Frosted Ghost-Box
 * where their lag/backtrack hitbox sits, and optionally lets your crosshair raycast hit
 * the backtracked ghost box directly.
 */
public class Backtrack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> delayMs = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ms")
        .description("How far back in milliseconds the backtrack ghost hitbox sits.")
        .defaultValue(240)
        .min(40)
        .sliderRange(40, 800)
        .build()
    );

    private final Setting<Boolean> hitGhostBox = sgGeneral.add(new BoolSetting.Builder()
        .name("hit-ghost-box")
        .description("Allows you to attack the delayed ghost hitbox directly when aiming at it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-range")
        .description("Maximum distance to track and render player backtrack boxes.")
        .defaultValue(14.0)
        .min(3.0)
        .sliderRange(3.0, 32.0)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not backtrack friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> showTrail = sgRender.add(new BoolSetting.Builder()
        .name("show-trail")
        .description("Draws fading intermediate frost frames and a tether line to the player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> ghostColor = sgRender.add(new ColorSetting.Builder()
        .name("ghost-color")
        .description("Color of the backtracked Frosted Ghost-Box.")
        .defaultValue(new SettingColor(135, 220, 255, 190))
        .build()
    );

    private final Setting<SettingColor> hitColor = sgRender.add(new ColorSetting.Builder()
        .name("hit-color")
        .description("Color of the Ghost-Box right after you attack the target.")
        .defaultValue(new SettingColor(255, 105, 180, 220))
        .build()
    );

    private record Snapshot(double x, double y, double z, double halfW, double height, long time) {
        Box toBox() {
            return new Box(x - halfW, y, z - halfW, x + halfW, y + height, z + halfW);
        }
    }

    private final Map<UUID, Deque<Snapshot>> history = new HashMap<>();
    private final Map<UUID, Long> lastAttacked = new HashMap<>();

    private final Color cFill = new Color();
    private final Color cLine = new Color();
    private final Color cTether = new Color();

    public Backtrack() {
        super(FrostedAddon.COMBAT, "backtrack", "Shows a 3D Frosted lag hitbox behind players and lets you hit their delayed position.");
    }

    @Override
    public void onActivate() {
        history.clear();
        lastAttacked.clear();
    }

    @Override
    public void onDeactivate() {
        history.clear();
        lastAttacked.clear();
    }

    @Override
    public String getInfoString() {
        return delayMs.get() + "ms";
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof PlayerEntity player) {
            lastAttacked.put(player.getUuid(), System.currentTimeMillis());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            history.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long maxAge = delayMs.get() + 150L;
        double rangeSq = maxRange.get() * maxRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            if (mc.player.squaredDistanceTo(player) > rangeSq) continue;

            Deque<Snapshot> deque = history.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
            Box box = player.getBoundingBox();
            double halfW = (box.maxX - box.minX) * 0.5;
            double height = box.maxY - box.minY;

            deque.addLast(new Snapshot(player.getX(), player.getY(), player.getZ(), halfW, height, now));
            while (deque.size() > 1 && now - deque.peekFirst().time > maxAge) {
                deque.removeFirst();
            }
        }

        Iterator<Map.Entry<UUID, Deque<Snapshot>>> it = history.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Deque<Snapshot>> entry = it.next();
            Deque<Snapshot> deque = entry.getValue();
            while (!deque.isEmpty() && now - deque.peekFirst().time > maxAge) {
                deque.removeFirst();
            }
            if (deque.isEmpty()) it.remove();
        }
    }

    /**
     * Called from MinecraftClientMixin right after GameRenderer.updateCrosshairTarget(F)
     * so aiming at a player's backtracked ghost box allows attacking that player.
     */
    public void updateCrosshairRetarget() {
        if (!isActive() || !hitGhostBox.get()) return;
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.targetedEntity != null) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        double reach = mc.player.getEntityInteractionRange();
        Vec3d end = eye.add(look.multiply(reach));

        long now = System.currentTimeMillis();
        PlayerEntity bestPlayer = null;
        Vec3d bestHit = null;
        double bestDistSq = reach * reach;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

            Snapshot snap = getDelayedSnapshot(player.getUuid(), now);
            if (snap == null) continue;

            Box ghostBox = snap.toBox().expand(player.getTargetingMargin());
            if (ghostBox.contains(eye)) {
                bestPlayer = player;
                bestHit = eye;
                break;
            }

            Optional<Vec3d> hit = ghostBox.raycast(eye, end);
            if (hit.isPresent()) {
                double dSq = eye.squaredDistanceTo(hit.get());
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestPlayer = player;
                    bestHit = hit.get();
                }
            }
        }

        if (bestPlayer != null && bestHit != null) {
            mc.crosshairTarget = new EntityHitResult(bestPlayer, bestHit);
            mc.targetedEntity = bestPlayer;
        }
    }

    private Snapshot getDelayedSnapshot(UUID uuid, long now) {
        Deque<Snapshot> deque = history.get(uuid);
        if (deque == null || deque.isEmpty()) return null;

        long targetTime = now - delayMs.get();
        Snapshot chosen = deque.peekFirst();
        for (Snapshot s : deque) {
            if (s.time <= targetTime) {
                chosen = s;
            } else {
                break;
            }
        }
        return chosen;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null || history.isEmpty()) return;

        long now = System.currentTimeMillis();
        SettingColor base = ghostColor.get();
        SettingColor hit = hitColor.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            Deque<Snapshot> deque = history.get(player.getUuid());
            if (deque == null || deque.isEmpty()) continue;

            Snapshot ghost = getDelayedSnapshot(player.getUuid(), now);
            if (ghost == null) continue;

            // Only draw when the player has actually moved away from the ghost position
            double dx = player.getX() - ghost.x;
            double dy = player.getY() - ghost.y;
            double dz = player.getZ() - ghost.z;
            if (dx * dx + dy * dy + dz * dz < 0.015) continue;

            long lastHit = lastAttacked.getOrDefault(player.getUuid(), 0L);
            double hitMix = Math.max(0.0, 1.0 - (now - lastHit) / 450.0);

            int r = (int) Math.round(base.r + (hit.r - base.r) * hitMix);
            int g = (int) Math.round(base.g + (hit.g - base.g) * hitMix);
            int b = (int) Math.round(base.b + (hit.b - base.b) * hitMix);

            double pulse = 0.82 + 0.18 * Math.sin(now * 0.008);
            int lineA = clamp((int) (base.a * pulse));
            int fillA = clamp((int) (base.a * 0.24 * pulse));

            cFill.set(r, g, b, fillA);
            cLine.set(lighten(r, 0.35), lighten(g, 0.35), lighten(b, 0.35), lineA);

            Box box = ghost.toBox();
            event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                cFill, cLine, shapeMode.get(), 0);

            // Inner frost spine inside the ghost hitbox
            cTether.set(230, 248, 255, clamp((int) (lineA * 0.7)));
            event.renderer.line(ghost.x, box.minY + 0.05, ghost.z, ghost.x, box.maxY - 0.05, ghost.z, cTether);

            if (showTrail.get()) {
                // Draw tether from ghost center to current interpolated player center
                double curX = player.lastRenderX + (player.getX() - player.lastRenderX) * event.tickDelta;
                double curY = player.lastRenderY + (player.getY() - player.lastRenderY) * event.tickDelta;
                double curZ = player.lastRenderZ + (player.getZ() - player.lastRenderZ) * event.tickDelta;

                double midH = ghost.height * 0.5;
                cTether.set(r, g, b, clamp((int) (lineA * 0.65)));
                event.renderer.line(ghost.x, ghost.y + midH, ghost.z, curX, curY + midH, curZ, cLine, cTether);

                // Intermediate trail footprints
                int idx = 0;
                int size = deque.size();
                for (Snapshot step : deque) {
                    idx++;
                    if (idx % 2 != 0 || step == ghost) continue;
                    double frac = idx / (double) Math.max(1, size);
                    cTether.set(r, g, b, clamp((int) (lineA * 0.35 * (1.0 - frac))));
                    double w = step.halfW * 0.75;
                    event.renderer.line(step.x - w, step.y + 0.03, step.z - w, step.x + w, step.y + 0.03, step.z - w, cTether);
                    event.renderer.line(step.x + w, step.y + 0.03, step.z - w, step.x + w, step.y + 0.03, step.z + w, cTether);
                    event.renderer.line(step.x + w, step.y + 0.03, step.z + w, step.x - w, step.y + 0.03, step.z + w, cTether);
                    event.renderer.line(step.x - w, step.y + 0.03, step.z + w, step.x - w, step.y + 0.03, step.z - w, cTether);
                }
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int lighten(int channel, double amount) {
        return Math.min(255, (int) Math.round(channel + (255 - channel) * amount));
    }
}
