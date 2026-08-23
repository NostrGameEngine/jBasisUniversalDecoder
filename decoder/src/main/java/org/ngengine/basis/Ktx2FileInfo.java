package org.ngengine.basis;

/**
 * Java parity representation of {@code basist::basisu_file_info}.
 */
public final class Ktx2FileInfo {

    private final int version;
    private final int totalHeaderSize;

    private final int totalSelectors;
    private final int selectorCodebookOffset;
    private final int selectorCodebookSize;

    private final int totalEndpoints;
    private final int endpointCodebookOffset;
    private final int endpointCodebookSize;

    private final int tablesOffset;
    private final int tablesSize;

    private final int slicesSize;
    private final Ktx2BasisTextureType textureType;
    private final int microsecondsPerFrame;

    private final int totalImages;
    private final int[] imageMipmapLevels;

    private final int userData0;
    private final int userData1;

    public Ktx2FileInfo(int version,
                        int totalHeaderSize,
                        int totalSelectors,
                        int selectorCodebookOffset,
                        int selectorCodebookSize,
                        int totalEndpoints,
                        int endpointCodebookOffset,
                        int endpointCodebookSize,
                        int tablesOffset,
                        int tablesSize,
                        int slicesSize,
                        Ktx2BasisTextureType textureType,
                        int microsecondsPerFrame,
                        int totalImages,
                        int[] imageMipmapLevels,
                        int userData0,
                        int userData1) {
        this.version = version;
        this.totalHeaderSize = totalHeaderSize;
        this.totalSelectors = totalSelectors;
        this.selectorCodebookOffset = selectorCodebookOffset;
        this.selectorCodebookSize = selectorCodebookSize;
        this.totalEndpoints = totalEndpoints;
        this.endpointCodebookOffset = endpointCodebookOffset;
        this.endpointCodebookSize = endpointCodebookSize;
        this.tablesOffset = tablesOffset;
        this.tablesSize = tablesSize;
        this.slicesSize = slicesSize;
        this.textureType = textureType;
        this.microsecondsPerFrame = microsecondsPerFrame;
        this.totalImages = totalImages;
        this.imageMipmapLevels = imageMipmapLevels;
        this.userData0 = userData0;
        this.userData1 = userData1;
    }

    public int getVersion() {
        return version;
    }

    public int getTotalHeaderSize() {
        return totalHeaderSize;
    }

    public int getTotalSelectors() {
        return totalSelectors;
    }

    public int getSelectorCodebookOffset() {
        return selectorCodebookOffset;
    }

    public int getSelectorCodebookSize() {
        return selectorCodebookSize;
    }

    public int getTotalEndpoints() {
        return totalEndpoints;
    }

    public int getEndpointCodebookOffset() {
        return endpointCodebookOffset;
    }

    public int getEndpointCodebookSize() {
        return endpointCodebookSize;
    }

    public int getTablesOffset() {
        return tablesOffset;
    }

    public int getTablesSize() {
        return tablesSize;
    }

    public int getSlicesSize() {
        return slicesSize;
    }

    public Ktx2BasisTextureType getTextureType() {
        return textureType;
    }

    public int getMicrosecondsPerFrame() {
        return microsecondsPerFrame;
    }

    public int getTotalImages() {
        return totalImages;
    }

    public int[] getImageMipmapLevels() {
        return imageMipmapLevels.clone();
    }

    public int getUserData0() {
        return userData0;
    }

    public int getUserData1() {
        return userData1;
    }
}
