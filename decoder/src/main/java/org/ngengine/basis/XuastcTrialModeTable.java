package org.ngengine.basis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java representation of Basis Universal's XUASTC LDR ASTC trial-mode table.
 */
final class XuastcTrialModeTable {
    private static final int CFG_PACK_EISE_BITS = 5;
    private static final int CFG_PACK_WISE_BITS = 4;
    private static final int CFG_PACK_CCS_BITS = 3;
    private static final int CFG_PACK_SUBSETS_BITS = 2;
    private static final int CFG_PACK_CEM_BITS = 3;
    private static final int CFG_PACK_GRID_MASK = 0x7F;
    private static final XuastcTrialModeTable[] CACHE =
            new XuastcTrialModeTable[XuastcLdrImageHeader.getAstcBlockSizeCount()];

    private final int blockWidth;
    private final int blockHeight;
    private final List<XuastcTrialMode> modes;
    private final int[][][][][][] groupedModeIndices;
    private final int usedGroupCount;

    private XuastcTrialModeTable(int blockWidth, int blockHeight) {
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.modes = new ArrayList<XuastcTrialMode>();
        int cems = XuastcAstcConstants.OTM_NUM_CEMS;
        int subsets = XuastcAstcConstants.OTM_NUM_SUBSETS;
        int ccsCount = XuastcAstcConstants.OTM_NUM_CCS;
        int gridSizes = XuastcAstcConstants.OTM_NUM_GRID_SIZES;
        int gridAnisos = XuastcAstcConstants.OTM_NUM_GRID_ANISOS;
        this.groupedModeIndices = new int[cems][subsets][ccsCount][gridSizes][gridAnisos][];

        @SuppressWarnings("unchecked")
        ArrayList<Integer>[][][][][] groups =
                new ArrayList[cems][subsets][ccsCount][gridSizes][gridAnisos];

        for (int[] chunk : XuastcAstcConfigTable.getPackedConfigChunks()) {
            for (int packedMode : chunk) {
                if (!addPackedMode(packedMode, groups)) {
                    break;
                }
            }
        }

        int count = 0;
        for (int cem = 0; cem < XuastcAstcConstants.OTM_NUM_CEMS; cem++) {
            for (int subset = 0; subset < XuastcAstcConstants.OTM_NUM_SUBSETS; subset++) {
                for (int ccs = 0; ccs < XuastcAstcConstants.OTM_NUM_CCS; ccs++) {
                    for (int gridSize = 0; gridSize < XuastcAstcConstants.OTM_NUM_GRID_SIZES; gridSize++) {
                        for (int aniso = 0; aniso < XuastcAstcConstants.OTM_NUM_GRID_ANISOS; aniso++) {
                            ArrayList<Integer> group = groups[cem][subset][ccs][gridSize][aniso];
                            if (group != null && !group.isEmpty()) {
                                groupedModeIndices[cem][subset][ccs][gridSize][aniso] = toIntArray(group);
                                count++;
                            } else {
                                groupedModeIndices[cem][subset][ccs][gridSize][aniso] = new int[0];
                            }
                        }
                    }
                }
            }
        }
        this.usedGroupCount = count;
    }

    static XuastcTrialModeTable forBlockSize(int blockWidth, int blockHeight) {
        int index = XuastcLdrImageHeader.findAstcBlockSizeIndex(blockWidth, blockHeight);
        synchronized (CACHE) {
            XuastcTrialModeTable table = CACHE[index];
            if (table == null) {
                table = new XuastcTrialModeTable(blockWidth, blockHeight);
                CACHE[index] = table;
            }
            return table;
        }
    }

    int getBlockWidth() {
        return blockWidth;
    }

    int getBlockHeight() {
        return blockHeight;
    }

    int size() {
        return modes.size();
    }

    int getUsedGroupCount() {
        return usedGroupCount;
    }

    XuastcTrialMode get(int index) {
        if (index < 0 || index >= modes.size()) {
            throw new BasisDecodeException("Invalid XUASTC trial mode index: " + index);
        }
        return modes.get(index);
    }

    int[] getCandidateIndices(int cem, int subsetIndex, int ccsIndex, int gridSize, int gridAniso) {
        if (cem < 0 || cem >= XuastcAstcConstants.OTM_NUM_CEMS
                || subsetIndex < 0 || subsetIndex >= XuastcAstcConstants.OTM_NUM_SUBSETS
                || ccsIndex < 0 || ccsIndex >= XuastcAstcConstants.OTM_NUM_CCS
                || gridSize < 0 || gridSize >= XuastcAstcConstants.OTM_NUM_GRID_SIZES
                || gridAniso < 0 || gridAniso >= XuastcAstcConstants.OTM_NUM_GRID_ANISOS) {
            throw new BasisDecodeException("Invalid XUASTC trial mode group key");
        }
        return groupedModeIndices[cem][subsetIndex][ccsIndex][gridSize][gridAniso];
    }

    List<XuastcTrialMode> getModes() {
        return Collections.unmodifiableList(modes);
    }

    private boolean addPackedMode(
            int packedMode,
            ArrayList<Integer>[][][][][] groups) {
        final int endpointIseRange = unpack(packedMode, CFG_PACK_EISE_BITS);
        packedMode >>>= CFG_PACK_EISE_BITS;
        final int weightIseRange = unpack(packedMode, CFG_PACK_WISE_BITS);
        packedMode >>>= CFG_PACK_WISE_BITS;
        final int ccsIndex = unpack(packedMode, CFG_PACK_CCS_BITS);
        packedMode >>>= CFG_PACK_CCS_BITS;
        final int subsetIndex = unpack(packedMode, CFG_PACK_SUBSETS_BITS);
        packedMode >>>= CFG_PACK_SUBSETS_BITS;
        final int uniqueCemIndex = unpack(packedMode, CFG_PACK_CEM_BITS);
        packedMode >>>= CFG_PACK_CEM_BITS;
        int gridWh = packedMode & CFG_PACK_GRID_MASK;

        int gridWidth = gridWh / 11 + 2;
        if (gridWidth > blockWidth) {
            return false;
        }
        int gridHeight = gridWh % 11 + 2;
        if (gridHeight > blockHeight) {
            return true;
        }

        int colorEndpointMode = XuastcAstcConstants.uniqueLdrIndexToAstcCem(uniqueCemIndex);
        XuastcTrialMode mode = new XuastcTrialMode(
                gridWidth,
                gridHeight,
                colorEndpointMode,
                ccsIndex - 1,
                endpointIseRange + XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE,
                weightIseRange,
                subsetIndex + 1);
        int modeIndex = modes.size();
        modes.add(mode);

        int groupGridSize = gridWidth >= blockWidth - 1 && gridHeight >= blockHeight - 1
                ? 1 : 0;
        int groupGridAniso = XuastcAstcConstants.gridAniso(gridWidth, gridHeight, blockWidth, blockHeight);
        ArrayList<Integer> group =
                groups[colorEndpointMode][subsetIndex][ccsIndex][groupGridSize][groupGridAniso];
        if (group == null) {
            group = new ArrayList<Integer>(64);
            groups[colorEndpointMode][subsetIndex][ccsIndex][groupGridSize][groupGridAniso] = group;
        }
        group.add(modeIndex);
        return true;
    }

    private static int unpack(int value, int bits) {
        return value & ((1 << bits) - 1);
    }

    private static int[] toIntArray(ArrayList<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
