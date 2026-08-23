package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;

/**
 * Java parity representation of {@code basist::basis_file_header}.
 */
public final class Ktx2BasisFileHeader {
    public static final int SIZE_BYTES = 77;

    private final int signature;
    private final int version;
    private final int headerSize;
    private final int headerCrc16;
    private final long dataSize;
    private final int dataCrc16;
    private final int totalSlices;
    private final int totalImages;
    private final int textureFormat;
    private final int flags;
    private final int textureType;
    private final int usPerFrame;
    private final long reserved;
    private final long userData0;
    private final long userData1;
    private final long totalEndpoints;
    private final long endpointCodebookFileOffset;
    private final long endpointCodebookFileSize;
    private final long totalSelectors;
    private final long selectorCodebookFileOffset;
    private final long selectorCodebookFileSize;
    private final long tablesFileOffset;
    private final long tablesFileSize;
    private final long sliceDescFileOffset;
    private final long extendedDataOffset;
    private final long extendedDataSize;

    public Ktx2BasisFileHeader(int signature,
                               int version,
                               int headerSize,
                               int headerCrc16,
                               long dataSize,
                               int dataCrc16,
                               int totalSlices,
                               int totalImages,
                               int textureFormat,
                               int flags,
                               int textureType,
                               int usPerFrame,
                               long reserved,
                               long userData0,
                               long userData1,
                               long totalEndpoints,
                               long endpointCodebookFileOffset,
                               long endpointCodebookFileSize,
                               long totalSelectors,
                               long selectorCodebookFileOffset,
                               long selectorCodebookFileSize,
                               long tablesFileOffset,
                               long tablesFileSize,
                               long sliceDescFileOffset,
                               long extendedDataOffset,
                               long extendedDataSize) {
        this.signature = signature;
        this.version = version;
        this.headerSize = headerSize;
        this.headerCrc16 = headerCrc16;
        this.dataSize = dataSize;
        this.dataCrc16 = dataCrc16;
        this.totalSlices = totalSlices;
        this.totalImages = totalImages;
        this.textureFormat = textureFormat;
        this.flags = flags;
        this.textureType = textureType;
        this.usPerFrame = usPerFrame;
        this.reserved = reserved;
        this.userData0 = userData0;
        this.userData1 = userData1;
        this.totalEndpoints = totalEndpoints;
        this.endpointCodebookFileOffset = endpointCodebookFileOffset;
        this.endpointCodebookFileSize = endpointCodebookFileSize;
        this.totalSelectors = totalSelectors;
        this.selectorCodebookFileOffset = selectorCodebookFileOffset;
        this.selectorCodebookFileSize = selectorCodebookFileSize;
        this.tablesFileOffset = tablesFileOffset;
        this.tablesFileSize = tablesFileSize;
        this.sliceDescFileOffset = sliceDescFileOffset;
        this.extendedDataOffset = extendedDataOffset;
        this.extendedDataSize = extendedDataSize;
    }

    public int getSignature() {
        return signature;
    }

    public int getVersion() {
        return version;
    }

    public int getHeaderSize() {
        return headerSize;
    }

    public int getHeaderCrc16() {
        return headerCrc16;
    }

    public long getDataSize() {
        return dataSize;
    }

    public int getDataCrc16() {
        return dataCrc16;
    }

    public int getTotalSlices() {
        return totalSlices;
    }

    public int getTotalImages() {
        return totalImages;
    }

    public int getTextureFormat() {
        return textureFormat;
    }

    public int getFlags() {
        return flags;
    }

    public int getTextureType() {
        return textureType;
    }

    public int getUsPerFrame() {
        return usPerFrame;
    }

    public long getReserved() {
        return reserved;
    }

    public long getUserData0() {
        return userData0;
    }

    public long getUserData1() {
        return userData1;
    }

    public long getTotalEndpoints() {
        return totalEndpoints;
    }

    public long getEndpointCodebookFileOffset() {
        return endpointCodebookFileOffset;
    }

    public long getEndpointCodebookFileSize() {
        return endpointCodebookFileSize;
    }

    public long getTotalSelectors() {
        return totalSelectors;
    }

    public long getSelectorCodebookFileOffset() {
        return selectorCodebookFileOffset;
    }

    public long getSelectorCodebookFileSize() {
        return selectorCodebookFileSize;
    }

    public long getTablesFileOffset() {
        return tablesFileOffset;
    }

    public long getTablesFileSize() {
        return tablesFileSize;
    }

    public long getSliceDescFileOffset() {
        return sliceDescFileOffset;
    }

    public long getExtendedDataOffset() {
        return extendedDataOffset;
    }

    public long getExtendedDataSize() {
        return extendedDataSize;
    }

    public static Ktx2BasisFileHeader parse(ByteBuffer buffer, int offset) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative: " + offset);
        }
        int available = buffer.limit() - offset;
        if (available < SIZE_BYTES) {
            throw new IllegalArgumentException("basis_file_header requires at least " + SIZE_BYTES
                    + " bytes at offset " + offset + " but only " + available + " available");
        }

        ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        view.position(offset);

        int sig = read16(view);
        int ver = read16(view);
        int headerSize = read16(view);
        int headerCrc16 = read16(view);
        long dataSize = readUInt32(view);
        int dataCrc16 = read16(view);

        int totalSlices = read24(view);
        int totalImages = read24(view);
        int textureFormat = readUInt8(view);
        int flags = read16(view);
        int textureType = readUInt8(view);
        int usPerFrame = read24(view);
        long reserved = readUInt32(view);
        long userData0 = readUInt32(view);
        long userData1 = readUInt32(view);
        long endpointCount = read16(view);
        long endpointCodebookOffset = readUInt32(view);
        long endpointCodebookSize = read24(view);
        long selectorCount = read16(view);
        long selectorCodebookOffset = readUInt32(view);
        long selectorCodebookSize = read24(view);
        long tablesOffset = readUInt32(view);
        long tablesSize = readUInt32(view);
        long sliceDescOffset = readUInt32(view);
        long extendedOffset = readUInt32(view);
        long extendedSize = readUInt32(view);

        return new Ktx2BasisFileHeader(
                sig,
                ver,
                headerSize,
                headerCrc16,
                dataSize,
                dataCrc16,
                totalSlices,
                totalImages,
                textureFormat,
                flags,
                textureType,
                usPerFrame,
                reserved,
                userData0,
                userData1,
                endpointCount,
                endpointCodebookOffset,
                endpointCodebookSize,
                selectorCount,
                selectorCodebookOffset,
                selectorCodebookSize,
                tablesOffset,
                tablesSize,
                sliceDescOffset,
                extendedOffset,
                extendedSize);
    }

    public EnumSet<Ktx2BasisHeaderFlag> getHeaderFlags() {
        return Ktx2BasisHeaderFlag.fromMask(flags);
    }

    public Ktx2BasisTextureType getTextureTypeEnum() {
        return Ktx2BasisTextureType.fromCode(textureType);
    }

    private static int readUInt8(ByteBuffer buffer) {
        return buffer.get() & 0xFF;
    }

    private static int read16(ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    private static int read24(ByteBuffer buffer) {
        return (buffer.get() & 0xFF)
                | ((buffer.get() & 0xFF) << 8)
                | ((buffer.get() & 0xFF) << 16);
    }

    private static long readUInt32(ByteBuffer buffer) {
        return buffer.getInt() & 0xFFFFFFFFL;
    }
}