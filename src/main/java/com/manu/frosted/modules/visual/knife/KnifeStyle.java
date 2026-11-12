package com.manu.frosted.modules.visual.knife;

import java.util.function.DoubleUnaryOperator;

/**
 * Knife models. Units are "knife units" (about one GUI pixel at a 360 px tall screen).
 * The blade runs along +u from the guard, v is across it. spine(t) and edge(t) give the
 * two outlines of the blade for t = 0 (guard) .. 1 (tip).
 */
public enum KnifeStyle {
    Karambit(88, 46, 7.2, 8.5, 32, KnifeFlags.RING | KnifeFlags.FULLER,
        t -> -24.0 * t * t - 9.2 * Math.pow(Math.max(0.0, 1.0 - t), 0.55),
        t -> -24.0 * t * t + 7.2 * Math.pow(Math.max(0.0, 1.0 - t), 0.68)),

    Bayonet(104, 54, 6.8, 12.5, 28, KnifeFlags.FULLER,
        t -> -8.8 * (t < 0.72 ? 1.0 : Math.pow(Math.max(0.0, 1.0 - (t - 0.72) / 0.28), 1.3)),
        t -> 8.8 * (1.0 - Math.pow(t, 4.2))),

    Flip(94, 52, 6.8, 8.5, 40, KnifeFlags.FULLER,
        t -> -8.0 * (t < 0.6 ? 1.0 : Math.pow(Math.max(0.0, 1.0 - (t - 0.6) / 0.4), 1.4)),
        t -> 8.0 * (1.0 - Math.pow(t, 2.8))),

    Huntsman(90, 50, 8.0, 9.5, 30, 0,
        t -> -10.2 * (1.0 - Math.pow(t, 3.2)),
        t -> 10.2 * (1.0 - Math.pow(t, 2.2))),

    Gut(92, 52, 7.0, 8.5, 32, KnifeFlags.FULLER,
        t -> -8.6 * (1.0 - Math.pow(t, 4.0)) + (t > 0.4 && t < 0.56 ? 3.4 * Math.sin((t - 0.4) / 0.16 * Math.PI) : 0.0),
        t -> 8.6 * (1.0 - Math.pow(t, 1.7)) * (1.0 + 0.25 * Math.sin(Math.PI * t))),

    Butterfly(88, 54, 6.2, 6.5, 34, KnifeFlags.BUTTERFLY | KnifeFlags.FULLER,
        t -> -7.4 * (1.0 - Math.pow(t, 5.0)),
        t -> 7.4 * (1.0 - Math.pow(t, 3.0))),

    Stiletto(116, 48, 5.6, 8.5, 26, 0,
        t -> -4.8 * (1.0 - Math.pow(t, 2.4)),
        t -> 4.8 * (1.0 - Math.pow(t, 2.4))),

    Daggers(62, 30, 6.5, 0.0, 48, KnifeFlags.TWIN | KnifeFlags.PUSH | KnifeFlags.FULLER,
        t -> -9.6 * (1.0 - t * t),
        t -> 9.6 * (1.0 - t * t));

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
