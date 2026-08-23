package org.ngengine.basis;

/**
 * Minimal Zstandard frame header parser used by KTX2/XUASTC section handling.
 */
final class ZstdFrameHeader {
    static final int MAGIC = 0xFD2FB528;
    static final long UNKNOWN_CONTENT_SIZE = -1L;

    private final int headerLength;
    private final long contentSize;
    private final long windowSize;
    private final long dictionaryId;
    private final boolean contentChecksum;

    private ZstdFrameHeader(
            int headerLength,
            long contentSize,
            long windowSize,
            long dictionaryId,
            boolean contentChecksum) {
        this.headerLength = headerLength;
        this.contentSize = contentSize;
        this.windowSize = windowSize;
        this.dictionaryId = dictionaryId;
        this.contentChecksum = contentChecksum;
    }

    static ZstdFrameHeader parse(byte[] data, int offset, int length) {
        if (data == null) {
            throw new BasisDecodeException("Zstd frame data must not be null");
        }
        if (offset < 0 || length < 5 || offset > data.length || length > data.length - offset) {
            throw new BasisDecodeException("Zstd frame range extends beyond input buffer");
        }
        if (readIntLittleEndian(data, offset) != MAGIC) {
            throw new BasisDecodeException("Input is not a Zstd frame");
        }

        final int descriptor = Byte.toUnsignedInt(data[offset + 4]);
        if ((descriptor & 0x08) != 0) {
            throw new BasisDecodeException("Zstd frame descriptor has reserved bits set");
        }

        final int frameContentSizeFlag = descriptor >>> 6;
        final boolean singleSegment = (descriptor & 0x20) != 0;
        final boolean contentChecksum = (descriptor & 0x04) != 0;
        final int dictionaryIdFlag = descriptor & 0x03;
        int position = offset + 5;

        long windowSize = UNKNOWN_CONTENT_SIZE;
        if (!singleSegment) {
            requireAvailable(data, position, 1);
            int windowDescriptor = Byte.toUnsignedInt(data[position++]);
            long windowBase = 1L << (10 + (windowDescriptor >>> 3));
            windowSize = windowBase + ((windowBase >>> 3) * (windowDescriptor & 0x07));
        }

        final int dictionaryIdBytes = dictionaryIdBytes(dictionaryIdFlag);
        requireAvailable(data, position, dictionaryIdBytes);
        final long dictionaryId = readUnsignedLittleEndian(data, position, dictionaryIdBytes);
        position += dictionaryIdBytes;

        final int contentSizeBytes = contentSizeBytes(frameContentSizeFlag, singleSegment);
        requireAvailable(data, position, contentSizeBytes);
        long contentSize = contentSizeBytes == 0
                ? UNKNOWN_CONTENT_SIZE
                : readUnsignedLittleEndian(data, position, contentSizeBytes);
        if (contentSizeBytes == Short.BYTES) {
            contentSize += 256L;
        }
        position += contentSizeBytes;

        if (singleSegment) {
            windowSize = contentSize;
        }

        int headerLength = position - offset;
        if (headerLength > length) {
            throw new BasisDecodeException("Zstd frame header exceeds compressed range");
        }
        return new ZstdFrameHeader(
                headerLength,
                contentSize,
                windowSize,
                dictionaryId,
                contentChecksum);
    }

    int getHeaderLength() {
        return headerLength;
    }

    long getContentSize() {
        return contentSize;
    }

    long getWindowSize() {
        return windowSize;
    }

    long getDictionaryId() {
        return dictionaryId;
    }

    boolean hasContentChecksum() {
        return contentChecksum;
    }

    boolean hasKnownContentSize() {
        return contentSize != UNKNOWN_CONTENT_SIZE;
    }

    private static int dictionaryIdBytes(int flag) {
        switch (flag) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 4;
            default:
                throw new BasisDecodeException("Invalid Zstd dictionary ID flag");
        }
    }

    private static int contentSizeBytes(int flag, boolean singleSegment) {
        switch (flag) {
            case 0:
                return singleSegment ? 1 : 0;
            case 1:
                return 2;
            case 2:
                return 4;
            case 3:
                return 8;
            default:
                throw new BasisDecodeException("Invalid Zstd content size flag");
        }
    }

    private static int readIntLittleEndian(byte[] data, int offset) {
        requireAvailable(data, offset, Integer.BYTES);
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                | (Byte.toUnsignedInt(data[offset + 3]) << 24);
    }

    private static long readUnsignedLittleEndian(byte[] data, int offset, int byteCount) {
        long value = 0;
        for (int i = 0; i < byteCount; i++) {
            value |= (long) Byte.toUnsignedInt(data[offset + i]) << (i * 8);
        }
        return value;
    }

    private static void requireAvailable(byte[] data, int offset, int length) {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new BasisDecodeException("Zstd frame header range exceeds input buffer");
        }
    }
}
