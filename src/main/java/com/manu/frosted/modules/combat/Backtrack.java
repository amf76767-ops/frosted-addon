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
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
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
 * where their lag/backtrack hitbox sits, and includes a Freeze Keybind that locks the
 * enemy's backtrack hitbox in place so you can spam-hit them repeatedly.
 */
public class Backtrack extends Module {
    public enum FreezeMode {
        Toggle,
        Hold
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFreeze = settings.createGroup("Freeze");
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

    // -------------------- Freeze Settings --------------------

    private final Setting<Keybind> freezeKey = sgFreeze.add(new KeybindSetting.Builder()
        .name("freeze-key")
        .description("Extra keybind to freeze the enemy's backtrack position in place.")
        .defaultValue(Keybind.none())
        .action(this::onFreezeKeyAction)
        .build()
    );

    private final Setting<FreezeMode> freezeMode = sgFreeze.add(new EnumSetting.Builder<FreezeMode>()
        .name("freeze-mode")
        .description("Toggle freezes/unfreezes on press; Hold stays frozen while holding the key.")
        .defaultValue(FreezeMode.Toggle)
        .build()
    );

    private final Setting<Integer> maxFreezeMs = sgFreeze.add(new IntSetting.Builder()
        .name("max-freeze-ms")
        .description("Auto-unfreeze after this many milliseconds (0 = stay frozen until toggled off).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 15000)
        .build()
    );

    private final Setting<Boolean> lockEntityModel = sgFreeze.add(new BoolSetting.Builder()
        .name("lock-player-model")
        .description("Also locks the enemy player's model at the frozen backtrack position on your screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> multiHitPackets = sgFreeze.add(new IntSetting.Builder()
        .name("hits-per-swing")
        .description("How many attack packets to send per swing when hitting a frozen enemy.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> rapidAutoHit = sgFreeze.add(new BoolSetting.Builder()
        .name("rapid-multi-hit")
        .description("Rapidly attacks the frozen target every tick while holding attack (or aiming at them).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireHoldingAttack = sgFreeze.add(new BoolSetting.Builder()
        .name("only-when-clicking")
        .description("Only rapid-hit while holding the left mouse button (if off, auto-hits whenever aiming at the frozen box).")
        .defaultValue(true)
        .visible(rapidAutoHit::get)
        .build()
    );

    // -------------------- Render Settings --------------------

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

    private final Setting<SettingColor> frozenColor = sgRender.add(new ColorSetting.Builder()
        .name("frozen-color")
        .description("Color of the Ghost-Box while frozen.")
        .defaultValue(new SettingColor(80, 245, 255, 235))
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
    private final Map<UUID, Snapshot> frozenSnapshots = new HashMap<>();
    private final Map<UUID, Long> lastAttacked = new HashMap<>();

    private boolean frozen;
    private long freezeStartTime;
    private boolean burstingPackets;

    private final Color cFill = new Color();
    private final Color cLine = new Color();
    private final Color cTether = new Color();

    public Backtrack() {
        super(FrostedAddon.COMBAT, "backtrack", "Shows a 3D Frosted lag hitbox behind players and lets you freeze & multi-hit their backtrack position.");
    }

    @Override
    public void onActivate() {
        history.clear();
        frozenSnapshots.clear();
        lastAttacked.clear();
        frozen = false;
        burstingPackets = false;
    }

    @Override
    public void onDeactivate() {
        history.clear();
        frozenSnapshots.clear();
        lastAttacked.clear();
        frozen = false;
        burstingPackets = false;
    }

    @Override
    public String getInfoString() {
        if (frozen) return "FROZEN (" + frozenSnapshots.size() + ")";
        return delayMs.get() + "ms";
    }

    private void onFreezeKeyAction() {
        if (!isActive() || mc.world == null || mc.player == null) return;
        if (freezeMode.get() == FreezeMode.Toggle) {
            setFrozen(!frozen);
        }
    }

    private void setFrozen(boolean enable) {
        if (enable == frozen) return;
        if (enable) {
            captureFrozenPositions();
            if (!frozenSnapshots.isEmpty()) {
                frozen = true;
                freezeStartTime = System.currentTimeMillis();
            }
        } else {
            frozen = false;
            frozenSnapshots.clear();
        }
    }

    private void captureFrozenPositions() {
        frozenSnapshots.clear();
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        double rangeSq = maxRange.get() * maxRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            if (mc.player.squaredDistanceTo(player) > rangeSq) continue;

            Snapshot snap = getDelayedSnapshot(player.getUuid(), now);
            if (snap == null) {
                Box box = player.getBoundingBox();
                snap = new Snapshot(
                    player.getX(), player.getY(), player.getZ(),
                    (box.maxX - box.minX) * 0.5, box.maxY - box.minY, now
                );
            }
            frozenSnapshots.put(player.getUuid(), snap);
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (burstingPackets) return;
        if (!(event.entity instanceof PlayerEntity player)) return;

        lastAttacked.put(player.getUuid(), System.currentTimeMillis());

        if (frozen && frozenSnapshots.containsKey(player.getUuid()) && mc.player != null && mc.player.networkHandler != null) {
            player.hurtTime = 0;
            player.timeUntilRegen = 0;
            int extra = multiHitPackets.get() - 1;
            if (extra > 0) {
                burstingPackets = true;
                try {
                    for (int i = 0; i < extra; i++) {
                        mc.player.networkHandler.sendPacket(
                            PlayerInteractEntityC2SPacket.attack(player, mc.player.isSneaking())
                        );
                    }
                } finally {
                    burstingPackets = false;
                }
            }
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) {
            frozen = false;
            frozenSnapshots.clear();
            return;
        }

        // Handle Hold mode and max-freeze-ms timeout
        if (freezeMode.get() == FreezeMode.Hold) {
            boolean held = freezeKey.get().isSet() && freezeKey.get().isPressed() && mc.currentScreen == null;
            setFrozen(held);
        } else if (frozen && maxFreezeMs.get() > 0) {
            if (System.currentTimeMillis() - freezeStartTime >= maxFreezeMs.get()) {
                setFrozen(false);
            }
        }

        if (!frozen) return;

        // Capture any newly in-range player if map became empty
        if (frozenSnapshots.isEmpty()) {
            captureFrozenPositions();
        }

        // Lock frozen players at their frozen coordinates if lockEntityModel is enabled
        if (lockEntityModel.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                Snapshot f = frozenSnapshots.get(player.getUuid());
                if (f != null && player.isAlive()) {
                    player.setPosition(f.x, f.y, f.z);
                    player.hurtTime = 0;
                    player.timeUntilRegen = 0;
                }
            }
        }

        // Rapid multi-hit while frozen
        if (rapidAutoHit.get() && mc.currentScreen == null && mc.interactionManager != null) {
            boolean shouldAttack = !requireHoldingAttack.get() || mc.options.attackKey.isPressed();
            if (shouldAttack) {
                updateCrosshairRetarget();
                if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity target) {
                    if (frozenSnapshots.containsKey(target.getUuid())) {
                        target.hurtTime = 0;
                        target.timeUntilRegen = 0;
                        mc.interactionManager.attackEntity(mc.player, target);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) {
            history.clear();
            frozenSnapshots.clear();
            frozen = false;
            return;
        }

        long now = System.currentTimeMillis();
        long maxAge = delayMs.get() + 150L;
        double rangeSq = maxRange.get() * maxRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            if (mc.player.squaredDistanceTo(player) > rangeSq) continue;

            if (frozen && lockEntityModel.get() && frozenSnapshots.containsKey(player.getUuid())) {
                Snapshot f = frozenSnapshots.get(player.getUuid());
                player.setPosition(f.x, f.y, f.z);
                continue;
            }

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
     * so aiming at a player's backtracked or frozen ghost box allows attacking that player.
     */
    public void updateCrosshairRetarget() {
        if (!isActive() || (!hitGhostBox.get() && !frozen)) return;
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.targetedEntity instanceof PlayerEntity) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        double reach = Math.max(mc.player.getEntityInteractionRange(), frozen ? 6.0 : mc.player.getEntityInteractionRange());
        Vec3d end = eye.add(look.multiply(reach));

        long now = System.currentTimeMillis();
        PlayerEntity bestPlayer = null;
        Vec3d bestHit = null;
        double bestDistSq = reach * reach;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

            Snapshot snap = getActiveSnapshot(player.getUuid(), now);
            if (snap == null) continue;

            Box ghostBox = snap.toBox().expand(player.getTargetingMargin() + (frozen ? 0.15 : 0.0));
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

    private Snapshot getActiveSnapshot(UUID uuid, long now) {
        if (frozen) {
            Snapshot f = frozenSnapshots.get(uuid);
            if (f != null) return f;
        }
        return getDelayedSnapshot(uuid, now);
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
        if (mc.world == null || mc.player == null) return;
        if (history.isEmpty() && frozenSnapshots.isEmpty()) return;

        long now = System.currentTimeMillis();
        SettingColor base = frozen ? frozenColor.get() : ghostColor.get();
        SettingColor hit = hitColor.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;

            Snapshot ghost = getActiveSnapshot(player.getUuid(), now);
            if (ghost == null) continue;

            if (!frozen) {
                double dx = player.getX() - ghost.x;
                double dy = player.getY() - ghost.y;
                double dz = player.getZ() - ghost.z;
                if (dx * dx + dy * dy + dz * dz < 0.015) continue;
            }

            long lastHit = lastAttacked.getOrDefault(player.getUuid(), 0L);
            double hitMix = Math.max(0.0, 1.0 - (now - lastHit) / 380.0);

            int r = (int) Math.round(base.r + (hit.r - base.r) * hitMix);
            int g = (int) Math.round(base.g + (hit.g - base.g) * hitMix);
            int b = (int) Math.round(base.b + (hit.b - base.b) * hitMix);

            double pulse = 0.82 + 0.18 * Math.sin(now * (frozen ? 0.016 : 0.008));
            int lineA = clamp((int) (base.a * pulse));
            int fillA = clamp((int) (base.a * (frozen ? 0.36 : 0.24) * pulse));

            cFill.set(r, g, b, fillA);
            cLine.set(lighten(r, 0.35), lighten(g, 0.35), lighten(b, 0.35), lineA);

            Box box = ghost.toBox();
            event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                cFill, cLine, shapeMode.get(), 0);

            // Inner frost spine inside the ghost hitbox
            cTether.set(235, 250, 255, clamp((int) (lineA * 0.8)));
            event.renderer.line(ghost.x, box.minY + 0.05, ghost.z, ghost.x, box.maxY - 0.05, ghost.z, cTether);

            // If frozen, draw a rotating 3D ice cage ring around the frozen hitbox
            if (frozen) {
                double spin = now * 0.004;
                double rad = ghost.halfW * 1.45;
                for (int i = 0; i < 6; i++) {
                    double a1 = spin + i * (Math.PI / 3.0);
                    double a2 = spin + (i + 1) * (Math.PI / 3.0);
                    double x1 = ghost.x + Math.cos(a1) * rad;
                    double z1 = ghost.z + Math.sin(a1) * rad;
                    double x2 = ghost.x + Math.cos(a2) * rad;
                    double z2 = ghost.z + Math.sin(a2) * rad;
                    event.renderer.line(x1, box.minY + 0.08, z1, x2, box.minY + 0.08, z2, cLine);
                    event.renderer.line(x1, box.maxY - 0.08, z1, x2, box.maxY - 0.08, z2, cLine);
                }
            }

            Deque<Snapshot> deque = history.get(player.getUuid());
            if (showTrail.get() && deque != null && !deque.isEmpty()) {
                double curX = player.lastRenderX + (player.getX() - player.lastRenderX) * event.tickDelta;
                double curY = player.lastRenderY + (player.getY() - player.lastRenderY) * event.tickDelta;
                double curZ = player.lastRenderZ + (player.getZ() - player.lastRenderZ) * event.tickDelta;

                double midH = ghost.height * 0.5;
                cTether.set(r, g, b, clamp((int) (lineA * 0.65)));
                event.renderer.line(ghost.x, ghost.y + midH, ghost.z, curX, curY + midH, curZ, cLine, cTether);
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
