package org.ngengine.basis;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves integer container codes to Java enum values.
 */
public final class EnumCodeResolver {
    private static final ConcurrentHashMap<Class<?>, Map<Integer, Enum<?>>> LOOKUP =
            new ConcurrentHashMap<>();

    private EnumCodeResolver() {
    }

    public static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> enumType, int code,
            String notFoundMessage) {
        Objects.requireNonNull(enumType, "enumType");
        Objects.requireNonNull(notFoundMessage, "notFoundMessage");
        Map<Integer, Enum<?>> lookup = resolver(enumType);
        Enum<?> resolved = lookup.get(code);
        if (resolved == null) {
            throw new IllegalArgumentException(notFoundMessage + code);
        }
        @SuppressWarnings("unchecked")
        E cast = (E) resolved;
        return cast;
    }

    public static boolean hasCode(Class<?> enumType, int code) {
        if (enumType == null || !enumType.isEnum() || !CodedEnum.class.isAssignableFrom(enumType)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Enum<?>> casted = (Class<? extends Enum<?>>) enumType;
        return resolver(casted).containsKey(code);
    }

    public static String describe(int code, Class<? extends Enum<?>> enumType) {
        return "code=" + code
                + " enum=" + enumType.getSimpleName().toLowerCase(Locale.ENGLISH)
                + " mapped=" + hasCode(enumType, code);
    }

    private static Map<Integer, Enum<?>> resolver(Class<? extends Enum<?>> enumType) {
        return LOOKUP.computeIfAbsent(enumType, type -> {
            Map<Integer, Enum<?>> map = new HashMap<>();
            if (!CodedEnum.class.isAssignableFrom(type)) {
                return map;
            }
            for (Object valueObj : type.getEnumConstants()) {
                Enum<?> value = (Enum<?>) valueObj;
                map.putIfAbsent(((CodedEnum) value).getCode(), value);
            }
            return map;
        });
    }
}
