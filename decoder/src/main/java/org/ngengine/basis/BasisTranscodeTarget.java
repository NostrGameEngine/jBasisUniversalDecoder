package org.ngengine.basis;

/**
 * Target formats that a decoder backend may transcode to.
 */
public enum BasisTranscodeTarget {
    ASTC_HDR_4X4(BasisImageFormat.ASTC_HDR_4X4, "TextureCompressionASTC_HDR", true, 4, 4, true),
    ASTC_HDR_6X6(BasisImageFormat.ASTC_HDR_6X6, "TextureCompressionASTC_HDR", true, 6, 6, true),
    ASTC_LDR_4X4(BasisImageFormat.ASTC_LDR_4X4, "TextureCompressionASTC", true, 4, 4),
    ASTC_LDR_5X4(BasisImageFormat.ASTC_LDR_5X4, "TextureCompressionASTC", true, 5, 4),
    ASTC_LDR_5X5(BasisImageFormat.ASTC_LDR_5X5, "TextureCompressionASTC", true, 5, 5),
    ASTC_LDR_6X5(BasisImageFormat.ASTC_LDR_6X5, "TextureCompressionASTC", true, 6, 5),
    ASTC_LDR_6X6(BasisImageFormat.ASTC_LDR_6X6, "TextureCompressionASTC", true, 6, 6),
    ASTC_LDR_8X5(BasisImageFormat.ASTC_LDR_8X5, "TextureCompressionASTC", true, 8, 5),
    ASTC_LDR_8X6(BasisImageFormat.ASTC_LDR_8X6, "TextureCompressionASTC", true, 8, 6),
    ASTC_LDR_8X8(BasisImageFormat.ASTC_LDR_8X8, "TextureCompressionASTC", true, 8, 8),
    ASTC_LDR_10X5(BasisImageFormat.ASTC_LDR_10X5, "TextureCompressionASTC", true, 10, 5),
    ASTC_LDR_10X6(BasisImageFormat.ASTC_LDR_10X6, "TextureCompressionASTC", true, 10, 6),
    ASTC_LDR_10X8(BasisImageFormat.ASTC_LDR_10X8, "TextureCompressionASTC", true, 10, 8),
    ASTC_LDR_10X10(BasisImageFormat.ASTC_LDR_10X10, "TextureCompressionASTC", true, 10, 10),
    ASTC_LDR_12X10(BasisImageFormat.ASTC_LDR_12X10, "TextureCompressionASTC", true, 12, 10),
    ASTC_LDR_12X12(BasisImageFormat.ASTC_LDR_12X12, "TextureCompressionASTC", true, 12, 12),
    BC6H(BasisImageFormat.BC6H, "TextureCompressionBPTC", false, 4, 4, true),
    BC7(BasisImageFormat.BC7, "TextureCompressionBPTC", true),
    BC3(BasisImageFormat.BC3, "TextureCompressionS3TC", true),
    BC4(BasisImageFormat.BC4, "TextureCompressionRGTC", false),
    BC5(BasisImageFormat.BC5, "TextureCompressionRGTC", false),
    BC1(BasisImageFormat.BC1, "TextureCompressionS3TC", false),
    ETC2(BasisImageFormat.ETC2, "TextureCompressionETC2", true),
    ETC2_NO_ALPHA(BasisImageFormat.ETC2_NO_ALPHA, "TextureCompressionETC2", false),
    ETC2_EAC_R11(BasisImageFormat.ETC2_EAC_R11, "TextureCompressionETC2", false),
    ETC2_EAC_RG11(BasisImageFormat.ETC2_EAC_RG11, "TextureCompressionETC2", false),
    ETC1(BasisImageFormat.ETC1, "TextureCompressionETC1", false),
    RGB565(BasisImageFormat.RGB565, null, false),
    BGR565(BasisImageFormat.BGR565, null, false),
    RGBA4444(BasisImageFormat.RGBA4444, null, true),
    RGB_HALF(BasisImageFormat.RGB_HALF, null, false, 0, 0, true),
    RGBA_HALF(BasisImageFormat.RGBA_HALF, null, true, 0, 0, true),
    RGB_9E5(BasisImageFormat.RGB_9E5, null, false, 0, 0, true),
    RGBA8(BasisImageFormat.RGBA8, null, true);

    private final BasisImageFormat format;
    private final String requiredCap;
    private final boolean supportsAlpha;
    private final int blockWidth;
    private final int blockHeight;
    private final boolean hdr;

    BasisTranscodeTarget(BasisImageFormat format, String requiredCap, boolean supportsAlpha) {
        this(format, requiredCap, supportsAlpha, 0, 0);
    }

    BasisTranscodeTarget(
            BasisImageFormat format,
            String requiredCap,
            boolean supportsAlpha,
            int blockWidth,
            int blockHeight) {
        this(format, requiredCap, supportsAlpha, blockWidth, blockHeight, false);
    }

    BasisTranscodeTarget(
            BasisImageFormat format,
            String requiredCap,
            boolean supportsAlpha,
            int blockWidth,
            int blockHeight,
            boolean hdr) {
        this.format = format;
        this.requiredCap = requiredCap;
        this.supportsAlpha = supportsAlpha;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.hdr = hdr;
    }

    public BasisImageFormat getImageFormat() {
        return format;
    }

    public String getRequiredCapability() {
        return requiredCap;
    }

    public boolean supportsAlpha() {
        return supportsAlpha;
    }

    public int getBlockWidth() {
        return blockWidth;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public boolean isAstcLdr() {
        return blockWidth != 0 && blockHeight != 0 && !hdr && format.name().startsWith("ASTC_LDR");
    }

    public boolean isAstcHdr() {
        return blockWidth != 0 && blockHeight != 0 && hdr && format.name().startsWith("ASTC_HDR");
    }

    public boolean isHdr() {
        return hdr;
    }

    static BasisTranscodeTarget astcLdrForBlockSize(int blockWidth, int blockHeight) {
        for (BasisTranscodeTarget target : values()) {
            if (target.isAstcLdr()
                    && target.blockWidth == blockWidth
                    && target.blockHeight == blockHeight) {
                return target;
            }
        }
        return null;
    }
}
