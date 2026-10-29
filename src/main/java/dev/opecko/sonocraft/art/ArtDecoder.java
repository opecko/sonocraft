package dev.opecko.sonocraft.art;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Decodes artwork (JPEG, PNG; anything ImageIO can read) into a square of {@code size} pixels. Non-square images
 * (16:9 video thumbnails) are cropped to the centre square.
 */
public final class ArtDecoder {
    private ArtDecoder() {
    }

    public static Pixels decode(byte[] data, int size) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null) {
            throw new IOException("unsupported image format");
        }
        int side = Math.min(src.getWidth(), src.getHeight());
        int sx = (src.getWidth() - side) / 2;
        int sy = (src.getHeight() - side) / 2;
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, size, size, sx, sy, sx + side, sy + side, null);
        } finally {
            g.dispose();
        }
        int[] argb = dst.getRGB(0, 0, size, size, null, 0, size);
        return new Pixels(size, size, argb);
    }
}
