package dev.opecko.sonocraft.theme;

/**
 * Colour maths: sRGB ↔ OKLab (for even-looking transitions and lightness changes), WCAG relative luminance and
 * contrast. Colours are 0xRRGGBB ints unless a name says ARGB.
 */
public final class Colors {
    private Colors() {
    }

    public static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int argb(int alpha, int rgb) {
        return (clamp(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static double toLinear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int fromLinear(double v) {
        double s = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, s)) * 255);
    }

    /** WCAG relative luminance 0–1. */
    public static double luminance(int rgb) {
        return 0.2126 * toLinear(rgb >> 16 & 0xFF) + 0.7152 * toLinear(rgb >> 8 & 0xFF) + 0.0722 * toLinear(rgb & 0xFF);
    }

    /** WCAG contrast ratio 1–21. */
    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** OKLab {L, a, b}. */
    public static double[] toOklab(int rgb) {
        double r = toLinear(rgb >> 16 & 0xFF);
        double g = toLinear(rgb >> 8 & 0xFF);
        double b = toLinear(rgb & 0xFF);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] {
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    public static int fromOklab(double lightness, double a, double b) {
        double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
        double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
        double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
        return rgb(fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
                fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
                fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s));
    }

    /** Interpolates in OKLab, {@code t} 0–1. */
    public static int lerp(int from, int to, double t) {
        if (t <= 0) {
            return from;
        }
        if (t >= 1) {
            return to;
        }
        double[] a = toOklab(from);
        double[] b = toOklab(to);
        return fromOklab(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
    }

    /** Same hue and chroma with OKLab lightness changed by {@code delta}. */
    public static int shiftLightness(int rgb, double delta) {
        double[] lab = toOklab(rgb);
        return fromOklab(Math.max(0, Math.min(1, lab[0] + delta)), lab[1], lab[2]);
    }

    /** Raises lightness until the colour reaches {@code ratio} contrast against a dark {@code background}. */
    public static int ensureContrast(int rgb, int background, double ratio) {
        double[] lab = toOklab(rgb);
        int out = rgb;
        for (double l = lab[0]; contrast(out, background) < ratio && l < 1; l += 0.02) {
            out = fromOklab(Math.min(1, l), lab[1], lab[2]);
        }
        return out;
    }

    /** HSV-style saturation 0–1. */
    public static double saturation(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / (double) max;
    }
}
