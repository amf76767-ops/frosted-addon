package com.manu.frosted.modules.visual.knife;

import com.manu.frosted.util.Draw2D;

/** Colour schemes for the knife. All colours are 0xRRGGBB. */
public enum KnifeFinish {
    Custom, Frost, Glacier, Aurora, Obsidian, Ruby, Sapphire, Emerald;

    /** Blade colour at t along the blade (0 guard, 1 tip). */
    public int blade(double t, double time, int custom) {
        double wave = 0.5 + 0.5 * Math.sin(t * 4.2 - time * 1.1);
        return switch (this) {
            case Custom -> Draw2D.shade(custom, (wave - 0.5) * 0.16);
            case Frost -> {
                int base = Draw2D.mix(0xF2FAFF, 0x7EC4F8, t);
                yield Draw2D.mix(base, 0xD8F3FF, wave * 0.35);
            }
            case Glacier -> {
                int base = Draw2D.mix(0x88E4FF, 0x1E64C8, t);
                yield Draw2D.mix(base, 0xBDF4FF, wave * 0.30);
            }
            case Aurora -> aurora(t, time);
            case Obsidian -> {
                int base = Draw2D.mix(0x34405E, 0x131826, t);
                yield Draw2D.mix(base, 0x5C78B0, Math.pow(wave, 2.0) * 0.32);
            }
            case Ruby -> {
                int base = Draw2D.mix(0xFF425E, 0x7E1026, t);
                yield Draw2D.mix(base, 0xFF8AA0, Math.pow(wave, 2.0) * 0.35);
            }
            case Sapphire -> {
                int base = Draw2D.mix(0x3E8BFF, 0x122D8A, t);
                yield Draw2D.mix(base, 0x82C8FF, Math.pow(wave, 2.0) * 0.38);
            }
            case Emerald -> {
                int base = Draw2D.mix(0x36F09A, 0x0D6B42, t);
                yield Draw2D.mix(base, 0x9EFFD4, Math.pow(wave, 2.0) * 0.35);
            }
        };
    }

    public int handle(int custom) {
        return switch (this) {
            case Custom -> custom;
            case Frost -> 0x1A2642;
            case Glacier -> 0x122B50;
            case Aurora -> 0x1A153E;
            case Obsidian -> 0x10131C;
            case Ruby -> 0x220B13;
            case Sapphire -> 0x0E1A38;
            case Emerald -> 0x0C241C;
        };
    }

    public int accent() {
        return switch (this) {
            case Custom, Frost -> 0x78C2FF;
            case Glacier -> 0xA0EEFF;
            case Aurora -> 0xC490FF;
            case Obsidian -> 0x6498D8;
            case Ruby -> 0xFF728A;
            case Sapphire -> 0x68B4FF;
            case Emerald -> 0x62FFB8;
        };
    }

    private static int aurora(double t, double time) {
        int r = clamp((int) (115 + 95 * Math.sin(t * 5.2 + time * 0.9)));
        int g = clamp((int) (195 + 58 * Math.sin(t * 4.1 - time * 1.1)));
        int b = clamp((int) (240 + 15 * Math.sin(t * 3.2 + time * 0.6)));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
