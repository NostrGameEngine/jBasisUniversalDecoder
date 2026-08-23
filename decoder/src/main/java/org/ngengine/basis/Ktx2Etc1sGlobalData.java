package org.ngengine.basis;

import java.util.Arrays;

/**
 * Parsed ETC1S KTX2 supercompression global data.
 */
public final class Ktx2Etc1sGlobalData {
    private final int endpointCount;
    private final int selectorCount;
    private final long endpointsByteLength;
    private final long selectorsByteLength;
    private final long tablesByteLength;
    private final long extendedByteLength;
    private final long endpointsByteOffset;
    private final long selectorsByteOffset;
    private final long tablesByteOffset;
    private final long extendedByteOffset;
    private final Ktx2Etc1sImageDesc[] imageDescriptors;

    Ktx2Etc1sGlobalData(
            int endpointCount,
            int selectorCount,
            long endpointsByteLength,
            long selectorsByteLength,
            long tablesByteLength,
            long extendedByteLength,
            long endpointsByteOffset,
            long selectorsByteOffset,
            long tablesByteOffset,
            long extendedByteOffset,
            Ktx2Etc1sImageDesc[] imageDescriptors) {
        this.endpointCount = endpointCount;
        this.selectorCount = selectorCount;
        this.endpointsByteLength = endpointsByteLength;
        this.selectorsByteLength = selectorsByteLength;
        this.tablesByteLength = tablesByteLength;
        this.extendedByteLength = extendedByteLength;
        this.endpointsByteOffset = endpointsByteOffset;
        this.selectorsByteOffset = selectorsByteOffset;
        this.tablesByteOffset = tablesByteOffset;
        this.extendedByteOffset = extendedByteOffset;
        this.imageDescriptors = Arrays.copyOf(imageDescriptors, imageDescriptors.length);
    }

    public int getEndpointCount() {
        return endpointCount;
    }

    public int getSelectorCount() {
        return selectorCount;
    }

    public long getEndpointsByteLength() {
        return endpointsByteLength;
    }

    public long getSelectorsByteLength() {
        return selectorsByteLength;
    }

    public long getTablesByteLength() {
        return tablesByteLength;
    }

    public long getExtendedByteLength() {
        return extendedByteLength;
    }

    public long getEndpointsByteOffset() {
        return endpointsByteOffset;
    }

    public long getSelectorsByteOffset() {
        return selectorsByteOffset;
    }

    public long getTablesByteOffset() {
        return tablesByteOffset;
    }

    public long getExtendedByteOffset() {
        return extendedByteOffset;
    }

    public Ktx2Etc1sImageDesc[] getImageDescriptors() {
        return Arrays.copyOf(imageDescriptors, imageDescriptors.length);
    }
}
