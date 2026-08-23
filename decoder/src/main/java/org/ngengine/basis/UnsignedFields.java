package org.ngengine.basis;

/**
 * Bounds-checked conversions for unsigned integer fields encoded in texture containers.
 */
public final class UnsignedFields {

    private UnsignedFields() {
    }

    public static int uint32ToInt(int rawValue, String fieldName) {
        long unsignedValue = Integer.toUnsignedLong(rawValue);
        if (unsignedValue > Integer.MAX_VALUE) {
            throw new BasisDecodeException(
                    "Unsigned 32-bit field cannot be represented as Java int without overflow: "
                            + fieldName + "=" + unsignedValue);
        }
        return (int) unsignedValue;
    }

    public static long uint32ToLong(int rawValue) {
        return Integer.toUnsignedLong(rawValue);
    }

    public static int uint16ToInt(int rawValue) {
        return rawValue & 0xFFFF;
    }

    public static int uint8ToInt(int rawValue) {
        return rawValue & 0xFF;
    }

    public static long uint64ToLong(long rawValue, String fieldName) {
        if (rawValue < 0) {
            throw new BasisDecodeException(
                    "Unsigned 64-bit field does not fit Java signed long semantics: "
                            + fieldName + "=" + rawValue);
        }
        return rawValue;
    }

    public static int sizeToInt(long value, String fieldName) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new BasisDecodeException(
                    "Size field does not fit Java int: " + fieldName + "=" + value);
        }
        return (int) value;
    }
}
