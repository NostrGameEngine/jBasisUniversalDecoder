package org.ngengine.basis;

/**
 * XUASTC LDR arithmetic block mode.
 */
enum XuastcLdrMode {
    SOLID(0),
    RAW(1),
    REUSE_CFG_ENDPOINTS_LEFT(2),
    REUSE_CFG_ENDPOINTS_UP(3),
    REUSE_CFG_ENDPOINTS_DIAG(4),
    RUN(5);

    private final int code;

    XuastcLdrMode(int code) {
        this.code = code;
    }

    int getCode() {
        return code;
    }

    static XuastcLdrMode fromCode(int code) {
        for (XuastcLdrMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new BasisDecodeException("Unsupported XUASTC LDR mode index: " + code);
    }
}
