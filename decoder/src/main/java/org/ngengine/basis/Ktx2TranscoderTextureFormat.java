package org.ngengine.basis;

import java.util.Locale;

/**
 * Java parity mapping for {@code basist::transcoder_texture_format}.
 */
public enum Ktx2TranscoderTextureFormat {
    cTFETC1_RGB(0, false, false, 4, 4, 8, false),
    cTFETC2_RGBA(1, true, false, 4, 4, 8, false),
    cTFBC1_RGB(2, false, false, 4, 4, 8, false),
    cTFBC3_RGBA(3, true, false, 4, 4, 16, false),
    cTFBC4_R(4, false, false, 4, 4, 8, false),
    cTFBC5_RG(5, false, false, 4, 4, 16, false),
    cTFBC7_RGBA(6, true, false, 4, 4, 16, false),
    cTFBC7_ALT(7, true, false, 4, 4, 16, true),
    cTFPVRTC1_4_RGB(8, false, false, 4, 4, 8, false),
    cTFPVRTC1_4_RGBA(9, true, false, 4, 4, 8, false),
    cTFASTC_LDR_4x4_RGBA(10, true, false, 4, 4, 16, false),
    cTFATC_RGB(11, false, false, 4, 4, 8, false),
    cTFATC_RGBA(12, true, false, 4, 4, 16, false),
    cTFFXT1_RGB(17, false, false, 8, 4, 8, false),
    cTFPVRTC2_4_RGB(18, false, false, 4, 4, 8, false),
    cTFPVRTC2_4_RGBA(19, true, false, 4, 4, 8, false),
    cTFETC2_EAC_R11(20, false, false, 4, 4, 8, false),
    cTFETC2_EAC_RG11(21, false, false, 4, 4, 8, false),
    cTFBC6H(22, false, true, 4, 4, 16, false),
    cTFASTC_HDR_4x4_RGBA(23, true, true, 4, 4, 16, false),
    cTFRGBA32(13, true, false, 1, 1, 4, false),
    cTFRGB565(14, false, false, 1, 1, 2, false),
    cTFBGR565(15, false, false, 1, 1, 2, false),
    cTFRGBA4444(16, true, false, 1, 1, 2, false),
    cTFRGB_HALF(24, false, true, 1, 1, 6, false),
    cTFRGBA_HALF(25, true, true, 1, 1, 8, false),
    cTFRGB_9E5(26, false, true, 1, 1, 4, false),
    cTFASTC_HDR_6x6_RGBA(27, true, true, 6, 6, 16, false),
    cTFASTC_LDR_5x4_RGBA(28, true, false, 5, 4, 16, false),
    cTFASTC_LDR_5x5_RGBA(29, true, false, 5, 5, 16, false),
    cTFASTC_LDR_6x5_RGBA(30, true, false, 6, 5, 16, false),
    cTFASTC_LDR_6x6_RGBA(31, true, false, 6, 6, 16, false),
    cTFASTC_LDR_8x5_RGBA(32, true, false, 8, 5, 16, false),
    cTFASTC_LDR_8x6_RGBA(33, true, false, 8, 6, 16, false),
    cTFASTC_LDR_10x5_RGBA(34, true, false, 10, 5, 16, false),
    cTFASTC_LDR_10x6_RGBA(35, true, false, 10, 6, 16, false),
    cTFASTC_LDR_8x8_RGBA(36, true, false, 8, 8, 16, false),
    cTFASTC_LDR_10x8_RGBA(37, true, false, 10, 8, 16, false),
    cTFASTC_LDR_10x10_RGBA(38, true, false, 10, 10, 16, false),
    cTFASTC_LDR_12x10_RGBA(39, true, false, 12, 10, 16, false),
    cTFASTC_LDR_12x12_RGBA(40, true, false, 12, 12, 16, false);

    public static final Ktx2TranscoderTextureFormat cTFPVRTC1 = cTFPVRTC1_4_RGB;
    public static final Ktx2TranscoderTextureFormat cTFPVRTC2 = cTFPVRTC2_4_RGB;
    public static final Ktx2TranscoderTextureFormat cTFATC = cTFATC_RGB;
    public static final Ktx2TranscoderTextureFormat cTFASTC_4x4 = cTFASTC_LDR_4x4_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFASTC_4x4_RGBA = cTFASTC_LDR_4x4_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFASTC_HDR_4x4 = cTFASTC_HDR_4x4_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFASTC_HDR_6x6 = cTFASTC_HDR_6x6_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFBC1 = cTFBC1_RGB;
    public static final Ktx2TranscoderTextureFormat cTFBC3 = cTFBC3_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFBC4 = cTFBC4_R;
    public static final Ktx2TranscoderTextureFormat cTFBC5 = cTFBC5_RG;
    public static final Ktx2TranscoderTextureFormat cTFATC_RGBA_INTERPOLATED_ALPHA = cTFATC_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFRGB = cTFRGB565;
    public static final Ktx2TranscoderTextureFormat cTFRGB32 = cTFRGBA32;

    public static final Ktx2TranscoderTextureFormat cTFETC1 = cTFETC1_RGB;
    public static final Ktx2TranscoderTextureFormat cTFETC2 = cTFETC2_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFBC7_M6_RGB = cTFBC7_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFBC7_M5_RGBA = cTFBC7_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFBC7_M6_OPAQUE_ONLY = cTFBC7_RGBA;
    public static final Ktx2TranscoderTextureFormat cTFBC7_M5 = cTFBC7_RGBA;

    private final int code;
    private final boolean hasAlpha;
    private final boolean hdr;
    private final int blockWidth;
    private final int blockHeight;
    private final int bytesPerBlockOrPixel;
    private final boolean alias;

    private static final int MAX_CODE = 40;
    private static final Ktx2TranscoderTextureFormat[] TABLE = new Ktx2TranscoderTextureFormat[MAX_CODE + 1];

    static {
        for (Ktx2TranscoderTextureFormat value : values()) {
            int idx = value.code;
            if (!value.alias && idx >= 0 && idx < TABLE.length && TABLE[idx] == null) {
                TABLE[idx] = value;
            }
        }
    }

    Ktx2TranscoderTextureFormat(int code,
                                boolean hasAlpha,
                                boolean hdr,
                                int blockWidth,
                                int blockHeight,
                                int bytesPerBlockOrPixel,
                                boolean alias) {
        this.code = code;
        this.hasAlpha = hasAlpha;
        this.hdr = hdr;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.bytesPerBlockOrPixel = bytesPerBlockOrPixel;
        this.alias = alias;
    }

    public int getCode() {
        return code;
    }

    public boolean isHasAlpha() {
        return hasAlpha;
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

    public int getBytesPerBlockOrPixel() {
        return bytesPerBlockOrPixel;
    }

    public int getUncompressedBytesPerPixel() {
        return isUncompressed() ? bytesPerBlockOrPixel : 0;
    }

    public boolean isUncompressed() {
        return blockWidth == 1 && blockHeight == 1;
    }

    public boolean isAstc() {
        return name().contains("ASTC");
    }

    public static Ktx2TranscoderTextureFormat fromCode(int code) {
        if (code < 0 || code >= TABLE.length || TABLE[code] == null) {
            throw new IllegalArgumentException("Unknown format code: " + code);
        }
        return TABLE[code];
    }

    public String toNativeName() {
        switch (this) {
            case cTFETC1_RGB:
                return "ETC1_RGB";
            case cTFBC1_RGB:
                return "BC1_RGB";
            case cTFBC4_R:
                return "BC4_R";
            case cTFPVRTC1_4_RGB:
                return "PVRTC1_4_RGB";
            case cTFPVRTC1_4_RGBA:
                return "PVRTC1_4_RGBA";
            case cTFBC7_RGBA:
            case cTFBC7_ALT:
                return "BC7_RGBA";
            case cTFETC2_RGBA:
                return "ETC2_RGBA";
            case cTFBC3_RGBA:
                return "BC3_RGBA";
            case cTFBC5_RG:
                return "BC5_RG";
            case cTFASTC_LDR_4x4_RGBA:
                return "ASTC_LDR_4X4_RGBA";
            case cTFATC_RGB:
                return "ATC_RGB";
            case cTFATC_RGBA:
                return "ATC_RGBA";
            case cTFRGBA32:
                return "RGBA32";
            case cTFRGB565:
                return "RGB565";
            case cTFBGR565:
                return "BGR565";
            case cTFRGBA4444:
                return "RGBA4444";
            case cTFRGB_HALF:
                return "RGB_HALF";
            case cTFRGBA_HALF:
                return "RGBA_HALF";
            case cTFRGB_9E5:
                return "RGB_9E5";
            case cTFFXT1_RGB:
                return "FXT1_RGB";
            case cTFPVRTC2_4_RGB:
                return "PVRTC2_4_RGB";
            case cTFPVRTC2_4_RGBA:
                return "PVRTC2_4_RGBA";
            case cTFETC2_EAC_R11:
                return "ETC2_EAC_R11";
            case cTFETC2_EAC_RG11:
                return "ETC2_EAC_RG11";
            case cTFBC6H:
                return "BC6H";
            case cTFASTC_HDR_4x4_RGBA:
                return "ASTC_HDR_4X4_RGBA";
            case cTFASTC_HDR_6x6_RGBA:
                return "ASTC_HDR_6X6_RGBA";
            case cTFASTC_LDR_5x4_RGBA:
                return "ASTC_LDR_5X4_RGBA";
            case cTFASTC_LDR_5x5_RGBA:
                return "ASTC_LDR_5X5_RGBA";
            case cTFASTC_LDR_6x5_RGBA:
                return "ASTC_LDR_6X5_RGBA";
            case cTFASTC_LDR_6x6_RGBA:
                return "ASTC_LDR_6X6_RGBA";
            case cTFASTC_LDR_8x5_RGBA:
                return "ASTC_LDR_8X5_RGBA";
            case cTFASTC_LDR_8x6_RGBA:
                return "ASTC_LDR_8X6_RGBA";
            case cTFASTC_LDR_10x5_RGBA:
                return "ASTC_LDR_10X5_RGBA";
            case cTFASTC_LDR_10x6_RGBA:
                return "ASTC_LDR_10X6_RGBA";
            case cTFASTC_LDR_8x8_RGBA:
                return "ASTC_LDR_8X8_RGBA";
            case cTFASTC_LDR_10x8_RGBA:
                return "ASTC_LDR_10X8_RGBA";
            case cTFASTC_LDR_10x10_RGBA:
                return "ASTC_LDR_10X10_RGBA";
            case cTFASTC_LDR_12x10_RGBA:
                return "ASTC_LDR_12X10_RGBA";
            case cTFASTC_LDR_12x12_RGBA:
                return "ASTC_LDR_12X12_RGBA";
            default:
                return name();
        }
    }

    public static String toNativeName(int code) {
        return fromCode(code).toNativeName();
    }

    public String toJavaName() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
