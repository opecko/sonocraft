package dev.opecko.sonocraft.art;

/** A decoded image as ARGB ints, row by row. */
public record Pixels(int width, int height, int[] argb) {
    public int at(int x, int y) {
        return argb[y * width + x];
    }
}
