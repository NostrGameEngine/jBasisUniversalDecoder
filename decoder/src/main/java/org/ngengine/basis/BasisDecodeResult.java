package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Decoded texture payload.
 */
public final class BasisDecodeResult {
    private final int width;
    private final int height;
    private final ByteBuffer pixelData;
    private final BasisImageFormat imageFormat;
    private final int[] mipMapSizes;
    private final BasisColorSpace colorSpace;
    private final int imageCount;
    private final int levelCount;

    public BasisDecodeResult(int width,
                            int height,
                            ByteBuffer pixelData,
                            BasisImageFormat imageFormat,
                            int[] mipMapSizes,
                            BasisColorSpace colorSpace) {
        this(width, height, pixelData, imageFormat, mipMapSizes, colorSpace, 1,
                mipMapSizes == null ? 1 : mipMapSizes.length);
    }

    public BasisDecodeResult(int width,
                            int height,
                            ByteBuffer pixelData,
                            BasisImageFormat imageFormat,
                            int[] mipMapSizes,
                            BasisColorSpace colorSpace,
                            int imageCount,
                            int levelCount) {
        if (imageCount <= 0) {
            throw new IllegalArgumentException("imageCount must be positive: " + imageCount);
        }
        if (levelCount <= 0) {
            throw new IllegalArgumentException("levelCount must be positive: " + levelCount);
        }
        this.width = width;
        this.height = height;
        this.pixelData = pixelData == null ? null : pixelData.duplicate();
        this.imageFormat = imageFormat;
        this.mipMapSizes = mipMapSizes == null ? null : Arrays.copyOf(mipMapSizes, mipMapSizes.length);
        this.colorSpace = colorSpace == null ? BasisColorSpace.Linear : colorSpace;
        this.imageCount = imageCount;
        this.levelCount = levelCount;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ByteBuffer getPixelData() {
        return pixelData == null ? null : pixelData.asReadOnlyBuffer();
    }

    public BasisImageFormat getImageFormat() {
        return imageFormat;
    }

    public int[] getMipMapSizes() {
        return mipMapSizes == null ? null : Arrays.copyOf(mipMapSizes, mipMapSizes.length);
    }

    public BasisColorSpace getColorSpace() {
        return colorSpace;
    }

    public int getImageCount() {
        return imageCount;
    }

    public int getLevelCount() {
        return levelCount;
    }
}
