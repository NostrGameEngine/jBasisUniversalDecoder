package org.ngengine.basis;

/**
 * XUASTC LDR compressed image syntax selector.
 */
enum XuastcLdrSyntax {
    FULL_ARITH(0),
    HYBRID_ARITH_ZSTD(1),
    FULL_ZSTD(2);

    private final int code;

    XuastcLdrSyntax(int code) {
        this.code = code;
    }

    int getCode() {
        return code;
    }

    static XuastcLdrSyntax fromCode(int code) {
        for (XuastcLdrSyntax syntax : values()) {
            if (syntax.code == code) {
                return syntax;
            }
        }
        throw new BasisDecodeException("Unsupported XUASTC LDR syntax byte: " + code);
    }
}
