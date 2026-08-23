package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

public class TestKtx2StructBinaryParsingParity {

    @Test
    public void testKtx2BasisFileHeaderParsesLittleEndianFieldsAndWiderUnsignedTypes() {
        byte[] raw = new byte[Ktx2BasisFileHeader.SIZE_BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        writeUInt16(buffer, 0x4B54); // signature low two bytes
        writeUInt16(buffer, 0x0040); // version
        writeUInt16(buffer, Ktx2Constants.KTX2_HEADER_SIZE); // header_size
        writeUInt16(buffer, 0x1A2B); // header CRC

        writeUInt32(buffer, 0xFFFF_FFFFL); // data_size
        writeUInt16(buffer, 0xBEEF); // data crc

        writeUInt24(buffer, 0x12_34_56); // total_slices
        writeUInt24(buffer, 0x00_FF_FF); // total_images
        buffer.put((byte) 0x99); // texture format
        writeUInt16(buffer, 0x55AA); // flags
        buffer.put((byte) 0x77); // texture type
        writeUInt24(buffer, 0x89ABCD); // us_per_frame

        writeUInt32(buffer, 0x0102_0304L); // reserved
        writeUInt32(buffer, 0x1111_2222L); // user0
        writeUInt32(buffer, 0x3333_4444L); // user1

        writeUInt16(buffer, 0xFFFF); // total_endpoints
        writeUInt32(buffer, 0x7777_8888L); // endpoint file offset
        writeUInt24(buffer, 0x00ABCD); // endpoint size

        writeUInt16(buffer, 0x00FE); // total_selectors
        writeUInt32(buffer, 0xAAAA_BBBB); // selector file offset
        writeUInt24(buffer, 0x00CDEF); // selector size

        writeUInt32(buffer, 0x0102_0304L); // tables file offset
        writeUInt32(buffer, 0x0506_0708L); // tables file size
        writeUInt32(buffer, 0x0A0B_0C0DL); // slice desc file offset
        writeUInt32(buffer, 0x0100_0200L); // extended data offset
        writeUInt32(buffer, 0x0300_0400L); // extended size

        Ktx2BasisFileHeader header = Ktx2BasisFileHeader.parse(buffer, 0);
        assertNotNull(header);

        assertEquals(0x4B54, header.getSignature());
        assertEquals(0x0040, header.getVersion());
        assertEquals(Ktx2Constants.KTX2_HEADER_SIZE, header.getHeaderSize());
        assertEquals(0x1A2B, header.getHeaderCrc16());

        assertEquals(0xFFFF_FFFFL, header.getDataSize());
        assertEquals(0xBEEF, header.getDataCrc16());
        assertEquals(0x123456, header.getTotalSlices());
        assertEquals(0x00FFFF, header.getTotalImages());
        assertEquals(0x99, header.getTextureFormat());
        assertEquals(0x55AA, header.getFlags());
        assertEquals(0x77, header.getTextureType());
        assertEquals(0x89ABCD, header.getUsPerFrame());

        assertEquals(0x01020304L, header.getReserved());
        assertEquals(0x11112222L, header.getUserData0());
        assertEquals(0x33334444L, header.getUserData1());
        assertEquals(0xFFFF, header.getTotalEndpoints());
        assertEquals(0x77778888L, header.getEndpointCodebookFileOffset());
        assertEquals(0xABCDL, header.getEndpointCodebookFileSize());
        assertEquals(0x00FE, header.getTotalSelectors());
        assertEquals(0xAAAABBBBL, header.getSelectorCodebookFileOffset());
        assertEquals(0xCDEFL, header.getSelectorCodebookFileSize());
        assertEquals(0x1020304L, header.getTablesFileOffset());
        assertEquals(0x5060708L, header.getTablesFileSize());
        assertEquals(0x0A0B0C0DL, header.getSliceDescFileOffset());
        assertEquals(0x1000200L, header.getExtendedDataOffset());
        assertEquals(0x3000400L, header.getExtendedDataSize());
    }

    @Test
    public void testKtx2BasisSliceDescParses24bitAndUnsignedFields() {
        byte[] raw = new byte[Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        writeUInt24(buffer, 0xFFFFFF); // image_index
        buffer.put((byte) 0xAB); // level_index
        buffer.put((byte) 0xCD); // flags
        writeUInt16(buffer, 0xFFFF); // original width
        writeUInt16(buffer, 0x0100); // original height
        writeUInt16(buffer, 0x0002); // num blocks x
        writeUInt16(buffer, 0x0030); // num blocks y
        writeUInt32(buffer, 0xFFFF_FFFFL); // file offset
        writeUInt32(buffer, 0x1234_5678L); // file size
        writeUInt16(buffer, 0xBEEF); // crc16

        Ktx2BasisSliceDesc desc = Ktx2BasisSliceDesc.parse(buffer, 0);
        assertNotNull(desc);

        assertEquals(0xFFFFFF, desc.getImageIndex());
        assertEquals(0xAB, desc.getLevelIndex());
        assertEquals(0xCD, desc.getFlags());
        assertEquals(0xFFFF, desc.getOriginalWidth());
        assertEquals(0x0100, desc.getOriginalHeight());
        assertEquals(0x0002, desc.getNumBlocksX());
        assertEquals(0x0030, desc.getNumBlocksY());
        assertEquals(0xFFFF_FFFFL, desc.getFileOffset());
        assertEquals(0x12345678L, desc.getFileSize());
        assertEquals(0xBEEF, desc.getCrc16());
    }

    @Test
    public void testKtx2BasisSliceDescRejectsTruncatedPayload() {
        ByteBuffer truncated = ByteBuffer.allocate(Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES - 1);
        assertThrows(IllegalArgumentException.class, () -> Ktx2BasisSliceDesc.parse(truncated, 0));
    }

    @Test
    public void testParseStructsFromOffsetWithoutMutatingOriginalBufferState() {
        byte[] headerBytes = new byte[Ktx2BasisFileHeader.SIZE_BYTES];
        ByteBuffer headerBuilder = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        writeUInt16(headerBuilder, 0x4B54); // signature low two bytes
        writeUInt16(headerBuilder, 0x0040); // version
        writeUInt16(headerBuilder, Ktx2Constants.KTX2_HEADER_SIZE);
        writeUInt16(headerBuilder, 0x1A2B); // header CRC
        writeUInt32(headerBuilder, 0xFFFF_FFFFL);
        writeUInt16(headerBuilder, 0xBEEF);
        writeUInt24(headerBuilder, 0x001234);
        writeUInt24(headerBuilder, 0x000100);
        headerBuilder.put((byte) 0x99);
        writeUInt16(headerBuilder, 0x0007);
        headerBuilder.put((byte) 0x02);
        writeUInt24(headerBuilder, 0x000001);
        writeUInt32(headerBuilder, 1);
        writeUInt32(headerBuilder, 2);
        writeUInt32(headerBuilder, 3);
        writeUInt16(headerBuilder, 0x0001);
        writeUInt32(headerBuilder, 4);
        writeUInt24(headerBuilder, 5);
        writeUInt16(headerBuilder, 6);
        writeUInt32(headerBuilder, 7);
        writeUInt24(headerBuilder, 8);
        writeUInt32(headerBuilder, 9);
        writeUInt32(headerBuilder, 10);
        writeUInt32(headerBuilder, 11);
        writeUInt32(headerBuilder, 12);
        writeUInt32(headerBuilder, 13);

        byte[] raw = new byte[128 + Ktx2BasisFileHeader.SIZE_BYTES + Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES];
        ByteBuffer host = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        host.position(37);
        host.put(headerBytes);
        host.put(new byte[24]);

        final int fileHeaderOffset = 37;
        final int sliceDescOffset = 37 + Ktx2BasisFileHeader.SIZE_BYTES + 10;
        ByteBuffer sliceBuffer = ByteBuffer
                .allocate(Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        writeUInt24(sliceBuffer, 0x000123);
        sliceBuffer.put((byte) 0x03);
        sliceBuffer.put((byte) 0x00);
        writeUInt16(sliceBuffer, 33);
        writeUInt16(sliceBuffer, 44);
        writeUInt16(sliceBuffer, 1);
        writeUInt16(sliceBuffer, 2);
        writeUInt32(sliceBuffer, 16);
        writeUInt32(sliceBuffer, 8);
        writeUInt16(sliceBuffer, 0xBEEF);
        host.position(sliceDescOffset);
        host.put(sliceBuffer.array());

        final int beforeHeaderPos = host.position();
        final int beforeHeaderLimit = host.limit();

        final Ktx2BasisFileHeader parsedHeader =
                Ktx2BasisFileHeader.parse(host, fileHeaderOffset);
        assertEquals(0x4B54, parsedHeader.getSignature());
        assertEquals(0x001234, parsedHeader.getTotalSlices());

        final Ktx2BasisSliceDesc parsedSlice =
                Ktx2BasisSliceDesc.parse(host, sliceDescOffset);
        assertEquals(
                beforeHeaderPos,
                host.position(),
                "parse(byte[],offset) must not mutate caller buffer position");
        assertEquals(
                beforeHeaderLimit,
                host.limit(),
                "parse(byte[],offset) must not mutate caller buffer limit");
        assertEquals(0x000123, parsedSlice.getImageIndex());
        assertEquals(33, parsedSlice.getOriginalWidth());
    }

    private static void writeUInt16(ByteBuffer buffer, int value) {
        buffer.put((byte) (value & 0xFF));
        buffer.put((byte) ((value >>> 8) & 0xFF));
    }

    private static void writeUInt24(ByteBuffer buffer, int value) {
        buffer.put((byte) (value & 0xFF));
        buffer.put((byte) ((value >>> 8) & 0xFF));
        buffer.put((byte) ((value >>> 16) & 0xFF));
    }

    private static void writeUInt32(ByteBuffer buffer, long value) {
        buffer.put((byte) (value & 0xFF));
        buffer.put((byte) ((value >>> 8) & 0xFF));
        buffer.put((byte) ((value >>> 16) & 0xFF));
        buffer.put((byte) ((value >>> 24) & 0xFF));
    }
}
