package org.ngengine.basis;

import java.util.Objects;

/**
 * Backend factory and default entrypoint.
 */
public final class BasisDecoderFactory {

    private static final String MISSING_HINT = "No decoder backend configured.";

    private BasisDecoderFactory() {
    }

    /**
     * Create the default backend.
     */
    public static BasisDecoder createDefault() {
        return new BasisuJavaDecoder();
    }

    public static BasisDecoder create(BasisDecoder decoder) {
        return Objects.requireNonNull(decoder, "decoder");
    }

    public static BasisDecoder missingBackend() {
        return new BasisDecoder() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public String getBackendName() {
                return "MissingBasisDecoder";
            }

            @Override
            public BasisDecodeResult decode(BasisDecodeRequest request) {
                throw new BasisDecodeException("No backend available. " + MISSING_HINT);
            }
        };
    }
}
