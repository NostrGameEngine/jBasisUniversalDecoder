package org.ngengine.basis;

/**
 * Java mapping for the `ktx2_supercompression` enum in basisu_transcoder.h.
 */
public enum Ktx2SupercompressionScheme implements CodedEnum {

    NONE(Ktx2Constants.KTX2_SS_NONE),
    BASISLZ(Ktx2Constants.KTX2_SS_BASISLZ),
    ZSTANDARD(Ktx2Constants.KTX2_SS_ZSTANDARD),
    DEFLATE(Ktx2Constants.KTX2_SS_DEFLATE),
    UASTC_HDR_6X6I(Ktx2Constants.KTX2_SS_UASTC_HDR_6x6I),
    XUASTC_LDR(Ktx2Constants.KTX2_SS_XUASTC_LDR);

    private final int code;

    Ktx2SupercompressionScheme(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Ktx2SupercompressionScheme fromCode(int code) {
        return EnumCodeResolver.fromCode(Ktx2SupercompressionScheme.class, code,
                "Unsupported KTX2 supercompression code: ");
    }

    /**
     * Supercompression schemes whose level payloads can be materialized directly in Java.
     * BasisLZ and the custom Basis Universal schemes are handled by their texture-specific paths.
     */
    public boolean isSupportedByJavaDecoder() {
        return this == NONE || this == ZSTANDARD || this == DEFLATE;
    }
}
