package dev.opecko.sonocraft.art;

/** Rounded corners as an alpha mask, with one pixel of anti-aliasing. */
public final class Corners {
    private Corners() {
    }

    /** The ARGB pixel at (x, y) of a w×h image with corners of {@code radius} pixels cut away. */
    public static int mask(int argb, int x, int y, int w, int h, int radius) {
        if (radius <= 0) {
            return argb;
        }
        double cx;
        double cy;
        if (x < radius) {
            cx = radius;
        } else if (x >= w - radius) {
            cx = w - radius;
        } else {
            return argb;
        }
        if (y < radius) {
            cy = radius;
        } else if (y >= h - radius) {
            cy = h - radius;
        } else {
            return argb;
        }
        double dist = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
        double coverage = Math.max(0, Math.min(1, radius - dist + 0.5));
        int alpha = (int) Math.round((argb >>> 24) * coverage);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
