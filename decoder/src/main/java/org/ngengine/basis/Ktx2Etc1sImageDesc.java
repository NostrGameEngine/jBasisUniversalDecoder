package org.ngengine.basis;

/**
 * One ETC1S image descriptor from KTX2 supercompression global data.
 */
public final class Ktx2Etc1sImageDesc {
    private final long imageFlags;
    private final long rgbSliceByteOffset;
    private final long rgbSliceByteLength;
    private final long alphaSliceByteOffset;
    private final long alphaSliceByteLength;

    Ktx2Etc1sImageDesc(
            long imageFlags,
            long rgbSliceByteOffset,
            long rgbSliceByteLength,
            long alphaSliceByteOffset,
            long alphaSliceByteLength) {
        this.imageFlags = imageFlags;
        this.rgbSliceByteOffset = rgbSliceByteOffset;
        this.rgbSliceByteLength = rgbSliceByteLength;
        this.alphaSliceByteOffset = alphaSliceByteOffset;
        this.alphaSliceByteLength = alphaSliceByteLength;
    }

    public long getImageFlags() {
        return imageFlags;
    }

    public long getRgbSliceByteOffset() {
        return rgbSliceByteOffset;
    }

    public long getRgbSliceByteLength() {
        return rgbSliceByteLength;
    }

    public long getAlphaSliceByteOffset() {
        return alphaSliceByteOffset;
    }

    public long getAlphaSliceByteLength() {
        return alphaSliceByteLength;
    }
}
