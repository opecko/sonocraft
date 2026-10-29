package dev.opecko.sonocraft.art;

import dev.opecko.sonocraft.theme.Colors;

/**
 * Picks an accent colour from artwork (PLAN §8): colours are grouped into buckets (4 bits per channel), each
 * bucket scores {@code share × saturation²}, near-black, near-white and grey buckets score nothing. Returns -1 when
 * the art has no usable colour (a black-and-white cover), so the caller falls back.
 */
public final class ArtPalette {
    private static final int SAMPLE = 32;

    private ArtPalette() {
    }

    public static int accent(Pixels p) {
        int[] count = new int[4096];
        long[] sumR = new long[4096];
        long[] sumG = new long[4096];
        long[] sumB = new long[4096];
        int stepX = Math.max(1, p.width() / SAMPLE);
        int stepY = Math.max(1, p.height() / SAMPLE);
        int total = 0;
        for (int y = 0; y < p.height(); y += stepY) {
            for (int x = 0; x < p.width(); x += stepX) {
                int c = p.at(x, y);
                if ((c >>> 24) < 128) {
                    continue;
                }
                int r = c >> 16 & 0xFF;
                int g = c >> 8 & 0xFF;
                int b = c & 0xFF;
                int bucket = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4);
                count[bucket]++;
                sumR[bucket] += r;
                sumG[bucket] += g;
                sumB[bucket] += b;
                total++;
            }
        }
        if (total == 0) {
            return -1;
        }
        double best = 0;
        int bestColor = -1;
        for (int i = 0; i < 4096; i++) {
            if (count[i] == 0) {
                continue;
            }
            int rgb = Colors.rgb((int) (sumR[i] / count[i]), (int) (sumG[i] / count[i]), (int) (sumB[i] / count[i]));
            double sat = Colors.saturation(rgb);
            double lum = Colors.luminance(rgb);
            if (sat < 0.2 || lum < 0.01 || lum > 0.85) {
                continue;
            }
            // neighbouring buckets of the same colour would split its share; a small share still needs to count
            double share = (double) count[i] / total;
            double score = Math.sqrt(share) * sat * sat;
            if (score > best) {
                best = score;
                bestColor = rgb;
            }
        }
        return bestColor;
    }
}
