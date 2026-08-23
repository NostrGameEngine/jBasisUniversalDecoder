package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

public class TestKtx2HeaderParsing {

    private static byte[] validHeaderBytes(
            int width,
            int height,
            int layerCountRaw,
            int faceCount,
            int levelCount) {
        byte[] data = new byte[Ktx2Constants.KTX2_HEADER_SIZE];
        for (int i = 0; i < Ktx2Constants.KTX2_FILE_IDENTIFIER.length; i++) {
            data[i] = Ktx2Constants.KTX2_FILE_IDENTIFIER[i];
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(Ktx2Constants.KTX2_FILE_IDENTIFIER.length);
        buffer.putInt(37); // vkFormat
        buffer.putInt(1); // typeSize
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.putInt(0); // pixelDepth
        buffer.putInt(layerCountRaw);
        buffer.putInt(faceCount);
        buffer.putInt(levelCount);
        buffer.putInt(Ktx2Constants.KTX2_SS_NONE); // supercompression
        buffer.putInt(0); // dfdByteOffset
        buffer.putInt(0); // dfdByteLength
        buffer.putInt(0); // kvdByteOffset
        buffer.putInt(0); // kvdByteLength
        buffer.putLong(0L); // sgdByteOffset
        buffer.putLong(0L); // sgdByteLength

        return data;
    }

    @Test
    public void testParseAcceptsMinimalValidHeader() {
        byte[] bytes = validHeaderBytes(64, 64, 0, 1, 1);
        Ktx2Header header = Ktx2Header.parse(bytes);

        assertEquals(64, header.getPixelWidth());
        assertEquals(64, header.getPixelHeight());
        assertEquals(1, header.getLayerCount());
        assertEquals(0, header.getRawLayerCount());
    }

    @Test
    public void testParseRejectsWrongMagic() {
        byte[] bytes = validHeaderBytes(64, 64, 0, 1, 1);
        bytes[0] = (byte) 0x00;
        BasisDecodeException exception = assertThrows(
                BasisDecodeException.class,
                () -> Ktx2Header.parse(bytes));
        assertTrue(exception.getMessage().contains("not a valid KTX2 container"));
    }

    @Test
    public void testParseRejectsTooShort() {
        assertThrows(BasisDecodeException.class, () -> Ktx2Header.parse(new byte[3]));
    }

    @Test
    public void testParseRejectsUnsupportedSupercompression() {
        byte[] bytes = validHeaderBytes(64, 64, 0, 1, 1);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(Ktx2Constants.KTX2_FILE_IDENTIFIER.length + (8 * 4));
        buffer.putInt(123456);

        BasisDecodeException exception = assertThrows(
                BasisDecodeException.class,
                () -> Ktx2Header.parse(bytes));
        assertTrue(exception.getMessage().contains("Unsupported KTX2 supercompression code"));
    }

    @Test
    public void testParseRejectsUnsigned32OverflowForWidth() {
        byte[] bytes = validHeaderBytes(0x80000000, 1, 0, 1, 1);
        BasisDecodeException exception = assertThrows(
                BasisDecodeException.class,
                () -> Ktx2Header.parse(bytes));
        assertTrue(exception.getMessage().contains("without overflow")
                || exception.getMessage().contains("unsupported"));
    }

    @Test
    public void testParseRejectsUnsigned64OverflowForSgdOffset() {
        byte[] bytes = validHeaderBytes(64, 64, 0, 1, 1);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(Ktx2Constants.KTX2_FILE_IDENTIFIER.length + 13 * 4, Long.MIN_VALUE);

        BasisDecodeException exception = assertThrows(
                BasisDecodeException.class,
                () -> Ktx2Header.parse(bytes));
        assertTrue(exception.getMessage().contains("does not fit Java signed long")
                || exception.getMessage().contains("signed long"));
    }
}
