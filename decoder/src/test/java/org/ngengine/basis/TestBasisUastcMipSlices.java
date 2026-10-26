package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Verifies UASTC mip selection independently of each level's alpha flag.
 */
public class TestBasisUastcMipSlices {
    @Test
    public void preservesMipsWhenAlphaStartsAfterLevelZero() throws IOException {
        assertMixedAlphaChain(new int[] {0, 1, 1});
    }

    @Test
    public void preservesMipsWhenAlphaEndsAfterLevelZero() throws IOException {
        assertMixedAlphaChain(new int[] {1, 0, 0});
    }

    @Test
    public void preservesAlternatingAlphaAndAllAlphaChains() throws IOException {
        assertMixedAlphaChain(new int[] {0, 1, 0});
        assertMixedAlphaChain(new int[] {1, 1, 1});
    }

    @Test
    public void rejectsDuplicateLevelsWithDifferentAlphaFlags() throws IOException {
        byte[] payload = payload(new int[] {0, 1, 0});
        payload[Ktx2BasisFileHeader.SIZE_BYTES + Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES + 3] = 0;
        assertThrows(BasisDecodeException.class, () -> decode(payload, BasisTranscodeTarget.RGBA8, 0));
    }

    @Test
    public void rejectsMissingMipLevelAcrossAlphaFlags() throws IOException {
        byte[] payload = payload(new int[] {0, 1, 0});
        payload[Ktx2BasisFileHeader.SIZE_BYTES + 2 * Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES + 3] = 3;
        assertThrows(BasisDecodeException.class, () -> decode(payload, BasisTranscodeTarget.BC7, 0));
    }

    @Test
    public void rejectsOutOfRangeImageIndices() throws IOException {
        byte[] payload = payload(new int[] {1, 0, 1});
        assertThrows(IllegalArgumentException.class, () -> decode(payload, BasisTranscodeTarget.RGBA8, -1));
        assertThrows(BasisDecodeException.class, () -> decode(payload, BasisTranscodeTarget.RGBA8, 1));
    }

    private static void assertMixedAlphaChain(int[] flags) throws IOException {
        byte[] mixed = payload(flags);
        byte[] reference = payload(new int[] {0, 0, 0});
        for (BasisTranscodeTarget target : new BasisTranscodeTarget[] {
                BasisTranscodeTarget.RGBA8, BasisTranscodeTarget.BC7}) {
            final BasisDecodeResult expected = decode(reference, target, 0);
            BasisDecodeResult actual = decode(mixed, target, 0);
            assertEquals(3, actual.getLevelCount());
            assertArrayEquals(target == BasisTranscodeTarget.RGBA8
                    ? new int[] {256, 64, 16} : new int[] {64, 16, 16}, actual.getMipMapSizes());
            assertEquals(8, actual.getWidth());
            assertEquals(8, actual.getHeight());
            assertEquals(expected.getPixelData(), actual.getPixelData());
        }
    }

    private static BasisDecodeResult decode(byte[] payload, BasisTranscodeTarget target, int imageIndex) {
        return new BasisuJavaDecoder().decode(BasisDecodeRequest.builder(payload)
                .target(target).imageIndex(imageIndex).allocator(ByteBuffer::allocate).build());
    }

    // Reuse a valid encoded UASTC block from the licensed reference fixture.
    // The small container changes metadata only; the decoded pixels must agree.
    private static byte[] payload(int[] flags) throws IOException {
        byte[] ktx;
        try (InputStream stream = TestBasisUastcMipSlices.class.getClassLoader()
                .getResourceAsStream("fixtures/ktx2/kodim_uastc4x4.ktx2")) {
            if (stream == null) {
                throw new IOException("Missing UASTC reference fixture");
            }
            ktx = stream.readAllBytes();
        }
        int blockOffset = (int) Ktx2Container.parse(ktx).getLevel(0).getByteOffset();
        byte[] block = Arrays.copyOfRange(ktx, blockOffset, blockOffset + 16);
        int tableOffset = Ktx2BasisFileHeader.SIZE_BYTES;
        int dataOffset = tableOffset + flags.length * Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES;
        byte[] bytes = new byte[dataOffset + 96];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short) Ktx2Constants.cBASISSigValue);
        buffer.putShort(2, (short) 1);
        buffer.putShort(4, (short) Ktx2BasisFileHeader.SIZE_BYTES);
        buffer.putInt(8, bytes.length - tableOffset);
        buffer.put(14, (byte) flags.length);
        buffer.put(17, (byte) 1);
        buffer.put(20, (byte) Ktx2BasisTextureFormat.cUASTC_LDR_4x4.getCode());
        buffer.put(23, (byte) Ktx2BasisTextureType.cBASISTexType2D.getCode());
        buffer.putInt(65, tableOffset);
        for (int level = 0; level < flags.length; level++) {
            int dimension = 8 >> level;
            int blocks = Math.max(1, (dimension + 3) / 4);
            int size = blocks * blocks * 16;
            buffer.position(tableOffset + level * Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES + 3);
            buffer.put((byte) level);
            buffer.put((byte) flags[level]);
            buffer.putShort((short) dimension);
            buffer.putShort((short) dimension);
            buffer.putShort((short) blocks);
            buffer.putShort((short) blocks);
            buffer.putInt(dataOffset);
            buffer.putInt(size);
            buffer.putShort((short) 0);
            for (int offset = 0; offset < size; offset += block.length) {
                System.arraycopy(block, 0, bytes, dataOffset + offset, block.length);
            }
            dataOffset += size;
        }
        return bytes;
    }
}
