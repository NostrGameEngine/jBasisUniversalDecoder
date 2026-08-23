package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class TestKtx2Header {

    @Test
    public void testParseValidKtx2Header() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        Ktx2Header header = Ktx2Header.parse(payload);

        assertEquals(512, header.getPixelWidth());
        assertEquals(512, header.getPixelHeight());
        assertEquals(1, header.getFaceCount());
        assertEquals(1, header.getLayerCount());
        assertEquals(1, header.getLevelCount());
        assertEquals(Ktx2SupercompressionScheme.BASISLZ, header.getSupercompressionScheme());
        assertEquals(0, header.getVkFormat());
    }

    @Test
    public void testRejectsInvalidMagic() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        payload[0] ^= (byte) 0xFF;
        assertThrows(BasisDecodeException.class, () -> Ktx2Header.parse(payload));
    }

    @Test
    public void testSupportsNonTrivialLayerCountInHeader() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        // layerCount field is at byte offset 32 in little-endian uint32.
        int layerCountOffset = 32;
        payload[layerCountOffset] = 2;
        payload[layerCountOffset + 1] = 0;
        payload[layerCountOffset + 2] = 0;
        payload[layerCountOffset + 3] = 0;

        assertNotNull(Ktx2Header.parse(payload));
    }


    @Test
    public void testRejectsTooManyMipLevels() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        int levelCountOffset = 40;
        payload[levelCountOffset] = 20;
        payload[levelCountOffset + 1] = 0;
        payload[levelCountOffset + 2] = 0;
        payload[levelCountOffset + 3] = 0;

        assertThrows(BasisDecodeException.class, () -> Ktx2Header.parse(payload));
    }

    @Test
    public void testRejectsUnknownSupercompressionCode() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        int supercompressionOffset = 44;
        payload[supercompressionOffset] = (byte) 0xFF;
        payload[supercompressionOffset + 1] = 0;
        payload[supercompressionOffset + 2] = 0;
        payload[supercompressionOffset + 3] = 0;

        assertThrows(BasisDecodeException.class, () -> Ktx2Header.parse(payload));
    }

    @Test
    public void testRejectsLayerCountOutsideIntRange() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        int layerCountOffset = 32;
        payload[layerCountOffset] = (byte) 0xFF;
        payload[layerCountOffset + 1] = (byte) 0xFF;
        payload[layerCountOffset + 2] = (byte) 0xFF;
        payload[layerCountOffset + 3] = (byte) 0xFF;

        assertThrows(BasisDecodeException.class, () -> Ktx2Header.parse(payload));
    }

    private static byte[] loadFixture(String name) {
        try (InputStream stream = TestKtx2Header.class.getClassLoader().getResourceAsStream(name)) {
            Objects.requireNonNull(stream, "Fixture not found: " + name);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[1024];
            int read;
            while ((read = stream.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Could not read fixture: " + name, exception);
        }
    }
}
