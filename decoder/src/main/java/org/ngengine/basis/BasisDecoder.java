package org.ngengine.basis;

/**
 * Pluggable contract for Basis Universal decoding backends.
 */
public interface BasisDecoder {

    /**
     * Returns true when the backend is available and can decode now.
     */
    boolean isAvailable();

    /**
     * Human-readable backend name.
     */
    String getBackendName();

    /**
     * Decode the request to pixel data and metadata.
     */
    BasisDecodeResult decode(BasisDecodeRequest request);
}
