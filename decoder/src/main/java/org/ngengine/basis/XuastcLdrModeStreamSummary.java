package org.ngengine.basis;

import java.util.Arrays;

/**
 * Incremental summary of an XUASTC LDR arithmetic mode stream.
 */
final class XuastcLdrModeStreamSummary {
    private final XuastcLdrImageHeader imageHeader;
    private final int totalBlocks;
    private final int decodedBlocks;
    private final int solidBlocks;
    private final int runCopiedBlocks;
    private final int[] explicitModeCounts;
    private final XuastcLdrMode firstUnsupportedMode;
    private final int firstUnsupportedBlockX;
    private final int firstUnsupportedBlockY;

    XuastcLdrModeStreamSummary(
            XuastcLdrImageHeader imageHeader,
            int totalBlocks,
            int decodedBlocks,
            int solidBlocks,
            int runCopiedBlocks,
            int[] explicitModeCounts,
            XuastcLdrMode firstUnsupportedMode,
            int firstUnsupportedBlockX,
            int firstUnsupportedBlockY) {
        this.imageHeader = imageHeader;
        this.totalBlocks = totalBlocks;
        this.decodedBlocks = decodedBlocks;
        this.solidBlocks = solidBlocks;
        this.runCopiedBlocks = runCopiedBlocks;
        this.explicitModeCounts = Arrays.copyOf(explicitModeCounts, explicitModeCounts.length);
        this.firstUnsupportedMode = firstUnsupportedMode;
        this.firstUnsupportedBlockX = firstUnsupportedBlockX;
        this.firstUnsupportedBlockY = firstUnsupportedBlockY;
    }

    XuastcLdrImageHeader getImageHeader() {
        return imageHeader;
    }

    int getTotalBlocks() {
        return totalBlocks;
    }

    int getDecodedBlocks() {
        return decodedBlocks;
    }

    int getSolidBlocks() {
        return solidBlocks;
    }

    int getRunCopiedBlocks() {
        return runCopiedBlocks;
    }

    int getExplicitModeCount(XuastcLdrMode mode) {
        return explicitModeCounts[mode.getCode()];
    }

    XuastcLdrMode getFirstUnsupportedMode() {
        return firstUnsupportedMode;
    }

    int getFirstUnsupportedBlockX() {
        return firstUnsupportedBlockX;
    }

    int getFirstUnsupportedBlockY() {
        return firstUnsupportedBlockY;
    }

    boolean isComplete() {
        return decodedBlocks == totalBlocks && firstUnsupportedMode == null;
    }
}
