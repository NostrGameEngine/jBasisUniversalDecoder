package org.ngengine.basis;

/**
 * Logical ASTC trial mode used by XUASTC LDR config decoding.
 */
final class XuastcTrialMode {
    private final int gridWidth;
    private final int gridHeight;
    private final int colorEndpointMode;
    private final int colorComponentSelector;
    private final int endpointIseRange;
    private final int weightIseRange;
    private final int numberOfPartitions;

    XuastcTrialMode(
            int gridWidth,
            int gridHeight,
            int colorEndpointMode,
            int colorComponentSelector,
            int endpointIseRange,
            int weightIseRange,
            int numberOfPartitions) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.colorEndpointMode = colorEndpointMode;
        this.colorComponentSelector = colorComponentSelector;
        this.endpointIseRange = endpointIseRange;
        this.weightIseRange = weightIseRange;
        this.numberOfPartitions = numberOfPartitions;
    }

    int getGridWidth() {
        return gridWidth;
    }

    int getGridHeight() {
        return gridHeight;
    }

    int getColorEndpointMode() {
        return colorEndpointMode;
    }

    int getColorComponentSelector() {
        return colorComponentSelector;
    }

    int getEndpointIseRange() {
        return endpointIseRange;
    }

    int getWeightIseRange() {
        return weightIseRange;
    }

    int getNumberOfPartitions() {
        return numberOfPartitions;
    }
}
