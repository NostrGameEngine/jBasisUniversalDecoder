package org.ngengine.basis;

/**
 * Semantic header decoded from an XUASTC LDR payload.
 */
final class XuastcLdrImageHeader {
    private static final int MARKER = 1;
    private static final int MARKER_BITS = 5;
    private static final int BLOCK_SIZE_BITS = 4;
    private static final int DIMENSION_BITS = 16;
    private static final int DCT_QUALITY_BITS = 8;
    private static final int FULL_ZSTD_HEADER_BYTES = 85;
    private static final int HYBRID_HEADER_BYTES = 45;
    private static final int FULL_ZSTD_RAW_BITS_LENGTH_OFFSET = 1;
    private static final int HYBRID_ARITH_LENGTH_OFFSET = 1;
    private static final int[][] ASTC_BLOCK_SIZES = {
            {4, 4}, {5, 4}, {5, 5}, {6, 5},
            {6, 6}, {8, 5}, {8, 6}, {10, 5},
            {10, 6}, {8, 8}, {10, 8}, {10, 10},
            {12, 10}, {12, 12}
    };

    private final XuastcLdrSyntax syntax;
    private final int blockWidth;
    private final int blockHeight;
    private final int width;
    private final int height;
    private final boolean hasAlpha;
    private final boolean srgbDecodeProfile;
    private final boolean usesDct;
    private final float dctQuality;

    private XuastcLdrImageHeader(
            XuastcLdrSyntax syntax,
            int blockWidth,
            int blockHeight,
            int width,
            int height,
            boolean hasAlpha,
            boolean srgbDecodeProfile,
            boolean usesDct,
            float dctQuality) {
        this.syntax = syntax;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.width = width;
        this.height = height;
        this.hasAlpha = hasAlpha;
        this.srgbDecodeProfile = srgbDecodeProfile;
        this.usesDct = usesDct;
        this.dctQuality = dctQuality;
    }

    static XuastcLdrImageHeader parse(byte[] data, int offset, int length) {
        XuastcLdrPayload payload = XuastcLdrPayload.parse(data, offset, length);
        if (payload.getSyntax() == XuastcLdrSyntax.FULL_ZSTD) {
            int rawBitsLength = readIntLittleEndian(data, offset + FULL_ZSTD_RAW_BITS_LENGTH_OFFSET);
            validateRange(data, offset + FULL_ZSTD_HEADER_BYTES, rawBitsLength);
            BasisBitReader reader = new BasisBitReader(data, offset + FULL_ZSTD_HEADER_BYTES, rawBitsLength);
            return readWithBitReader(payload.getSyntax(), reader);
        }

        int arithOffset = offset + 1;
        int arithLength = length - 1;
        if (payload.getSyntax() == XuastcLdrSyntax.HYBRID_ARITH_ZSTD) {
            arithOffset = offset + HYBRID_HEADER_BYTES;
            arithLength = readIntLittleEndian(data, offset + HYBRID_ARITH_LENGTH_OFFSET);
        }
        validateRange(data, arithOffset, arithLength);
        XuastcArithmeticDecoder decoder = new XuastcArithmeticDecoder(data, arithOffset, arithLength);
        return readWithArithmeticDecoder(payload.getSyntax(), decoder);
    }

    XuastcLdrSyntax getSyntax() {
        return syntax;
    }

    int getBlockWidth() {
        return blockWidth;
    }

    int getBlockHeight() {
        return blockHeight;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    boolean hasAlpha() {
        return hasAlpha;
    }

    boolean isSrgbDecodeProfile() {
        return srgbDecodeProfile;
    }

    boolean usesDct() {
        return usesDct;
    }

    float getDctQuality() {
        return dctQuality;
    }

    static int getAstcBlockSizeCount() {
        return ASTC_BLOCK_SIZES.length;
    }

    static int findAstcBlockSizeIndex(int blockWidth, int blockHeight) {
        for (int i = 0; i < ASTC_BLOCK_SIZES.length; i++) {
            if (ASTC_BLOCK_SIZES[i][0] == blockWidth && ASTC_BLOCK_SIZES[i][1] == blockHeight) {
                return i;
            }
        }
        throw new BasisDecodeException(
                "Unsupported XUASTC ASTC block size: " + blockWidth + "x" + blockHeight);
    }

    private static XuastcLdrImageHeader readWithBitReader(
            XuastcLdrSyntax syntax,
            BasisBitReader reader) {
        int marker = reader.getBits(MARKER_BITS);
        if (marker != MARKER) {
            throw new BasisDecodeException("XUASTC image header marker mismatch");
        }
        int blockSizeIndex = reader.getBits(BLOCK_SIZE_BITS);
        boolean srgb = reader.getBits(1) != 0;
        int width = reader.getBits(DIMENSION_BITS);
        int height = reader.getBits(DIMENSION_BITS);
        boolean hasAlpha = reader.getBits(1) != 0;
        boolean usesDct = reader.getBits(1) != 0;
        int dctQuality = usesDct ? reader.getBits(DCT_QUALITY_BITS) : 0;
        return fromFields(syntax, blockSizeIndex, width, height, hasAlpha, srgb, usesDct, dctQuality);
    }

    private static XuastcLdrImageHeader readWithArithmeticDecoder(
            XuastcLdrSyntax syntax,
            XuastcArithmeticDecoder decoder) {
        int marker = decoder.getBits(MARKER_BITS);
        if (marker != MARKER) {
            throw new BasisDecodeException("XUASTC image header marker mismatch");
        }
        int blockSizeIndex = decoder.getBits(BLOCK_SIZE_BITS);
        boolean srgb = decoder.getBit() != 0;
        int width = decoder.getBits(DIMENSION_BITS);
        int height = decoder.getBits(DIMENSION_BITS);
        boolean hasAlpha = decoder.getBit() != 0;
        boolean usesDct = decoder.getBits(1) != 0;
        int dctQuality = usesDct ? decoder.getBits(DCT_QUALITY_BITS) : 0;
        return fromFields(syntax, blockSizeIndex, width, height, hasAlpha, srgb, usesDct, dctQuality);
    }

    private static XuastcLdrImageHeader fromFields(
            XuastcLdrSyntax syntax,
            int blockSizeIndex,
            int width,
            int height,
            boolean hasAlpha,
            boolean srgb,
            boolean usesDct,
            int encodedDctQuality) {
        if (blockSizeIndex < 0 || blockSizeIndex >= ASTC_BLOCK_SIZES.length) {
            throw new BasisDecodeException("Invalid XUASTC ASTC block size index");
        }
        if (width < 1 || height < 1) {
            throw new BasisDecodeException("Invalid XUASTC image dimensions");
        }
        float dctQuality = encodedDctQuality / 2.0f;
        if (usesDct && (dctQuality <= 0.0f || dctQuality > 100.0f)) {
            throw new BasisDecodeException("Invalid XUASTC DCT quality factor");
        }
        int[] blockSize = ASTC_BLOCK_SIZES[blockSizeIndex];
        return new XuastcLdrImageHeader(
                syntax,
                blockSize[0],
                blockSize[1],
                width,
                height,
                hasAlpha,
                srgb,
                usesDct,
                dctQuality);
    }

    private static int readIntLittleEndian(byte[] data, int offset) {
        validateRange(data, offset, Integer.BYTES);
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                | (Byte.toUnsignedInt(data[offset + 3]) << 24);
    }

    private static void validateRange(byte[] data, int offset, int length) {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new BasisDecodeException("XUASTC header range extends beyond input buffer");
        }
    }
}
