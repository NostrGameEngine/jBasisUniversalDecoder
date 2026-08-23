package org.ngengine.basis;

/**
 * Java parity mapping for {@code basist::ktx2_df_color_primaries} from basisu_transcoder.h.
 */
public enum Ktx2DfdColorPrimaries implements CodedEnum {
    KTX2_DF_PRIMARIES_UNSPECIFIED(0),
    KTX2_DF_PRIMARIES_BT709(1),
    KTX2_DF_PRIMARIES_SRGB(1),
    KTX2_DF_PRIMARIES_BT601_EBU(2),
    KTX2_DF_PRIMARIES_BT601_SMPTE(3),
    KTX2_DF_PRIMARIES_BT2020(4),
    KTX2_DF_PRIMARIES_CIEXYZ(5),
    KTX2_DF_PRIMARIES_ACES(6),
    KTX2_DF_PRIMARIES_ACESCC(7),
    KTX2_DF_PRIMARIES_NTSC1953(8),
    KTX2_DF_PRIMARIES_PAL525(9),
    KTX2_DF_PRIMARIES_DISPLAYP3(10),
    KTX2_DF_PRIMARIES_ADOBERGB(11);

    private final int code;

    Ktx2DfdColorPrimaries(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Ktx2DfdColorPrimaries fromCode(int code) {
        return EnumCodeResolver.fromCode(Ktx2DfdColorPrimaries.class, code,
                "Unknown KTX2 DF color primaries: ");
    }
}
