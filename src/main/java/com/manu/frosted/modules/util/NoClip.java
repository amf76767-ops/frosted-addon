package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTickMovementEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Normal player NoClip without a boat: removes all block collisions and either
 * lets you fall straight through the ground/world (FallThrough) or phase freely (FreePhase).
 */
public class NoClip extends Module {
    public enum Mode {
        FallThrough,
        FreePhase
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("FallThrough drops you straight through the world; FreePhase lets you fly through blocks.")
        .defaultValue(Mode.FallThrough)
        .build()
    );

    private final Setting<Boolean> naturalGravity = sgGeneral.add(new BoolSetting.Builder()
        .name("natural-gravity")
        .description("Use normal Minecraft gravity acceleration when falling through the world.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.FallThrough)
        .build()
    );

    private final Setting<Double> fallSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("fall-speed")
        .description("Fixed downward speed when natural-gravity is off (or in FreePhase).")
        .defaultValue(0.45)
        .min(0.05)
        .sliderRange(0.05, 3.0)
        .build()
    );

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal steering speed while phasing through blocks.")
        .defaultValue(0.35)
        .min(0.05)
        .sliderRange(0.05, 2.5)
        .build()
    );

    private final Setting<Boolean> spaceBrake = sgGeneral.add(new BoolSetting.Builder()
        .name("space-to-hover")
        .description("Hold Space in FallThrough mode to pause falling / hover inside blocks.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.FallThrough)
        .build()
    );

    public NoClip() {
        super(FrostedAddon.UTIL, "noclip", "Disables block collision so you fall straight through the ground/world or phase through walls.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.noClip = false;
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    @EventHandler
    private void onPlayerTickMovement(PlayerTickMovementEvent event) {
        if (mc.player == null) return;
        mc.player.noClip = true;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        mc.player.noClip = true;
        mc.player.fallDistance = 0f;
        mc.player.setOnGround(false);
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null) return;
        mc.player.noClip = true;
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (mc.player == null) return;
        event.shape = VoxelShapes.empty();
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null) return;
        mc.player.noClip = true;

        boolean forward = mc.currentScreen == null && Input.isKeyPressed(Input.getKey(mc.options.forwardKey));
        boolean back = mc.currentScreen == null && Input.isKeyPressed(Input.getKey(mc.options.backKey));
        boolean left = mc.currentScreen == null && Input.isKeyPressed(Input.getKey(mc.options.leftKey));
        boolean right = mc.currentScreen == null && Input.isKeyPressed(Input.getKey(mc.options.rightKey));
        boolean up = mc.currentScreen == null && Input.isKeyPressed(Input.getKey(mc.options.jumpKey));
        boolean down = mc.currentScreen == null && Input.isKeyPressed(Input.getKey(mc.options.sneakKey));

        double forwardAmt = (forward ? 1.0 : 0.0) - (back ? 1.0 : 0.0);
        double strafeAmt = (left ? 1.0 : 0.0) - (right ? 1.0 : 0.0);

        double vx = event.movement.x;
        double vz = event.movement.z;
        if (forwardAmt != 0.0 || strafeAmt != 0.0) {
            double len = Math.hypot(forwardAmt, strafeAmt);
            forwardAmt /= len;
            strafeAmt /= len;

            double yawRad = Math.toRadians(mc.player.getYaw());
            double sin = -Math.sin(yawRad);
            double cos = Math.cos(yawRad);

            double speed = horizontalSpeed.get();
            vx = (forwardAmt * sin + strafeAmt * cos) * speed;
            vz = (forwardAmt * cos - strafeAmt * sin) * speed;
        } else if (mode.get() == Mode.FreePhase) {
            vx = 0.0;
            vz = 0.0;
        }

        double vy;
        if (mode.get() == Mode.FallThrough) {
            if (spaceBrake.get() && up) {
                vy = 0.0;
                mc.player.setVelocity(vx, 0.0, vz);
            } else if (naturalGravity.get()) {
                vy = Math.min(-0.08, event.movement.y);
            } else {
                vy = -fallSpeed.get();
                mc.player.setVelocity(vx, vy, vz);
            }
        } else {
            vy = 0.0;
            if (up && !down) vy = fallSpeed.get();
            else if (down && !up) vy = -fallSpeed.get();
            mc.player.setVelocity(vx, vy, vz);
        }

        ((IVec3d) event.movement).meteor$set(vx, vy, vz);
    }
}
