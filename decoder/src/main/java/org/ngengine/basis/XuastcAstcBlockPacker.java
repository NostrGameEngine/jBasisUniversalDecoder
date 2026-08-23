package org.ngengine.basis;

/**
 * Packs reconstructed XUASTC LDR logical blocks into physical ASTC blocks.
 */
final class XuastcAstcBlockPacker {
    private static final int ASTC_BLOCK_BYTES = 16;
    private static final int NUM_PARTITION_PATTERNS = 1024;
    private static final int MAX_ENDPOINTS = 18;
    private static final int MAX_GRID_WEIGHTS = 64;
    static final int[] TRIT_ENCODE = {
        0, 1, 2, 4, 5, 6, 8, 9, 10, 16, 17, 18, 20, 21, 22, 24, 25, 26, 3, 7, 11, 19, 23, 27,
        12, 13, 14, 32, 33, 34, 36, 37, 38, 40, 41, 42, 48, 49, 50, 52, 53, 54, 56, 57, 58, 35,
        39, 43, 51, 55, 59, 44, 45, 46, 64, 65, 66, 68, 69, 70, 72, 73, 74, 80, 81, 82, 84, 85,
        86, 88, 89, 90, 67, 71, 75, 83, 87, 91, 76, 77, 78, 128, 129, 130, 132, 133, 134, 136,
        137, 138, 144, 145, 146, 148, 149, 150, 152, 153, 154, 131, 135, 139, 147, 151, 155, 140,
        141, 142, 160, 161, 162, 164, 165, 166, 168, 169, 170, 176, 177, 178, 180, 181, 182, 184,
        185, 186, 163, 167, 171, 179, 183, 187, 172, 173, 174, 192, 193, 194, 196, 197, 198, 200,
        201, 202, 208, 209, 210, 212, 213, 214, 216, 217, 218, 195, 199, 203, 211, 215, 219, 204,
        205, 206, 96, 97, 98, 100, 101, 102, 104, 105, 106, 112, 113, 114, 116, 117, 118, 120, 121,
        122, 99, 103, 107, 115, 119, 123, 108, 109, 110, 224, 225, 226, 228, 229, 230, 232, 233,
        234, 240, 241, 242, 244, 245, 246, 248, 249, 250, 227, 231, 235, 243, 247, 251, 236, 237,
        238, 28, 29, 30, 60, 61, 62, 92, 93, 94, 156, 157, 158, 188, 189, 190, 220, 221, 222, 31,
        63, 95, 159, 191, 223, 124, 125, 126
    };
    static final int[] QUINT_ENCODE = {
        0, 1, 2, 3, 4, 8, 9, 10, 11, 12, 16, 17, 18, 19, 20, 24, 25, 26, 27, 28, 5, 13, 21, 29,
        6, 32, 33, 34, 35, 36, 40, 41, 42, 43, 44, 48, 49, 50, 51, 52, 56, 57, 58, 59, 60, 37, 45,
        53, 61, 14, 64, 65, 66, 67, 68, 72, 73, 74, 75, 76, 80, 81, 82, 83, 84, 88, 89, 90, 91,
        92, 69, 77, 85, 93, 22, 96, 97, 98, 99, 100, 104, 105, 106, 107, 108, 112, 113, 114, 115,
        116, 120, 121, 122, 123, 124, 101, 109, 117, 125, 30, 102, 103, 70, 71, 38, 110, 111, 78,
        79, 46, 118, 119, 86, 87, 54, 126, 127, 94, 95, 62, 39, 47, 55, 63, 7
    };

    private XuastcAstcBlockPacker() {
    }

    static byte[] packImage(byte[] data, int offset, int length) {
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        XuastcLdrDecodedBlock[] blocks =
                XuastcLdrRawBlockConfigReader.readDecodedBlocks(data, offset, length);
        int expectedBlocks = divideRoundUp(header.getWidth(), header.getBlockWidth())
                * divideRoundUp(header.getHeight(), header.getBlockHeight());
        if (blocks.length != expectedBlocks) {
            throw new BasisDecodeException("XUASTC decoded block count mismatch");
        }

        byte[] astc = new byte[blocks.length * ASTC_BLOCK_BYTES];
        int dst = 0;
        for (XuastcLdrDecodedBlock block : blocks) {
            byte[] packed = block.isSolid()
                    ? packSolidBlock(block.getSolidRgba())
                    : packConfigBlock(block.getConfig());
            System.arraycopy(packed, 0, astc, dst, packed.length);
            dst += ASTC_BLOCK_BYTES;
        }
        return astc;
    }

    static byte[] packSolidBlock(int[] rgba) {
        if (rgba == null || rgba.length != 4) {
            throw new BasisDecodeException("Invalid XUASTC solid ASTC block");
        }
        byte[] block = new byte[ASTC_BLOCK_BYTES];
        for (int i = 0; i < block.length; i++) {
            block[i] = (byte) 0xFF;
        }
        block[0] = (byte) 0xFC;
        block[1] = (byte) 0xFD;
        for (int c = 0; c < 4; c++) {
            int replicated = (rgba[c] & 0xFF) | ((rgba[c] & 0xFF) << 8);
            block[8 + c * 2] = (byte) replicated;
            block[9 + c * 2] = (byte) (replicated >>> 8);
        }
        return block;
    }

    private static byte[] packConfigBlock(XuastcLdrRawBlockConfig config) {
        XuastcTrialMode mode = config.getTrialMode();
        byte[] block = new byte[ASTC_BLOCK_BYTES];
        int[] bitPos = {0};

        int configBits = configBits(config);
        setBits(block, bitPos, configBits, 11);

        int totalGridWeights = config.getWeightGrid().getWeights().length;
        int totalWeightBits = iseSequenceBits(totalGridWeights, mode.getWeightIseRange());
        if (totalGridWeights == 0 || totalGridWeights > MAX_GRID_WEIGHTS
                || totalWeightBits < 24 || totalWeightBits > 96) {
            throw new BasisDecodeException("Invalid XUASTC ASTC weight-grid encoding");
        }

        int partitions = mode.getNumberOfPartitions();
        if (partitions < 1 || partitions > 4) {
            throw new BasisDecodeException("Invalid XUASTC ASTC partition count");
        }
        setBits(block, bitPos, partitions - 1, 2);

        int extraBits = 0;
        if (partitions > 1) {
            int partitionId = config.getPartitionSeed();
            if (partitionId < 0 || partitionId >= NUM_PARTITION_PATTERNS) {
                throw new BasisDecodeException("Invalid XUASTC ASTC partition seed");
            }
            setBits(block, bitPos, partitionId, 10);

            int cem = config.getActualColorEndpointMode();
            if (cem > 15) {
                throw new BasisDecodeException("Invalid XUASTC ASTC CEM");
            }
            setBits(block, bitPos, cem << 2 & 0x3F, 6);
        } else {
            if (config.getPartitionSeed() != 0) {
                throw new BasisDecodeException("Invalid XUASTC ASTC single-partition seed");
            }
            setBits(block, bitPos, config.getActualColorEndpointMode(), 4);
        }

        if (mode.getColorComponentSelector() >= 0) {
            if (partitions > 3) {
                throw new BasisDecodeException("Invalid XUASTC ASTC dual-plane partition count");
            }
            extraBits += 2;
            int[] ccsBitPos = {128 - totalWeightBits - extraBits};
            setBits(block, ccsBitPos, mode.getColorComponentSelector(), 2);
        }

        int remainingBits = 128 - bitPos[0] - extraBits - totalWeightBits;
        if (remainingBits < 0) {
            throw new BasisDecodeException("XUASTC ASTC block has no endpoint bit budget");
        }

        int totalEndpointValues = partitions * XuastcAstcConstants.numCemEndpointValues(
                config.getActualColorEndpointMode());
        if (totalEndpointValues > MAX_ENDPOINTS) {
            throw new BasisDecodeException("XUASTC ASTC block has too many endpoints");
        }
        int endpointRange = selectEndpointRange(totalEndpointValues, remainingBits);
        if (mode.getEndpointIseRange() != endpointRange) {
            throw new BasisDecodeException("XUASTC ASTC endpoint range mismatch");
        }

        encodeBise(block, config.getEndpoints(), bitPos[0], totalEndpointValues, endpointRange);

        byte[] encodedWeights = new byte[ASTC_BLOCK_BYTES];
        encodeBise(
                encodedWeights,
                config.getWeightGrid().getWeights(),
                0,
                totalGridWeights,
                mode.getWeightIseRange());
        for (int i = 0; i < 4; i++) {
            int reversed = Integer.reverse(readLittleEndianInt(encodedWeights, (3 - i) * 4));
            int existing = readLittleEndianInt(block, i * 4);
            writeLittleEndianInt(block, i * 4, existing | reversed);
        }
        return block;
    }

    private static int configBits(XuastcLdrRawBlockConfig config) {
        XuastcLdrWeightGrid weights = config.getWeightGrid();
        int w = weights.getGridWidth();
        int h = weights.getGridHeight();
        int weightRange = config.getTrialMode().getWeightIseRange();
        int highPrecision = weightRange >= 6 ? 1 : 0;
        int dualPlanePrecision = ((weights.getPlaneCount() == 2 ? 1 : 0) << 1) | highPrecision;
        int p = 2 + weightRange - (highPrecision != 0 ? 6 : 0);
        p = (p >>> 1) + ((p & 1) << 2);

        if (isPackable(w - 4, 2) && isPackable(h - 2, 2)) {
            return (dualPlanePrecision << 9) | ((w - 4) << 7) | ((h - 2) << 5)
                    | ((p & 4) << 2) | (p & 3);
        }
        if (isPackable(w - 8, 2) && isPackable(h - 2, 2)) {
            return (dualPlanePrecision << 9) | ((w - 8) << 7) | ((h - 2) << 5)
                    | ((p & 4) << 2) | 4 | (p & 3);
        }
        if (isPackable(w - 2, 2) && isPackable(h - 8, 2)) {
            return (dualPlanePrecision << 9) | ((h - 8) << 7) | ((w - 2) << 5)
                    | ((p & 4) << 2) | 8 | (p & 3);
        }
        if (isPackable(w - 2, 2) && isPackable(h - 6, 1)) {
            return (dualPlanePrecision << 9) | ((h - 6) << 7) | ((w - 2) << 5)
                    | ((p & 4) << 2) | 12 | (p & 3);
        }
        if (isPackable(w - 2, 1) && isPackable(h - 2, 2)) {
            return (dualPlanePrecision << 9) | (w << 7) | ((h - 2) << 5)
                    | ((p & 4) << 2) | 12 | (p & 3);
        }
        if (w == 12 && isPackable(h - 2, 2)) {
            return (dualPlanePrecision << 9) | ((h - 2) << 5) | (p << 2);
        }
        if (h == 12 && isPackable(w - 2, 2)) {
            return (dualPlanePrecision << 9) | (1 << 7) | ((w - 2) << 5) | (p << 2);
        }
        if (w == 6 && h == 10) {
            return (dualPlanePrecision << 9) | (3 << 7) | (p << 2);
        }
        if (w == 10 && h == 6) {
            return (dualPlanePrecision << 9) | (13 << 5) | (p << 2);
        }
        if (dualPlanePrecision == 0 && isPackable(w - 6, 2) && isPackable(h - 6, 2)) {
            return ((h - 6) << 9) | 256 | ((w - 6) << 5) | (p << 2);
        }
        throw new BasisDecodeException("Unsupported XUASTC ASTC weight-grid dimensions");
    }

    private static int selectEndpointRange(int totalEndpointValues, int remainingBits) {
        for (int range = XuastcAstcConstants.LAST_VALID_ENDPOINT_ISE_RANGE; range > 0; range--) {
            if (iseSequenceBits(totalEndpointValues, range) <= remainingBits) {
                if (range < XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE) {
                    break;
                }
                return range;
            }
        }
        throw new BasisDecodeException("XUASTC ASTC block has invalid endpoint ISE range");
    }

    static void encodeBise(byte[] dst, int[] values, int bitOffset, int valueCount, int range) {
        byte[] temp = new byte[20];
        int numBits = XuastcAstcConstants.getIseBitCount(range);
        int groupSize = XuastcAstcConstants.getIseTritCount(range) != 0
                ? 5
                : XuastcAstcConstants.getIseQuintCount(range) != 0 ? 3 : 0;
        int levels = XuastcAstcConstants.getIseLevels(range);
        for (int i = 0; i < valueCount; i++) {
            if (values[i] < 0 || values[i] >= levels) {
                throw new BasisDecodeException("XUASTC ASTC BISE value is out of range");
            }
        }
        int[] pos = {bitOffset};
        if (groupSize == 0) {
            for (int i = 0; i < valueCount; i++) {
                setBits1To9(temp, pos, values[i], numBits);
            }
        } else {
            int groups = groupSize == 5 ? (valueCount + 4) / 5 : (valueCount + 2) / 3;
            for (int group = 0; group < groups; group++) {
                int[] vals = new int[5];
                int limit = Math.min(groupSize, valueCount - group * groupSize);
                for (int i = 0; i < limit; i++) {
                    vals[i] = values[group * groupSize + i];
                }
                if (groupSize == 5) {
                    encodeTrits(temp, vals, pos, numBits);
                } else {
                    encodeQuints(temp, vals, pos, numBits);
                }
            }
        }
        for (int i = 0; i < ASTC_BLOCK_BYTES; i++) {
            dst[i] |= temp[i];
        }
    }

    private static void encodeTrits(byte[] dst, int[] values, int[] bitPos, int n) {
        int trits = 0;
        int[] bits = new int[5];
        int bitMask = (1 << n) - 1;
        int[] muls = {1, 3, 9, 27, 81};
        for (int i = 0; i < 5; i++) {
            trits += (values[i] >>> n) * muls[i];
            bits[i] = values[i] & bitMask;
        }
        int t = TRIT_ENCODE[trits];
        setBits(dst, bitPos, bits[0] | (extractBits(t, 0, 1) << n) | (bits[1] << (2 + n)), n * 2 + 2);
        setBits(dst, bitPos,
                extractBits(t, 2, 3) | (bits[2] << 2) | (extractBits(t, 4, 4) << (2 + n))
                        | (bits[3] << (3 + n)) | (extractBits(t, 5, 6) << (3 + n * 2))
                        | (bits[4] << (5 + n * 2)) | (extractBits(t, 7, 7) << (5 + n * 3)),
                n * 3 + 6);
    }

    private static void encodeQuints(byte[] dst, int[] values, int[] bitPos, int n) {
        int quints = 0;
        int[] bits = new int[3];
        int bitMask = (1 << n) - 1;
        int[] muls = {1, 5, 25};
        for (int i = 0; i < 3; i++) {
            quints += (values[i] >>> n) * muls[i];
            bits[i] = values[i] & bitMask;
        }
        int t = QUINT_ENCODE[quints];
        setBits(dst, bitPos,
                bits[0] | (extractBits(t, 0, 2) << n) | (bits[1] << (3 + n))
                        | (extractBits(t, 3, 4) << (3 + n * 2))
                        | (bits[2] << (5 + n * 2)) | (extractBits(t, 5, 6) << (5 + n * 3)),
                7 + n * 3);
    }

    private static void setBits1To9(byte[] dst, int[] bitPos, int value, int totalBits) {
        if (totalBits == 0) {
            return;
        }
        int byteBitOffset = bitPos[0] & 7;
        int val = value << byteBitOffset;
        int index = bitPos[0] >>> 3;
        dst[index] |= (byte) val;
        if (totalBits > 8 - byteBitOffset) {
            dst[index + 1] |= (byte) (val >>> 8);
        }
        bitPos[0] += totalBits;
    }

    static void setBits(byte[] dst, int[] bitPos, int value, int totalBits) {
        if (totalBits < 0 || totalBits > 31) {
            throw new BasisDecodeException("Invalid XUASTC ASTC bit count");
        }
        while (totalBits != 0) {
            int bitsToWrite = Math.min(totalBits, 8 - (bitPos[0] & 7));
            dst[bitPos[0] >>> 3] |= (byte) (value << (bitPos[0] & 7));
            bitPos[0] += bitsToWrite;
            totalBits -= bitsToWrite;
            value >>>= bitsToWrite;
        }
    }

    private static int iseSequenceBits(int count, int range) {
        int totalBits = XuastcAstcConstants.getIseBitCount(range) * count;
        totalBits += (XuastcAstcConstants.getIseTritCount(range) * 8 * count + 4) / 5;
        totalBits += (XuastcAstcConstants.getIseQuintCount(range) * 7 * count + 2) / 3;
        return totalBits;
    }

    private static boolean isPackable(int value, int bits) {
        return value >= 0 && value < (1 << bits);
    }

    private static int extractBits(int value, int low, int high) {
        return (value >>> low) & ((1 << (high - low + 1)) - 1);
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                | (Byte.toUnsignedInt(data[offset + 3]) << 24);
    }

    private static void writeLittleEndianInt(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
