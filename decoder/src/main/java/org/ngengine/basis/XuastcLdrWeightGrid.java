package org.ngengine.basis;

/**
 * Reconstructed ASTC weight-grid ISE symbols for an XUASTC block.
 */
final class XuastcLdrWeightGrid {
    private final int gridWidth;
    private final int gridHeight;
    private final int planeCount;
    private final int[] weights;

    XuastcLdrWeightGrid(int gridWidth, int gridHeight, int planeCount, int[] weights) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.planeCount = planeCount;
        this.weights = weights.clone();
    }

    int getGridWidth() {
        return gridWidth;
    }

    int getGridHeight() {
        return gridHeight;
    }

    int getPlaneCount() {
        return planeCount;
    }

    int[] getWeights() {
        return weights.clone();
    }
}
