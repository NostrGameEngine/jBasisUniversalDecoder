package org.ngengine.basis;

/**
 * Section table for XUASTC LDR FULL_ZSTD payloads.
 */
final class XuastcLdrFullZstdSectionTable {
    static final int RAW_BITS = 0;
    static final int MODE_BYTES = 1;
    static final int SOLID_DPCM_BYTES = 2;
    static final int ENDPOINT_DPCM_REUSE_INDICES = 3;
    static final int USE_BC_BITS = 4;
    static final int ENDPOINT_DPCM_3BIT = 5;
    static final int ENDPOINT_DPCM_4BIT = 6;
    static final int ENDPOINT_DPCM_5BIT = 7;
    static final int ENDPOINT_DPCM_6BIT = 8;
    static final int ENDPOINT_DPCM_7BIT = 9;
    static final int ENDPOINT_DPCM_8BIT = 10;
    static final int MEAN0_BITS = 11;
    static final int MEAN1_BYTES = 12;
    static final int RUN_BYTES = 13;
    static final int COEFF_BYTES = 14;
    static final int SIGN_BITS = 15;
    static final int WEIGHT2_BITS = 16;
    static final int WEIGHT3_BITS = 17;
    static final int WEIGHT4_BITS = 18;
    static final int WEIGHT8_BYTES = 19;
    static final int SECTION_COUNT = 20;

    private static final int HEADER_BYTES = 1 + (SECTION_COUNT + 1) * Integer.BYTES;

    private final Section[] sections;

    private XuastcLdrFullZstdSectionTable(Section[] sections) {
        this.sections = sections.clone();
    }

    static XuastcLdrFullZstdSectionTable parse(byte[] data, int offset, int length) {
        if (data == null) {
            throw new BasisDecodeException("XUASTC FULL_ZSTD data must not be null");
        }
        if (offset < 0 || length < HEADER_BYTES || offset > data.length
                || length > data.length - offset) {
            throw new BasisDecodeException("XUASTC FULL_ZSTD range extends beyond input buffer");
        }
        if (XuastcLdrSyntax.fromCode(Byte.toUnsignedInt(data[offset]))
                != XuastcLdrSyntax.FULL_ZSTD) {
            throw new BasisDecodeException("XUASTC payload is not FULL_ZSTD");
        }

        long[] lengths = new long[SECTION_COUNT];
        long totalLength = 0;
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = readUInt32LittleEndian(data, offset + 1 + i * Integer.BYTES);
            totalLength = checkedAdd(totalLength, lengths[i]);
        }
        if (HEADER_BYTES + totalLength > length) {
            throw new BasisDecodeException("XUASTC FULL_ZSTD sections exceed payload length");
        }

        Section[] sections = new Section[SECTION_COUNT];
        int sectionOffset = offset + HEADER_BYTES;
        for (int i = 0; i < sections.length; i++) {
            int sectionLength = UnsignedFields.sizeToInt(lengths[i], "FULL_ZSTD section length");
            boolean zstdCompressed = isZstdCompressedSection(i);
            ZstdFrameHeader zstdHeader = null;
            long decodedLength = sectionLength;
            if (zstdCompressed && sectionLength > 0) {
                zstdHeader = ZstdFrameHeader.parse(data, sectionOffset, sectionLength);
                if (!zstdHeader.hasKnownContentSize()) {
                    throw new BasisDecodeException("XUASTC FULL_ZSTD section has unknown decoded size");
                }
                decodedLength = zstdHeader.getContentSize();
            }
            sections[i] = new Section(
                    i,
                    sectionOffset,
                    sectionLength,
                    decodedLength,
                    zstdCompressed,
                    zstdHeader);
            sectionOffset += sectionLength;
        }
        return new XuastcLdrFullZstdSectionTable(sections);
    }

    int getSectionCount() {
        return sections.length;
    }

    Section getSection(int index) {
        if (index < 0 || index >= sections.length) {
            throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD section index");
        }
        return sections[index];
    }

    int getZstdCompressedSectionCount() {
        int count = 0;
        for (Section section : sections) {
            if (section.isZstdCompressed()) {
                count++;
            }
        }
        return count;
    }

    private static boolean isZstdCompressedSection(int index) {
        return index != RAW_BITS && index != SIGN_BITS;
    }

    private static long readUInt32LittleEndian(byte[] data, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(data[offset])
                        | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                        | (Byte.toUnsignedInt(data[offset + 3]) << 24));
    }

    private static long checkedAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new BasisDecodeException("XUASTC FULL_ZSTD section length overflow");
        }
        return left + right;
    }

    static final class Section {
        private final int index;
        private final int byteOffset;
        private final int byteLength;
        private final long decodedByteLength;
        private final boolean zstdCompressed;
        private final ZstdFrameHeader zstdHeader;

        private Section(
                int index,
                int byteOffset,
                int byteLength,
                long decodedByteLength,
                boolean zstdCompressed,
                ZstdFrameHeader zstdHeader) {
            this.index = index;
            this.byteOffset = byteOffset;
            this.byteLength = byteLength;
            this.decodedByteLength = decodedByteLength;
            this.zstdCompressed = zstdCompressed;
            this.zstdHeader = zstdHeader;
        }

        int getIndex() {
            return index;
        }

        int getByteOffset() {
            return byteOffset;
        }

        int getByteLength() {
            return byteLength;
        }

        long getDecodedByteLength() {
            return decodedByteLength;
        }

        boolean isZstdCompressed() {
            return zstdCompressed;
        }

        ZstdFrameHeader getZstdHeader() {
            return zstdHeader;
        }
    }
}
