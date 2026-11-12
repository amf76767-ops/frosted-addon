package com.manu.frosted.modules.visual.knife;

import com.manu.frosted.util.Draw2D;

/** Colour schemes for the knife. All colours are 0xRRGGBB. */
public enum KnifeFinish {
    Custom, Frost, Glacier, Aurora, Obsidian, Ruby;

    /** Blade colour at t along the blade (0 guard, 1 tip). */
    public int blade(double t, double time, int custom) {
        return switch (this) {
            case Custom -> custom;
            case Frost -> Draw2D.mix(0xEBF7FF, 0x96CDF5, t);
            case Glacier -> Draw2D.mix(0x78D7FF, 0x2878D2, t);
            case Aurora -> aurora(t, time);
            case Obsidian -> Draw2D.mix(0x323C55, 0x192033, t);
            case Ruby -> Draw2D.mix(0xE64659, 0x8C1932, t);
        };
    }

    public int handle(int custom) {
        return switch (this) {
            case Custom -> custom;
            case Frost -> 0x1C2846;
            case Glacier -> 0x14325A;
            case Aurora -> 0x1E1946;
            case Obsidian -> 0x12141E;
            case Ruby -> 0x230C14;
        };
    }

    public int accent() {
        return switch (this) {
            case Custom, Frost -> 0x6EB4FF;
            case Glacier -> 0x9BE6FF;
            case Aurora -> 0xB488FF;
            case Obsidian -> 0x5A8CC8;
            case Ruby -> 0xFF788C;
        };
    }

    private static int aurora(double t, double time) {
        int r = clamp((int) (120 + 80 * Math.sin(t * 5.0 + time * 0.8)));
        int g = clamp((int) (190 + 60 * Math.sin(t * 4.0 - time)));
        int b = clamp((int) (235 + 20 * Math.sin(t * 3.0 + time * 0.5)));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
