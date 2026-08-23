package org.ngengine.basis;

/**
 * ASTC constants used by the XUASTC LDR transcoder path.
 */
final class XuastcAstcConstants {
    static final int CEM_LDR_LUM_DIRECT = 0;
    static final int CEM_LDR_LUM_ALPHA_DIRECT = 4;
    static final int CEM_LDR_RGB_BASE_SCALE = 6;
    static final int CEM_LDR_RGB_DIRECT = 8;
    static final int CEM_LDR_RGB_BASE_PLUS_OFFSET = 9;
    static final int CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A = 10;
    static final int CEM_LDR_RGBA_DIRECT = 12;
    static final int CEM_LDR_RGBA_BASE_PLUS_OFFSET = 13;

    static final int FIRST_VALID_ENDPOINT_ISE_RANGE = 4;
    static final int FIRST_VALID_WEIGHT_ISE_RANGE = 0;
    static final int LAST_VALID_ENDPOINT_ISE_RANGE = 20;
    static final int LAST_VALID_WEIGHT_ISE_RANGE = 11;
    static final int TOTAL_ENDPOINT_ISE_RANGES =
            LAST_VALID_ENDPOINT_ISE_RANGE - FIRST_VALID_ENDPOINT_ISE_RANGE + 1;
    static final int TOTAL_WEIGHT_ISE_RANGES =
            LAST_VALID_WEIGHT_ISE_RANGE - FIRST_VALID_WEIGHT_ISE_RANGE + 1;

    static final int OTM_NUM_CEMS = 14;
    static final int OTM_NUM_SUBSETS = 3;
    static final int OTM_NUM_CCS = 5;
    static final int OTM_NUM_GRID_SIZES = 2;
    static final int OTM_NUM_GRID_ANISOS = 3;

    private static final int[] UNIQUE_LDR_INDEX_TO_ASTC_CEM = {
            CEM_LDR_LUM_DIRECT,
            CEM_LDR_LUM_ALPHA_DIRECT,
            CEM_LDR_RGB_BASE_SCALE,
            CEM_LDR_RGB_DIRECT,
            CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A,
            CEM_LDR_RGBA_DIRECT
    };
    private static final int[][] ISE_RANGE_TABLE = {
            {1, 0, 0}, {0, 1, 0}, {2, 0, 0}, {0, 0, 1},
            {1, 1, 0}, {3, 0, 0}, {1, 0, 1}, {2, 1, 0},
            {4, 0, 0}, {2, 0, 1}, {3, 1, 0}, {5, 0, 0},
            {3, 0, 1}, {4, 1, 0}, {6, 0, 0}, {4, 0, 1},
            {5, 1, 0}, {7, 0, 0}, {5, 0, 1}, {6, 1, 0},
            {8, 0, 0}
    };
    private static final int[][] TOTAL_UNIQUE_PATTERNS = {
            {437, 329}, {559, 405}, {659, 486}, {720, 534},
            {521, 333}, {584, 377}, {640, 410}, {672, 436},
            {710, 468}, {701, 476}, {759, 528}, {799, 568},
            {818, 597}, {838, 626}
    };
    private static final float[] SCALE_QUANT_STEPS = {
            1.51333141f, 1.41198814f, 1.35588217f, 1.31743157f,
            1.28835952f, 1.24573100f, 1.21481407f, 1.19067919f,
            1.15431654f, 1.12734985f, 1.10601568f, 1.07348967f
    };
    private static final int[][] WEIGHT_ISE_TO_RANK = buildIseToRankTables(
            FIRST_VALID_WEIGHT_ISE_RANGE, TOTAL_WEIGHT_ISE_RANGES, true);
    private static final int[][] WEIGHT_RANK_TO_ISE = buildRankToIseTables(
            FIRST_VALID_WEIGHT_ISE_RANGE, TOTAL_WEIGHT_ISE_RANGES, true);
    private static final int[][] ENDPOINT_ISE_TO_RANK = buildIseToRankTables(
            FIRST_VALID_ENDPOINT_ISE_RANGE, TOTAL_ENDPOINT_ISE_RANGES, false);
    private static final int[][] ENDPOINT_RANK_TO_ISE = buildRankToIseTables(
            FIRST_VALID_ENDPOINT_ISE_RANGE, TOTAL_ENDPOINT_ISE_RANGES, false);

    private XuastcAstcConstants() {
    }

    static int uniqueLdrIndexToAstcCem(int uniqueIndex) {
        if (uniqueIndex < 0 || uniqueIndex >= UNIQUE_LDR_INDEX_TO_ASTC_CEM.length) {
            throw new BasisDecodeException("Invalid XUASTC LDR CEM index: " + uniqueIndex);
        }
        return UNIQUE_LDR_INDEX_TO_ASTC_CEM[uniqueIndex];
    }

    static int cemToLdrCemIndex(int cem) {
        switch (cem) {
            case CEM_LDR_LUM_DIRECT:
                return 0;
            case CEM_LDR_LUM_ALPHA_DIRECT:
                return 1;
            case CEM_LDR_RGB_BASE_SCALE:
                return 2;
            case CEM_LDR_RGB_DIRECT:
                return 3;
            case CEM_LDR_RGB_BASE_PLUS_OFFSET:
                return 4;
            case CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A:
                return 5;
            case CEM_LDR_RGBA_DIRECT:
                return 6;
            case CEM_LDR_RGBA_BASE_PLUS_OFFSET:
                return 7;
            default:
                throw new BasisDecodeException("Unsupported XUASTC LDR CEM: " + cem);
        }
    }

    static boolean isDirectCem(int cem) {
        return cem == CEM_LDR_RGB_DIRECT || cem == CEM_LDR_RGBA_DIRECT;
    }

    static int numCemEndpointValues(int cem) {
        if (cem < 0 || cem > 15) {
            throw new BasisDecodeException("Invalid ASTC CEM: " + cem);
        }
        return 2 + 2 * (cem >> 2);
    }

    static int getIseLevels(int iseRange) {
        if (iseRange < 0 || iseRange >= ISE_RANGE_TABLE.length) {
            throw new BasisDecodeException("Invalid ASTC ISE range: " + iseRange);
        }
        int[] range = ISE_RANGE_TABLE[iseRange];
        return (1 + 2 * range[1] + 4 * range[2]) << range[0];
    }

    static int getIseBitCount(int iseRange) {
        return getIseRangeField(iseRange, 0);
    }

    static int getIseTritCount(int iseRange) {
        return getIseRangeField(iseRange, 1);
    }

    static int getIseQuintCount(int iseRange) {
        return getIseRangeField(iseRange, 2);
    }

    static int getTotalUniquePatterns(int blockSizeIndex, int numberOfPartitions) {
        if (blockSizeIndex < 0 || blockSizeIndex >= TOTAL_UNIQUE_PATTERNS.length
                || numberOfPartitions < 2 || numberOfPartitions > 3) {
            throw new BasisDecodeException("Invalid ASTC partition-pattern lookup");
        }
        return TOTAL_UNIQUE_PATTERNS[blockSizeIndex][numberOfPartitions - 2];
    }

    static float getScaleQuantStep(int weightIseRange) {
        if (weightIseRange < FIRST_VALID_WEIGHT_ISE_RANGE || weightIseRange > LAST_VALID_WEIGHT_ISE_RANGE) {
            throw new BasisDecodeException("Invalid ASTC weight ISE range: " + weightIseRange);
        }
        return SCALE_QUANT_STEPS[weightIseRange];
    }

    static int dequantBiseWeight(int val, int iseRange) {
        if (val < 0 || val >= getIseLevels(iseRange)) {
            throw new BasisDecodeException("Invalid ASTC weight ISE value");
        }
        int result;
        switch (iseRange) {
            case 0:
                result = val != 0 ? 63 : 0;
                break;
            case 1:
                result = new int[] {0, 32, 63}[val];
                break;
            case 2:
                result = bitReplicationScale(val, 2, 6);
                break;
            case 3:
                result = new int[] {0, 16, 32, 47, 63}[val];
                break;
            case 5:
                result = bitReplicationScale(val, 3, 6);
                break;
            case 8:
                result = bitReplicationScale(val, 4, 6);
                break;
            case 11:
                result = bitReplicationScale(val, 5, 6);
                break;
            default:
                result = dequantBiseMixedWeight(val, iseRange);
                break;
        }
        return result > 32 ? result + 1 : result;
    }

    static int dequantBiseEndpoint(int val, int iseRange) {
        if (val < 0 || val >= getIseLevels(iseRange)) {
            throw new BasisDecodeException("Invalid ASTC endpoint ISE value");
        }
        switch (iseRange) {
            case 5:
                return bitReplicationScale(val, 3, 8);
            case 8:
                return bitReplicationScale(val, 4, 8);
            case 11:
                return bitReplicationScale(val, 5, 8);
            case 14:
                return bitReplicationScale(val, 6, 8);
            case 17:
                return bitReplicationScale(val, 7, 8);
            case 20:
                return val;
            default:
                return dequantBiseMixedEndpoint(val, iseRange);
        }
    }

    static int[] decodeRgbDirectEndpoints(int[] endpoints, int offset, int endpointIseRange) {
        return decodeLdrEndpoints(CEM_LDR_RGB_DIRECT, endpoints, offset, endpointIseRange);
    }

    static int[] decodeLdrEndpoints(int cem, int[] endpoints, int offset, int endpointIseRange) {
        int count = numCemEndpointValues(cem);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = dequantBiseEndpoint(endpoints[offset + i], endpointIseRange);
        }
        switch (cem) {
            case CEM_LDR_LUM_DIRECT:
                return new int[] {values[0], values[0], values[0], 255, values[1], values[1], values[1], 255};
            case CEM_LDR_LUM_ALPHA_DIRECT:
                return new int[] {
                        values[0], values[0], values[0], values[2],
                        values[1], values[1], values[1], values[3]
                };
            case CEM_LDR_RGB_BASE_SCALE:
                return new int[] {
                        (values[0] * values[3]) >> 8,
                        (values[1] * values[3]) >> 8,
                        (values[2] * values[3]) >> 8,
                        255,
                        values[0], values[1], values[2], 255
                };
            case CEM_LDR_RGB_DIRECT:
                return decodeRgbDirectValues(
                        values[0], values[1], values[2], values[3], values[4], values[5]);
            case CEM_LDR_RGB_BASE_PLUS_OFFSET:
                return decodeRgbBasePlusOffset(
                        values[0], values[1], values[2], values[3], values[4], values[5]);
            case CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A:
                return new int[] {
                        (values[0] * values[3]) >> 8,
                        (values[1] * values[3]) >> 8,
                        (values[2] * values[3]) >> 8,
                        values[4],
                        values[0], values[1], values[2], values[5]
                };
            case CEM_LDR_RGBA_DIRECT:
                return decodeRgbaDirectValues(
                        values[0], values[1], values[2], values[3],
                        values[4], values[5], values[6], values[7]);
            case CEM_LDR_RGBA_BASE_PLUS_OFFSET:
                return decodeRgbaBasePlusOffset(
                        values[0], values[1], values[2], values[3],
                        values[4], values[5], values[6], values[7]);
            default:
                throw new BasisDecodeException("Unsupported XUASTC LDR endpoint CEM: " + cem);
        }
    }

    static int weightIseToRank(int ise, int iseRange) {
        int[] table = tableForRange(
                WEIGHT_ISE_TO_RANK, iseRange, FIRST_VALID_WEIGHT_ISE_RANGE, LAST_VALID_WEIGHT_ISE_RANGE);
        if (ise < 0 || ise >= table.length) {
            throw new BasisDecodeException("Invalid ASTC weight ISE value");
        }
        return table[ise];
    }

    static int weightRankToIse(int rank, int iseRange) {
        int[] table = tableForRange(
                WEIGHT_RANK_TO_ISE, iseRange, FIRST_VALID_WEIGHT_ISE_RANGE, LAST_VALID_WEIGHT_ISE_RANGE);
        if (rank < 0 || rank >= table.length) {
            throw new BasisDecodeException("Invalid ASTC weight rank value");
        }
        return table[rank];
    }

    static int endpointIseToRank(int ise, int iseRange) {
        int[] table = tableForRange(
                ENDPOINT_ISE_TO_RANK,
                iseRange,
                FIRST_VALID_ENDPOINT_ISE_RANGE,
                LAST_VALID_ENDPOINT_ISE_RANGE);
        if (ise < 0 || ise >= table.length) {
            throw new BasisDecodeException("Invalid ASTC endpoint ISE value");
        }
        return table[ise];
    }

    static int endpointRankToIse(int rank, int iseRange) {
        int[] table = tableForRange(
                ENDPOINT_RANK_TO_ISE,
                iseRange,
                FIRST_VALID_ENDPOINT_ISE_RANGE,
                LAST_VALID_ENDPOINT_ISE_RANGE);
        if (rank < 0 || rank >= table.length) {
            throw new BasisDecodeException("Invalid ASTC endpoint rank value");
        }
        return table[rank];
    }

    static boolean cemSupportsBlueContraction(int cem) {
        return cem == CEM_LDR_RGB_DIRECT
                || cem == CEM_LDR_RGBA_DIRECT
                || cem == CEM_LDR_RGB_BASE_PLUS_OFFSET
                || cem == CEM_LDR_RGBA_BASE_PLUS_OFFSET;
    }

    static boolean usedBlueContraction(int cem, int[] endpoints, int endpointIseRange) {
        if (cem == CEM_LDR_RGB_DIRECT || cem == CEM_LDR_RGBA_DIRECT) {
            int r0 = dequantBiseEndpoint(endpoints[0], endpointIseRange);
            int r1 = dequantBiseEndpoint(endpoints[1], endpointIseRange);
            int g0 = dequantBiseEndpoint(endpoints[2], endpointIseRange);
            int g1 = dequantBiseEndpoint(endpoints[3], endpointIseRange);
            int b0 = dequantBiseEndpoint(endpoints[4], endpointIseRange);
            int b1 = dequantBiseEndpoint(endpoints[5], endpointIseRange);
            return r1 + g1 + b1 < r0 + g0 + b0;
        }
        if (cem == CEM_LDR_RGB_BASE_PLUS_OFFSET || cem == CEM_LDR_RGBA_BASE_PLUS_OFFSET) {
            int[] red = bitTransferSigned(
                    dequantBiseEndpoint(endpoints[1], endpointIseRange),
                    dequantBiseEndpoint(endpoints[0], endpointIseRange));
            int[] green = bitTransferSigned(
                    dequantBiseEndpoint(endpoints[3], endpointIseRange),
                    dequantBiseEndpoint(endpoints[2], endpointIseRange));
            int[] blue = bitTransferSigned(
                    dequantBiseEndpoint(endpoints[5], endpointIseRange),
                    dequantBiseEndpoint(endpoints[4], endpointIseRange));
            return red[0] + green[0] + blue[0] < 0;
        }
        return false;
    }

    static int nearestWeightIse(int value, int iseRange) {
        int clamped = Math.max(0, Math.min(64, value));
        int bestIndex = 0;
        int bestError = Integer.MAX_VALUE;
        int levels = getIseLevels(iseRange);
        for (int i = 0; i < levels; i++) {
            int error = Math.abs(dequantBiseWeight(i, iseRange) - clamped);
            if (error < bestError) {
                bestError = error;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    static int gridAniso(int gridWidth, int gridHeight, int blockWidth, int blockHeight) {
        int lhs = gridWidth * blockHeight;
        int rhs = gridHeight * blockWidth;
        if (lhs == rhs) {
            return 0;
        }
        return lhs >= rhs ? 1 : 2;
    }

    private static int[][] buildIseToRankTables(int firstRange, int rangeCount, boolean weights) {
        int[][] rankToIse = buildRankToIseTables(firstRange, rangeCount, weights);
        int[][] iseToRank = new int[rangeCount][];
        for (int rangeOffset = 0; rangeOffset < rangeCount; rangeOffset++) {
            int[] rankTable = rankToIse[rangeOffset];
            int[] inverse = new int[rankTable.length];
            for (int rank = 0; rank < rankTable.length; rank++) {
                inverse[rankTable[rank]] = rank;
            }
            iseToRank[rangeOffset] = inverse;
        }
        return iseToRank;
    }

    private static int[][] buildRankToIseTables(int firstRange, int rangeCount, boolean weights) {
        int[][] tables = new int[rangeCount][];
        for (int rangeOffset = 0; rangeOffset < rangeCount; rangeOffset++) {
            int range = firstRange + rangeOffset;
            int levels = getIseLevels(range);
            int[] table = new int[levels];
            if (ISE_RANGE_TABLE[range][1] == 0 && ISE_RANGE_TABLE[range][2] == 0) {
                for (int i = 0; i < levels; i++) {
                    table[i] = i;
                }
            } else {
                int[] sortable = new int[levels];
                for (int ise = 0; ise < levels; ise++) {
                    int dequantized = weights
                            ? dequantBiseWeight(ise, range)
                            : dequantBiseEndpoint(ise, range);
                    sortable[ise] = (dequantized << 16) | ise;
                }
                java.util.Arrays.sort(sortable);
                for (int rank = 0; rank < levels; rank++) {
                    table[rank] = sortable[rank] & 0xFFFF;
                }
            }
            tables[rangeOffset] = table;
        }
        return tables;
    }

    private static int[] tableForRange(int[][] tables, int range, int firstRange, int lastRange) {
        if (range < firstRange || range > lastRange) {
            throw new BasisDecodeException("Invalid ASTC ISE range: " + range);
        }
        return tables[range - firstRange];
    }

    private static int[] decodeRgbDirectValues(int v0, int v1, int v2, int v3, int v4, int v5) {
        if (v1 + v3 + v5 >= v0 + v2 + v4) {
            return new int[] {v0, v2, v4, 255, v1, v3, v5, 255};
        }
        return new int[] {
                (v1 + v5) >> 1, (v3 + v5) >> 1, v5, 255,
                (v0 + v4) >> 1, (v2 + v4) >> 1, v4, 255
        };
    }

    private static int[] decodeRgbaDirectValues(
            int v0,
            int v1,
            int v2,
            int v3,
            int v4,
            int v5,
            int v6,
            int v7) {
        if (v1 + v3 + v5 >= v0 + v2 + v4) {
            return new int[] {v0, v2, v4, v6, v1, v3, v5, v7};
        }
        int[] low = blueContract(v1, v3, v5, v7);
        int[] high = blueContract(v0, v2, v4, v6);
        return new int[] {low[0], low[1], low[2], low[3], high[0], high[1], high[2], high[3]};
    }

    private static int[] decodeRgbBasePlusOffset(int v0, int v1, int v2, int v3, int v4, int v5) {
        int[] red = bitTransferSigned(v1, v0);
        int[] green = bitTransferSigned(v3, v2);
        int[] blue = bitTransferSigned(v5, v4);
        if (red[0] + green[0] + blue[0] >= 0) {
            return new int[] {
                    red[1], green[1], blue[1], 255,
                    clamp(red[1] + red[0], 0, 255),
                    clamp(green[1] + green[0], 0, 255),
                    clamp(blue[1] + blue[0], 0, 255),
                    255
            };
        }
        int[] low = blueContract(red[1] + red[0], green[1] + green[0], blue[1] + blue[0], 255);
        int[] high = blueContract(red[1], green[1], blue[1], 255);
        return clampEndpoints(new int[] {
                low[0], low[1], low[2], low[3],
                high[0], high[1], high[2], high[3]
        });
    }

    private static int[] decodeRgbaBasePlusOffset(
            int v0,
            int v1,
            int v2,
            int v3,
            int v4,
            int v5,
            int v6,
            int v7) {
        int[] red = bitTransferSigned(v1, v0);
        int[] green = bitTransferSigned(v3, v2);
        int[] blue = bitTransferSigned(v5, v4);
        int[] alpha = bitTransferSigned(v7, v6);
        if (red[0] + green[0] + blue[0] >= 0) {
            return clampEndpoints(new int[] {
                    red[1], green[1], blue[1], alpha[1],
                    red[1] + red[0], green[1] + green[0], blue[1] + blue[0], alpha[1] + alpha[0]
            });
        }
        int[] low = blueContract(
                red[1] + red[0], green[1] + green[0], blue[1] + blue[0], alpha[1] + alpha[0]);
        int[] high = blueContract(red[1], green[1], blue[1], alpha[1]);
        return clampEndpoints(new int[] {
                low[0], low[1], low[2], low[3],
                high[0], high[1], high[2], high[3]
        });
    }

    private static int[] bitTransferSigned(int delta, int base) {
        int transferredBase = (base >> 1) | (delta & 0x80);
        int signedDelta = (delta >> 1) & 0x3F;
        if ((signedDelta & 0x20) != 0) {
            signedDelta -= 0x40;
        }
        return new int[] {signedDelta, transferredBase};
    }

    private static int[] blueContract(int r, int g, int b, int a) {
        return new int[] {(r + b) >> 1, (g + b) >> 1, b, a};
    }

    private static int[] clampEndpoints(int[] endpoints) {
        for (int i = 0; i < endpoints.length; i++) {
            endpoints[i] = clamp(endpoints[i], 0, 255);
        }
        return endpoints;
    }

    private static int clamp(int value, int low, int high) {
        if (value < low) {
            return low;
        }
        return Math.min(value, high);
    }

    private static int dequantBiseMixedWeight(int val, int iseRange) {
        int numBits = ISE_RANGE_TABLE[iseRange][0];
        boolean hasQuints = ISE_RANGE_TABLE[iseRange][2] != 0;
        int rangeIndex = numBits * 2 + (hasQuints ? 1 : 0);
        int bits = val & ((1 << numBits) - 1);
        int d = val >> numBits;
        int a = bits & 1;
        int b = (bits >> 1) & 1;
        int c = (bits >> 2) & 1;
        int aa = a == 0 ? 0 : 0x7F;
        int bb = 0;
        if (rangeIndex == 4) {
            bb = (b << 6) | (b << 2) | b;
        } else if (rangeIndex == 5) {
            bb = (b << 6) | (b << 1);
        } else if (rangeIndex == 6) {
            bb = (c << 6) | (b << 5) | (c << 1) | b;
        }
        int[] cTable = {50, 28, 23, 13, 11};
        int u = d * cTable[rangeIndex - 2] + bb;
        u ^= aa;
        return (aa & 0x20) | (u >> 2);
    }

    private static int dequantBiseMixedEndpoint(int val, int iseRange) {
        int numBits = ISE_RANGE_TABLE[iseRange][0];
        boolean hasQuints = ISE_RANGE_TABLE[iseRange][2] != 0;
        int rangeIndex = (numBits * 2 + (hasQuints ? 1 : 0)) - 2;
        int bits = val & ((1 << numBits) - 1);
        int tval = val >> numBits;
        int a = bits & 1;
        int b = (bits >> 1) & 1;
        int c = (bits >> 2) & 1;
        int d = (bits >> 3) & 1;
        int e = (bits >> 4) & 1;
        int f = (bits >> 5) & 1;
        int aa = a != 0 ? 511 : 0;
        int bb = 0;
        switch (rangeIndex) {
            case 2:
                bb = (b << 1) | (b << 2) | (b << 4) | (b << 8);
                break;
            case 3:
                bb = (b << 2) | (b << 3) | (b << 8);
                break;
            case 4:
                bb = b | (c << 1) | (b << 2) | (c << 3) | (b << 7) | (c << 8);
                break;
            case 5:
                bb = c | (b << 1) | (c << 2) | (b << 7) | (c << 8);
                break;
            case 6:
                bb = b | (c << 1) | (d << 2) | (b << 6) | (c << 7) | (d << 8);
                break;
            case 7:
                bb = c | (d << 1) | (b << 6) | (c << 7) | (d << 8);
                break;
            case 8:
                bb = d | (e << 1) | (b << 5) | (c << 6) | (d << 7) | (e << 8);
                break;
            case 9:
                bb = e | (b << 5) | (c << 6) | (d << 7) | (e << 8);
                break;
            case 10:
                bb = f | (b << 4) | (c << 5) | (d << 6) | (e << 7) | (f << 8);
                break;
            default:
                break;
        }
        int[] cVals = {204, 113, 93, 54, 44, 26, 22, 13, 11, 6, 5};
        int u = tval * cVals[rangeIndex] + bb;
        u ^= aa;
        return (aa & 0x80) | (u >> 2);
    }

    private static int bitReplicationScale(int src, int numSrcBits, int numDstBits) {
        int dst = 0;
        for (int shift = numDstBits - numSrcBits; shift > -numSrcBits; shift -= numSrcBits) {
            dst |= shift >= 0 ? src << shift : src >> -shift;
        }
        return dst;
    }

    private static int getIseRangeField(int iseRange, int field) {
        if (iseRange < 0 || iseRange >= ISE_RANGE_TABLE.length) {
            throw new BasisDecodeException("Invalid ASTC ISE range: " + iseRange);
        }
        return ISE_RANGE_TABLE[iseRange][field];
    }
}
