package dev.opecko.sonocraft.art;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.opecko.sonocraft.theme.Colors;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ArtTest {
    @Test
    void resizesGoogleArtAndLeavesOtherUrls() {
        assertEquals("https://lh3.googleusercontent.com/abc=w96-h96-l90-rj",
                ArtUrls.sized("https://lh3.googleusercontent.com/abc=w544-h544-l90-rj", 96));
        assertEquals("https://lh3.googleusercontent.com/abc=w96-h96-l90-rj",
                ArtUrls.sized("https://lh3.googleusercontent.com/abc=w544-h544", 96));
        assertEquals("https://yt3.ggpht.com/x=w64-h64-l90-rj", ArtUrls.sized("https://yt3.ggpht.com/x=s88-c-k", 64));
        assertEquals("https://lh3.googleusercontent.com/plain=w32-h32-l90-rj",
                ArtUrls.sized("https://lh3.googleusercontent.com/plain", 32));
        String yt = "https://i.ytimg.com/vi/abc/hqdefault.jpg";
        assertEquals(yt, ArtUrls.sized(yt, 96));
        assertEquals(null, ArtUrls.sized(null, 96));
    }

    private static byte[] encode(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void decodesAndCropsToCentreSquare() throws Exception {
        // 160×90 "video thumbnail": black bars left and right of a red centre square
        BufferedImage img = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 160, 90);
        g.setColor(Color.RED);
        g.fillRect(35, 0, 90, 90);
        g.dispose();
        for (String format : new String[] {"png", "jpg"}) {
            Pixels p = ArtDecoder.decode(encode(img, format), 32);
            assertEquals(32, p.width());
            int corner = p.at(0, 0);
            assertTrue((corner >> 16 & 0xFF) > 200 && (corner >> 8 & 0xFF) < 60, format + " corner " + Integer.toHexString(corner));
        }
        assertThrows(IOException.class, () -> ArtDecoder.decode(new byte[] {1, 2, 3}, 32));
    }

    private static Pixels image(int size, int background, int spot, int spotSize) {
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                px[y * size + x] = 0xFF000000 | (x < spotSize && y < spotSize ? spot : background);
            }
        }
        return new Pixels(size, size, px);
    }

    @Test
    void picksTheColourfulPartAndIgnoresGreys() {
        assertEquals(0x2040E0, ArtPalette.accent(image(64, 0x202020, 0x2040E0, 16)));
        assertEquals(0x2040E0, ArtPalette.accent(image(64, 0xF8F8F8, 0x2040E0, 12)));
        assertEquals(-1, ArtPalette.accent(image(64, 0x808080, 0x101010, 30)));
        // a saturated colour beats a larger dull one
        int dull = 0x806860;
        int vivid = 0xF0C020;
        assertEquals(vivid, ArtPalette.accent(image(64, dull, vivid, 20)));
        assertTrue(Colors.saturation(dull) < Colors.saturation(vivid));
    }
}
