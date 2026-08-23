package org.ngengine.basis;

import java.util.Arrays;

/**
 * One KTX2 key/value metadata entry.
 */
public final class Ktx2KeyValue {
    private final String key;
    private final byte[] value;

    Ktx2KeyValue(String key, byte[] value) {
        this.key = key;
        this.value = Arrays.copyOf(value, value.length);
    }

    public String getKey() {
        return key;
    }

    public byte[] getValue() {
        return Arrays.copyOf(value, value.length);
    }
}
