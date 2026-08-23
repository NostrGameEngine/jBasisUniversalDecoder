package org.ngengine.basis;

/**
 * Java parity mapping for {@code basist::ktx2_df_channel_id} from basisu_transcoder.h.
 */
public enum Ktx2DfdChannelId implements CodedEnum {
    KTX2_DF_CHANNEL_ETC1S_RGB(0),
    KTX2_DF_CHANNEL_UASTC_DATA(0),
    KTX2_DF_CHANNEL_UASTC_RGB(0),
    KTX2_DF_CHANNEL_ETC1S_RRR(3),
    KTX2_DF_CHANNEL_ETC1S_GGG(4),
    KTX2_DF_CHANNEL_UASTC_RRR(4),
    KTX2_DF_CHANNEL_UASTC_RRRG(5),
    KTX2_DF_CHANNEL_UASTC_RG(6),
    KTX2_DF_CHANNEL_ETC1S_AAA(15),
    KTX2_DF_CHANNEL_UASTC_RGBA(3);

    private final int code;

    Ktx2DfdChannelId(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Ktx2DfdChannelId fromCode(int code) {
        return EnumCodeResolver.fromCode(Ktx2DfdChannelId.class, code,
                "Unknown KTX2 DF channel id: ");
    }
}
