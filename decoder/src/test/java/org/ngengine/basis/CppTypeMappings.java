package org.ngengine.basis;

/**
 * Java representations for common C/C++ scalar and size mappings used by the decoder facade.
 */
public final class CppTypeMappings {

    private CppTypeMappings() {
    }

    public static int toIntFromUnsigned32(int rawValue, String fieldName) {
        return UnsignedFields.uint32ToInt(rawValue, fieldName);
    }

    public static long toLongFromUnsigned32(int rawValue, String fieldName) {
        return UnsignedFields.uint32ToLong(rawValue);
    }

    public static int toIntFromUnsigned16(int rawValue, String fieldName) {
        return UnsignedFields.uint16ToInt(rawValue);
    }

    public static int toIntFromUnsigned8(int rawValue, String fieldName) {
        return UnsignedFields.uint8ToInt(rawValue);
    }

    public static long toLongFromUnsigned64(long rawValue, String fieldName) {
        return UnsignedFields.uint64ToLong(rawValue, fieldName);
    }

    public static int toIntFromSizeT(long value, String fieldName) {
        return UnsignedFields.sizeToInt(value, fieldName);
    }
}
