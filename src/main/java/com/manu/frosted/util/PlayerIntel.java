package com.manu.frosted.util;

import net.minecraft.entity.player.PlayerEntity;

/** Direction helpers for "where is that player" readouts. */
public final class PlayerIntel {
    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private PlayerIntel() {}

    /** Compass bearing of an offset in the world: 0 = north (-Z), 90 = east (+X). */
    public static double bearing(double dx, double dz) {
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        return deg < 0 ? deg + 360.0 : deg;
    }

    public static String compass(double bearing) {
        int i = (int) Math.round(bearing / 45.0) & 7;
        return COMPASS[i];
    }

    /** Arrow showing where the target is relative to where you look (up = straight ahead). */
    public static String arrow(double bearing, double playerYaw) {
        double facing = ((playerYaw + 180.0) % 360.0 + 360.0) % 360.0;
        double rel = ((bearing - facing + 540.0) % 360.0) - 180.0;
        return switch ((int) Math.round(rel / 45.0)) {
            case 0 -> "↑";
            case 1 -> "↗";
            case 2 -> "→";
            case 3 -> "↘";
            case -1 -> "↖";
            case -2 -> "←";
            case -3 -> "↙";
            default -> "↓";
        };
    }

    /** Real accounts have version 4 UUIDs; server NPCs and some bots do not. */
    public static boolean isRealPlayer(PlayerEntity player) {
        return player.getUuid().version() == 4;
    }
}
