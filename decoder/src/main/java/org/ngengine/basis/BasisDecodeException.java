package org.ngengine.basis;

/**
 * Decoder-level exception. Used when payload parsing/transcoding fails.
 */
public class BasisDecodeException extends RuntimeException {

    public BasisDecodeException(String message) {
        super(message);
    }

    public BasisDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
