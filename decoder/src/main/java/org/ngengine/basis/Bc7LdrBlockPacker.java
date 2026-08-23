package org.ngengine.basis;

/**
 * BC7 block writer used by the LDR UASTC path.
 */
final class Bc7LdrBlockPacker {
    private static final int[][] BC7_WEIGHTS = {
        {0, 64},
        {0, 21, 43, 64},
        {0, 9, 18, 27, 37, 46, 55, 64},
        {0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64}
    };
    private static final int[] NUM_SUBSETS = {3, 2, 3, 2, 1, 1, 1, 2};
    private static final int[] PARTITION_BITS = {4, 6, 6, 6, 0, 0, 0, 6};
    private static final int[] COLOR_INDEX_BITS = {3, 3, 2, 2, 2, 2, 4, 2};
    private static final int[] ALPHA_INDEX_BITS = {0, 0, 0, 0, 3, 2, 4, 2};
    private static final int[] COLOR_PRECISION = {4, 6, 5, 7, 5, 7, 7, 5};
    private static final int[] ALPHA_PRECISION = {0, 0, 0, 0, 6, 8, 7, 5};
    private static final boolean[] HAS_P_BITS = {true, true, false, true, false, false, true, true};
    private static final boolean[] HAS_SHARED_P_BITS = {
        false, true, false, false, false, false, false, false
    };
    private static final int[] SECOND_SUBSET_ANCHOR = {
        15, 15, 15, 15, 15, 15, 15, 15,
        15, 15, 15, 15, 15, 15, 15, 15,
        15, 2, 8, 2, 2, 8, 8, 15,
        2, 8, 2, 2, 8, 8, 2, 2,
        15, 15, 6, 8, 2, 8, 15, 15,
        2, 8, 2, 2, 2, 15, 15, 6,
        6, 2, 6, 8, 15, 15, 2, 2,
        15, 15, 15, 15, 15, 2, 2, 15
    };
    private static final int[] THIRD_SUBSET_ANCHOR_1 = {
        3, 3, 15, 15, 8, 3, 15, 15,
        8, 8, 6, 6, 6, 5, 3, 3,
        3, 3, 8, 15, 3, 3, 6, 10,
        5, 8, 8, 6, 8, 5, 15, 15,
        8, 15, 3, 5, 6, 10, 8, 15,
        15, 3, 15, 5, 15, 15, 15, 15,
        3, 15, 5, 5, 5, 8, 5, 10,
        5, 10, 8, 13, 15, 12, 3, 3
    };
    private static final int[] THIRD_SUBSET_ANCHOR_2 = {
        15, 8, 8, 3, 15, 15, 3, 8,
        15, 15, 15, 15, 15, 15, 15, 8,
        15, 8, 15, 3, 15, 8, 15, 8,
        3, 15, 6, 10, 15, 15, 10, 8,
        15, 3, 15, 10, 10, 8, 9, 10,
        6, 15, 8, 15, 3, 6, 6, 8,
        15, 3, 15, 15, 15, 15, 15, 15,
        15, 15, 15, 15, 3, 15, 15, 8
    };

    private Bc7LdrBlockPacker() {
    }

    static void writeBlock(byte[] output, int offset, Block source) {
        Block block = source.copy();
        int mode = block.mode;
        int totalSubsets = NUM_SUBSETS[mode];
        int[] partition = block.partitionMap == null ? new int[16] : block.partitionMap;
        int[] anchors = anchors(mode, block.partition);

        for (int subset = 0; subset < totalSubsets; subset++) {
            int anchor = anchors[subset];
            int colorIndexBits = colorIndexBits(block);
            int colorSelectorCount = 1 << colorIndexBits;
            if ((block.selectors[anchor] & (colorSelectorCount >>> 1)) != 0) {
                for (int i = 0; i < 16; i++) {
                    if (partition[i] == subset) {
                        block.selectors[i] = colorSelectorCount - 1 - block.selectors[i];
                    }
                }
                if (hasSeparateAlphaSelectors(mode)) {
                    swapRgbEndpoints(block, subset);
                } else {
                    swapEndpoints(block, subset);
                }
                if (!HAS_SHARED_P_BITS[mode]) {
                    int tmp = block.pbits[subset][0];
                    block.pbits[subset][0] = block.pbits[subset][1];
                    block.pbits[subset][1] = tmp;
                }
            }

            if (hasSeparateAlphaSelectors(mode)) {
                int alphaIndexBits = alphaIndexBits(block);
                int alphaSelectorCount = 1 << alphaIndexBits;
                if ((block.alphaSelectors[anchor] & (alphaSelectorCount >>> 1)) != 0) {
                    for (int i = 0; i < 16; i++) {
                        if (partition[i] == subset) {
                            block.alphaSelectors[i] = alphaSelectorCount - 1 - block.alphaSelectors[i];
                        }
                    }
                    int tmp = block.low[subset][3];
                    block.low[subset][3] = block.high[subset][3];
                    block.high[subset][3] = tmp;
                }
            }
        }

        for (int i = 0; i < 16; i++) {
            output[offset + i] = 0;
        }
        int[] bitOffset = {0};
        setBits(output, offset, 1 << mode, mode + 1, bitOffset);
        if (mode == 4 || mode == 5) {
            setBits(output, offset, block.rotation, 2, bitOffset);
        }
        if (mode == 4) {
            setBits(output, offset, block.indexSelector, 1, bitOffset);
        }
        if (PARTITION_BITS[mode] != 0) {
            setBits(output, offset, block.partition, PARTITION_BITS[mode], bitOffset);
        }

        int totalComps = mode >= 4 ? 4 : 3;
        for (int comp = 0; comp < totalComps; comp++) {
            int bits = comp == 3 ? ALPHA_PRECISION[mode] : COLOR_PRECISION[mode];
            for (int subset = 0; subset < totalSubsets; subset++) {
                setBits(output, offset, block.low[subset][comp], bits, bitOffset);
                setBits(output, offset, block.high[subset][comp], bits, bitOffset);
            }
        }

        if (HAS_P_BITS[mode]) {
            for (int subset = 0; subset < totalSubsets; subset++) {
                setBits(output, offset, block.pbits[subset][0], 1, bitOffset);
                if (!HAS_SHARED_P_BITS[mode]) {
                    setBits(output, offset, block.pbits[subset][1], 1, bitOffset);
                }
            }
        }

        writeSelectors(output, offset, block, anchors, block.indexSelector != 0, bitOffset);
        if (hasSeparateAlphaSelectors(mode)) {
            writeSelectors(output, offset, block, anchors, block.indexSelector == 0, bitOffset);
        }
        if (bitOffset[0] != 128) {
            throw new BasisDecodeException("BC7 block writer emitted an invalid bit count for mode "
                    + mode + ": " + bitOffset[0]);
        }
    }

    static int[] mode6OptimalEndpoint(int value, int pbit) {
        int weight = BC7_WEIGHTS[3][5];
        int bestError = Integer.MAX_VALUE;
        int bestLow = 0;
        int bestHigh = 0;
        for (int low = 0; low < 128; low++) {
            int lowEndpoint = (low << 1) | pbit;
            for (int high = 0; high < 128; high++) {
                int highEndpoint = (high << 1) | pbit;
                int decoded = (lowEndpoint * (64 - weight) + highEndpoint * weight + 32) >> 6;
                int error = decoded - value;
                error *= error;
                if (error < bestError) {
                    bestError = error;
                    bestLow = low;
                    bestHigh = high;
                }
            }
        }
        return new int[] {bestLow, bestHigh, bestError};
    }

    static int[] mode5OptimalEndpoint(int value) {
        int weight = BC7_WEIGHTS[1][1];
        int bestError = Integer.MAX_VALUE;
        int bestLow = 0;
        int bestHigh = 0;
        for (int low = 0; low < 128; low++) {
            int lowEndpoint = (low << 1) | (low >>> 6);
            for (int high = 0; high < 128; high++) {
                int highEndpoint = (high << 1) | (high >>> 6);
                int decoded = (lowEndpoint * (64 - weight) + highEndpoint * weight + 32) >> 6;
                int error = decoded - value;
                error *= error;
                if (error < bestError) {
                    bestError = error;
                    bestLow = low;
                    bestHigh = high;
                }
            }
        }
        return new int[] {bestLow, bestHigh, bestError};
    }

    static int[] colorSelectorAnchors(int mode, int partition) {
        return anchors(mode, partition);
    }

    static void decodeBlock(byte[] source, int offset, int[] rgba) {
        if (rgba.length < 16 * 4) {
            throw new BasisDecodeException("BC7 decode output block is too small");
        }
        int[] bitOffset = {0};
        int mode = -1;
        for (int bit = 0; bit < 8; bit++) {
            if (getBits(source, offset, 1, bitOffset) != 0) {
                mode = bit;
                break;
            }
        }
        if (mode < 0) {
            throw new BasisDecodeException("Invalid BC7 block mode");
        }

        Block block = new Block(mode);
        if (mode == 4 || mode == 5) {
            block.rotation = getBits(source, offset, 2, bitOffset);
        }
        if (mode == 4) {
            block.indexSelector = getBits(source, offset, 1, bitOffset);
        }
        if (PARTITION_BITS[mode] != 0) {
            block.partition = getBits(source, offset, PARTITION_BITS[mode], bitOffset);
        }
        int totalSubsets = NUM_SUBSETS[mode];
        block.partitionMap = totalSubsets == 1
                ? new int[16]
                : totalSubsets == 2
                        ? Bc7PartitionTables.partition2Map(block.partition)
                        : Bc7PartitionTables.partition3Map(block.partition);

        int totalComps = mode >= 4 ? 4 : 3;
        for (int comp = 0; comp < totalComps; comp++) {
            int bits = comp == 3 ? ALPHA_PRECISION[mode] : COLOR_PRECISION[mode];
            for (int subset = 0; subset < totalSubsets; subset++) {
                block.low[subset][comp] = getBits(source, offset, bits, bitOffset);
                block.high[subset][comp] = getBits(source, offset, bits, bitOffset);
            }
        }

        if (HAS_P_BITS[mode]) {
            for (int subset = 0; subset < totalSubsets; subset++) {
                block.pbits[subset][0] = getBits(source, offset, 1, bitOffset);
                block.pbits[subset][1] = HAS_SHARED_P_BITS[mode]
                        ? block.pbits[subset][0]
                        : getBits(source, offset, 1, bitOffset);
            }
        }

        boolean firstSelectorStreamIsAlpha = mode == 4 && block.indexSelector != 0;
        readSelectors(source, offset, block, firstSelectorStreamIsAlpha, bitOffset);
        if (hasSeparateAlphaSelectors(mode)) {
            readSelectors(source, offset, block, !firstSelectorStreamIsAlpha, bitOffset);
        }
        if (bitOffset[0] != 128) {
            throw new BasisDecodeException("BC7 block reader consumed an invalid bit count for mode "
                    + mode + ": " + bitOffset[0]);
        }

        int[][] low = new int[totalSubsets][4];
        int[][] high = new int[totalSubsets][4];
        for (int subset = 0; subset < totalSubsets; subset++) {
            for (int comp = 0; comp < 4; comp++) {
                if (comp == 3 && mode < 4) {
                    low[subset][comp] = 255;
                    high[subset][comp] = 255;
                } else {
                    low[subset][comp] = endpointTo8(mode, comp, block.low[subset][comp],
                            block.pbits[subset][0]);
                    high[subset][comp] = endpointTo8(mode, comp, block.high[subset][comp],
                            block.pbits[subset][1]);
                }
            }
        }

        int colorIndexBits = colorIndexBits(block);
        int alphaIndexBits = alphaIndexBits(block);
        int[] colorWeights = BC7_WEIGHTS[colorIndexBits - 1];
        int[] alphaWeights = hasSeparateAlphaSelectors(mode)
                ? BC7_WEIGHTS[alphaIndexBits - 1]
                : colorWeights;
        for (int i = 0; i < 16; i++) {
            int subset = block.partitionMap[i];
            int colorWeight = colorWeights[block.selectors[i]];
            int alphaWeight = alphaWeights[hasSeparateAlphaSelectors(mode)
                    ? block.alphaSelectors[i]
                    : block.selectors[i]];
            int out = i * 4;
            for (int comp = 0; comp < 3; comp++) {
                rgba[out + comp] = interpolate(low[subset][comp], high[subset][comp], colorWeight);
            }
            rgba[out + 3] = mode < 4
                    ? 255
                    : interpolate(low[subset][3], high[subset][3], alphaWeight);
            if (hasSeparateAlphaSelectors(mode) && block.rotation != 0) {
                int swap = block.rotation - 1;
                int tmp = rgba[out + swap];
                rgba[out + swap] = rgba[out + 3];
                rgba[out + 3] = tmp;
            }
        }
    }

    static void packMode5Solid(byte[] output, int offset, int r, int g, int b, int a) {
        int[] rEndpoints = mode5OptimalEndpoint(r);
        int[] gEndpoints = mode5OptimalEndpoint(g);
        int[] bEndpoints = mode5OptimalEndpoint(b);

        long bits = rEndpoints[0] | ((long) rEndpoints[1] << 7);
        bits |= ((long) gEndpoints[0] << 14) | ((long) gEndpoints[1] << 21);
        bits |= ((long) bEndpoints[0] << 28) | ((long) bEndpoints[1] << 35);
        bits |= ((long) a << 42) | ((long) a << 50);

        output[offset] = 0b00100000;
        for (int i = 1; i < 8; i++) {
            output[offset + i] = (byte) (bits >>> ((i - 1) * 8));
        }

        long carry = (bits >>> 56) & 3;
        output[offset + 8] = (byte) (0xac | carry);
        output[offset + 9] = (byte) 0xaa;
        output[offset + 10] = (byte) 0xaa;
        output[offset + 11] = (byte) 0xaa;
        for (int i = 12; i < 16; i++) {
            output[offset + i] = 0;
        }
    }

    private static void writeSelectors(
            byte[] output,
            int offset,
            Block block,
            int[] anchors,
            boolean alpha,
            int[] bitOffset) {
        int mode = block.mode;
        int indexBits = alpha ? alphaIndexBits(block) : colorIndexBits(block);
        int[] selectors = alpha ? block.alphaSelectors : block.selectors;
        for (int i = 0; i < 16; i++) {
            int bits = indexBits;
            if (i == anchors[0] || i == anchors[1] || i == anchors[2]) {
                bits--;
            }
            setBits(output, offset, selectors[i], bits, bitOffset);
        }
    }

    private static void readSelectors(
            byte[] source,
            int offset,
            Block block,
            boolean alpha,
            int[] bitOffset) {
        int mode = block.mode;
        int indexBits = alpha ? alphaIndexBits(block) : colorIndexBits(block);
        int[] selectors = alpha ? block.alphaSelectors : block.selectors;
        int[] anchors = anchors(mode, block.partition);
        for (int i = 0; i < 16; i++) {
            int bits = indexBits;
            if (i == anchors[0] || i == anchors[1] || i == anchors[2]) {
                bits--;
            }
            selectors[i] = getBits(source, offset, bits, bitOffset);
        }
    }

    private static int endpointTo8(int mode, int comp, int endpoint, int pbit) {
        int bits = comp == 3 ? ALPHA_PRECISION[mode] : COLOR_PRECISION[mode];
        if (HAS_P_BITS[mode]) {
            return scaleEndpoint((endpoint << 1) | pbit, bits + 1);
        }
        return scaleEndpoint(endpoint, bits);
    }

    private static int scaleEndpoint(int value, int totalBits) {
        int scaled = value << (8 - totalBits);
        return scaled | (scaled >>> totalBits);
    }

    private static int interpolate(int low, int high, int weight) {
        return (low * (64 - weight) + high * weight + 32) >> 6;
    }

    private static int getBits(byte[] source, int offset, int count, int[] bitOffset) {
        int result = 0;
        int shift = 0;
        int remaining = count;
        while (remaining > 0) {
            int byteIndex = offset + (bitOffset[0] >>> 3);
            int sourceByte = Byte.toUnsignedInt(source[byteIndex]);
            int bitInByte = bitOffset[0] & 7;
            int bits = Math.min(8 - bitInByte, remaining);
            int mask = (1 << bits) - 1;
            result |= ((sourceByte >>> bitInByte) & mask) << shift;
            bitOffset[0] += bits;
            shift += bits;
            remaining -= bits;
        }
        return result;
    }

    private static int[] anchors(int mode, int partition) {
        int[] anchors = {0, -1, -1};
        int totalSubsets = NUM_SUBSETS[mode];
        if (totalSubsets > 1) {
            anchors[1] = totalSubsets == 3
                    ? THIRD_SUBSET_ANCHOR_1[partition]
                    : SECOND_SUBSET_ANCHOR[partition];
        }
        if (totalSubsets > 2) {
            anchors[2] = THIRD_SUBSET_ANCHOR_2[partition];
        }
        return anchors;
    }

    private static boolean hasSeparateAlphaSelectors(int mode) {
        return mode == 4 || mode == 5;
    }

    private static int colorIndexBits(Block block) {
        return block.mode == 4 && block.indexSelector != 0 ? ALPHA_INDEX_BITS[block.mode]
                : COLOR_INDEX_BITS[block.mode];
    }

    private static int alphaIndexBits(Block block) {
        return block.mode == 4 && block.indexSelector != 0 ? COLOR_INDEX_BITS[block.mode]
                : ALPHA_INDEX_BITS[block.mode];
    }

    private static void swapRgbEndpoints(Block block, int subset) {
        for (int comp = 0; comp < 3; comp++) {
            int tmp = block.low[subset][comp];
            block.low[subset][comp] = block.high[subset][comp];
            block.high[subset][comp] = tmp;
        }
    }

    private static void swapEndpoints(Block block, int subset) {
        for (int comp = 0; comp < 4; comp++) {
            int tmp = block.low[subset][comp];
            block.low[subset][comp] = block.high[subset][comp];
            block.high[subset][comp] = tmp;
        }
    }

    private static void setBits(byte[] output, int offset, int value, int count, int[] bitOffset) {
        int remaining = count;
        int current = count == 32 ? value : value & ((1 << count) - 1);
        while (remaining > 0) {
            int bits = Math.min(8 - (bitOffset[0] & 7), remaining);
            output[offset + (bitOffset[0] >>> 3)] |= (byte) (current << (bitOffset[0] & 7));
            current >>>= bits;
            remaining -= bits;
            bitOffset[0] += bits;
        }
    }

    static final class Block {
        final int[][] low = new int[3][4];
        final int[][] high = new int[3][4];
        final int[][] pbits = new int[3][2];
        final int[] selectors = new int[16];
        final int[] alphaSelectors = new int[16];
        int mode;
        int rotation;
        int indexSelector;
        int partition;
        int[] partitionMap;

        Block(int mode) {
            this.mode = mode;
        }

        Block copy() {
            Block copy = new Block(mode);
            copy.rotation = rotation;
            copy.indexSelector = indexSelector;
            copy.partition = partition;
            copy.partitionMap = partitionMap == null ? null : partitionMap.clone();
            for (int i = 0; i < low.length; i++) {
                System.arraycopy(low[i], 0, copy.low[i], 0, low[i].length);
                System.arraycopy(high[i], 0, copy.high[i], 0, high[i].length);
                System.arraycopy(pbits[i], 0, copy.pbits[i], 0, pbits[i].length);
            }
            System.arraycopy(selectors, 0, copy.selectors, 0, selectors.length);
            System.arraycopy(alphaSelectors, 0, copy.alphaSelectors, 0, alphaSelectors.length);
            return copy;
        }
    }
}
