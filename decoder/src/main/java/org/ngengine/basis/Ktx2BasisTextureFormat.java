package org.ngengine.basis;

/**
 * Java parity mapping for {@code basist::basis_tex_format}.
 */
public enum Ktx2BasisTextureFormat {
    cETC1S(0, false, 4, 4),
    cUASTC_LDR_4x4(1, false, 4, 4),
    cUASTC_HDR_4x4(2, true, 4, 4),
    cASTC_HDR_6x6(3, true, 6, 6),
    cUASTC_HDR_6x6_INTERMEDIATE(4, true, 6, 6),
    cXUASTC_LDR_4x4(5, false, 4, 4),
    cXUASTC_LDR_5x4(6, false, 5, 4),
    cXUASTC_LDR_5x5(7, false, 5, 5),
    cXUASTC_LDR_6x5(8, false, 6, 5),
    cXUASTC_LDR_6x6(9, false, 6, 6),
    cXUASTC_LDR_8x5(10, false, 8, 5),
    cXUASTC_LDR_8x6(11, false, 8, 6),
    cXUASTC_LDR_10x5(12, false, 10, 5),
    cXUASTC_LDR_10x6(13, false, 10, 6),
    cXUASTC_LDR_8x8(14, false, 8, 8),
    cXUASTC_LDR_10x8(15, false, 10, 8),
    cXUASTC_LDR_10x10(16, false, 10, 10),
    cXUASTC_LDR_12x10(17, false, 12, 10),
    cXUASTC_LDR_12x12(18, false, 12, 12),
    cASTC_LDR_4x4(19, false, 4, 4),
    cASTC_LDR_5x4(20, false, 5, 4),
    cASTC_LDR_5x5(21, false, 5, 5),
    cASTC_LDR_6x5(22, false, 6, 5),
    cASTC_LDR_6x6(23, false, 6, 6),
    cASTC_LDR_8x5(24, false, 8, 5),
    cASTC_LDR_8x6(25, false, 8, 6),
    cASTC_LDR_10x5(26, false, 10, 5),
    cASTC_LDR_10x6(27, false, 10, 6),
    cASTC_LDR_8x8(28, false, 8, 8),
    cASTC_LDR_10x8(29, false, 10, 8),
    cASTC_LDR_10x10(30, false, 10, 10),
    cASTC_LDR_12x10(31, false, 12, 10),
    cASTC_LDR_12x12(32, false, 12, 12);

    public static final Ktx2BasisTextureFormat cUASTC_HDR_6x6 = cUASTC_HDR_6x6_INTERMEDIATE;

    private final int code;
    private final boolean hdr;
    private final int blockWidth;
    private final int blockHeight;

    private static final int MAX_CODE = 32;
    private static final Ktx2BasisTextureFormat[] TABLE = new Ktx2BasisTextureFormat[MAX_CODE + 1];

    static {
        for (Ktx2BasisTextureFormat value : values()) {
            TABLE[value.code] = value;
        }
    }

    Ktx2BasisTextureFormat(int code, boolean hdr, int blockWidth, int blockHeight) {
        this.code = code;
        this.hdr = hdr;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
    }

    public int getCode() {
        return code;
    }

    public boolean isHdr() {
        return hdr;
    }

    public boolean isLdr() {
        return !hdr;
    }

    public int getBlockWidth() {
        return blockWidth;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public boolean isXUastcLdr() {
        return code >= cXUASTC_LDR_4x4.code && code <= cXUASTC_LDR_12x12.code;
    }

    public boolean isAstcLdr() {
        return code >= cASTC_LDR_4x4.code && code <= cASTC_LDR_12x12.code;
    }

    public static Ktx2BasisTextureFormat astcLdrFromBlockSize(int blockWidth, int blockHeight) {
        return fromBlockSize(blockWidth, blockHeight, true);
    }

    public static Ktx2BasisTextureFormat xuastcLdrFromBlockSize(int blockWidth, int blockHeight) {
        return fromBlockSize(blockWidth, blockHeight, false);
    }

    public String toNativeName() {
        switch (this) {
            case cETC1S:
                return "ETC1S";
            case cUASTC_LDR_4x4:
                return "UASTC LDR 4x4";
            case cUASTC_HDR_4x4:
                return "UASTC_HDR_4x4";
            case cASTC_HDR_6x6:
                return "ASTC_HDR_6x6";
            case cUASTC_HDR_6x6_INTERMEDIATE:
                return "UASTC_HDR_6x6";
            case cXUASTC_LDR_4x4:
                return "XUASTC LDR 4x4";
            case cXUASTC_LDR_5x4:
                return "XUASTC LDR 5x4";
            case cXUASTC_LDR_5x5:
                return "XUASTC LDR 5x5";
            case cXUASTC_LDR_6x5:
                return "XUASTC LDR 6x5";
            case cXUASTC_LDR_6x6:
                return "XUASTC LDR 6x6";
            case cXUASTC_LDR_8x5:
                return "XUASTC LDR 8x5";
            case cXUASTC_LDR_8x6:
                return "XUASTC LDR 8x6";
            case cXUASTC_LDR_10x5:
                return "XUASTC LDR 10x5";
            case cXUASTC_LDR_10x6:
                return "XUASTC LDR 10x6";
            case cXUASTC_LDR_8x8:
                return "XUASTC LDR 8x8";
            case cXUASTC_LDR_10x8:
                return "XUASTC LDR 10x8";
            case cXUASTC_LDR_10x10:
                return "XUASTC LDR 10x10";
            case cXUASTC_LDR_12x10:
                return "XUASTC LDR 12x10";
            case cXUASTC_LDR_12x12:
                return "XUASTC LDR 12x12";
            case cASTC_LDR_4x4:
                return "ASTC LDR 4x4";
            case cASTC_LDR_5x4:
                return "ASTC LDR 5x4";
            case cASTC_LDR_5x5:
                return "ASTC LDR 5x5";
            case cASTC_LDR_6x5:
                return "ASTC LDR 6x5";
            case cASTC_LDR_6x6:
                return "ASTC LDR 6x6";
            case cASTC_LDR_8x5:
                return "ASTC LDR 8x5";
            case cASTC_LDR_8x6:
                return "ASTC LDR 8x6";
            case cASTC_LDR_10x5:
                return "ASTC LDR 10x5";
            case cASTC_LDR_10x6:
                return "ASTC LDR 10x6";
            case cASTC_LDR_8x8:
                return "ASTC LDR 8x8";
            case cASTC_LDR_10x8:
                return "ASTC LDR 10x8";
            case cASTC_LDR_10x10:
                return "ASTC LDR 10x10";
            case cASTC_LDR_12x10:
                return "ASTC LDR 12x10";
            case cASTC_LDR_12x12:
                return "ASTC LDR 12x12";
            default:
                return name();
        }
    }

    public static Ktx2BasisTextureFormat fromCode(int code) {
        if (code < 0 || code >= TABLE.length || TABLE[code] == null) {
            throw new IllegalArgumentException("Unknown basis_tex_format code: " + code);
        }
        return TABLE[code];
    }

    private static Ktx2BasisTextureFormat fromBlockSize(int blockWidth, int blockHeight, boolean astc) {
        for (Ktx2BasisTextureFormat value : values()) {
            if (value.blockWidth == blockWidth && value.blockHeight == blockHeight
                    && (astc ? value.isAstcLdr() : value.isXUastcLdr())) {
                return value;
            }
        }
        String family = astc ? "ASTC LDR" : "XUASTC LDR";
        throw new BasisDecodeException(
                "Unsupported " + family + " block size " + blockWidth + "x" + blockHeight);
    }
}
