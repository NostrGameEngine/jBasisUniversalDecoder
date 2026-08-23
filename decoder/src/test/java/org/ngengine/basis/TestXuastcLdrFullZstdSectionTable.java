package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class TestXuastcLdrFullZstdSectionTable {
    private static final int[] EXPECTED_COMPRESSED_LENGTHS = {
            5_892, 5_607, 248, 6_835, 1_256, 345, 4_474, 8_195, 6_617, 7_617,
            19_281, 3_315, 3_135, 10_020, 10_820, 4_218, 1_354, 5_610, 2_959, 1_684
    };
    private static final int[] EXPECTED_DECODED_LENGTHS = {
            5_892, 13_184, 372, 12_172, 1_588, 480, 5_629, 15_580, 10_862,
            15_422, 36_392, 5_238, 5_730, 49_947, 33_742, 4_218, 1_396,
            8_205, 3_142, 2_824
    };

    @Test
    public void parsesFullZstdSectionFrameSizesFromNativeGoldFixture() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        XuastcLdrFullZstdSectionTable table = XuastcLdrFullZstdSectionTable.parse(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());

        assertEquals(XuastcLdrFullZstdSectionTable.SECTION_COUNT, table.getSectionCount());
        assertEquals(18, table.getZstdCompressedSectionCount());
        for (int i = 0; i < table.getSectionCount(); i++) {
            XuastcLdrFullZstdSectionTable.Section section = table.getSection(i);
            assertEquals(i, section.getIndex());
            assertEquals(EXPECTED_COMPRESSED_LENGTHS[i], section.getByteLength());
            assertEquals(EXPECTED_DECODED_LENGTHS[i], section.getDecodedByteLength());
            if (i == XuastcLdrFullZstdSectionTable.RAW_BITS
                    || i == XuastcLdrFullZstdSectionTable.SIGN_BITS) {
                assertFalse(section.isZstdCompressed());
                assertNull(section.getZstdHeader());
            } else {
                assertTrue(section.isZstdCompressed());
                assertEquals(7, section.getZstdHeader().getHeaderLength());
                assertEquals(section.getDecodedByteLength(), section.getZstdHeader().getContentSize());
                assertEquals(section.getDecodedByteLength(), section.getZstdHeader().getWindowSize());
                assertEquals(0, section.getZstdHeader().getDictionaryId());
                assertFalse(section.getZstdHeader().hasContentChecksum());
            }
        }
    }

    @Test
    public void acceptsZeroLengthOptionalFullZstdSections() {
        byte[] payload = loadFixture("fixtures/ktx2/tough_xuastc6x6_zstd.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        XuastcLdrFullZstdSectionTable table = XuastcLdrFullZstdSectionTable.parse(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());

        for (int index = XuastcLdrFullZstdSectionTable.MEAN0_BITS;
                index <= XuastcLdrFullZstdSectionTable.COEFF_BYTES;
                index++) {
            XuastcLdrFullZstdSectionTable.Section section = table.getSection(index);
            assertTrue(section.isZstdCompressed());
            assertEquals(0, section.getByteLength());
            assertEquals(0, section.getDecodedByteLength());
            assertNull(section.getZstdHeader());
        }
    }

    @Test
    public void rejectsInvalidZstdFrameMagic() {
        byte[] frame = {
                0, 0, 0, 0,
                0x20,
                0
        };

        assertThrows(BasisDecodeException.class, () -> ZstdFrameHeader.parse(frame, 0, frame.length));
    }

    private static byte[] loadFixture(String name) {
        try (InputStream stream =
                     TestXuastcLdrFullZstdSectionTable.class.getClassLoader().getResourceAsStream(name)) {
            Objects.requireNonNull(stream, "Fixture not found: " + name);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[4096];
            int read;
            while ((read = stream.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Unable to load fixture: " + name, exception);
        }
    }
}
