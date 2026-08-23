package org.ngengine.basis;

import java.util.Arrays;

/**
 * Decodes physical ASTC HDR blocks to the CPU HDR targets exposed by basisu.
 */
final class AstcHdrBlockDecoder {
    private static final int ASTC_BLOCK_BYTES = 16;
    private static final int MAX_GRID_WEIGHTS = 64;
    private static final int MAX_ENDPOINTS = 18;
    private static final int MAX_PARTITIONS = 4;
    private static final int CEM_LDR_LUM_BASE_PLUS_OFS = 1;
    private static final int CEM_HDR_LUM_LARGE_RANGE = 2;
    private static final int CEM_HDR_LUM_SMALL_RANGE = 3;
    private static final int CEM_LDR_LUM_ALPHA_BASE_PLUS_OFS = 5;
    private static final int CEM_HDR_RGB_BASE_SCALE = 7;
    private static final int CEM_HDR_RGB = 11;
    private static final int CEM_HDR_RGB_LDR_ALPHA = 14;
    private static final int CEM_HDR_RGB_HDR_ALPHA = 15;
    private static final int[][] DEC_ROWS = {
            {10, 9, 7, 2, 5, 2, 4, 2, 4, 0, 1},
            {10, 9, 7, 2, 5, 2, 8, 2, 4, 0, 1},
            {10, 9, 5, 2, 7, 2, 2, 8, 4, 0, 1},
            {10, 9, 5, 2, 7, 1, 2, 6, 4, 0, 1},
            {10, 9, 7, 1, 5, 2, 2, 2, 4, 0, 1},
            {10, 9, 0, 0, 5, 2, 12, 2, 4, 2, 3},
            {10, 9, 5, 2, 0, 0, 2, 12, 4, 2, 3},
            {10, 9, 0, 0, 0, 0, 6, 10, 4, 2, 3},
            {10, 9, 0, 0, 0, 0, 10, 6, 4, 2, 3},
            {-1, -1, 5, 2, 9, 2, 6, 6, 4, 2, 3}
    };
    private static final int[][] TRIT_DECODE = buildTritDecode();
    private static final int[][] QUINT_DECODE = buildQuintDecode();

    private AstcHdrBlockDecoder() {
    }

    static byte[] decodeToRgbHalf(byte[] astc, int width, int height, int blockWidth, int blockHeight) {
        return decodeImage(astc, width, height, blockWidth, blockHeight, 3, false);
    }

    static byte[] decodeToRgbaHalf(byte[] astc, int width, int height, int blockWidth, int blockHeight) {
        return decodeImage(astc, width, height, blockWidth, blockHeight, 4, false);
    }

    static byte[] decodeToRgb9e5(byte[] astc, int width, int height, int blockWidth, int blockHeight) {
        return decodeImage(astc, width, height, blockWidth, blockHeight, 1, true);
    }

    static int[] decodeBlockToRgbHalf(byte[] astc, int offset, int blockWidth, int blockHeight) {
        LogicalBlock block = unpackLogicalBlock(astc, offset, blockWidth, blockHeight);
        int[] halfRgba = new int[blockWidth * blockHeight * 4];
        int[] rgb = new int[blockWidth * blockHeight * 3];
        decodeBlockHalfRgba(block, halfRgba, blockWidth, blockHeight);
        for (int i = 0; i < blockWidth * blockHeight; i++) {
            rgb[i * 3] = halfRgba[i * 4];
            rgb[i * 3 + 1] = halfRgba[i * 4 + 1];
            rgb[i * 3 + 2] = halfRgba[i * 4 + 2];
        }
        return rgb;
    }

    static LogicalBlock unpackLogicalBlock(byte[] astc, int offset, int blockWidth, int blockHeight) {
        LogicalBlock block = new LogicalBlock();
        unpackBlock(astc, offset, block, blockWidth, blockHeight);
        return block;
    }

    static boolean decodeQlogEndpoint(
            int colorEndpointMode,
            int endpointIseRange,
            int[] endpoints,
            int endpointOffset,
            int[][] decodedEndpoint) {
        int count = XuastcAstcConstants.numCemEndpointValues(colorEndpointMode);
        int totalEndpointLevels = XuastcAstcConstants.getIseLevels(endpointIseRange);
        int[] dequantizedEndpoints = new int[count];
        for (int i = 0; i < count; i++) {
            int endpoint = endpoints[endpointOffset + i];
            if (endpoint >= totalEndpointLevels) {
                return false;
            }
            dequantizedEndpoints[i] = XuastcAstcConstants.dequantBiseEndpoint(endpoint, endpointIseRange);
        }
        decodeEndpoint(colorEndpointMode, decodedEndpoint, dequantizedEndpoints);
        for (int c = 0; c < 3; c++) {
            if (decodedEndpoint[c][0] > 3967 || decodedEndpoint[c][1] > 3967) {
                return false;
            }
        }
        return true;
    }

    static int qlogToHalf(int qlog, int bits) {
        return qlog16ToHalf(qlog << (16 - bits));
    }

    static boolean isInvalidHalf(int half) {
        return isHalfInfOrNan(half);
    }

    private static byte[] decodeImage(
            byte[] astc,
            int width,
            int height,
            int blockWidth,
            int blockHeight,
            int halfComponents,
            boolean rgb9e5) {
        int blocksX = divideRoundUp(width, blockWidth);
        int blocksY = divideRoundUp(height, blockHeight);
        int expectedLength = checkedMultiply(blocksX * blocksY, ASTC_BLOCK_BYTES);
        if (astc.length != expectedLength) {
            throw new BasisDecodeException("ASTC HDR payload size mismatch");
        }
        int bytesPerTexel = rgb9e5 ? 4 : halfComponents * 2;
        byte[] output = new byte[checkedMultiply(width * height, bytesPerTexel)];
        int[] blockHalfRgba = new int[blockWidth * blockHeight * 4];
        int[] blockRgb9e5 = new int[blockWidth * blockHeight];
        LogicalBlock block = new LogicalBlock();
        int srcOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                unpackBlock(astc, srcOffset, block, blockWidth, blockHeight);
                if (rgb9e5) {
                    decodeBlockRgb9e5(block, blockRgb9e5, blockWidth, blockHeight);
                    copyRgb9e5Block(blockRgb9e5, output, bx, by, width, height, blockWidth, blockHeight);
                } else {
                    decodeBlockHalfRgba(block, blockHalfRgba, blockWidth, blockHeight);
                    copyHalfBlock(blockHalfRgba, output, bx, by, width, height, blockWidth, blockHeight, halfComponents);
                }
                srcOffset += ASTC_BLOCK_BYTES;
            }
        }
        return output;
    }

    private static void unpackBlock(byte[] source, int offset, LogicalBlock block, int blockWidth, int blockHeight) {
        block.clear();
        BitBlock bits = new BitBlock(source, offset);
        if (!decodeConfig(bits, block)) {
            block.error = true;
            return;
        }
        if (block.solidHdr || block.solidLdr) {
            return;
        }
        if (block.gridWidth > blockWidth || block.gridHeight > blockHeight) {
            block.error = true;
            return;
        }
        int totalGridWeights = (block.dualPlane ? 2 : 1) * block.gridWidth * block.gridHeight;
        int totalWeightBits = iseSequenceBits(totalGridWeights, block.weightIseRange);
        if (totalGridWeights == 0 || totalGridWeights > MAX_GRID_WEIGHTS
                || totalWeightBits < 24 || totalWeightBits > 96) {
            block.error = true;
            return;
        }
        int endOfWeightBitOffset = 128 - totalWeightBits;
        int totalExtraBits = 0;
        block.numPartitions = bits.getBits(11, 2) + 1;
        if (block.numPartitions == 1) {
            block.colorEndpointModes[0] = bits.getBits(13, 4);
        } else {
            if (block.dualPlane && block.numPartitions == 4) {
                block.error = true;
                return;
            }
            block.partitionId = bits.getBits(13, 10);
            int cemBits = bits.getBits(23, 6);
            if ((cemBits & 3) == 0) {
                Arrays.fill(block.colorEndpointModes, 0, block.numPartitions, cemBits >> 2);
            } else {
                int firstCemIndex = ((cemBits & 3) - 1) * 4;
                totalExtraBits = 3 * block.numPartitions - 4;
                if (totalWeightBits + totalExtraBits > 128) {
                    block.error = true;
                    return;
                }
                int[] bitPos = {endOfWeightBitOffset - totalExtraBits};
                int[] c = new int[4];
                int[] m = new int[4];
                cemBits >>>= 2;
                for (int i = 0; i < block.numPartitions; i++, cemBits >>>= 1) {
                    c[i] = cemBits & 1;
                }
                switch (block.numPartitions) {
                    case 2:
                        m[0] = cemBits & 3;
                        m[1] = bits.nextBits(bitPos, 2);
                        break;
                    case 3:
                        m[0] = cemBits & 1;
                        m[0] |= bits.nextBits(bitPos, 1) << 1;
                        m[1] = bits.nextBits(bitPos, 2);
                        m[2] = bits.nextBits(bitPos, 2);
                        break;
                    case 4:
                        for (int i = 0; i < 4; i++) {
                            m[i] = bits.nextBits(bitPos, 2);
                        }
                        break;
                    default:
                        block.error = true;
                        return;
                }
                for (int i = 0; i < block.numPartitions; i++) {
                    block.colorEndpointModes[i] = firstCemIndex + c[i] * 4 + m[i];
                }
            }
        }
        if (block.dualPlane) {
            totalExtraBits += 2;
            if (totalExtraBits > endOfWeightBitOffset) {
                block.error = true;
                return;
            }
            block.colorComponentSelector = bits.getBits(endOfWeightBitOffset - totalExtraBits, 2);
        }
        int configBitPos = 13 + (block.numPartitions == 1 ? 4 : 16);
        int remainingBits = 128 - configBitPos - totalExtraBits - totalWeightBits;
        if (remainingBits < 0) {
            block.error = true;
            return;
        }
        int totalEndpointValues = 0;
        for (int i = 0; i < block.numPartitions; i++) {
            totalEndpointValues += XuastcAstcConstants.numCemEndpointValues(block.colorEndpointModes[i]);
        }
        if (totalEndpointValues > MAX_ENDPOINTS) {
            block.error = true;
            return;
        }
        int endpointRange = -1;
        for (int range = XuastcAstcConstants.LAST_VALID_ENDPOINT_ISE_RANGE; range > 0; range--) {
            if (iseSequenceBits(totalEndpointValues, range) <= remainingBits) {
                endpointRange = range;
                break;
            }
        }
        if (endpointRange < XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE) {
            block.error = true;
            return;
        }
        block.endpointIseRange = endpointRange;
        decodeBise(block.endpointIseRange, block.endpoints, totalEndpointValues, bits, configBitPos);
        decodeBise(block.weightIseRange, block.weights, totalGridWeights, bits.reversed(), 0);
    }

    private static boolean decodeConfig(BitBlock bits, LogicalBlock block) {
        if (bits.getBits(0, 4) == 0) {
            return false;
        }
        if (bits.getBits(0, 2) == 0 && bits.getBits(6, 3) == 7 && bits.getBits(2, 4) != 15) {
            return false;
        }
        if (bits.getBits(0, 9) == 0x1FC) {
            return decodeVoidExtent(bits, block);
        }
        int x02 = bits.getBits(0, 2);
        int x22 = bits.getBits(2, 2);
        int x54 = bits.getBits(5, 4);
        int x81 = bits.getBits(8, 1);
        int x72 = bits.getBits(7, 2);
        int rowIndex = -1;
        if (x02 == 0) {
            if (x72 == 0) {
                rowIndex = 5;
            } else if (x72 == 1) {
                rowIndex = 6;
            } else if (x54 == 12) {
                rowIndex = 7;
            } else if (x54 == 13) {
                rowIndex = 8;
            } else if (x72 == 2) {
                rowIndex = 9;
            }
        } else if (x22 == 0) {
            rowIndex = 0;
        } else if (x22 == 1) {
            rowIndex = 1;
        } else if (x22 == 2) {
            rowIndex = 2;
        } else if (x81 == 0) {
            rowIndex = 3;
        } else {
            rowIndex = 4;
        }
        if (rowIndex < 0) {
            return false;
        }
        int[] row = DEC_ROWS[rowIndex];
        boolean p = row[1] >= 0 && bits.getBits(row[1], 1) != 0;
        block.dualPlane = row[0] >= 0 && bits.getBits(row[0], 1) != 0;
        int w = row[6] + (row[3] != 0 ? bits.getBits(row[2], row[3]) : 0);
        int h = row[7] + (row[5] != 0 ? bits.getBits(row[4], row[5]) : 0);
        int pValue = bits.getBits(row[8], 1) | (bits.getBits(row[9], 1) << 1) | (bits.getBits(row[10], 1) << 2);
        if (pValue < 2) {
            return false;
        }
        block.gridWidth = w;
        block.gridHeight = h;
        block.weightIseRange = pValue - 2 + (p ? 6 : 0);
        return block.weightIseRange <= XuastcAstcConstants.LAST_VALID_WEIGHT_ISE_RANGE;
    }

    private static boolean decodeVoidExtent(BitBlock bits, LogicalBlock block) {
        if (bits.getBits(10, 2) != 3) {
            return false;
        }
        int[] bitPos = {12};
        int minS = bits.nextBits(bitPos, 13);
        int maxS = bits.nextBits(bitPos, 13);
        int minT = bits.nextBits(bitPos, 13);
        int maxT = bits.nextBits(bitPos, 13);
        boolean allOnes = minS == 0x1FFF && maxS == 0x1FFF && minT == 0x1FFF && maxT == 0x1FFF;
        if (!allOnes && (minS >= maxS || minT >= maxT)) {
            return false;
        }
        block.solidHdr = bits.getBits(9, 1) != 0;
        block.solidLdr = !block.solidHdr;
        for (int c = 0; c < 4; c++) {
            block.solidColor[c] = bits.getBits(64 + c * 16, 16);
            if (block.solidHdr && isHalfInfOrNan(block.solidColor[c])) {
                return false;
            }
        }
        return true;
    }

    private static void decodeBlockHalfRgba(LogicalBlock block, int[] output, int blockWidth, int blockHeight) {
        if (block.error) {
            Arrays.fill(output, 0, blockWidth * blockHeight * 4, 0xFFFF);
            return;
        }
        int pixelCount = blockWidth * blockHeight;
        if (block.solidLdr) {
            int[] half = new int[4];
            for (int c = 0; c < 4; c++) {
                int v = block.solidColor[c];
                half[c] = v == 0xFFFF ? 0x3C00 : floatToHalf((float) v * (1.0f / 65536.0f), true);
            }
            fillHalf(output, pixelCount, half);
            return;
        }
        if (block.solidHdr) {
            fillHalf(output, pixelCount, block.solidColor);
            return;
        }
        DecodedBlock decoded = decodeEndpointsAndWeights(block, blockWidth, blockHeight);
        if (!decoded.valid) {
            Arrays.fill(output, 0, blockWidth * blockHeight * 4, 0xFFFF);
            return;
        }
        int[] partitionMap = block.numPartitions > 1
                ? XuastcAstcPartitioner.computePartitionMap(blockWidth, blockHeight, block.partitionId, block.numPartitions)
                : null;
        int ccs = block.dualPlane ? block.colorComponentSelector : -1;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int subset = partitionMap == null ? 0 : partitionMap[pixel];
            for (int c = 0; c < 4; c++) {
                int w = decoded.weights[c == ccs ? 1 : 0][pixel];
                int out;
                if (decoded.ldrEndpoints[subset]
                        || (block.colorEndpointModes[subset] == CEM_HDR_RGB_LDR_ALPHA && c == 3)) {
                    int le = decoded.decodedEndpoints[subset][c][0];
                    int he = decoded.decodedEndpoints[subset][c][1];
                    int k = weightInterpolate((le << 8) | le, (he << 8) | he, w);
                    out = k == 0xFFFF ? 0x3C00 : floatToHalf((float) k * (1.0f / 65536.0f), true);
                } else {
                    int le = decoded.decodedEndpoints[subset][c][0] << 4;
                    int he = decoded.decodedEndpoints[subset][c][1] << 4;
                    out = qlog16ToHalf(weightInterpolate(le, he, w));
                    if (isHalfInfOrNan(out)) {
                        out = 0x7BFF;
                    }
                }
                output[pixel * 4 + c] = out;
            }
        }
    }

    private static void decodeBlockRgb9e5(LogicalBlock block, int[] output, int blockWidth, int blockHeight) {
        if (block.error) {
            Arrays.fill(output, 0, blockWidth * blockHeight, packRgb9e5(1.0f, 0.0f, 1.0f));
            return;
        }
        int pixelCount = blockWidth * blockHeight;
        if (block.solidLdr) {
            float r = block.solidColor[0] == 0xFFFF ? 1.0f : block.solidColor[0] * (1.0f / 65536.0f);
            float g = block.solidColor[1] == 0xFFFF ? 1.0f : block.solidColor[1] * (1.0f / 65536.0f);
            float b = block.solidColor[2] == 0xFFFF ? 1.0f : block.solidColor[2] * (1.0f / 65536.0f);
            Arrays.fill(output, 0, pixelCount, packRgb9e5(r, g, b));
            return;
        }
        if (block.solidHdr) {
            Arrays.fill(output, 0, pixelCount, packRgb9e5(
                    halfToFloat(block.solidColor[0]),
                    halfToFloat(block.solidColor[1]),
                    halfToFloat(block.solidColor[2])));
            return;
        }
        DecodedBlock decoded = decodeEndpointsAndWeights(block, blockWidth, blockHeight);
        if (!decoded.valid) {
            Arrays.fill(output, 0, pixelCount, packRgb9e5(1.0f, 0.0f, 1.0f));
            return;
        }
        int[] partitionMap = block.numPartitions > 1
                ? XuastcAstcPartitioner.computePartitionMap(blockWidth, blockHeight, block.partitionId, block.numPartitions)
                : null;
        int ccs = block.dualPlane ? block.colorComponentSelector : -1;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int subset = partitionMap == null ? 0 : partitionMap[pixel];
            int[] comp = new int[3];
            for (int c = 0; c < 3; c++) {
                int w = decoded.weights[c == ccs ? 1 : 0][pixel];
                if (decoded.ldrEndpoints[subset]) {
                    int le = decoded.decodedEndpoints[subset][c][0];
                    int he = decoded.decodedEndpoints[subset][c][1];
                    comp[c] = weightInterpolate((le << 8) | le, (he << 8) | he, w);
                } else {
                    int le = decoded.decodedEndpoints[subset][c][0] << 4;
                    int he = decoded.decodedEndpoints[subset][c][1] << 4;
                    comp[c] = qlog16ToHalf(weightInterpolate(le, he, w));
                    if (isHalfInfOrNan(comp[c])) {
                        comp[c] = 0x7BFF;
                    }
                }
            }
            output[pixel] = decoded.ldrEndpoints[subset]
                    ? packRgb9e5LdrAstc(comp[0], comp[1], comp[2])
                    : packRgb9e5HdrAstc(comp[0], comp[1], comp[2]);
        }
    }

    private static DecodedBlock decodeEndpointsAndWeights(LogicalBlock block, int blockWidth, int blockHeight) {
        DecodedBlock decoded = new DecodedBlock();
        if (block.gridWidth < 2 || block.gridHeight < 2
                || block.gridWidth > blockWidth || block.gridHeight > blockHeight
                || block.endpointIseRange < XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE
                || block.endpointIseRange > XuastcAstcConstants.LAST_VALID_ENDPOINT_ISE_RANGE
                || block.weightIseRange < XuastcAstcConstants.FIRST_VALID_WEIGHT_ISE_RANGE
                || block.weightIseRange > XuastcAstcConstants.LAST_VALID_WEIGHT_ISE_RANGE
                || block.numPartitions < 1 || block.numPartitions > MAX_PARTITIONS
                || block.partitionId >= 1024
                || (block.numPartitions == 1 && block.partitionId > 0)
                || block.colorComponentSelector > 3) {
            return decoded;
        }
        int totalEndpointValues = 0;
        for (int i = 0; i < block.numPartitions; i++) {
            if (block.colorEndpointModes[i] < 0 || block.colorEndpointModes[i] > 15) {
                return decoded;
            }
            totalEndpointValues += XuastcAstcConstants.numCemEndpointValues(block.colorEndpointModes[i]);
            decoded.ldrEndpoints[i] = isCemLdr(block.colorEndpointModes[i]);
        }
        if (totalEndpointValues > MAX_ENDPOINTS) {
            return decoded;
        }
        int totalEndpointLevels = XuastcAstcConstants.getIseLevels(block.endpointIseRange);
        int[] dequantizedEndpoints = new int[totalEndpointValues];
        for (int i = 0; i < totalEndpointValues; i++) {
            if (block.endpoints[i] >= totalEndpointLevels) {
                return decoded;
            }
            dequantizedEndpoints[i] = XuastcAstcConstants.dequantBiseEndpoint(block.endpoints[i], block.endpointIseRange);
        }
        int totalWeightLevels = XuastcAstcConstants.getIseLevels(block.weightIseRange);
        int totalWeightValues = (block.dualPlane ? 2 : 1) * block.gridWidth * block.gridHeight;
        int[][] gridWeights = new int[2][12 * 12];
        for (int i = 0; i < totalWeightValues; i++) {
            if (block.weights[i] >= totalWeightLevels) {
                return decoded;
            }
            int plane = block.dualPlane ? i & 1 : 0;
            int grid = block.dualPlane ? i >>> 1 : i;
            gridWeights[plane][grid] = XuastcAstcConstants.dequantBiseWeight(block.weights[i], block.weightIseRange);
        }
        upsampleWeightGrid(blockWidth, blockHeight, block.gridWidth, block.gridHeight, gridWeights[0], decoded.weights[0]);
        if (block.dualPlane) {
            upsampleWeightGrid(blockWidth, blockHeight, block.gridWidth, block.gridHeight, gridWeights[1], decoded.weights[1]);
        }
        int endpointIndex = 0;
        for (int subset = 0; subset < block.numPartitions; subset++) {
            int cem = block.colorEndpointModes[subset];
            int count = XuastcAstcConstants.numCemEndpointValues(cem);
            int[] values = Arrays.copyOfRange(dequantizedEndpoints, endpointIndex, endpointIndex + count);
            decodeEndpoint(cem, decoded.decodedEndpoints[subset], values);
            endpointIndex += count;
        }
        decoded.valid = true;
        return decoded;
    }

    private static void decodeEndpoint(int cem, int[][] endpoints, int[] e) {
        int v0 = e[0];
        int v1 = e[1];
        switch (cem) {
            case XuastcAstcConstants.CEM_LDR_LUM_DIRECT:
                setLum(endpoints, v0, v1, 0xFF, 0xFF);
                break;
            case CEM_LDR_LUM_BASE_PLUS_OFS:
                int l0 = (v0 >>> 2) | (v1 & 0xC0);
                int l1 = Math.min(0xFF, l0 + (v1 & 0x3F));
                setLum(endpoints, l0, l1, 0xFF, 0xFF);
                break;
            case CEM_HDR_LUM_LARGE_RANGE:
                int y0;
                int y1;
                if (v1 >= v0) {
                    y0 = v0 << 4;
                    y1 = v1 << 4;
                } else {
                    y0 = (v1 << 4) + 8;
                    y1 = (v0 << 4) - 8;
                }
                setLum(endpoints, y0, y1, 0x780, 0x780);
                break;
            case CEM_HDR_LUM_SMALL_RANGE:
                int d;
                if ((v0 & 0x80) != 0) {
                    y0 = ((v1 & 0xE0) << 4) | ((v0 & 0x7F) << 2);
                    d = (v1 & 0x1F) << 2;
                } else {
                    y0 = ((v1 & 0xF0) << 4) | ((v0 & 0x7F) << 1);
                    d = (v1 & 0x0F) << 1;
                }
                setLum(endpoints, y0, Math.min(0xFFF, y0 + d), 0x780, 0x780);
                break;
            case XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT:
                setLum(endpoints, v0, v1, e[2], e[3]);
                break;
            case CEM_LDR_LUM_ALPHA_BASE_PLUS_OFS:
                int[] lum = bitTransferSigned(v1, v0);
                int[] alpha = bitTransferSigned(e[3], e[2]);
                setLum(endpoints, lum[1], clamp(lum[1] + lum[0], 0, 255),
                        alpha[1], clamp(alpha[1] + alpha[0], 0, 255));
                break;
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE:
                endpoints[0][0] = (v0 * e[3]) >> 8;
                endpoints[0][1] = v0;
                endpoints[1][0] = (v1 * e[3]) >> 8;
                endpoints[1][1] = v1;
                endpoints[2][0] = (e[2] * e[3]) >> 8;
                endpoints[2][1] = e[2];
                endpoints[3][0] = 0xFF;
                endpoints[3][1] = 0xFF;
                break;
            case CEM_HDR_RGB_BASE_SCALE:
                decodeHdrRgbBaseScale(endpoints, e);
                break;
            case XuastcAstcConstants.CEM_LDR_RGB_DIRECT:
                decodeLdrRgbDirect(endpoints, v0, v1, e[2], e[3], e[4], e[5], 0xFF, 0xFF);
                break;
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET:
                decodeLdrRgbBasePlusOffset(endpoints, e, false);
                break;
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A:
                endpoints[0][0] = (v0 * e[3]) >> 8;
                endpoints[0][1] = v0;
                endpoints[1][0] = (v1 * e[3]) >> 8;
                endpoints[1][1] = v1;
                endpoints[2][0] = (e[2] * e[3]) >> 8;
                endpoints[2][1] = e[2];
                endpoints[3][0] = e[4];
                endpoints[3][1] = e[5];
                break;
            case CEM_HDR_RGB:
            case CEM_HDR_RGB_LDR_ALPHA:
            case CEM_HDR_RGB_HDR_ALPHA:
                decodeHdrRgb(endpoints, cem, e);
                break;
            case XuastcAstcConstants.CEM_LDR_RGBA_DIRECT:
                decodeLdrRgbDirect(endpoints, v0, v1, e[2], e[3], e[4], e[5], e[6], e[7]);
                break;
            case XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET:
                decodeLdrRgbBasePlusOffset(endpoints, e, true);
                break;
            default:
                for (int c = 0; c < 4; c++) {
                    endpoints[c][0] = 0;
                    endpoints[c][1] = 0;
                }
        }
    }

    private static void decodeHdrRgbBaseScale(int[][] endpoints, int[] e) {
        int v0 = e[0];
        int v1 = e[1];
        int v2 = e[2];
        int v3 = e[3];
        int modeValue = ((v0 & 0xC0) >>> 6) | ((v1 & 0x80) >>> 5) | ((v2 & 0x80) >>> 4);
        int majorComponent;
        int mode;
        if ((modeValue & 0xC) != 0xC) {
            majorComponent = modeValue >>> 2;
            mode = modeValue & 3;
        } else if (modeValue != 0xF) {
            majorComponent = modeValue & 3;
            mode = 4;
        } else {
            majorComponent = 0;
            mode = 5;
        }
        int red = v0 & 0x3F;
        int green = v1 & 0x1F;
        int blue = v2 & 0x1F;
        int scale = v3 & 0x1F;
        int x0 = (v1 >>> 6) & 1;
        int x1 = (v1 >>> 5) & 1;
        int x2 = (v2 >>> 6) & 1;
        int x3 = (v2 >>> 5) & 1;
        int x4 = (v3 >>> 7) & 1;
        int x5 = (v3 >>> 6) & 1;
        int x6 = (v3 >>> 5) & 1;
        int ohm = 1 << mode;
        if ((ohm & 0x30) != 0) green |= x0 << 6;
        if ((ohm & 0x3A) != 0) green |= x1 << 5;
        if ((ohm & 0x30) != 0) blue |= x2 << 6;
        if ((ohm & 0x3A) != 0) blue |= x3 << 5;
        if ((ohm & 0x3D) != 0) scale |= x6 << 5;
        if ((ohm & 0x2D) != 0) scale |= x5 << 6;
        if ((ohm & 0x04) != 0) scale |= x4 << 7;
        if ((ohm & 0x3B) != 0) red |= x4 << 6;
        if ((ohm & 0x04) != 0) red |= x3 << 6;
        if ((ohm & 0x10) != 0) red |= x5 << 7;
        if ((ohm & 0x0F) != 0) red |= x2 << 7;
        if ((ohm & 0x05) != 0) red |= x1 << 8;
        if ((ohm & 0x0A) != 0) red |= x0 << 8;
        if ((ohm & 0x05) != 0) red |= x0 << 9;
        if ((ohm & 0x02) != 0) red |= x6 << 9;
        if ((ohm & 0x01) != 0) red |= x3 << 10;
        if ((ohm & 0x02) != 0) red |= x5 << 10;
        int shift = new int[] {1, 1, 2, 3, 4, 5}[mode];
        red <<= shift;
        green <<= shift;
        blue <<= shift;
        scale <<= shift;
        if (mode != 5) {
            green = red - green;
            blue = red - blue;
        }
        if (majorComponent == 1) {
            int temp = red;
            red = green;
            green = temp;
        } else if (majorComponent == 2) {
            int temp = red;
            red = blue;
            blue = temp;
        }
        endpoints[0][1] = clamp(red, 0, 0xFFF);
        endpoints[1][1] = clamp(green, 0, 0xFFF);
        endpoints[2][1] = clamp(blue, 0, 0xFFF);
        endpoints[3][1] = 0x780;
        endpoints[0][0] = clamp(red - scale, 0, 0xFFF);
        endpoints[1][0] = clamp(green - scale, 0, 0xFFF);
        endpoints[2][0] = clamp(blue - scale, 0, 0xFFF);
        endpoints[3][0] = 0x780;
    }

    private static void decodeHdrRgb(int[][] endpoints, int cem, int[] e) {
        int v0 = e[0];
        int v1 = e[1];
        int v2 = e[2];
        int v3 = e[3];
        int v4 = e[4];
        int v5 = e[5];
        int majorComponent = ((v4 & 0x80) >>> 7) | ((v5 & 0x80) >>> 6);
        endpoints[3][0] = 0x780;
        endpoints[3][1] = 0x780;
        if (majorComponent == 3) {
            endpoints[0][0] = v0 << 4;
            endpoints[1][0] = v2 << 4;
            endpoints[2][0] = (v4 & 0x7F) << 5;
            endpoints[0][1] = v1 << 4;
            endpoints[1][1] = v3 << 4;
            endpoints[2][1] = (v5 & 0x7F) << 5;
        } else {
            int mode = ((v1 & 0x80) >>> 7) | ((v2 & 0x80) >>> 6) | ((v3 & 0x80) >>> 5);
            int va = v0 | ((v1 & 0x40) << 2);
            int vb0 = v2 & 0x3F;
            int vb1 = v3 & 0x3F;
            int vc = v1 & 0x3F;
            int vd0 = signExtend(v4 & 0x7F, new int[] {7, 6, 7, 6, 5, 6, 5, 6}[mode]);
            int vd1 = signExtend(v5 & 0x7F, new int[] {7, 6, 7, 6, 5, 6, 5, 6}[mode]);
            int x0 = (v2 >>> 6) & 1;
            int x1 = (v3 >>> 6) & 1;
            int x2 = (v4 >>> 6) & 1;
            int x3 = (v5 >>> 6) & 1;
            int x4 = (v4 >>> 5) & 1;
            int x5 = (v5 >>> 5) & 1;
            int ohm = 1 << mode;
            if ((ohm & 0xA4) != 0) va |= x0 << 9;
            if ((ohm & 0x08) != 0) va |= x2 << 9;
            if ((ohm & 0x50) != 0) va |= x4 << 9;
            if ((ohm & 0x50) != 0) va |= x5 << 10;
            if ((ohm & 0xA0) != 0) va |= x1 << 10;
            if ((ohm & 0xC0) != 0) va |= x2 << 11;
            if ((ohm & 0x04) != 0) vc |= x1 << 6;
            if ((ohm & 0xE8) != 0) vc |= x3 << 6;
            if ((ohm & 0x20) != 0) vc |= x2 << 7;
            if ((ohm & 0x5B) != 0) vb0 |= x0 << 6;
            if ((ohm & 0x5B) != 0) vb1 |= x1 << 6;
            if ((ohm & 0x12) != 0) vb0 |= x2 << 7;
            if ((ohm & 0x12) != 0) vb1 |= x3 << 7;
            int shift = (mode >>> 1) ^ 3;
            va <<= shift;
            vb0 <<= shift;
            vb1 <<= shift;
            vc <<= shift;
            vd0 <<= shift;
            vd1 <<= shift;
            endpoints[0][1] = clamp(va, 0, 0xFFF);
            endpoints[1][1] = clamp(va - vb0, 0, 0xFFF);
            endpoints[2][1] = clamp(va - vb1, 0, 0xFFF);
            endpoints[0][0] = clamp(va - vc, 0, 0xFFF);
            endpoints[1][0] = clamp(va - vb0 - vc - vd0, 0, 0xFFF);
            endpoints[2][0] = clamp(va - vb1 - vc - vd1, 0, 0xFFF);
            if (majorComponent == 1) {
                swapComponent(endpoints, 0, 1);
            } else if (majorComponent == 2) {
                swapComponent(endpoints, 0, 2);
            }
        }
        if (cem == CEM_HDR_RGB_LDR_ALPHA) {
            endpoints[3][0] = e[6];
            endpoints[3][1] = e[7];
        } else if (cem == CEM_HDR_RGB_HDR_ALPHA) {
            int v6 = e[6];
            int v7 = e[7];
            int mode = ((v6 >>> 7) & 1) | ((v7 >>> 6) & 2);
            v6 &= 0x7F;
            v7 &= 0x7F;
            if (mode == 3) {
                endpoints[3][0] = v6 << 5;
                endpoints[3][1] = v7 << 5;
            } else {
                v6 |= (v7 << (mode + 1)) & 0x780;
                v7 &= 0x3F >>> mode;
                v7 ^= 0x20 >>> mode;
                v7 -= 0x20 >>> mode;
                v6 <<= 4 - mode;
                v7 <<= 4 - mode;
                endpoints[3][0] = v6;
                endpoints[3][1] = clamp(v7 + v6, 0, 0xFFF);
            }
        }
    }

    private static void decodeLdrRgbDirect(
            int[][] endpoints,
            int r0,
            int r1,
            int g0,
            int g1,
            int b0,
            int b1,
            int a0,
            int a1) {
        if (r1 + g1 + b1 >= r0 + g0 + b0) {
            endpoints[0][0] = r0;
            endpoints[0][1] = r1;
            endpoints[1][0] = g0;
            endpoints[1][1] = g1;
            endpoints[2][0] = b0;
            endpoints[2][1] = b1;
            endpoints[3][0] = a0;
            endpoints[3][1] = a1;
        } else {
            int[] low = blueContract(r1, g1, b1, a1);
            int[] high = blueContract(r0, g0, b0, a0);
            for (int c = 0; c < 4; c++) {
                endpoints[c][0] = low[c];
                endpoints[c][1] = high[c];
            }
        }
    }

    private static void decodeLdrRgbBasePlusOffset(int[][] endpoints, int[] e, boolean alphaMode) {
        int[] red = bitTransferSigned(e[1], e[0]);
        int[] green = bitTransferSigned(e[3], e[2]);
        int[] blue = bitTransferSigned(e[5], e[4]);
        int[] alpha = alphaMode ? bitTransferSigned(e[7], e[6]) : new int[] {0, 0xFF};
        if (red[0] + green[0] + blue[0] >= 0) {
            endpoints[0][0] = red[1];
            endpoints[0][1] = red[1] + red[0];
            endpoints[1][0] = green[1];
            endpoints[1][1] = green[1] + green[0];
            endpoints[2][0] = blue[1];
            endpoints[2][1] = blue[1] + blue[0];
            endpoints[3][0] = alpha[1];
            endpoints[3][1] = alphaMode ? alpha[1] + alpha[0] : 0xFF;
        } else {
            int[] low = blueContract(red[1] + red[0], green[1] + green[0], blue[1] + blue[0],
                    alphaMode ? alpha[1] + alpha[0] : 0xFF);
            int[] high = blueContract(red[1], green[1], blue[1], alphaMode ? alpha[1] : 0xFF);
            for (int c = 0; c < 4; c++) {
                endpoints[c][0] = low[c];
                endpoints[c][1] = high[c];
            }
        }
        for (int c = 0; c < 4; c++) {
            endpoints[c][0] = clamp(endpoints[c][0], 0, 255);
            endpoints[c][1] = clamp(endpoints[c][1], 0, 255);
        }
    }

    private static void upsampleWeightGrid(
            int blockWidth,
            int blockHeight,
            int gridWidth,
            int gridHeight,
            int[] source,
            int[] destination) {
        if (blockWidth == gridWidth && blockHeight == gridHeight) {
            System.arraycopy(source, 0, destination, 0, blockWidth * blockHeight);
            return;
        }
        int scaleX = (1024 + blockWidth / 2) / (blockWidth - 1);
        int scaleY = (1024 + blockHeight / 2) / (blockHeight - 1);
        for (int y = 0; y < blockHeight; y++) {
            for (int x = 0; x < blockWidth; x++) {
                int gX = (scaleX * x * (gridWidth - 1) + 32) >>> 6;
                int gY = (scaleY * y * (gridHeight - 1) + 32) >>> 6;
                int jX = gX >>> 4;
                int jY = gY >>> 4;
                int fX = gX & 0xF;
                int fY = gY & 0xF;
                int w11 = (fX * fY + 8) >>> 4;
                int w10 = fY - w11;
                int w01 = fX - w11;
                int w00 = 16 - fX - fY + w11;
                int total = 8;
                if (w00 != 0) total += source[jX + jY * gridWidth] * w00;
                if (w01 != 0) total += source[jX + 1 + jY * gridWidth] * w01;
                if (w10 != 0) total += source[jX + (jY + 1) * gridWidth] * w10;
                if (w11 != 0) total += source[jX + 1 + (jY + 1) * gridWidth] * w11;
                destination[x + y * blockWidth] = total >>> 4;
            }
        }
    }

    private static void decodeBise(int range, int[] output, int count, BitBlock bits, int bitOffset) {
        int bitCount = XuastcAstcConstants.getIseBitCount(range);
        int[] pos = {bitOffset};
        if (XuastcAstcConstants.getIseTritCount(range) != 0) {
            for (int base = 0; base < count; base += 5) {
                decodeTritBlock(output, base, Math.min(5, count - base), bits, pos, bitCount);
            }
        } else if (XuastcAstcConstants.getIseQuintCount(range) != 0) {
            for (int base = 0; base < count; base += 3) {
                decodeQuintBlock(output, base, Math.min(3, count - base), bits, pos, bitCount);
            }
        } else {
            for (int i = 0; i < count; i++) {
                output[i] = bits.nextBits(pos, bitCount);
            }
        }
    }

    private static void decodeTritBlock(int[] output, int offset, int count, BitBlock bits, int[] bitPos, int bitsPerValue) {
        int[] m = new int[5];
        int t = 0;
        int tOffset = 0;
        int[] tBits = {2, 2, 1, 2, 1};
        for (int i = 0; i < count; i++) {
            if (bitsPerValue != 0) {
                m[i] = bits.nextBits(bitPos, bitsPerValue);
            }
            t |= bits.nextBits(bitPos, tBits[i]) << tOffset;
            tOffset += tBits[i];
        }
        for (int i = 0; i < count; i++) {
            output[offset + i] = (TRIT_DECODE[t][i] << bitsPerValue) | m[i];
        }
    }

    private static void decodeQuintBlock(int[] output, int offset, int count, BitBlock bits, int[] bitPos, int bitsPerValue) {
        int[] m = new int[3];
        int t = 0;
        int tOffset = 0;
        int[] tBits = {3, 2, 2};
        for (int i = 0; i < count; i++) {
            if (bitsPerValue != 0) {
                m[i] = bits.nextBits(bitPos, bitsPerValue);
            }
            t |= bits.nextBits(bitPos, tBits[i]) << tOffset;
            tOffset += tBits[i];
        }
        for (int i = 0; i < count; i++) {
            output[offset + i] = (QUINT_DECODE[t][i] << bitsPerValue) | m[i];
        }
    }

    private static int[][] buildTritDecode() {
        int[][] table = new int[256][5];
        for (int trits = 0; trits < XuastcAstcBlockPacker.TRIT_ENCODE.length; trits++) {
            int encoded = XuastcAstcBlockPacker.TRIT_ENCODE[trits];
            int value = trits;
            for (int i = 0; i < 5; i++) {
                table[encoded][i] = value % 3;
                value /= 3;
            }
        }
        return table;
    }

    private static int[][] buildQuintDecode() {
        int[][] table = new int[128][3];
        for (int quints = 0; quints < XuastcAstcBlockPacker.QUINT_ENCODE.length; quints++) {
            int encoded = XuastcAstcBlockPacker.QUINT_ENCODE[quints];
            int value = quints;
            for (int i = 0; i < 3; i++) {
                table[encoded][i] = value % 5;
                value /= 5;
            }
        }
        return table;
    }

    private static void fillHalf(int[] output, int pixelCount, int[] color) {
        for (int i = 0; i < pixelCount; i++) {
            System.arraycopy(color, 0, output, i * 4, 4);
        }
    }

    private static void copyHalfBlock(
            int[] block,
            byte[] output,
            int blockX,
            int blockY,
            int width,
            int height,
            int blockWidth,
            int blockHeight,
            int components) {
        int maxX = Math.min(blockWidth, width - blockX * blockWidth);
        int maxY = Math.min(blockHeight, height - blockY * blockHeight);
        int bytesPerTexel = components * 2;
        for (int y = 0; y < maxY; y++) {
            for (int x = 0; x < maxX; x++) {
                int src = (x + y * blockWidth) * 4;
                int dst = ((blockX * blockWidth + x) + (blockY * blockHeight + y) * width) * bytesPerTexel;
                for (int c = 0; c < components; c++) {
                    writeShortLE(output, dst + c * 2, block[src + c]);
                }
            }
        }
    }

    private static void copyRgb9e5Block(
            int[] block,
            byte[] output,
            int blockX,
            int blockY,
            int width,
            int height,
            int blockWidth,
            int blockHeight) {
        int maxX = Math.min(blockWidth, width - blockX * blockWidth);
        int maxY = Math.min(blockHeight, height - blockY * blockHeight);
        for (int y = 0; y < maxY; y++) {
            for (int x = 0; x < maxX; x++) {
                int dst = ((blockX * blockWidth + x) + (blockY * blockHeight + y) * width) * 4;
                writeIntLE(output, dst, block[x + y * blockWidth]);
            }
        }
    }

    private static void setLum(int[][] endpoints, int l0, int l1, int a0, int a1) {
        endpoints[0][0] = l0;
        endpoints[0][1] = l1;
        endpoints[1][0] = l0;
        endpoints[1][1] = l1;
        endpoints[2][0] = l0;
        endpoints[2][1] = l1;
        endpoints[3][0] = a0;
        endpoints[3][1] = a1;
    }

    private static void swapComponent(int[][] endpoints, int a, int b) {
        int low = endpoints[a][0];
        int high = endpoints[a][1];
        endpoints[a][0] = endpoints[b][0];
        endpoints[a][1] = endpoints[b][1];
        endpoints[b][0] = low;
        endpoints[b][1] = high;
    }

    private static int[] bitTransferSigned(int delta, int base) {
        int newBase = (base >>> 1) | (delta & 0x80);
        int signedDelta = (delta >>> 1) & 0x3F;
        if ((signedDelta & 0x20) != 0) {
            signedDelta -= 0x40;
        }
        return new int[] {signedDelta, newBase};
    }

    private static int[] blueContract(int r, int g, int b, int a) {
        return new int[] {(r + b) >> 1, (g + b) >> 1, b, a};
    }

    private static boolean isCemLdr(int cem) {
        return cem == XuastcAstcConstants.CEM_LDR_LUM_DIRECT
                || cem == CEM_LDR_LUM_BASE_PLUS_OFS
                || cem == XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT
                || cem == CEM_LDR_LUM_ALPHA_BASE_PLUS_OFS
                || cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE
                || cem == XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET
                || cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET;
    }

    private static int qlog16ToHalf(int k) {
        int exponent = (k & 0xF800) >>> 11;
        int mantissa = k & 0x7FF;
        int transformedMantissa;
        if (mantissa < 512) {
            transformedMantissa = 3 * mantissa;
        } else if (mantissa >= 1536) {
            transformedMantissa = 5 * mantissa - 2048;
        } else {
            transformedMantissa = 4 * mantissa - 512;
        }
        return (exponent << 10) + (transformedMantissa >>> 3);
    }

    private static int floatToHalf(float value, boolean towardZero) {
        int bits = Float.floatToRawIntBits(value);
        int fltMantissa = bits & 0x7FFFFF;
        int fltExponent = (bits >>> 23) & 0xFF;
        int sign = bits >>> 31;
        int exponent = 0;
        int mantissa = 0;
        if (fltExponent == 0xFF) {
            exponent = 31;
            if (fltMantissa != 0) {
                mantissa = 1;
            }
        } else if (fltExponent != 0) {
            int newExponent = fltExponent - 127;
            if (newExponent > 15) {
                exponent = 31;
            } else if (newExponent < -14) {
                float scaled = (float) ((1 << 24) * Math.abs(value));
                mantissa = towardZero ? (int) scaled : Math.round(scaled);
            } else {
                exponent = newExponent + 15;
                float scaled = fltMantissa * (1.0f / (1 << 13));
                mantissa = towardZero ? (int) scaled : Math.round(scaled);
            }
        }
        if (mantissa == 1024) {
            exponent++;
            mantissa = 0;
        }
        return (sign << 15) | (exponent << 10) | mantissa;
    }

    private static float halfToFloat(int half) {
        int sign = (half >>> 15) & 1;
        int exponent = (half >>> 10) & 0x1F;
        int mantissa = half & 0x3FF;
        if (exponent == 0) {
            if (mantissa == 0) {
                return Float.intBitsToFloat(sign << 31);
            }
            while ((mantissa & 0x400) == 0) {
                mantissa <<= 1;
                exponent--;
            }
            exponent++;
            mantissa &= ~0x400;
        } else if (exponent == 31) {
            int raw = (sign << 31) | 0x7F800000 | (mantissa << 13);
            return Float.intBitsToFloat(raw);
        }
        exponent += 127 - 15;
        return Float.intBitsToFloat((sign << 31) | (exponent << 23) | (mantissa << 13));
    }

    private static int packRgb9e5(float r, float g, float b) {
        r = clamp(r, 0.0f, 0xFF80);
        g = clamp(g, 0.0f, 0xFF80);
        b = clamp(b, 0.0f, 0xFF80);
        float max = Math.max(r, Math.max(g, b));
        int sharedExponent = Math.max(-16, floorLog2(max)) + 16;
        float denom = (float) Math.pow(2.0f, sharedExponent - 15 - 9);
        int maxMantissa = (int) Math.floor(max / denom + 0.5f);
        if (maxMantissa == 512) {
            denom *= 2.0f;
            sharedExponent++;
        }
        int rm = (int) Math.floor(r / denom + 0.5f);
        int gm = (int) Math.floor(g / denom + 0.5f);
        int bm = (int) Math.floor(b / denom + 0.5f);
        return rm | (gm << 9) | (bm << 18) | (sharedExponent << 27);
    }

    private static int packRgb9e5LdrAstc(int r, int g, int b) {
        int leadingZeros = clz17(r | g | b | 1);
        if (r == 65535) {
            r = 65536;
            leadingZeros = 0;
        }
        if (g == 65535) {
            g = 65536;
            leadingZeros = 0;
        }
        if (b == 65535) {
            b = 65536;
            leadingZeros = 0;
        }
        r = ((r << leadingZeros) >>> 8) & 0x1FF;
        g = ((g << leadingZeros) >>> 8) & 0x1FF;
        b = ((b << leadingZeros) >>> 8) & 0x1FF;
        int exponent = 16 - leadingZeros;
        return (exponent << 27) | (b << 18) | (g << 9) | r;
    }

    private static int packRgb9e5HdrAstc(int r, int g, int b) {
        r = sanitizeHalfForRgb9e5(r);
        g = sanitizeHalfForRgb9e5(g);
        b = sanitizeHalfForRgb9e5(b);
        int re = (r >>> 10) & 0x1F;
        int ge = (g >>> 10) & 0x1F;
        int be = (b >>> 10) & 0x1F;
        int rex = re == 0 ? 1 : re;
        int gex = ge == 0 ? 1 : ge;
        int bex = be == 0 ? 1 : be;
        int xm = ((r | g | b) & 0x200) >>> 9;
        int xe = re | ge | be;
        int rshift;
        int gshift;
        int bshift;
        int exponent;
        if (xe == 0) {
            exponent = xm;
            rshift = xm;
            gshift = xm;
            bshift = xm;
        } else if (re >= ge && re >= be) {
            exponent = rex + 1;
            rshift = 2;
            gshift = rex - gex + 2;
            bshift = rex - bex + 2;
        } else if (ge >= be) {
            exponent = gex + 1;
            rshift = gex - rex + 2;
            gshift = 2;
            bshift = gex - bex + 2;
        } else {
            exponent = bex + 1;
            rshift = bex - rex + 2;
            gshift = bex - gex + 2;
            bshift = 2;
        }
        int rm = ((r & 0x3FF) | (re == 0 ? 0 : 0x400)) >>> rshift;
        int gm = ((g & 0x3FF) | (ge == 0 ? 0 : 0x400)) >>> gshift;
        int bm = ((b & 0x3FF) | (be == 0 ? 0 : 0x400)) >>> bshift;
        return (exponent << 27) | ((bm & 0x1FF) << 18) | ((gm & 0x1FF) << 9) | (rm & 0x1FF);
    }

    private static int sanitizeHalfForRgb9e5(int half) {
        if (half > 0x7C00) {
            return 0;
        }
        if (half == 0x7C00) {
            return 0x7BFF;
        }
        return half;
    }

    private static int weightInterpolate(int low, int high, int weight) {
        return (low * (64 - weight) + high * weight + 32) >>> 6;
    }

    private static int iseSequenceBits(int count, int range) {
        int totalBits = XuastcAstcConstants.getIseBitCount(range) * count;
        totalBits += (XuastcAstcConstants.getIseTritCount(range) * 8 * count + 4) / 5;
        totalBits += (XuastcAstcConstants.getIseQuintCount(range) * 7 * count + 2) / 3;
        return totalBits;
    }

    private static int signExtend(int value, int bits) {
        int signBit = 1 << (bits - 1);
        return (value & signBit) != 0 ? value | ~((1 << bits) - 1) : value & ((1 << bits) - 1);
    }

    private static boolean isHalfInfOrNan(int half) {
        return ((half >>> 10) & 0x1F) == 31;
    }

    private static int floorLog2(float value) {
        return ((Float.floatToRawIntBits(value) >>> 23) & 0xFF) - 127;
    }

    private static int clz17(int value) {
        value &= 0x1FFFF;
        if (value == 0) {
            return 17;
        }
        int count = 0;
        while ((value & 0x10000) == 0) {
            value <<= 1;
            count++;
        }
        return count;
    }

    static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int checkedMultiply(int left, int right) {
        long result = (long) left * right;
        if (result > Integer.MAX_VALUE) {
            throw new BasisDecodeException("Decoded ASTC HDR image is too large");
        }
        return (int) result;
    }

    private static int clamp(int value, int low, int high) {
        if (value < low) {
            return low;
        }
        return Math.min(value, high);
    }

    private static float clamp(float value, float low, float high) {
        if (value < low) {
            return low;
        }
        return Math.min(value, high);
    }

    private static void writeShortLE(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
    }

    static void writeIntLE(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
        output[offset + 2] = (byte) (value >>> 16);
        output[offset + 3] = (byte) (value >>> 24);
    }

    private static final class DecodedBlock {
        final boolean[] ldrEndpoints = new boolean[MAX_PARTITIONS];
        final int[][][] decodedEndpoints = new int[MAX_PARTITIONS][4][2];
        final int[][] weights = new int[2][12 * 12];
        boolean valid;
    }

    static final class LogicalBlock {
        final int[] colorEndpointModes = new int[MAX_PARTITIONS];
        final int[] endpoints = new int[MAX_ENDPOINTS];
        final int[] weights = new int[MAX_GRID_WEIGHTS];
        final int[] solidColor = new int[4];
        int gridWidth;
        int gridHeight;
        int weightIseRange;
        int endpointIseRange;
        int numPartitions;
        int partitionId;
        int colorComponentSelector;
        boolean dualPlane;
        boolean solidLdr;
        boolean solidHdr;
        boolean error;

        void clear() {
            Arrays.fill(colorEndpointModes, 0);
            Arrays.fill(endpoints, 0);
            Arrays.fill(weights, 0);
            Arrays.fill(solidColor, 0);
            gridWidth = 0;
            gridHeight = 0;
            weightIseRange = 0;
            endpointIseRange = 0;
            numPartitions = 0;
            partitionId = 0;
            colorComponentSelector = 0;
            dualPlane = false;
            solidLdr = false;
            solidHdr = false;
            error = false;
        }
    }

    private static final class BitBlock {
        private final byte[] data;
        private final int offset;

        BitBlock(byte[] data, int offset) {
            this.data = data;
            this.offset = offset;
        }

        int getBits(int bitOffset, int bitLength) {
            int result = 0;
            for (int i = 0; i < bitLength; i++) {
                int absoluteBit = bitOffset + i;
                int bit = (data[offset + (absoluteBit >>> 3)] >>> (absoluteBit & 7)) & 1;
                result |= bit << i;
            }
            return result;
        }

        int nextBits(int[] bitOffset, int bitLength) {
            int value = getBits(bitOffset[0], bitLength);
            bitOffset[0] += bitLength;
            return value;
        }

        BitBlock reversed() {
            byte[] reversed = new byte[ASTC_BLOCK_BYTES];
            for (int i = 0; i < 4; i++) {
                int value = readIntLE(data, offset + (3 - i) * 4);
                writeIntLE(reversed, i * 4, Integer.reverse(value));
            }
            return new BitBlock(reversed, 0);
        }

        private static int readIntLE(byte[] data, int offset) {
            return Byte.toUnsignedInt(data[offset])
                    | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                    | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                    | (Byte.toUnsignedInt(data[offset + 3]) << 24);
        }
    }
}
