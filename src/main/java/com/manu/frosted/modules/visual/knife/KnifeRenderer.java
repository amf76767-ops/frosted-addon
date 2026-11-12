package com.manu.frosted.modules.visual.knife;

import com.manu.frosted.util.Draw2D;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import net.minecraft.client.gui.DrawContext;

/**
 * True 3D volumetric CS knife renderer built on {@link Renderer2D#COLOR}.
 *
 * Models the blade, spine, hollow grind, fuller, guard, contoured grip, butterfly hinges
 * and karambit finger ring in 3D (u, v, w) space with perspective projection, 3D surface-normal
 * lighting, and back-to-front depth sorting (zero per-frame heap allocations).
 */
public final class KnifeRenderer {
    private static final int SHADOW_CORE = 0x48050A18;
    private static final double HINGE_U = -2.5;
    private static final int MAX_QUADS = 1024;

    private static final double[] QX0 = new double[MAX_QUADS];
    private static final double[] QY0 = new double[MAX_QUADS];
    private static final int[] QC0 = new int[MAX_QUADS];

    private static final double[] QX1 = new double[MAX_QUADS];
    private static final double[] QY1 = new double[MAX_QUADS];
    private static final int[] QC1 = new int[MAX_QUADS];

    private static final double[] QX2 = new double[MAX_QUADS];
    private static final double[] QY2 = new double[MAX_QUADS];
    private static final int[] QC2 = new int[MAX_QUADS];

    private static final double[] QX3 = new double[MAX_QUADS];
    private static final double[] QY3 = new double[MAX_QUADS];
    private static final int[] QC3 = new int[MAX_QUADS];

    private static final double[] QZ = new double[MAX_QUADS];
    private static final int[] ORDER = new int[MAX_QUADS];
    private static int quadCount;

    private KnifeRenderer() {}

    /**
     * @param ox,oy        guard position in screen pixels
     * @param tilt         degrees above the horizontal
     * @param spinDeg      in-plane rotation around the pivot in degrees
     * @param rollRad      3D roll angle in radians around the blade's longitudinal axis
     * @param pitchRad     3D pitch/yaw angle in radians tilting the tip toward/away from the camera
     * @param k            pixels per knife unit
     * @param handleFlip   radians of the butterfly's loose handle half
     * @param glint        0..1, how fast and bright the shine sweeps
     */
    public record Pose(double ox, double oy, double tilt, double spinDeg, double rollRad, double pitchRad,
                       double k, boolean mirror, double time, double glint, double handleFlip,
                       int customBlade, int customHandle, boolean shadow, boolean sparkles) {}

    private static final class Xf3D {
        final double ox, oy, dx, dy, px, py, k, pivot;
        final double cosS, sinS, cosR, sinR, cosP, sinP;
        final double mirrorSign;
        double sx, sy;

        double outX, outY, outZ;

        Xf3D(Pose p, double pivotU) {
            double rad = Math.toRadians(p.tilt());
            this.mirrorSign = p.mirror() ? 1.0 : -1.0;
            this.dx = mirrorSign * Math.cos(rad);
            this.dy = -Math.sin(rad);
            this.px = -dy;
            this.py = dx;
            this.ox = p.ox();
            this.oy = p.oy();
            this.k = p.k();
            this.pivot = pivotU;

            double s = Math.toRadians(p.spinDeg());
            this.cosS = Math.cos(s);
            this.sinS = Math.sin(s);
            this.cosR = Math.cos(p.rollRad());
            this.sinR = Math.sin(p.rollRad());
            this.cosP = Math.cos(p.pitchRad());
            this.sinP = Math.sin(p.pitchRad());
        }

        void project(double u, double v, double w) {
            double lu = u - pivot;
            // 1. 3D roll around the longitudinal (u) axis
            double v1 = v * cosR - w * sinR;
            double w1 = v * sinR + w * cosR;
            // 2. 3D pitch/yaw around the transverse (v) axis (tilts tip in/out of screen)
            double u2 = lu * cosP + w1 * sinP;
            double w2 = -lu * sinP + w1 * cosP;
            // 3. In-plane spin around pivot
            double ru = u2 * cosS - v1 * sinS + pivot;
            double rv = u2 * sinS + v1 * cosS;

            // 4. Perspective projection
            double fov = 380.0;
            double persp = fov / Math.max(110.0, fov - w2);
            double xFlat = (dx * ru + px * rv) * persp;
            double yFlat = (dy * ru + py * rv) * persp;

            outX = ox + xFlat * k + sx;
            outY = oy + yFlat * k + sy;
            outZ = w2;
        }

        /** Computes a 3D lighting shade factor (-0.35 .. +0.45) for a surface normal (nu, nv, nw). */
        double lightShade(double nu, double nv, double nw) {
            double v1 = nv * cosR - nw * sinR;
            double w1 = nv * sinR + nw * cosR;
            double u2 = nu * cosP + w1 * sinP;
            double w2 = -nu * sinP + w1 * cosP;
            double ru = u2 * cosS - v1 * sinS;
            double rv = u2 * sinS + v1 * cosS;

            // Light coming from top-left-front (-0.25, -0.55, 0.80)
            double dot = ru * -0.25 + rv * -0.55 + w2 * 0.80;
            double spec = Math.pow(Math.max(0.0, w2 * 0.88 - rv * 0.45), 4.0) * 0.28;
            return dot * 0.28 + spec;
        }
    }

    public static void draw(DrawContext ctx, KnifeStyle style, KnifeFinish finish, Pose p) {
        boolean ownBatch = !Renderer2D.COLOR.triangles.isBuilding();
        if (ownBatch) Renderer2D.COLOR.begin();

        double pivotU = style.has(KnifeFlags.RING) ? (-style.handleLen - 9.5) : (-style.handleLen * 0.45);
        Xf3D xf = new Xf3D(p, pivotU);
        int handleBase = finish.handle(p.customHandle());
        int accent = finish.accent();

        if (p.shadow()) {
            double off = Math.max(3.0, 3.6 * p.k());
            xf.sx = off;
            xf.sy = off * 1.35;
            emitShadow(xf, style, p);
            xf.sx = 0;
            xf.sy = 0;
        }

        quadCount = 0;
        emitBlade3D(xf, style, finish, p, accent);
        emitHandle3D(xf, style, p, handleBase, accent);
        flushSortedQuads();

        if (p.sparkles()) {
            emitSparkles3D(xf, style, finish, p);
        }

        if (ownBatch) Renderer2D.COLOR.render();
    }

    // ---------------------------------------------------------------- shadow

    private static void emitShadow(Xf3D xf, KnifeStyle style, Pose p) {
        int slices = 16;
        for (int i = 0; i < slices; i++) {
            double t0 = i / (double) slices;
            double t1 = (i + 1) / (double) slices;
            double u0 = t0 * style.bladeLen;
            double u1 = t1 * style.bladeLen;
            double s0 = style.spine.applyAsDouble(t0);
            double s1 = style.spine.applyAsDouble(t1);
            double e0 = style.edge.applyAsDouble(t0);
            double e1 = style.edge.applyAsDouble(t1);
            emitImmediateQuad(xf, SHADOW_CORE, u0, s0, 0, u1, s1, 0, u1, e1, 0, u0, e0, 0);
        }
        double hw = style.handleW;
        emitImmediateQuad(xf, SHADOW_CORE,
            HINGE_U, -hw, 0,
            HINGE_U - style.handleLen, -hw, 0,
            HINGE_U - style.handleLen, hw, 0,
            HINGE_U, hw, 0);
    }

    // ---------------------------------------------------------------- 3D blade

    private static void emitBlade3D(Xf3D xf, KnifeStyle style, KnifeFinish finish, Pose p, int accent) {
        int slices = 26;
        int glowEdge = Draw2D.argb(40, accent);
        int glowOuter = Draw2D.argb(0, accent);

        double frontUpperShade = xf.lightShade(0.0, -0.45, 0.89);
        double frontGrindShade = xf.lightShade(0.0, 0.38, 0.92);
        double backUpperShade = xf.lightShade(0.0, -0.45, -0.89);
        double backGrindShade = xf.lightShade(0.0, 0.38, -0.92);
        double spineWallShade = xf.lightShade(0.0, -1.0, 0.0);

        for (int i = 0; i < slices; i++) {
            double t0 = i / (double) slices;
            double t1 = (i + 1) / (double) slices;
            double u0 = t0 * style.bladeLen;
            double u1 = t1 * style.bladeLen;
            double s0 = style.spine.applyAsDouble(t0);
            double s1 = style.spine.applyAsDouble(t1);
            double e0 = style.edge.applyAsDouble(t0);
            double e1 = style.edge.applyAsDouble(t1);

            double m0 = s0 + (e0 - s0) * 0.44;
            double m1 = s1 + (e1 - s1) * 0.44;
            double r0 = e0 - (e0 - s0) * 0.16;
            double r1 = e1 - (e1 - s1) * 0.16;

            // 3D blade thickness profile (tapers toward the tip)
            double spineZ0 = 2.4 * (1.0 - 0.82 * t0 * t0);
            double spineZ1 = 2.4 * (1.0 - 0.82 * t1 * t1);
            double ridgeZ0 = 3.5 * (1.0 - 0.86 * Math.pow(t0, 1.3));
            double ridgeZ1 = 3.5 * (1.0 - 0.86 * Math.pow(t1, 1.3));
            double bevelZ0 = ridgeZ0 * 0.28;
            double bevelZ1 = ridgeZ1 * 0.28;

            int c0 = bladeColorAt(finish, t0, p);
            int c1 = bladeColorAt(finish, t1, p);

            // 1. 3D Spine top wall (connects -spineZ and +spineZ along the back of the blade)
            int spCol0 = opaque(Draw2D.shade(c0, -0.26 + spineWallShade));
            int spCol1 = opaque(Draw2D.shade(c1, -0.26 + spineWallShade));
            int spHi0 = opaque(Draw2D.shade(c0, 0.05 + spineWallShade));
            int spHi1 = opaque(Draw2D.shade(c1, 0.05 + spineWallShade));
            pushQuad3D(xf,
                u0, s0, -spineZ0, spCol0,
                u1, s1, -spineZ1, spCol1,
                u1, s1, spineZ1, spHi1,
                u0, s0, spineZ0, spHi0
            );

            // 2. Front side (+w) & Back side (-w) 3D beveled faces
            emitBladeSide(xf, u0, u1, s0, s1, m0, m1, r0, r1, e0, e1,
                spineZ0, spineZ1, ridgeZ0, ridgeZ1, bevelZ0, bevelZ1,
                c0, c1, frontUpperShade, frontGrindShade, style, t0, t1, accent);

            emitBladeSide(xf, u0, u1, s0, s1, m0, m1, r0, r1, e0, e1,
                -spineZ0, -spineZ1, -ridgeZ0, -ridgeZ1, -bevelZ0, -bevelZ1,
                c0, c1, backUpperShade, backGrindShade, style, t0, t1, accent);

            // 3. Soft icy aura along the razor cutting edge (at w = 0)
            double aura0 = e0 + (e0 - s0) * 0.15;
            double aura1 = e1 + (e1 - s1) * 0.15;
            pushQuad3D(xf,
                u0, e0, 0, glowEdge,
                u1, e1, 0, glowEdge,
                u1, aura1, 0, glowOuter,
                u0, aura0, 0, glowOuter
            );
        }
    }

    private static void emitBladeSide(Xf3D xf,
                                      double u0, double u1,
                                      double s0, double s1,
                                      double m0, double m1,
                                      double r0, double r1,
                                      double e0, double e1,
                                      double sz0, double sz1,
                                      double rz0, double rz1,
                                      double bz0, double bz1,
                                      int c0, int c1,
                                      double upperShade, double grindShade,
                                      KnifeStyle style, double t0, double t1, int accent) {
        int spine0 = opaque(Draw2D.shade(c0, -0.22 + upperShade));
        int spine1 = opaque(Draw2D.shade(c1, -0.22 + upperShade));
        int ridge0 = opaque(Draw2D.shade(c0, 0.22 + upperShade));
        int ridge1 = opaque(Draw2D.shade(c1, 0.22 + upperShade));
        int grind0 = opaque(Draw2D.shade(c0, 0.02 + grindShade));
        int grind1 = opaque(Draw2D.shade(c1, 0.02 + grindShade));
        int edge0 = opaque(Draw2D.shade(c0, 0.58 + grindShade * 0.5));
        int edge1 = opaque(Draw2D.shade(c1, 0.58 + grindShade * 0.5));

        // Upper flat (spine to 3D central ridge)
        pushQuad3D(xf,
            u0, s0, sz0, spine0,
            u1, s1, sz1, spine1,
            u1, m1, rz1, ridge1,
            u0, m0, rz0, ridge0
        );
        // Primary hollow grind (central ridge to bevel shoulder)
        pushQuad3D(xf,
            u0, m0, rz0, ridge0,
            u1, m1, rz1, ridge1,
            u1, r1, bz1, grind1,
            u0, r0, bz0, grind0
        );
        // Razor cutting bevel (bevel shoulder to w=0 sharp edge)
        pushQuad3D(xf,
            u0, r0, bz0, grind0,
            u1, r1, bz1, grind1,
            u1, e1, 0.0, edge1,
            u0, e0, 0.0, edge0
        );

        // Recessed 3D fuller groove
        if (style.has(KnifeFlags.FULLER) && t0 > 0.06 && t1 < 0.80) {
            double f0a = s0 + (e0 - s0) * 0.27;
            double f0b = s0 + (e0 - s0) * 0.37;
            double f1a = s1 + (e1 - s1) * 0.27;
            double f1b = s1 + (e1 - s1) * 0.37;
            double fz0 = sz0 * 0.55;
            double fz1 = sz1 * 0.55;
            int fDark0 = opaque(Draw2D.shade(c0, -0.46));
            int fDark1 = opaque(Draw2D.shade(c1, -0.46));
            int fGlow0 = opaque(Draw2D.mix(Draw2D.shade(c0, -0.18), accent, 0.42));
            int fGlow1 = opaque(Draw2D.mix(Draw2D.shade(c1, -0.18), accent, 0.42));
            pushQuad3D(xf,
                u0, f0a, fz0, fDark0,
                u1, f1a, fz1, fDark1,
                u1, f1b, fz1, fGlow1,
                u0, f0b, fz0, fGlow0
            );
        }
    }

    private static int bladeColorAt(KnifeFinish finish, double t, Pose p) {
        int base = finish.blade(t, p.time(), p.customBlade());
        double glint = glintAt(t, p.time(), p.glint());
        return Draw2D.mix(base, 0xFFFFFF, glint * 0.78);
    }

    private static double glintAt(double t, double time, double boost) {
        double speed = 0.55 + 1.9 * boost;
        double pos = (time * speed) % 2.6 - 0.6;
        double primary = Math.max(0.0, 1.0 - Math.abs(t - pos) / 0.13);
        double secondary = Math.max(0.0, 1.0 - Math.abs(t - (pos - 0.22)) / 0.07) * 0.45;
        return Math.max(Math.max(primary * primary, secondary), boost * 0.16);
    }

    // --------------------------------------------------------------- 3D handle

    private static void emitHandle3D(Xf3D xf, KnifeStyle style, Pose p, int base, int accent) {
        if (style.has(KnifeFlags.BUTTERFLY)) {
            double hw = style.handleW * 0.5;
            handleBar3D(xf, base, accent, HINGE_U, style.handleLen, hw * 1.10, hw, 2.8, 0.0);
            handleBar3D(xf, base, accent, HINGE_U, style.handleLen, -hw * 1.10, hw, 2.8, p.handleFlip());
        } else if (style.has(KnifeFlags.PUSH)) {
            emitBox3D(xf, -2.8, -9.2, -14.5, 14.5, 3.4, base, accent);
            handleBar3D(xf, base, accent, -9.2, style.handleLen - 6.0, 0.0, style.handleW * 0.72, 3.0, 0.0);
        } else {
            handleBar3D(xf, base, accent, HINGE_U, style.handleLen, 0.0, style.handleW, 3.4, 0.0);
        }

        // 3D volumetric guard / bolster
        if (style.guardW > 0 && !style.has(KnifeFlags.PUSH)) {
            double gw = style.guardW;
            emitBox3D(xf, 1.2, -3.5, -gw, gw, 4.2, Draw2D.shade(base, 0.12), accent);
            // Accent trim plate on front of guard
            int accFront = opaque(Draw2D.shade(accent, xf.lightShade(1, 0, 0.5)));
            pushQuad3D(xf,
                2.2, -gw * 0.88, 3.2, accFront,
                2.2, gw * 0.88, 3.2, accFront,
                1.0, gw * 0.88, 4.2, accFront,
                1.0, -gw * 0.88, 4.2, accFront
            );
            pushQuad3D(xf,
                2.2, -gw * 0.88, -3.2, accFront,
                2.2, gw * 0.88, -3.2, accFront,
                1.0, gw * 0.88, -4.2, accFront,
                1.0, -gw * 0.88, -4.2, accFront
            );
        }

        double end = -style.handleLen - 2.5;
        if (style.has(KnifeFlags.RING)) {
            emitKarambitRing3D(xf, end - 7.0, base, accent);
        } else if (!style.has(KnifeFlags.BUTTERFLY) && !style.has(KnifeFlags.PUSH)) {
            emitBox3D(xf, end, end - 4.5, -style.handleW * 0.82, style.handleW * 0.82, 3.0, Draw2D.shade(base, 0.15), accent);
        }
    }

    private static void handleBar3D(Xf3D xf, int base, int accent,
                                    double startU, double length, double vc, double hw, double hz, double rot) {
        int slices = 9;
        double step = length / slices;
        double c = Math.cos(rot);
        double s = Math.sin(rot);

        double frontShade = xf.lightShade(0, 0, 1);
        double backShade = xf.lightShade(0, 0, -1);
        double topWallShade = xf.lightShade(0, -1, 0);
        double botWallShade = xf.lightShade(0, 1, 0);

        int topWallCol = opaque(Draw2D.shade(base, 0.22 + topWallShade));
        int botWallCol = opaque(Draw2D.shade(base, -0.25 + botWallShade));
        int accCol = opaque(Draw2D.shade(accent, frontShade * 0.5));

        for (int i = 0; i < slices; i++) {
            double u0 = startU - i * step;
            double u1 = startU - (i + 1) * step;
            double w0 = hw * (0.90 + 0.14 * Math.sin(Math.PI * i / slices));
            double w1 = hw * (0.90 + 0.14 * Math.sin(Math.PI * (i + 1) / slices));

            int ribBase = i % 2 == 0 ? base : Draw2D.shade(base, 0.10);
            int frontHi = opaque(Draw2D.shade(ribBase, 0.18 + frontShade));
            int frontLo = opaque(Draw2D.shade(ribBase, -0.14 + frontShade));
            int backHi = opaque(Draw2D.shade(ribBase, 0.18 + backShade));
            int backLo = opaque(Draw2D.shade(ribBase, -0.14 + backShade));

            double du0 = u0 - startU;
            double du1 = u1 - startU;

            double va0 = vc - w0, vb0 = vc + w0;
            double va1 = vc - w1, vb1 = vc + w1;

            double ru0a = startU + du0 * c - va0 * s;
            double rv0a = du0 * s + va0 * c;
            double ru1a = startU + du1 * c - va1 * s;
            double rv1a = du1 * s + va1 * c;

            double ru0b = startU + du0 * c - vb0 * s;
            double rv0b = du0 * s + vb0 * c;
            double ru1b = startU + du1 * c - vb1 * s;
            double rv1b = du1 * s + vb1 * c;

            // Front face (+hz)
            pushQuad3D(xf, ru0a, rv0a, hz, frontHi, ru1a, rv1a, hz, frontHi, ru1b, rv1b, hz, frontLo, ru0b, rv0b, hz, frontLo);
            // Back face (-hz)
            pushQuad3D(xf, ru0a, rv0a, -hz, backHi, ru1a, rv1a, -hz, backHi, ru1b, rv1b, -hz, backLo, ru0b, rv0b, -hz, backLo);
            // 3D Top side wall (-hz to +hz)
            pushQuad3D(xf, ru0a, rv0a, -hz, topWallCol, ru1a, rv1a, -hz, topWallCol, ru1a, rv1a, hz, frontHi, ru0a, rv0a, hz, frontHi);
            // 3D Bottom side wall (-hz to +hz)
            pushQuad3D(xf, ru0b, rv0b, -hz, botWallCol, ru1b, rv1b, -hz, botWallCol, ru1b, rv1b, hz, accCol, ru0b, rv0b, hz, accCol);

            // Accent inlay stripe on both front and back faces
            double ia0 = vc + w0 * 0.50, ib0 = vc + w0 * 0.78;
            double ia1 = vc + w1 * 0.50, ib1 = vc + w1 * 0.78;
            double ria0u = startU + du0 * c - ia0 * s, ria0v = du0 * s + ia0 * c;
            double ria1u = startU + du1 * c - ia1 * s, ria1v = du1 * s + ia1 * c;
            double rib0u = startU + du0 * c - ib0 * s, rib0v = du0 * s + ib0 * c;
            double rib1u = startU + du1 * c - ib1 * s, rib1v = du1 * s + ib1 * c;
            pushQuad3D(xf, ria0u, ria0v, hz + 0.1, accCol, ria1u, ria1v, hz + 0.1, accCol, rib1u, rib1v, hz + 0.1, accCol, rib0u, rib0v, hz + 0.1, accCol);
            pushQuad3D(xf, ria0u, ria0v, -hz - 0.1, accCol, ria1u, ria1v, -hz - 0.1, accCol, rib1u, rib1v, -hz - 0.1, accCol, rib0u, rib0v, -hz - 0.1, accCol);
        }
    }

    private static void emitBox3D(Xf3D xf, double u0, double u1, double v0, double v1, double hz, int base, int accent) {
        int cFront = opaque(Draw2D.shade(base, 0.18 + xf.lightShade(0, 0, 1)));
        int cBack = opaque(Draw2D.shade(base, 0.10 + xf.lightShade(0, 0, -1)));
        int cTop = opaque(Draw2D.shade(base, 0.28 + xf.lightShade(0, -1, 0)));
        int cBot = opaque(Draw2D.shade(base, -0.22 + xf.lightShade(0, 1, 0)));
        int cCap = opaque(Draw2D.mix(base, accent, 0.35));

        pushQuad3D(xf, u0, v0, hz, cFront, u1, v0, hz, cFront, u1, v1, hz, cBot, u0, v1, hz, cBot);
        pushQuad3D(xf, u0, v0, -hz, cBack, u1, v0, -hz, cBack, u1, v1, -hz, cBot, u0, v1, -hz, cBot);
        pushQuad3D(xf, u0, v0, -hz, cTop, u1, v0, -hz, cTop, u1, v0, hz, cFront, u0, v0, hz, cFront);
        pushQuad3D(xf, u0, v1, -hz, cBot, u1, v1, -hz, cBot, u1, v1, hz, cBot, u0, v1, hz, cBot);
        pushQuad3D(xf, u0, v0, -hz, cCap, u0, v1, -hz, cCap, u0, v1, hz, cCap, u0, v0, hz, cCap);
        pushQuad3D(xf, u1, v0, -hz, cCap, u1, v1, -hz, cCap, u1, v1, hz, cCap, u1, v0, hz, cCap);
    }

    private static void emitKarambitRing3D(Xf3D xf, double cu, int base, int accent) {
        double outer = 10.6;
        double inner = 6.7;
        double rz = 2.6;
        int segs = 20;

        int cFront = opaque(Draw2D.mix(Draw2D.shade(base, 0.22 + xf.lightShade(0, 0, 1)), accent, 0.32));
        int cBack = opaque(Draw2D.mix(Draw2D.shade(base, 0.14 + xf.lightShade(0, 0, -1)), accent, 0.25));
        int cOuter = opaque(Draw2D.shade(base, -0.12));
        int cInner = opaque(Draw2D.shade(base, -0.28));

        for (int i = 0; i < segs; i++) {
            double a0 = i * 2.0 * Math.PI / segs;
            double a1 = (i + 1) * 2.0 * Math.PI / segs;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);

            double uOut0 = cu + outer * c0, vOut0 = outer * s0;
            double uOut1 = cu + outer * c1, vOut1 = outer * s1;
            double uIn0 = cu + inner * c0, vIn0 = inner * s0;
            double uIn1 = cu + inner * c1, vIn1 = inner * s1;

            // Front ring face (+rz)
            pushQuad3D(xf, uOut0, vOut0, rz, cFront, uOut1, vOut1, rz, cFront, uIn1, vIn1, rz, cInner, uIn0, vIn0, rz, cInner);
            // Back ring face (-rz)
            pushQuad3D(xf, uOut0, vOut0, -rz, cBack, uOut1, vOut1, -rz, cBack, uIn1, vIn1, -rz, cInner, uIn0, vIn0, -rz, cInner);
            // 3D Outer cylindrical wall (-rz to +rz)
            pushQuad3D(xf, uOut0, vOut0, -rz, cOuter, uOut1, vOut1, -rz, cOuter, uOut1, vOut1, rz, cFront, uOut0, vOut0, rz, cFront);
            // 3D Inner cylindrical wall (-rz to +rz)
            pushQuad3D(xf, uIn0, vIn0, -rz, cInner, uIn1, vIn1, -rz, cInner, uIn1, vIn1, rz, cInner, uIn0, vIn0, rz, cInner);
        }
    }

    // -------------------------------------------------------------- sparkles

    private static void emitSparkles3D(Xf3D xf, KnifeStyle style, KnifeFinish finish, Pose p) {
        int accent = finish.accent();
        for (int i = 0; i < 4; i++) {
            double t = 0.22 + i * 0.24;
            double wave = Math.sin(p.time() * 3.2 + i * 1.9);
            if (wave < 0.35) continue;

            double intensity = (wave - 0.35) / 0.65;
            double edgeV = style.edge.applyAsDouble(t);
            double spineV = style.spine.applyAsDouble(t);
            double v = spineV + (edgeV - spineV) * 0.72;
            xf.project(t * style.bladeLen, v, 1.5);

            int coreAlpha = (int) Math.round(255 * intensity);
            int glowAlpha = (int) Math.round(135 * intensity);
            double arm = Math.max(3.5, p.k() * (3.2 + 2.4 * intensity));
            double thick = Math.max(1.0, p.k() * 0.85);
            Draw2D.star(xf.outX, xf.outY, arm, thick, Draw2D.argb(coreAlpha, 0xFFFFFF), Draw2D.argb(glowAlpha, accent));
        }
    }

    // --------------------------------------------------------------- sorting & helpers

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static void emitImmediateQuad(Xf3D xf, int argb,
                                          double u0, double v0, double w0,
                                          double u1, double v1, double w1,
                                          double u2, double v2, double w2,
                                          double u3, double v3, double w3) {
        xf.project(u0, v0, w0);
        double x0 = xf.outX, y0 = xf.outY;
        xf.project(u1, v1, w1);
        double x1 = xf.outX, y1 = xf.outY;
        xf.project(u2, v2, w2);
        double x2 = xf.outX, y2 = xf.outY;
        xf.project(u3, v3, w3);
        double x3 = xf.outX, y3 = xf.outY;
        Draw2D.polyQuad(x0, y0, x1, y1, x2, y2, x3, y3, argb);
    }

    private static void pushQuad3D(Xf3D xf,
                                   double u0, double v0, double w0, int c0,
                                   double u1, double v1, double w1, int c1,
                                   double u2, double v2, double w2, int c2,
                                   double u3, double v3, double w3, int c3) {
        if (quadCount >= MAX_QUADS) return;
        int idx = quadCount++;

        xf.project(u0, v0, w0);
        QX0[idx] = xf.outX;
        QY0[idx] = xf.outY;
        double z0 = xf.outZ;
        QC0[idx] = c0;

        xf.project(u1, v1, w1);
        QX1[idx] = xf.outX;
        QY1[idx] = xf.outY;
        double z1 = xf.outZ;
        QC1[idx] = c1;

        xf.project(u2, v2, w2);
        QX2[idx] = xf.outX;
        QY2[idx] = xf.outY;
        double z2 = xf.outZ;
        QC2[idx] = c2;

        xf.project(u3, v3, w3);
        QX3[idx] = xf.outX;
        QY3[idx] = xf.outY;
        double z3 = xf.outZ;
        QC3[idx] = c3;

        QZ[idx] = (z0 + z1 + z2 + z3) * 0.25;
        ORDER[idx] = idx;
    }

    private static void flushSortedQuads() {
        // Fast insertion sort back-to-front by average Z depth
        for (int i = 1; i < quadCount; i++) {
            int key = ORDER[i];
            double keyZ = QZ[key];
            int j = i - 1;
            while (j >= 0 && QZ[ORDER[j]] > keyZ) {
                ORDER[j + 1] = ORDER[j];
                j--;
            }
            ORDER[j + 1] = key;
        }

        for (int i = 0; i < quadCount; i++) {
            int k = ORDER[i];
            Draw2D.polyQuadGrad(
                QX0[k], QY0[k], QC0[k],
                QX1[k], QY1[k], QC1[k],
                QX2[k], QY2[k], QC2[k],
                QX3[k], QY3[k], QC3[k]
            );
        }
    }
}
