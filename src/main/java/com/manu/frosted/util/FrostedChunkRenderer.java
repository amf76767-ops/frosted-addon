package com.manu.frosted.util;

import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Shared 3D Frosted Chunk & Beacon renderer for ChunkFinder, SusChunks,
 * DeepPlayerFinder and SoundFinder.
 *
 * Draws an icy translucent floor pad, an inner frost ring, 3D vertical gradient
 * ice walls around the 16x16 chunk perimeter, octagonal frost corner braces,
 * 4 glowing corner crystal spires, and an optional floating 3D octahedron crystal beacon.
 */
public final class FrostedChunkRenderer {
    private static final Color C0 = new Color();
    private static final Color C1 = new Color();
    private static final Color C2 = new Color();
    private static final Color C3 = new Color();

    private FrostedChunkRenderer() {}

    private static Color set(Color out, int r, int g, int b, int a) {
        out.r = Math.max(0, Math.min(255, r));
        out.g = Math.max(0, Math.min(255, g));
        out.b = Math.max(0, Math.min(255, b));
        out.a = Math.max(0, Math.min(255, a));
        return out;
    }

    /** Mixes an accent color slightly toward icy cyan/white for a signature frosted look. */
    private static int mixIceR(int r, double f) { return (int) Math.round(r + (185 - r) * f); }
    private static int mixIceG(int g, double f) { return (int) Math.round(g + (235 - g) * f); }
    private static int mixIceB(int b, double f) { return (int) Math.round(b + (255 - b) * f); }

    public static void drawChunk(Renderer3D r, double chunkX, double y, double chunkZ,
                                 Color baseColor, int fillAlpha, double time, boolean showCenterCrystal) {
        int cr = baseColor.r;
        int cg = baseColor.g;
        int cb = baseColor.b;

        // Blend base color with frost ice
        int ir = mixIceR(cr, 0.32);
        int ig = mixIceG(cg, 0.32);
        int ib = mixIceB(cb, 0.32);

        double pulse = 0.78 + 0.22 * Math.sin(time * 2.4 + (chunkX * 0.13 + chunkZ * 0.17));
        int padAlpha = (int) Math.round(fillAlpha * 0.55 * pulse);
        int innerAlpha = (int) Math.round(fillAlpha * 0.32 * pulse);
        int wallBaseA = (int) Math.round(Math.min(220, fillAlpha * 1.35) * pulse);
        int lineA = (int) Math.round(235 * pulse);

        double x0 = chunkX + 0.15;
        double z0 = chunkZ + 0.15;
        double x1 = chunkX + 15.85;
        double z1 = chunkZ + 15.85;

        // 1. Translucent frosted floor pad + inner ice plate
        r.quadHorizontal(x0, y, z0, x1, z1, set(C0, ir, ig, ib, padAlpha));
        r.quadHorizontal(chunkX + 2.0, y + 0.02, chunkZ + 2.0, chunkX + 14.0, chunkZ + 14.0,
            set(C0, mixIceR(cr, 0.65), mixIceG(cg, 0.65), mixIceB(cb, 0.65), innerAlpha));

        // 2. 3D vertical gradient frost walls around the 16x16 perimeter
        double wallH = 0.75;
        set(C0, ir, ig, ib, wallBaseA);
        set(C1, 225, 248, 255, 0);
        // North wall (z0)
        r.quad(x0, y, z0, x1, y, z0, x1, y + wallH, z0, x0, y + wallH, z0, C0, C0, C1, C1);
        // South wall (z1)
        r.quad(x0, y, z1, x1, y, z1, x1, y + wallH, z1, x0, y + wallH, z1, C0, C0, C1, C1);
        // West wall (x0)
        r.quad(x0, y, z0, x0, y, z1, x0, y + wallH, z1, x0, y + wallH, z0, C0, C0, C1, C1);
        // East wall (x1)
        r.quad(x1, y, z0, x1, y, z1, x1, y + wallH, z1, x1, y + wallH, z0, C0, C0, C1, C1);

        // 3. Crisp glowing perimeter lines & octagonal frost corner braces
        set(C2, mixIceR(cr, 0.55), mixIceG(cg, 0.55), mixIceB(cb, 0.55), lineA);
        r.line(x0, y + 0.02, z0, x1, y + 0.02, z0, C2);
        r.line(x1, y + 0.02, z0, x1, y + 0.02, z1, C2);
        r.line(x1, y + 0.02, z1, x0, y + 0.02, z1, C2);
        r.line(x0, y + 0.02, z1, x0, y + 0.02, z0, C2);

        double brace = 2.6;
        set(C3, 230, 248, 255, (int) (lineA * 0.8));
        r.line(x0 + brace, y + 0.03, z0, x0, y + 0.03, z0 + brace, C3);
        r.line(x1 - brace, y + 0.03, z0, x1, y + 0.03, z0 + brace, C3);
        r.line(x1 - brace, y + 0.03, z1, x1, y + 0.03, z1 - brace, C3);
        r.line(x0 + brace, y + 0.03, z1, x0, y + 0.03, z1 - brace, C3);

        // 4. Four corner ice crystal spires
        double spireH = 2.4 + 0.4 * pulse;
        set(C0, 235, 250, 255, lineA);
        set(C1, ir, ig, ib, 0);
        r.line(x0, y, z0, x0, y + spireH, z0, C0, C1);
        r.line(x1, y, z0, x1, y + spireH, z0, C0, C1);
        r.line(x1, y, z1, x1, y + spireH, z1, C0, C1);
        r.line(x0, y, z1, x0, y + spireH, z1, C0, C1);

        // 5. Optional floating 3D crystal in the center of the chunk
        if (showCenterCrystal) {
            drawBeacon(r, chunkX + 8.0, y, chunkZ + 8.0, baseColor, time, 6.5);
        }
    }

    /** Draws a rotating 3D octahedron frost crystal and vertical light beam at (cx, y, cz). */
    public static void drawBeacon(Renderer3D r, double cx, double y, double cz,
                                  Color baseColor, double time, double beamHeight) {
        int cr = mixIceR(baseColor.r, 0.35);
        int cg = mixIceG(baseColor.g, 0.35);
        int cb = mixIceB(baseColor.b, 0.35);

        double bob = Math.sin(time * 2.8 + cx * 0.1) * 0.35;
        double cy = y + 1.35 + bob;
        double rad = 0.65;
        double h = 1.05;
        double rot = time * 1.9 + (cx + cz) * 0.05;

        double cos0 = Math.cos(rot) * rad;
        double sin0 = Math.sin(rot) * rad;
        double cos1 = -sin0;
        double sin1 = cos0;

        double tx = cx, ty = cy + h, tz = cz;
        double bx = cx, by = cy - h, bz = cz;

        double[] vx = {cx + cos0, cx + cos1, cx - cos0, cx - cos1};
        double[] vz = {cz + sin0, cz + sin1, cz - sin0, cz - sin1};

        set(C0, 240, 252, 255, 185);
        set(C1, cr, cg, cb, 120);
        set(C2, cr, cg, cb, 240);

        for (int i = 0; i < 4; i++) {
            int n = (i + 1) & 3;
            // Upper pyramid face
            r.quad(tx, ty, tz, tx, ty, tz, vx[n], cy, vz[n], vx[i], cy, vz[i], C0, C0, C1, C1);
            // Lower pyramid face
            r.quad(bx, by, bz, bx, by, bz, vx[i], cy, vz[i], vx[n], cy, vz[n], C1, C1, C0, C0);
            // Crisp crystal edges
            r.line(tx, ty, tz, vx[i], cy, vz[i], C2);
            r.line(bx, by, bz, vx[i], cy, vz[i], C2);
            r.line(vx[i], cy, vz[i], vx[n], cy, vz[n], C0);
        }

        // Vertical glowing frost beam
        if (beamHeight > 0.5) {
            set(C0, 240, 252, 255, 220);
            set(C1, cr, cg, cb, 0);
            r.line(cx, y, cz, cx, y + beamHeight, cz, C0, C1);
        }
    }
}
