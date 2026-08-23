package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Parsed structure of a .basis file before texture data transcoding.
 */
public final class BasisContainer {
    private final Ktx2BasisFileHeader header;
    private final Ktx2BasisSliceDesc[] slices;

    private BasisContainer(Ktx2BasisFileHeader header, Ktx2BasisSliceDesc[] slices) {
        this.header = header;
        this.slices = Arrays.copyOf(slices, slices.length);
    }

    public static BasisContainer parse(byte[] data) {
        if (data == null) {
            throw new BasisDecodeException("Basis payload must not be null");
        }
        if (data.length < Ktx2BasisFileHeader.SIZE_BYTES) {
            throw new BasisDecodeException("Input is too short for a valid Basis container");
        }

        Ktx2BasisFileHeader header = Ktx2BasisFileHeader.parse(ByteBuffer.wrap(data), 0);
        validateHeader(header);

        int sliceCount = header.getTotalSlices();
        int sliceTableOffset = checkedOffset(header.getSliceDescFileOffset(), "slice descriptor offset");
        int sliceTableBytes = checkedMultiply(
                sliceCount,
                Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES,
                "slice descriptor table size");
        validateRange(data.length, sliceTableOffset, sliceTableBytes, "slice descriptor table");

        Ktx2BasisSliceDesc[] slices = new Ktx2BasisSliceDesc[sliceCount];
        for (int i = 0; i < sliceCount; i++) {
            int sliceOffset = sliceTableOffset + i * Ktx2BasisSliceDesc.ENCODED_LENGTH_BYTES;
            Ktx2BasisSliceDesc slice = Ktx2BasisSliceDesc.parse(ByteBuffer.wrap(data), sliceOffset);
            validateSlice(data.length, slice, i);
            slices[i] = slice;
        }

        return new BasisContainer(header, slices);
    }

    public Ktx2BasisFileHeader getHeader() {
        return header;
    }

    public Ktx2BasisTextureFormat getTextureFormat() {
        return Ktx2BasisTextureFormat.fromCode(header.getTextureFormat());
    }

    public Ktx2BasisTextureType getTextureType() {
        return header.getTextureTypeEnum();
    }

    public int getImageCount() {
        return header.getTotalImages();
    }

    public int getSliceCount() {
        return slices.length;
    }

    public Ktx2BasisSliceDesc[] getSlices() {
        return Arrays.copyOf(slices, slices.length);
    }

    public Ktx2BasisSliceDesc getSlice(int index) {
        if (index < 0 || index >= slices.length) {
            throw new IndexOutOfBoundsException("slice index out of range: " + index);
        }
        return slices[index];
    }

    private static void validateHeader(Ktx2BasisFileHeader header) {
        if (header.getSignature() != Ktx2Constants.cBASISSigValue) {
            throw new BasisDecodeException("Input is not a valid Basis container");
        }
        if (header.getHeaderSize() < Ktx2BasisFileHeader.SIZE_BYTES) {
            throw new BasisDecodeException("Basis header size is too small: " + header.getHeaderSize());
        }
        if (header.getTotalImages() <= 0) {
            throw new BasisDecodeException("Basis container has no images");
        }
        if (header.getTotalSlices() <= 0) {
            throw new BasisDecodeException("Basis container has no slices");
        }

        try {
            Ktx2BasisTextureFormat.fromCode(header.getTextureFormat());
            header.getTextureTypeEnum();
        } catch (IllegalArgumentException exception) {
            throw new BasisDecodeException("Basis container has unsupported texture metadata", exception);
        }
    }

    private static void validateSlice(int dataLength, Ktx2BasisSliceDesc slice, int index) {
        if (slice.getImageIndex() < 0 || slice.getLevelIndex() < 0) {
            throw new BasisDecodeException("Basis slice has invalid image or level index: " + index);
        }
        int fileOffset = checkedOffset(slice.getFileOffset(), "slice data offset");
        int fileSize = checkedOffset(slice.getFileSize(), "slice data size");
        validateRange(dataLength, fileOffset, fileSize, "slice data");
    }

    private static int checkedOffset(long value, String fieldName) {
        return UnsignedFields.sizeToInt(value, fieldName);
    }

    private static int checkedMultiply(int left, int right, String fieldName) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            throw new BasisDecodeException(fieldName + " overflow", exception);
        }
    }

    private static void validateRange(int dataLength, int offset, int length, String fieldName) {
        if (offset < 0 || length < 0 || offset > dataLength || length > dataLength - offset) {
            throw new BasisDecodeException("Basis " + fieldName + " extends beyond input buffer");
        }
    }
}
