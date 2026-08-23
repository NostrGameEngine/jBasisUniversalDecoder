package org.ngengine.basis;

/**
 * First decoded XUASTC LDR RAW block configuration.
 */
final class XuastcLdrRawBlockConfig {
    private final int blockX;
    private final int blockY;
    private final int configReuseIndex;
    private final int colorEndpointModeIndex;
    private final int subsetIndex;
    private final int colorComponentSelectorIndex;
    private final int gridSizeIndex;
    private final int gridAnisoIndex;
    private final int submodeIndex;
    private final int trialModeIndex;
    private final XuastcTrialMode trialMode;
    private final int actualColorEndpointMode;
    private final boolean baseOffsetMode;
    private final int uniquePatternIndex;
    private final int partitionSeed;
    private final boolean partitionHashUsed;
    private final boolean dpcmEndpoints;
    private final int[] endpoints;
    private final boolean blockUsesDct;
    private final XuastcLdrDctWeightPlane[] dctWeightPlanes;
    private final XuastcLdrWeightGrid weightGrid;

    XuastcLdrRawBlockConfig(
            int blockX,
            int blockY,
            int configReuseIndex,
            int colorEndpointModeIndex,
            int subsetIndex,
            int colorComponentSelectorIndex,
            int gridSizeIndex,
            int gridAnisoIndex,
            int submodeIndex,
            int trialModeIndex,
            XuastcTrialMode trialMode,
            int actualColorEndpointMode,
            boolean baseOffsetMode,
            int uniquePatternIndex,
            int partitionSeed,
            boolean partitionHashUsed,
            boolean dpcmEndpoints,
            int[] endpoints,
            boolean blockUsesDct,
            XuastcLdrDctWeightPlane[] dctWeightPlanes,
            XuastcLdrWeightGrid weightGrid) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.configReuseIndex = configReuseIndex;
        this.colorEndpointModeIndex = colorEndpointModeIndex;
        this.subsetIndex = subsetIndex;
        this.colorComponentSelectorIndex = colorComponentSelectorIndex;
        this.gridSizeIndex = gridSizeIndex;
        this.gridAnisoIndex = gridAnisoIndex;
        this.submodeIndex = submodeIndex;
        this.trialModeIndex = trialModeIndex;
        this.trialMode = trialMode;
        this.actualColorEndpointMode = actualColorEndpointMode;
        this.baseOffsetMode = baseOffsetMode;
        this.uniquePatternIndex = uniquePatternIndex;
        this.partitionSeed = partitionSeed;
        this.partitionHashUsed = partitionHashUsed;
        this.dpcmEndpoints = dpcmEndpoints;
        this.endpoints = endpoints.clone();
        this.blockUsesDct = blockUsesDct;
        this.dctWeightPlanes = dctWeightPlanes.clone();
        this.weightGrid = weightGrid;
    }

    int getBlockX() {
        return blockX;
    }

    int getBlockY() {
        return blockY;
    }

    int getConfigReuseIndex() {
        return configReuseIndex;
    }

    int getColorEndpointModeIndex() {
        return colorEndpointModeIndex;
    }

    int getSubsetIndex() {
        return subsetIndex;
    }

    int getColorComponentSelectorIndex() {
        return colorComponentSelectorIndex;
    }

    int getGridSizeIndex() {
        return gridSizeIndex;
    }

    int getGridAnisoIndex() {
        return gridAnisoIndex;
    }

    int getSubmodeIndex() {
        return submodeIndex;
    }

    int getTrialModeIndex() {
        return trialModeIndex;
    }

    XuastcTrialMode getTrialMode() {
        return trialMode;
    }

    int getActualColorEndpointMode() {
        return actualColorEndpointMode;
    }

    boolean isBaseOffsetMode() {
        return baseOffsetMode;
    }

    int getUniquePatternIndex() {
        return uniquePatternIndex;
    }

    int getPartitionSeed() {
        return partitionSeed;
    }

    boolean isPartitionHashUsed() {
        return partitionHashUsed;
    }

    boolean usesDpcmEndpoints() {
        return dpcmEndpoints;
    }

    int[] getEndpoints() {
        return endpoints.clone();
    }

    boolean blockUsesDct() {
        return blockUsesDct;
    }

    XuastcLdrDctWeightPlane[] getDctWeightPlanes() {
        return dctWeightPlanes.clone();
    }

    XuastcLdrWeightGrid getWeightGrid() {
        return weightGrid;
    }
}
