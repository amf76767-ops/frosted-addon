package com.manu.frosted.util;

import java.util.concurrent.ThreadLocalRandom;

/** Single place for every random roll in the addon. */
public final class Rand {
    private Rand() {}

    /** True with the given percent probability (0 never, 100 always). */
    public static boolean chance(int percent) {
        if (percent <= 0) return false;
        if (percent >= 100) return true;
        return ThreadLocalRandom.current().nextInt(100) < percent;
    }

    /** Uniform integer in [0, max], inclusive. Returns 0 for max <= 0. */
    public static int upTo(int max) {
        return max <= 0 ? 0 : ThreadLocalRandom.current().nextInt(max + 1);
    }

    /** Uniform double in [0, 1). */
    public static double unit() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** Uniform double in [-0.5 * spread, 0.5 * spread). */
    public static double centered(double spread) {
        return (unit() - 0.5) * spread;
    }
}
