package org.ngengine.basis;

/**
 * Java parity representation of {@code basisu::texture_format}.
 */
public enum Ktx2TextureFormat implements CodedEnum {
    cInvalidTextureFormat(-1),
    cETC1(0),
    cETC1S(1),
    cETC2_RGB(2),
    cETC2_RGBA(3),
    cETC2_ALPHA(4),
    cBC1(5),
    cBC3(6),
    cBC4(7),
    cBC5(8),
    cBC6HSigned(9),
    cBC6HUnsigned(10),
    cBC7(11),
    cASTC_LDR_4x4(12),
    cASTC_HDR_4x4(13),
    cASTC_HDR_6x6(14),
    cPVRTC1_4_RGB(15),
    cPVRTC1_4_RGBA(16),
    cATC_RGB(17),
    cATC_RGBA_INTERPOLATED_ALPHA(18),
    cFXT1_RGB(19),
    cPVRTC2_4_RGBA(20),
    cETC2_R11_EAC(21),
    cETC2_RG11_EAC(22),
    cUASTC4x4(23),
    cUASTC_HDR_4x4(24),
    cBC1_NV(25),
    cBC1_AMD(26),

    cRGBA32(27),
    cRGB565(28),
    cBGR565(29),
    cRGBA4444(30),
    cABGR4444(31),
    cRGBA_HALF(32),
    cRGB_HALF(33),
    cRGB_9E5(34),

    cASTC_LDR_5x4(35),
    cASTC_LDR_5x5(36),
    cASTC_LDR_6x5(37),
    cASTC_LDR_6x6(38),
    cASTC_LDR_8x5(39),
    cASTC_LDR_8x6(40),
    cASTC_LDR_10x5(41),
    cASTC_LDR_10x6(42),
    cASTC_LDR_8x8(43),
    cASTC_LDR_10x8(44),
    cASTC_LDR_10x10(45),
    cASTC_LDR_12x10(46),
    cASTC_LDR_12x12(47);

    private final int code;

    Ktx2TextureFormat(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Ktx2TextureFormat fromCode(int code) {
        return EnumCodeResolver.fromCode(Ktx2TextureFormat.class, code, "Unsupported texture_format code: ");
    }

    public String toNativeName() {
        return name();
    }
}
