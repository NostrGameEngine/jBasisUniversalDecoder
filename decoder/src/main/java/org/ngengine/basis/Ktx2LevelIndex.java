package org.ngengine.basis;

/**
 * One entry from the KTX2 level index table.
 */
public final class Ktx2LevelIndex {
    private final long byteOffset;
    private final long byteLength;
    private final long uncompressedByteLength;

    public Ktx2LevelIndex(long byteOffset, long byteLength, long uncompressedByteLength) {
        this.byteOffset = byteOffset;
        this.byteLength = byteLength;
        this.uncompressedByteLength = uncompressedByteLength;
    }

    public long getByteOffset() {
        return byteOffset;
    }

    public long getByteLength() {
        return byteLength;
    }

    public long getUncompressedByteLength() {
        return uncompressedByteLength;
    }
}
