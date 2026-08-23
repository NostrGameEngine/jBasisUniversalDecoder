package org.ngengine.basis;

/**
 * Helper for Java-friendly mapping of C++ enum-like integer codes to Java enum instances.
 */
public final class CppEnumCodeResolver {
    private CppEnumCodeResolver() {
    }

    public static <E extends Enum<E> & CppCodeMapped> E fromCode(Class<E> enumType, int code,
            String notFoundMessage) {
        return EnumCodeResolver.fromCode(enumType, code, notFoundMessage);
    }

    public static boolean hasCode(Class<?> enumType, int code) {
        return EnumCodeResolver.hasCode(enumType, code);
    }

    public static String describe(int code, Class<? extends Enum<?>> enumType) {
        return EnumCodeResolver.describe(code, enumType);
    }
}
