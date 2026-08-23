package org.ngengine.basis;

/**
 * Java parity representation of {@code basist::basisu_image_info}.
 */
public final class Ktx2ImageInfo {

    private final int imageIndex;
    private final int totalLevels;

    private final int originalWidth;
    private final int originalHeight;
    private final int width;
    private final int height;

    private final int blockWidth;
    private final int blockHeight;

    private final int numBlocksX;
    private final int numBlocksY;
    private final int totalBlocks;

    private final int firstSliceIndex;

    private final boolean alphaFlag;
    private final boolean iframeFlag;

    public Ktx2ImageInfo(int imageIndex,
                         int totalLevels,
                         int originalWidth,
                         int originalHeight,
                         int width,
                         int height,
                         int blockWidth,
                         int blockHeight,
                         int numBlocksX,
                         int numBlocksY,
                         int totalBlocks,
                         int firstSliceIndex,
                         boolean alphaFlag,
                         boolean iframeFlag) {
        this.imageIndex = imageIndex;
        this.totalLevels = totalLevels;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.width = width;
        this.height = height;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.numBlocksX = numBlocksX;
        this.numBlocksY = numBlocksY;
        this.totalBlocks = totalBlocks;
        this.firstSliceIndex = firstSliceIndex;
        this.alphaFlag = alphaFlag;
        this.iframeFlag = iframeFlag;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public int getTotalLevels() {
        return totalLevels;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBlockWidth() {
        return blockWidth;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public int getNumBlocksX() {
        return numBlocksX;
    }

    public int getNumBlocksY() {
        return numBlocksY;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getFirstSliceIndex() {
        return firstSliceIndex;
    }

    public boolean isAlphaFlag() {
        return alphaFlag;
    }

    public boolean isIframeFlag() {
        return iframeFlag;
    }
}
