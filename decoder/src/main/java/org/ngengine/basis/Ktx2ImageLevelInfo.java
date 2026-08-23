package org.ngengine.basis;

/**
 * Java parity representation of {@code basist::basisu_image_level_info}.
 */
public final class Ktx2ImageLevelInfo {

    private final int imageIndex;
    private final int levelIndex;

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

    private final int rgbFileOffset;
    private final int rgbFileLength;
    private final int alphaFileOffset;
    private final int alphaFileLength;

    private final boolean alphaFlag;
    private final boolean iframeFlag;

    public Ktx2ImageLevelInfo(int imageIndex,
                              int levelIndex,
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
                              int rgbFileOffset,
                              int rgbFileLength,
                              int alphaFileOffset,
                              int alphaFileLength,
                              boolean alphaFlag,
                              boolean iframeFlag) {
        this.imageIndex = imageIndex;
        this.levelIndex = levelIndex;
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
        this.rgbFileOffset = rgbFileOffset;
        this.rgbFileLength = rgbFileLength;
        this.alphaFileOffset = alphaFileOffset;
        this.alphaFileLength = alphaFileLength;
        this.alphaFlag = alphaFlag;
        this.iframeFlag = iframeFlag;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public int getLevelIndex() {
        return levelIndex;
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

    public int getRgbFileOffset() {
        return rgbFileOffset;
    }

    public int getRgbFileLength() {
        return rgbFileLength;
    }

    public int getAlphaFileOffset() {
        return alphaFileOffset;
    }

    public int getAlphaFileLength() {
        return alphaFileLength;
    }

    public boolean isAlphaFlag() {
        return alphaFlag;
    }

    public boolean isIframeFlag() {
        return iframeFlag;
    }
}
