package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class TestZstdFrameDecoder {
    private static final int[] COMPRESSED_SECTIONS = {
            XuastcLdrFullZstdSectionTable.MODE_BYTES,
            XuastcLdrFullZstdSectionTable.SOLID_DPCM_BYTES,
            XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_REUSE_INDICES,
            XuastcLdrFullZstdSectionTable.USE_BC_BITS,
            XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_3BIT,
            XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_4BIT,
            XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_5BIT,
            XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_6BIT,
            XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_7BIT,
            XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_8BIT,
            XuastcLdrFullZstdSectionTable.MEAN0_BITS,
            XuastcLdrFullZstdSectionTable.MEAN1_BYTES,
            XuastcLdrFullZstdSectionTable.RUN_BYTES,
            XuastcLdrFullZstdSectionTable.COEFF_BYTES,
            XuastcLdrFullZstdSectionTable.WEIGHT2_BITS,
            XuastcLdrFullZstdSectionTable.WEIGHT3_BITS,
            XuastcLdrFullZstdSectionTable.WEIGHT4_BITS,
            XuastcLdrFullZstdSectionTable.WEIGHT8_BYTES
    };

    private static final int[] DECOMPRESSED_LENGTHS = {
            13_184, 372, 12_172, 1_588, 480, 5_629, 15_580, 10_862, 15_422,
            36_392, 5_238, 5_730, 49_947, 33_742, 1_396, 8_205, 3_142, 2_824
    };

    private static final String[] SHA256 = {
            "bdf5ddc4c6be8f5333277241d838ddfbe1f72f8e4b575e90704c368922461c78",
            "c2c46e0263f9ed6680ccfd1a21966c53f51e24ff3f6b58df711afb081e8d2730",
            "d6d503343fc10c5a9a36fd959b5ddc15140877f0bd9725db2fc40597d1c21d0e",
            "79d3ce801069d46578f7537aaf684bd6becbfcaf3e4565c26671cc99f3a09077",
            "536fcfefa220a3ca781f26bdffd34e4df61f52b084c7889a9f352a886cf1f526",
            "21e0242410026379816630df90d9c05ec25e90e8e7578e47bee36ca892747b6a",
            "f084d37bcda1cb928326f123d6a9c9fbeeede87be932ba26ba95100139ea0c05",
            "8346cc6fb38969b763649062fa8c6315ff8bec1d95bf788a2bfc838bb6b4f679",
            "4282a9157dceb66ffbb44cdd008364af519a4dab252fff005419a8584aae7995",
            "c12074391dfb2946a7738e7fb34937401d10088e1b4a7e4543f01a205e4e75dc",
            "2e393de66f5f015fb67b548fa2e03e77d00aa28d9af0a5339f89a8690e5fd06f",
            "289d95e7e2af6a23ba021272a9803eacf42ba75487004ad486cbdb545ed9f673",
            "efaf9ca53c18a08faa5215e76f5629174ae86056134234ca3758740438f1a76a",
            "789483dbed6c95946d649d20223127ad4b1e56c6a0ac806498be6842e4164c23",
            "ff58db3258f4045cb07c22148f6a44fdcacde420896b824a6894ca1fad025054",
            "ad095ed85d19ca1d498edb45e618b641e16868f190816376645b6cb0e001dc9c",
            "78473e055b45aa5afc5a8d00aaa82a959cd0674deb339b07c7aa1874fbb89387",
            "8d2ab85018743a17ea0bf1cde95087c014978127f7a8e35ac26c40c78660cb61"
    };

    @Test
    public void decompressesXuastcFullZstdSideStreams() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];
        int sliceOffset = (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset());
        XuastcLdrFullZstdSectionTable sections = XuastcLdrFullZstdSectionTable.parse(
                payload,
                sliceOffset,
                (int) slice.getByteLength());

        for (int i = 0; i < COMPRESSED_SECTIONS.length; i++) {
            XuastcLdrFullZstdSectionTable.Section section = sections.getSection(COMPRESSED_SECTIONS[i]);
            byte[] compressed = Arrays.copyOfRange(
                    payload,
                    section.getByteOffset(),
                    section.getByteOffset() + section.getByteLength());

            byte[] decompressed;
            try {
                decompressed = ZstdFrameDecoder.decompress(compressed, 0, compressed.length);
            } catch (BasisDecodeException exception) {
                throw new AssertionError("Could not decode section " + section.getIndex(), exception);
            }

            assertEquals(DECOMPRESSED_LENGTHS[i], decompressed.length, Integer.toString(section.getIndex()));
            assertEquals(SHA256[i], sha256(decompressed), Integer.toString(section.getIndex()));
        }
    }

    @Test
    public void decompressesRawAndRleBlocks() {
        assertArrayEquals(new byte[] {1, 2, 3},
                ZstdFrameDecoder.decompress(new byte[] {
                        0x28, -75, 0x2F, -3, 0x20, 3, 0x19, 0, 0, 1, 2, 3
                }, 0, 12));
        assertArrayEquals(new byte[] {7, 7, 7, 7},
                ZstdFrameDecoder.decompress(new byte[] {
                        0x28, -75, 0x2F, -3, 0x20, 4, 0x23, 0, 0, 7
                }, 0, 10));
    }

    @Test
    public void decompressesFramesWithoutContentSize() {
        assertArrayEquals(new byte[] {1, 2, 3},
                ZstdFrameDecoder.decompress(new byte[] {
                        0x28, -75, 0x2F, -3, 0, 0, 0x19, 0, 0, 1, 2, 3
                }, 0, 12));
        assertArrayEquals(new byte[] {7, 7, 7, 7},
                ZstdFrameDecoder.decompress(new byte[] {
                        0x28, -75, 0x2F, -3, 0, 0, 0x23, 0, 0, 7
                }, 0, 10));
    }

    private static byte[] loadFixture(String name) {
        try (InputStream stream = TestZstdFrameDecoder.class.getClassLoader().getResourceAsStream(name)) {
            Objects.requireNonNull(stream, "Fixture not found: " + name);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[4096];
            int read;
            while ((read = stream.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Could not read fixture: " + name, exception);
        }
    }

    private static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", Byte.toUnsignedInt(value)));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("SHA-256 is not available", exception);
        }
    }
}
