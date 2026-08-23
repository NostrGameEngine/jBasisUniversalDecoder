package org.ngengine.basis.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.ngengine.basis.BasisColorSpace;
import org.ngengine.basis.BasisDecodeResult;
import org.ngengine.basis.BasisImageFormat;

class DecodedImageRendererTest {

    @Test
    void convertsRgba8ToBufferedImage() {
        ByteBuffer pixels = ByteBuffer.wrap(new byte[] {
                10, 20, 30, 40,
                50, 60, 70, (byte) 255
        });
        BufferedImage image = DecodedImageRenderer.toBufferedImage(result(2, 1, pixels, BasisImageFormat.RGBA8));

        assertEquals(0x280A141E, image.getRGB(0, 0));
        assertEquals(0xFF323C46, image.getRGB(1, 0));
    }

    @Test
    void convertsPackedFormatsToBufferedImage() {
        ByteBuffer pixels = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0xF800)
                .putShort((short) 0x07E0)
                .putShort((short) 0x0FFF)
                .flip();

        assertEquals(0xFFFF0000, DecodedImageRenderer.toBufferedImage(
                result(1, 1, slice(pixels, 0, 2), BasisImageFormat.RGB565)).getRGB(0, 0));
        assertEquals(0xFF00FF00, DecodedImageRenderer.toBufferedImage(
                result(1, 1, slice(pixels, 2, 2), BasisImageFormat.BGR565)).getRGB(0, 0));
        assertEquals(0xFF00FFFF, DecodedImageRenderer.toBufferedImage(
                result(1, 1, slice(pixels, 4, 2), BasisImageFormat.RGBA4444)).getRGB(0, 0));
    }

    private static BasisDecodeResult result(int width, int height, ByteBuffer pixels, BasisImageFormat format) {
        return new BasisDecodeResult(width, height, pixels, format, new int[] {pixels.remaining()}, BasisColorSpace.sRGB);
    }

    private static ByteBuffer slice(ByteBuffer source, int offset, int length) {
        ByteBuffer duplicate = source.asReadOnlyBuffer();
        duplicate.position(offset);
        duplicate.limit(offset + length);
        return duplicate.slice().order(ByteOrder.LITTLE_ENDIAN);
    }
}
