package com.manu.frosted.modules.util;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.entity.EntityMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTickMovementEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Boat Fly + NoClip: lets you fly a boat (or any vehicle) freely in 3D right through
 * the ground and walls without dismounting when holding Shift to descend.
 */
public class BoatNoClip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal boat flight speed in blocks per tick.")
        .defaultValue(1.4)
        .min(0.1)
        .sliderRange(0.1, 6.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical ascent/descent speed when pressing Space or Shift/Ctrl.")
        .defaultValue(0.85)
        .min(0.1)
        .sliderRange(0.1, 4.0)
        .build()
    );

    private final Setting<Double> fallSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("glide-speed")
        .description("Slow downward drift when neither ascending nor descending (0 = hover in place).")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 0.5)
        .build()
    );

    private final Setting<Boolean> shiftDescend = sgGeneral.add(new BoolSetting.Builder()
        .name("shift-descends")
        .description("Hold Sneak (Shift) to fly down through the ground without dismounting from the boat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ctrlDescend = sgGeneral.add(new BoolSetting.Builder()
        .name("ctrl-descends")
        .description("Also allow holding Sprint (Ctrl) to fly straight down through the floor.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> matchPlayerYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-boat-yaw")
        .description("Rotates the boat to face your camera direction.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiKick = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-kick")
        .description("Periodically dips the boat slightly downward to bypass vanilla fly kick.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> antiKickInterval = sgGeneral.add(new IntSetting.Builder()
        .name("anti-kick-interval")
        .description("Ticks between anti-kick downward pulses.")
        .defaultValue(25)
        .min(5)
        .sliderRange(10, 60)
        .visible(antiKick::get)
        .build()
    );

    private final Setting<Boolean> anyVehicle = sgGeneral.add(new BoolSetting.Builder()
        .name("any-vehicle")
        .description("Allow Boat NoClip to work on horses, minecarts, striders and pigs too.")
        .defaultValue(true)
        .build()
    );

    private int tickTimer;

    public BoatNoClip() {
        super(FrostedAddon.UTIL, "boat-noclip", "Fly with a boat straight through the ground and walls (Boat Fly + NoClip).");
    }

    @Override
    public void onActivate() {
        tickTimer = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.noClip = false;
            Entity vehicle = mc.player.getVehicle();
            if (vehicle != null) {
                vehicle.noClip = false;
            }
        }
    }

    private Entity getTargetVehicle() {
        if (mc.player == null) return null;
        Entity v = mc.player.getVehicle();
        if (v == null) return null;
        if (anyVehicle.get() || v instanceof AbstractBoatEntity) return v;
        return null;
    }

    @EventHandler
    private void onPlayerTickMovement(PlayerTickMovementEvent event) {
        Entity vehicle = getTargetVehicle();
        if (vehicle == null) return;

        vehicle.noClip = true;
        mc.player.noClip = true;

        // Suppress the sneak key so Minecraft does not dismount the player while flying down
        if (shiftDescend.get() && mc.currentScreen == null) {
            mc.options.sneakKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        Entity vehicle = getTargetVehicle();
        if (vehicle == null) return;

        tickTimer++;
        vehicle.noClip = true;
        mc.player.noClip = true;
        mc.player.fallDistance = 0f;
        vehicle.fallDistance = 0f;

        if (shiftDescend.get() && mc.currentScreen == null) {
            mc.options.sneakKey.setPressed(false);
        }

        if (matchPlayerYaw.get()) {
            vehicle.setYaw(mc.player.getYaw());
        }

        Vec3d vel = computeFlightVelocity();
        vehicle.setVelocity(vel);
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        Entity vehicle = getTargetVehicle();
        if (vehicle == null) return;
        vehicle.noClip = true;
        mc.player.noClip = true;
    }

    @EventHandler
    private void onEntityMove(EntityMoveEvent event) {
        Entity vehicle = getTargetVehicle();
        if (vehicle == null || event.entity != vehicle) return;

        vehicle.noClip = true;
        mc.player.noClip = true;

        Vec3d vel = computeFlightVelocity();
        ((IVec3d) event.movement).meteor$set(vel.x, vel.y, vel.z);
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (getTargetVehicle() != null) {
            event.shape = VoxelShapes.empty();
        }
    }

    private Vec3d computeFlightVelocity() {
        if (mc.player == null || mc.currentScreen != null) {
            return new Vec3d(0, -fallSpeed.get(), 0);
        }

        boolean forward = Input.isKeyPressed(Input.getKey(mc.options.forwardKey));
        boolean back = Input.isKeyPressed(Input.getKey(mc.options.backKey));
        boolean left = Input.isKeyPressed(Input.getKey(mc.options.leftKey));
        boolean right = Input.isKeyPressed(Input.getKey(mc.options.rightKey));
        boolean up = Input.isKeyPressed(Input.getKey(mc.options.jumpKey));
        boolean downShift = shiftDescend.get() && Input.isKeyPressed(Input.getKey(mc.options.sneakKey));
        boolean downCtrl = ctrlDescend.get() && Input.isKeyPressed(Input.getKey(mc.options.sprintKey));
        boolean down = downShift || downCtrl;

        double forwardAmt = (forward ? 1.0 : 0.0) - (back ? 1.0 : 0.0);
        double strafeAmt = (left ? 1.0 : 0.0) - (right ? 1.0 : 0.0);

        double vx = 0.0;
        double vz = 0.0;
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
        }

        double vy = -fallSpeed.get();
        if (up && !down) {
            vy = verticalSpeed.get();
        } else if (down && !up) {
            vy = -verticalSpeed.get();
        }

        if (antiKick.get() && tickTimer % antiKickInterval.get() == 0 && !down) {
            vy = -0.04;
        }

        return new Vec3d(vx, vy, vz);
    }
}
