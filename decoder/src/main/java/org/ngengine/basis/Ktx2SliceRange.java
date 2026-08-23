package org.ngengine.basis;

/**
 * KTX2 slice range relative to a level payload.
 */
public final class Ktx2SliceRange {
    private final long byteOffset;
    private final long byteLength;
    private final long profile;

    Ktx2SliceRange(long byteOffset, long byteLength, long profile) {
        this.byteOffset = byteOffset;
        this.byteLength = byteLength;
        this.profile = profile;
    }

    public long getByteOffset() {
        return byteOffset;
    }

    public long getByteLength() {
        return byteLength;
    }

    public long getProfile() {
        return profile;
    }
}
