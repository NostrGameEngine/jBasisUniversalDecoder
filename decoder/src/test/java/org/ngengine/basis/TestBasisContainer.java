package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

public class TestBasisContainer {

    @Test
    public void parsesBasisHeaderAndSliceTable() {
        byte[] payload = minimalBasisPayload();

        BasisContainer container = BasisContainer.parse(payload);

        assertEquals(1, container.getImageCount());
        assertEquals(1, container.getSliceCount());
        assertEquals(Ktx2BasisTextureFormat.cETC1S, container.getTextureFormat());
        assertEquals(Ktx2BasisTextureType.cBASISTexType2D, container.getTextureType());
        assertEquals(2, container.getSlice(0).getOriginalWidth());
        assertEquals(2, container.getSlice(0).getOriginalHeight());
        assertEquals(4, container.getSlice(0).getFileSize());
    }

    @Test
    public void rejectsInvalidBasisSignature() {
        byte[] payload = minimalBasisPayload();
        payload[0] = 0;

        assertThrows(BasisDecodeException.class, () -> BasisContainer.parse(payload));
    }

    @Test
    public void rejectsSliceDataOutsidePayload() {
        byte[] payload = minimalBasisPayload();
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int sliceOffset = Ktx2BasisFileHeader.SIZE_BYTES;
        buffer.putInt(sliceOffset + 13, payload.length + 100);

        assertThrows(BasisDecodeException.class, () -> BasisContainer.parse(payload));
    }

    private static byte[] minimalBasisPayload() {
        int sliceTableOffset = Ktx2BasisFileHeader.SIZE_BYTES;
        int sliceDataOffset = sliceTableOffset + Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES;
        byte[] payload = new byte[sliceDataOffset + 4];
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort((short) Ktx2Constants.cBASISSigValue);
        buffer.putShort((short) 1);
        buffer.putShort((short) Ktx2BasisFileHeader.SIZE_BYTES);
        buffer.putShort((short) 0);
        buffer.putInt(4);
        buffer.putShort((short) 0);
        put24(buffer, 1);
        put24(buffer, 1);
        buffer.put((byte) Ktx2BasisTextureFormat.cETC1S.getCode());
        buffer.putShort((short) 0);
        buffer.put((byte) Ktx2BasisTextureType.cBASISTexType2D.getCode());
        put24(buffer, 0);
        buffer.putInt(0);
        buffer.putInt(7);
        buffer.putInt(9);
        buffer.putShort((short) 0);
        buffer.putInt(0);
        put24(buffer, 0);
        buffer.putShort((short) 0);
        buffer.putInt(0);
        put24(buffer, 0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(sliceTableOffset);
        buffer.putInt(0);
        buffer.putInt(0);

        buffer.position(sliceTableOffset);
        put24(buffer, 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.putShort((short) 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sliceDataOffset);
        buffer.putInt(4);
        buffer.putShort((short) 0);

        payload[sliceDataOffset] = 1;
        payload[sliceDataOffset + 1] = 2;
        payload[sliceDataOffset + 2] = 3;
        payload[sliceDataOffset + 3] = 4;
        return payload;
    }

    private static void put24(ByteBuffer buffer, int value) {
        buffer.put((byte) (value & 0xFF));
        buffer.put((byte) ((value >>> 8) & 0xFF));
        buffer.put((byte) ((value >>> 16) & 0xFF));
    }
}
