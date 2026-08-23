package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Parsed KTX2 supercompression global data for Basis Universal payloads.
 */
public final class Ktx2SupercompressionGlobalData {
    private static final int ETC1S_HEADER_BYTES = 20;
    private static final int ETC1S_IMAGE_DESC_BYTES = 20;
    private static final int SLICE_DESC_ORIG_BYTES = 8;
    private static final int SLICE_DESC_STD_BYTES = 12;

    private final Ktx2Etc1sGlobalData etc1sGlobalData;
    private final Ktx2SliceRange[] sliceRanges;

    private Ktx2SupercompressionGlobalData(
            Ktx2Etc1sGlobalData etc1sGlobalData,
            Ktx2SliceRange[] sliceRanges) {
        this.etc1sGlobalData = etc1sGlobalData;
        this.sliceRanges = Arrays.copyOf(sliceRanges, sliceRanges.length);
    }

    public static Ktx2SupercompressionGlobalData parse(
            byte[] data,
            Ktx2Header header,
            Ktx2BasisTextureFormat basisTextureFormat) {
        long sgdLength = header.getSgdByteLength();
        if (sgdLength == 0) {
            if (header.getSgdByteOffset() != 0) {
                throw new BasisDecodeException("KTX2 SGD offset must be zero when SGD length is zero");
            }
            return null;
        }

        int offset = UnsignedFields.sizeToInt(header.getSgdByteOffset(), "SGD byte offset");
        int length = UnsignedFields.sizeToInt(sgdLength, "SGD byte length");
        validateRange(data.length, offset, length, "SGD");

        int imageCount = imageCount(header);
        if (basisTextureFormat == Ktx2BasisTextureFormat.cETC1S) {
            return new Ktx2SupercompressionGlobalData(
                    parseEtc1s(data, offset, length, imageCount),
                    new Ktx2SliceRange[0]);
        }

        return new Ktx2SupercompressionGlobalData(
                null,
                parseSliceRanges(data, offset, length, imageCount));
    }

    public Ktx2Etc1sGlobalData getEtc1sGlobalData() {
        return etc1sGlobalData;
    }

    public Ktx2SliceRange[] getSliceRanges() {
        return Arrays.copyOf(sliceRanges, sliceRanges.length);
    }

    private static Ktx2Etc1sGlobalData parseEtc1s(byte[] data, int offset, int length, int imageCount) {
        int imageDescBytes = Math.multiplyExact(imageCount, ETC1S_IMAGE_DESC_BYTES);
        if (length < ETC1S_HEADER_BYTES + imageDescBytes) {
            throw new BasisDecodeException("KTX2 ETC1S SGD is too small");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        final int endpointCount = Short.toUnsignedInt(buffer.getShort());
        final int selectorCount = Short.toUnsignedInt(buffer.getShort());
        final long endpointsByteLength = readUInt32(buffer);
        final long selectorsByteLength = readUInt32(buffer);
        final long tablesByteLength = readUInt32(buffer);
        final long extendedByteLength = readUInt32(buffer);

        if (endpointCount == 0 || selectorCount == 0) {
            throw new BasisDecodeException("KTX2 ETC1S SGD has empty endpoint or selector palettes");
        }
        if (endpointsByteLength == 0 || selectorsByteLength == 0 || tablesByteLength == 0) {
            throw new BasisDecodeException("KTX2 ETC1S SGD has empty codebook sections");
        }

        Ktx2Etc1sImageDesc[] imageDescriptors = new Ktx2Etc1sImageDesc[imageCount];
        for (int i = 0; i < imageDescriptors.length; i++) {
            imageDescriptors[i] = readImageDesc(buffer);
            if (imageDescriptors[i].getRgbSliceByteLength() == 0) {
                throw new BasisDecodeException("KTX2 ETC1S image descriptor has empty RGB slice");
            }
        }

        long codebookOffset = ETC1S_HEADER_BYTES + imageDescBytes;
        long selectorsOffset = checkedAdd(codebookOffset, endpointsByteLength);
        long tablesOffset = checkedAdd(selectorsOffset, selectorsByteLength);
        long extendedOffset = checkedAdd(tablesOffset, tablesByteLength);
        long totalLength = checkedAdd(extendedOffset, extendedByteLength);
        if (totalLength > length) {
            throw new BasisDecodeException("KTX2 ETC1S SGD codebook sections exceed metadata range");
        }

        return new Ktx2Etc1sGlobalData(
                endpointCount,
                selectorCount,
                endpointsByteLength,
                selectorsByteLength,
                tablesByteLength,
                extendedByteLength,
                offset + codebookOffset,
                offset + selectorsOffset,
                offset + tablesOffset,
                offset + extendedOffset,
                imageDescriptors);
    }

    private static Ktx2SliceRange[] parseSliceRanges(byte[] data, int offset, int length, int imageCount) {
        int entrySize;
        if (length == imageCount * SLICE_DESC_STD_BYTES) {
            entrySize = SLICE_DESC_STD_BYTES;
        } else if (length == imageCount * SLICE_DESC_ORIG_BYTES) {
            entrySize = SLICE_DESC_ORIG_BYTES;
        } else {
            throw new BasisDecodeException("KTX2 slice SGD length does not match image count");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        Ktx2SliceRange[] ranges = new Ktx2SliceRange[imageCount];
        for (int i = 0; i < ranges.length; i++) {
            long sliceOffset = readUInt32(buffer);
            long sliceLength = readUInt32(buffer);
            long profile = entrySize == SLICE_DESC_STD_BYTES ? readUInt32(buffer) : 0;
            if (sliceLength == 0) {
                throw new BasisDecodeException("KTX2 slice SGD has empty slice");
            }
            ranges[i] = new Ktx2SliceRange(sliceOffset, sliceLength, profile);
        }
        return ranges;
    }

    private static Ktx2Etc1sImageDesc readImageDesc(ByteBuffer buffer) {
        return new Ktx2Etc1sImageDesc(
                readUInt32(buffer),
                readUInt32(buffer),
                readUInt32(buffer),
                readUInt32(buffer),
                readUInt32(buffer));
    }

    private static long readUInt32(ByteBuffer buffer) {
        return Integer.toUnsignedLong(buffer.getInt());
    }

    private static int imageCount(Ktx2Header header) {
        int layers = Math.max(header.getLayerCount(), 1);
        int faces = header.getFaceCount();
        int levels = header.getLevelCount();
        return Math.multiplyExact(Math.multiplyExact(layers, faces), levels);
    }

    private static void validateRange(int dataLength, int offset, int length, String fieldName) {
        if (offset < Ktx2Constants.KTX2_HEADER_SIZE || offset > dataLength || length > dataLength - offset) {
            throw new BasisDecodeException("KTX2 " + fieldName + " range extends beyond input buffer");
        }
    }

    private static long checkedAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new BasisDecodeException("KTX2 SGD offset/length arithmetic overflow");
        }
        return left + right;
    }
}
