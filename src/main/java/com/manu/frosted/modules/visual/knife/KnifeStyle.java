package com.manu.frosted.modules.visual.knife;

import java.util.function.DoubleUnaryOperator;

/**
 * Knife models. Units are "knife units" (about one GUI pixel at a 360 px tall screen).
 * The blade runs along +u from the guard, v is across it. spine(t) and edge(t) give the
 * two outlines of the blade for t = 0 (guard) .. 1 (tip).
 */
public enum KnifeStyle {
    Karambit(80, 46, 7.5, 7.0, 34, KnifeFlags.RING | KnifeFlags.FULLER,
        t -> -22.0 * t * t - 7.0 * Math.pow(Math.max(0.0, 1.0 - t), 0.6),
        t -> -22.0 * t * t + 4.2 * Math.pow(Math.max(0.0, 1.0 - t), 0.6)),

    Bayonet(100, 56, 6.8, 12.0, 28, KnifeFlags.FULLER,
        t -> -7.4 * (t < 0.72 ? 1.0 : Math.pow(Math.max(0.0, 1.0 - (t - 0.72) / 0.28), 1.3)),
        t -> 7.4 * (1.0 - Math.pow(t, 4.5))),

    Flip(90, 54, 6.8, 8.0, 44, KnifeFlags.FULLER,
        t -> -6.8 * (t < 0.6 ? 1.0 : Math.pow(Math.max(0.0, 1.0 - (t - 0.6) / 0.4), 1.4)),
        t -> 6.8 * (1.0 - Math.pow(t, 3.0))),

    Huntsman(84, 52, 8.2, 9.0, 30, 0,
        t -> -8.6 * (1.0 - Math.pow(t, 3.2)),
        t -> 8.6 * (1.0 - Math.pow(t, 2.2))),

    Gut(88, 54, 7.0, 8.0, 32, KnifeFlags.FULLER,
        t -> -7.2 * (1.0 - Math.pow(t, 4.0)) + (t > 0.4 && t < 0.56 ? 3.0 * Math.sin((t - 0.4) / 0.16 * Math.PI) : 0.0),
        t -> 7.2 * (1.0 - Math.pow(t, 1.7)) * (1.0 + 0.25 * Math.sin(Math.PI * t))),

    Butterfly(82, 56, 6.2, 6.0, 36, KnifeFlags.BUTTERFLY | KnifeFlags.FULLER,
        t -> -6.2 * (1.0 - Math.pow(t, 5.0)),
        t -> 6.2 * (1.0 - Math.pow(t, 3.0))),

    Stiletto(112, 48, 5.6, 8.0, 26, 0,
        t -> -3.6 * (1.0 - Math.pow(t, 2.6)),
        t -> 3.6 * (1.0 - Math.pow(t, 2.6))),

    Daggers(56, 30, 6.5, 0.0, 52, KnifeFlags.TWIN | KnifeFlags.PUSH | KnifeFlags.FULLER,
        t -> -8.2 * (1.0 - t * t),
        t -> 8.2 * (1.0 - t * t));

    public final double bladeLen;
    public final double handleLen;
    public final double handleW;
    public final double guardW;
    public final double tilt;
    final int flags;
    final DoubleUnaryOperator spine;
    final DoubleUnaryOperator edge;

    KnifeStyle(double bladeLen, double handleLen, double handleW, double guardW, double tilt, int flags,
               DoubleUnaryOperator spine, DoubleUnaryOperator edge) {
        this.bladeLen = bladeLen;
        this.handleLen = handleLen;
        this.handleW = handleW;
        this.guardW = guardW;
        this.tilt = tilt;
        this.flags = flags;
        this.spine = spine;
        this.edge = edge;
    }

    boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** Two knives, one in each hand. */
    public boolean twin() {
        return has(KnifeFlags.TWIN);
    }

    /** Has a second handle half that can flip around the hinge. */
    public boolean butterfly() {
        return has(KnifeFlags.BUTTERFLY);
    }
}
