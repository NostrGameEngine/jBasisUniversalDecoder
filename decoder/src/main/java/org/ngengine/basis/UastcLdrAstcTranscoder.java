package org.ngengine.basis;

/**
 * Pure-Java UASTC LDR 4x4 to ASTC LDR 4x4 transcoder.
 */
final class UastcLdrAstcTranscoder {
    private static final int BLOCK_BYTES = 16;
    private static final int TOTAL_MODES = 19;
    private static final int SOLID_MODE = 8;
    private static final int[][] ETC1_INTENSITY_TABLES = {
        {-8, -2, 2, 8},
        {-17, -5, 5, 17},
        {-29, -9, 9, 29},
        {-42, -13, 13, 42},
        {-60, -18, 18, 60},
        {-80, -24, 24, 80},
        {-106, -33, 33, 106},
        {-183, -47, 47, 183}
    };
    private static final byte[][] ETC1_SOLID_SELECTORS = {
        {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
        {(byte) 0xFF, (byte) 0xFF, 0, 0},
        {0, 0, 0, 0},
        {0, 0, (byte) 0xFF, (byte) 0xFF}
    };
    private static final int[] ETC1_LUMA_SELECTOR_TRANSLATION = {1, 0, 2, 3};
    private static final int[][][][] ETC1_PIXEL_COORDS = {
        {
            {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {1, 0}, {1, 1}, {1, 2}, {1, 3}},
            {{2, 0}, {2, 1}, {2, 2}, {2, 3}, {3, 0}, {3, 1}, {3, 2}, {3, 3}}
        },
        {
            {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}},
            {{0, 2}, {1, 2}, {2, 2}, {3, 2}, {0, 3}, {1, 3}, {2, 3}, {3, 3}}
        }
    };
    private static final boolean[] MODE_HAS_ALPHA = {
        false, false, false, false, false, false, false, false,
        true, true, true, true, true, true, true, true, true, true,
        false
    };

    private static final int[] HUFF_MODE = {
        11, 0, 10, 3, 11, 15, 12, 7, 11, 18, 10, 5, 11, 14, 12, 9,
        11, 0, 10, 4, 11, 16, 12, 8, 11, 18, 10, 6, 11, 2, 12, 13,
        11, 0, 10, 3, 11, 17, 12, 7, 11, 18, 10, 5, 11, 14, 12, 9,
        11, 0, 10, 4, 11, 1, 12, 8, 11, 18, 10, 6, 11, 2, 12, 13,
        11, 0, 10, 3, 11, 19, 12, 7, 11, 18, 10, 5, 11, 14, 12, 9,
        11, 0, 10, 4, 11, 16, 12, 8, 11, 18, 10, 6, 11, 2, 12, 13,
        11, 0, 10, 3, 11, 17, 12, 7, 11, 18, 10, 5, 11, 14, 12, 9,
        11, 0, 10, 4, 11, 1, 12, 8, 11, 18, 10, 6, 11, 2, 12, 13
    };
    private static final int[][] HUFF_CODE = {
        {0x1, 4}, {0x35, 6}, {0x1D, 5}, {0x3, 5},
        {0x13, 5}, {0xB, 5}, {0x1B, 5}, {0x7, 5},
        {0x17, 5}, {0xF, 5}, {0x2, 3}, {0x0, 2},
        {0x6, 3}, {0x1F, 5}, {0xD, 5}, {0x5, 7},
        {0x15, 6}, {0x25, 6}, {0x9, 4}, {0x45, 7}
    };

    private static final int[] MODE_WEIGHT_BITS = {
        4, 2, 3, 2, 2, 3, 2, 2, 0, 2, 4, 2, 3, 1, 2, 4, 2, 2, 5
    };
    private static final int[] MODE_WEIGHT_RANGES = {
        8, 2, 5, 2, 2, 5, 2, 2, 0, 2, 8, 2, 5, 0, 2, 8, 2, 2, 11
    };
    private static final int[] MODE_ENDPOINT_RANGES = {
        19, 20, 8, 7, 12, 20, 18, 12, 0, 8, 13, 13, 19, 20, 20, 20, 20, 20, 11
    };
    private static final int[] MODE_SUBSETS = {
        1, 1, 2, 3, 2, 1, 1, 2, 0, 2, 1, 1, 1, 1, 1, 1, 2, 1, 1
    };
    private static final int[] MODE_PLANES = {
        1, 1, 1, 1, 1, 1, 2, 1, 0, 1, 1, 2, 1, 2, 1, 1, 1, 2, 1
    };
    private static final int[] MODE_COMPS = {
        3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 2, 2, 2, 3
    };
    private static final boolean[] MODE_HAS_ETC1_BIAS = {
        true, true, true, true, true, true, true, true,
        false, true, false, false, false, true, true, true, true, true,
        true
    };
    private static final boolean[] MODE_HAS_BC1_HINT0 = {
        true, true, true, true, true, true, true, true,
        false, true, true, true, true, true, true, true, true, true,
        true
    };
    private static final boolean[] MODE_HAS_BC1_HINT1 = {
        true, true, true, true, true, true, true, true,
        false, true, false, false, false, true, true, true, true, true,
        true
    };
    private static final int[][] UASTC_TO_BC1_WEIGHTS = {
        {},
        {0, 1},
        {0, 2, 3, 1},
        {0, 0, 2, 2, 3, 3, 1, 1},
        {0, 0, 0, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 1, 1, 1},
        {0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1}
    };
    private static final int[] MODE_CEM = {
        8, 8, 8, 8, 8, 8, 8, 8, 0, 12, 12, 12, 12, 12, 12, 4, 4, 4, 8
    };
    private static final int[] MODE_HINT_BITS = {
        15, 15, 15, 15, 15, 15, 15, 15, 0, 23, 17, 17, 17, 23, 23, 23, 23, 23, 15
    };
    private static final int[] MODE_ASTC_BLOCK_MODE = {
        0x242, 0x42, 0x53, 0x42, 0x42, 0x53, 0x442, 0x42, 0,
        0x42, 0x242, 0x442, 0x53, 0x441, 0x42, 0x242, 0x42, 0x442, 0x253
    };
    private static final int[] COMMON_PARTITION_2_ASTC = {
        28, 20, 16, 29, 91, 9, 107, 72, 149, 204, 50, 114, 496, 17, 78,
        39, 252, 828, 43, 156, 116, 210, 476, 273, 684, 359, 246, 195, 694, 524
    };
    private static final int[] COMMON_PARTITION_2_BC7 = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
        15, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 29, 32,
        33, 52
    };
    private static final boolean[] COMMON_PARTITION_2_INVERT = {
        false, false, true, false, true, false, true, true, false, true,
        false, true, true, true, false, true, true, true, false, false,
        false, true, true, false, true, false, true, true, true, true
    };
    private static final int[] BC7_3_ASTC_2_ASTC = {
        36, 48, 61, 137, 161, 183, 226, 281, 302, 307, 479, 495, 593, 594,
        605, 799, 812, 988, 993
    };
    private static final int[] BC7_3_ASTC_2_BC7 = {
        10, 11, 0, 2, 8, 13, 1, 33, 40, 20, 21, 58, 3, 32, 59, 34, 20, 14, 31
    };
    private static final int[] BC7_3_ASTC_2_K = {
        4, 4, 3, 4, 5, 4, 2, 2, 3, 4, 0, 3, 0, 2, 1, 3, 1, 4, 3
    };
    private static final int[] COMMON_PARTITION_3_ASTC = {
        260, 74, 32, 156, 183, 15, 745, 0, 335, 902, 254
    };
    private static final int[] COMMON_PARTITION_3_BC7 = {
        4, 8, 9, 10, 11, 12, 13, 20, 35, 36, 57
    };
    private static final int[] COMMON_PARTITION_3_PERM = {
        0, 5, 5, 2, 2, 0, 4, 1, 1, 5, 0
    };
    private static final int[][] ASTC_TO_BC7_PARTITION_PERM = {
        {0, 1, 2},
        {1, 2, 0},
        {2, 0, 1},
        {2, 1, 0},
        {0, 2, 1},
        {1, 0, 2}
    };
    private static final int[] BC7_PARTITION_2_ZERO = {
        0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1
    };

    private static final int[] REVERSE_BITS_2 = {0, 2, 1, 3};
    private static final int[] REVERSE_BITS_3 = {0, 4, 2, 6, 1, 5, 3, 7};
    private static final int[] REVERSE_BITS_4 = {
        0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15
    };
    private static final int[] REVERSE_BITS_5 = {
        0, 16, 8, 24, 4, 20, 12, 28, 2, 18, 10, 26, 6, 22, 14, 30,
        1, 17, 9, 25, 5, 21, 13, 29, 3, 19, 11, 27, 7, 23, 15, 31
    };
    private static final int[] QUINT_ENCODE = {
        0, 1, 2, 3, 4, 8, 9, 10, 11, 12, 16, 17, 18, 19, 20, 24, 25, 26, 27, 28, 5, 13, 21, 29,
        6, 32, 33, 34, 35, 36, 40, 41, 42, 43, 44, 48, 49, 50, 51, 52, 56, 57, 58, 59, 60, 37, 45,
        53, 61, 14, 64, 65, 66, 67, 68, 72, 73, 74, 75, 76, 80, 81, 82, 83, 84, 88, 89, 90, 91,
        92, 69, 77, 85, 93, 22, 96, 97, 98, 99, 100, 104, 105, 106, 107, 108, 112, 113, 114, 115,
        116, 120, 121, 122, 123, 124, 101, 109, 117, 125, 30, 102, 103, 70, 71, 38, 110, 111, 78,
        79, 46, 118, 119, 86, 87, 54, 126, 127, 94, 95, 62, 39, 47, 55, 63, 31
    };

    private UastcLdrAstcTranscoder() {
    }

    static byte[] transcodeToAstc(byte[] blocks, int width, int height) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }
        byte[] output = new byte[expectedBytes];
        for (int blockIndex = 0; blockIndex < blocksX * blocksY; blockIndex++) {
            byte[] astc = transcodeBlock(blocks, blockIndex * BLOCK_BYTES);
            System.arraycopy(astc, 0, output, blockIndex * BLOCK_BYTES, BLOCK_BYTES);
        }
        return output;
    }

    static byte[] transcodeToRgba(byte[] blocks, int width, int height) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }

        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                int blockIndex = bx + by * blocksX;
                byte[] rgbaBlock = decodeRgbaBlock(blocks, blockIndex * BLOCK_BYTES);
                copyRgbaBlock(rgbaBlock, output, width, height, bx * 4, by * 4);
            }
        }
        return output;
    }

    static byte[] transcodeToEac(byte[] blocks, int width, int height, BasisTranscodeTarget target) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }

        int bytesPerBlock = target == BasisTranscodeTarget.ETC2_EAC_RG11 ? 16 : 8;
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), bytesPerBlock)];
        for (int blockIndex = 0; blockIndex < blocksX * blocksY; blockIndex++) {
            int blockOffset = blockIndex * BLOCK_BYTES;
            int outputOffset = blockIndex * bytesPerBlock;
            UnpackedBlock block = unpack(blocks, blockOffset);
            if (block.mode == SOLID_MODE) {
                EacLdrBlockPacker.packSolidBlock(output, outputOffset, block.solidRgba[0]);
                if (target == BasisTranscodeTarget.ETC2_EAC_RG11) {
                    EacLdrBlockPacker.packSolidBlock(output, outputOffset + 8, block.solidRgba[3]);
                }
                continue;
            }

            byte[] rgbaBlock = decodeRgbaBlock(block);
            EacLdrBlockPacker.packRgbaBlockChannel(output, outputOffset, rgbaBlock, 0);
            if (target == BasisTranscodeTarget.ETC2_EAC_RG11) {
                EacLdrBlockPacker.packUastcAlphaHintBlock(
                        output,
                        outputOffset + 8,
                        rgbaBlock,
                        MODE_HAS_ALPHA[block.mode],
                        block.etc2Hints);
            }
        }
        return output;
    }

    static byte[] transcodeToEtc(byte[] blocks, int width, int height, BasisTranscodeTarget target) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }

        int bytesPerBlock = target == BasisTranscodeTarget.ETC2 ? 16 : 8;
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), bytesPerBlock)];
        for (int blockIndex = 0; blockIndex < blocksX * blocksY; blockIndex++) {
            int blockOffset = blockIndex * BLOCK_BYTES;
            int outputOffset = blockIndex * bytesPerBlock;
            UnpackedBlock rawBlock = unpack(blocks, blockOffset, false);
            byte[] rgbaBlock = rawBlock.mode == SOLID_MODE
                    ? solidRgbaBlock(rawBlock.solidRgba)
                    : decodeRgbaBlock(unpack(blocks, blockOffset));
            if (target == BasisTranscodeTarget.ETC2) {
                EacLdrBlockPacker.packUastcAlphaHintBlock(
                        output,
                        outputOffset,
                        rgbaBlock,
                        MODE_HAS_ALPHA[rawBlock.mode],
                        rawBlock.etc2Hints);
                packEtc1Block(output, outputOffset + 8, rawBlock, rgbaBlock);
            } else {
                packEtc1Block(output, outputOffset, rawBlock, rgbaBlock);
            }
        }
        return output;
    }

    static byte[] transcodeToBc4Bc5(byte[] blocks, int width, int height, BasisTranscodeTarget target) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }

        int bytesPerBlock = target == BasisTranscodeTarget.BC5 ? 16 : 8;
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), bytesPerBlock)];
        for (int blockIndex = 0; blockIndex < blocksX * blocksY; blockIndex++) {
            int blockOffset = blockIndex * BLOCK_BYTES;
            int outputOffset = blockIndex * bytesPerBlock;
            UnpackedBlock block = unpack(blocks, blockOffset);
            byte[] rgbaBlock = block.mode == SOLID_MODE
                    ? solidRgbaBlock(block.solidRgba)
                    : decodeRgbaBlock(block);
            Bc4LdrBlockPacker.packRgbaBlockChannel(output, outputOffset, rgbaBlock, 0);
            if (target == BasisTranscodeTarget.BC5) {
                Bc4LdrBlockPacker.packRgbaBlockChannel(output, outputOffset + 8, rgbaBlock, 3);
            }
        }
        return output;
    }

    static byte[] transcodeToBc1(byte[] blocks, int width, int height) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }

        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), 8)];
        for (int blockIndex = 0; blockIndex < blocksX * blocksY; blockIndex++) {
            int blockOffset = blockIndex * BLOCK_BYTES;
            int outputOffset = blockIndex * 8;
            UnpackedBlock rawBlock = unpack(blocks, blockOffset, false);
            if (rawBlock.mode == SOLID_MODE) {
                Bc1LdrBlockPacker.packSolidBlock(
                        output,
                        outputOffset,
                        rawBlock.solidRgba[0],
                        rawBlock.solidRgba[1],
                        rawBlock.solidRgba[2]);
            } else if (rawBlock.bc1Hint0) {
                packBc1Hint0(output, outputOffset, rawBlock);
            } else {
                UnpackedBlock pixelBlock = unpack(blocks, blockOffset);
                byte[] rgbaBlock = decodeRgbaBlock(pixelBlock);
                if (rawBlock.bc1Hint1) {
                    Bc1LdrBlockPacker.packRgbaBlockWithSelectors(
                            output,
                            outputOffset,
                            rgbaBlock,
                            packedBc1HintSelectors(rawBlock));
                } else {
                    Bc1LdrBlockPacker.packRgbaBlock(output, outputOffset, rgbaBlock);
                }
            }
        }
        return output;
    }

    static byte[] transcodeToBc3(byte[] blocks, int width, int height) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }

        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), 16)];
        for (int blockIndex = 0; blockIndex < blocksX * blocksY; blockIndex++) {
            int blockOffset = blockIndex * BLOCK_BYTES;
            int outputOffset = blockIndex * 16;
            UnpackedBlock rawBlock = unpack(blocks, blockOffset, false);
            if (rawBlock.mode == SOLID_MODE) {
                Bc3LdrBlockPacker.packSolidBlock(
                        output,
                        outputOffset,
                        rawBlock.solidRgba[0],
                        rawBlock.solidRgba[1],
                        rawBlock.solidRgba[2],
                        rawBlock.solidRgba[3]);
                continue;
            }

            UnpackedBlock pixelBlock = unpack(blocks, blockOffset);
            byte[] rgbaBlock = decodeRgbaBlock(pixelBlock);
            Bc4LdrBlockPacker.packRgbaBlockChannel(output, outputOffset, rgbaBlock, 3);
            if (rawBlock.bc1Hint0) {
                packBc1Hint0(output, outputOffset + 8, rawBlock);
            } else if (rawBlock.bc1Hint1) {
                Bc1LdrBlockPacker.packRgbaBlockWithSelectors(
                        output,
                        outputOffset + 8,
                        rgbaBlock,
                        packedBc1HintSelectors(rawBlock));
            } else {
                Bc1LdrBlockPacker.packRgbaBlock(output, outputOffset + 8, rgbaBlock);
            }
        }
        return output;
    }

    static byte[] transcodeToBc7(byte[] blocks, int width, int height) {
        if (blocks == null) {
            throw new BasisDecodeException("UASTC block data must not be null");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), BLOCK_BYTES);
        if (blocks.length != expectedBytes) {
            throw new BasisDecodeException("UASTC LDR block data size mismatch");
        }

        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), 16)];
        for (int blockIndex = 0; blockIndex < blocksX * blocksY; blockIndex++) {
            UnpackedBlock block = unpack(blocks, blockIndex * BLOCK_BYTES, false);
            Bc7LdrBlockPacker.writeBlock(output, blockIndex * 16, packBc7Block(block));
        }
        return output;
    }

    private static Bc7LdrBlockPacker.Block packBc7Block(UnpackedBlock block) {
        switch (block.mode) {
            case 0:
            case 5:
            case 10:
            case 12:
            case 14:
            case 15:
            case 18:
                return packBc7Mode6(block);
            case 1:
                return packBc7Mode3SinglePartition(block);
            case 2:
                return packBc7Mode1(block);
            case 3:
                return packBc7Mode2ThreeSubsets(block);
            case 4:
                return packBc7Mode3(block);
            case 6:
            case 11:
            case 13:
            case 17:
                return packBc7Mode5(block);
            case 7:
                return packBc7Mode2Bc7ThreeAstcTwo(block);
            case SOLID_MODE:
                return packBc7Solid(block);
            case 9:
            case 16:
                return packBc7Mode7(block);
            default:
                throw new BasisDecodeException("Unsupported UASTC mode for BC7 transcode");
        }
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode6(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(6);
        int totalComps = MODE_COMPS[block.mode];
        float[] low = new float[4];
        float[] high = new float[4];
        if (totalComps == 2) {
            low[0] = endpointFloat(block, 0);
            high[0] = endpointFloat(block, 1);
            low[1] = low[0];
            high[1] = high[0];
            low[2] = low[0];
            high[2] = high[0];
            low[3] = endpointFloat(block, 2);
            high[3] = endpointFloat(block, 3);
        } else {
            low[0] = endpointFloat(block, 0);
            low[1] = endpointFloat(block, 2);
            low[2] = endpointFloat(block, 4);
            high[0] = endpointFloat(block, 1);
            high[1] = endpointFloat(block, 3);
            high[2] = endpointFloat(block, 5);
            low[3] = totalComps == 4 ? endpointFloat(block, 6) : 1.0f;
            high[3] = totalComps == 4 ? endpointFloat(block, 7) : 1.0f;
        }

        PbitChoice pbits = determineUniquePbits(totalComps == 2 ? 4 : totalComps, 7, low, high);
        copyColor(result.low[0], pbits.low);
        copyColor(result.high[0], pbits.high);
        if (totalComps == 3) {
            result.low[0][3] = 127;
            result.high[0][3] = 127;
        }
        result.pbits[0][0] = pbits.lowPbit;
        result.pbits[0][1] = pbits.highPbit;

        if (block.mode == 18) {
            int[] table = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 6, 7,
                8, 9, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15};
            mapSelectors(result.selectors, block.weights, table, 1);
        } else if (block.mode == 14) {
            int[] table = {0, 5, 10, 15};
            mapSelectors(result.selectors, block.weights, table, 1);
        } else if (block.mode == 5 || block.mode == 12) {
            int[] table = {0, 2, 4, 6, 9, 11, 13, 15};
            mapSelectors(result.selectors, block.weights, table, 1);
        } else {
            System.arraycopy(block.weights, 0, result.selectors, 0, result.selectors.length);
        }
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode3SinglePartition(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(3);
        result.partitionMap = BC7_PARTITION_2_ZERO.clone();
        float[] low = {
            block.endpoints[0] / 255.0f,
            block.endpoints[2] / 255.0f,
            block.endpoints[4] / 255.0f,
            1.0f
        };
        float[] high = {
            block.endpoints[1] / 255.0f,
            block.endpoints[3] / 255.0f,
            block.endpoints[5] / 255.0f,
            1.0f
        };
        PbitChoice pbits = determineUniquePbits(3, 7, low, high);
        for (int subset = 0; subset < 2; subset++) {
            for (int comp = 0; comp < 3; comp++) {
                result.low[subset][comp] = pbits.low[comp];
                result.high[subset][comp] = pbits.high[comp];
            }
            result.pbits[subset][0] = pbits.lowPbit;
            result.pbits[subset][1] = pbits.highPbit;
        }
        System.arraycopy(block.weights, 0, result.selectors, 0, result.selectors.length);
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode1(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(1);
        setupCommonPartition2(result, block);
        for (int subset = 0; subset < 2; subset++) {
            float[] low = new float[4];
            float[] high = new float[4];
            low[3] = 1.0f;
            high[3] = 1.0f;
            for (int comp = 0; comp < 3; comp++) {
                int lo = block.endpoints[comp * 2 + subset * 6];
                int hi = block.endpoints[comp * 2 + subset * 6 + 1];
                low[comp] = ((lo << 4) | lo) / 255.0f;
                high[comp] = ((hi << 4) | hi) / 255.0f;
            }
            PbitChoice pbits = determineSharedPbits(3, 6, low, high);
            int bc7Subset = COMMON_PARTITION_2_INVERT[block.commonPattern] ? 1 - subset : subset;
            for (int comp = 0; comp < 3; comp++) {
                result.low[bc7Subset][comp] = pbits.low[comp];
                result.high[bc7Subset][comp] = pbits.high[comp];
            }
            result.pbits[bc7Subset][0] = pbits.lowPbit;
        }
        System.arraycopy(block.weights, 0, result.selectors, 0, result.selectors.length);
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode2ThreeSubsets(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(2);
        result.partition = COMMON_PARTITION_3_BC7[block.commonPattern];
        result.partitionMap = bc7PartitionMapFromAstc3(block);
        int perm = COMMON_PARTITION_3_PERM[block.commonPattern];
        for (int subset = 0; subset < 3; subset++) {
            int bc7Subset = ASTC_TO_BC7_PARTITION_PERM[perm][subset];
            for (int comp = 0; comp < 3; comp++) {
                int lo = dequantEndpoint(
                        block.endpoints[comp * 2 + subset * 6],
                        MODE_ENDPOINT_RANGES[block.mode]);
                int hi = dequantEndpoint(
                        block.endpoints[comp * 2 + subset * 6 + 1],
                        MODE_ENDPOINT_RANGES[block.mode]);
                result.low[bc7Subset][comp] = (lo * 31 + 127) / 255;
                result.high[bc7Subset][comp] = (hi * 31 + 127) / 255;
            }
        }
        System.arraycopy(block.weights, 0, result.selectors, 0, result.selectors.length);
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode3(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(3);
        setupCommonPartition2(result, block);
        for (int subset = 0; subset < 2; subset++) {
            float[] low = new float[4];
            float[] high = new float[4];
            low[3] = 1.0f;
            high[3] = 1.0f;
            for (int comp = 0; comp < 3; comp++) {
                low[comp] = endpointFloat(block, comp * 2 + subset * 6);
                high[comp] = endpointFloat(block, comp * 2 + subset * 6 + 1);
            }
            PbitChoice pbits = determineUniquePbits(3, 7, low, high);
            int bc7Subset = COMMON_PARTITION_2_INVERT[block.commonPattern] ? 1 - subset : subset;
            for (int comp = 0; comp < 3; comp++) {
                result.low[bc7Subset][comp] = pbits.low[comp];
                result.high[bc7Subset][comp] = pbits.high[comp];
            }
            result.low[bc7Subset][3] = 127;
            result.high[bc7Subset][3] = 127;
            result.pbits[bc7Subset][0] = pbits.lowPbit;
            result.pbits[bc7Subset][1] = pbits.highPbit;
        }
        System.arraycopy(block.weights, 0, result.selectors, 0, result.selectors.length);
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode5(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(5);
        result.rotation = (block.colorComponentSelector + 1) & 3;
        int totalComps = MODE_COMPS[block.mode];
        if (totalComps == 2) {
            int low = dequantEndpoint(block.endpoints[0], MODE_ENDPOINT_RANGES[block.mode]);
            int high = dequantEndpoint(block.endpoints[1], MODE_ENDPOINT_RANGES[block.mode]);
            result.low[0][0] = (low * 127 + 127) / 255;
            result.high[0][0] = (high * 127 + 127) / 255;
            result.low[0][1] = result.low[0][0];
            result.high[0][1] = result.high[0][0];
            result.low[0][2] = result.low[0][0];
            result.high[0][2] = result.high[0][0];
            result.low[0][3] = dequantEndpoint(block.endpoints[2], MODE_ENDPOINT_RANGES[block.mode]);
            result.high[0][3] = dequantEndpoint(block.endpoints[3], MODE_ENDPOINT_RANGES[block.mode]);
        } else {
            for (int astcComp = 0; astcComp < 4; astcComp++) {
                int bc7Comp = astcComp;
                if (astcComp == block.colorComponentSelector) {
                    bc7Comp = 3;
                } else if (astcComp == 3) {
                    bc7Comp = block.colorComponentSelector;
                }
                int low = 255;
                int high = 255;
                if (astcComp < totalComps) {
                    int endpoint = astcComp * 2;
                    low = dequantEndpoint(block.endpoints[endpoint], MODE_ENDPOINT_RANGES[block.mode]);
                    high = dequantEndpoint(block.endpoints[endpoint + 1], MODE_ENDPOINT_RANGES[block.mode]);
                }
                if (bc7Comp < 3) {
                    low = (low * 127 + 127) / 255;
                    high = (high * 127 + 127) / 255;
                }
                result.low[0][bc7Comp] = low;
                result.high[0][bc7Comp] = high;
            }
        }
        if (block.mode == 13) {
            for (int i = 0; i < 16; i++) {
                result.selectors[i] = block.weights[i * 2] != 0 ? 3 : 0;
                result.alphaSelectors[i] = block.weights[i * 2 + 1] != 0 ? 3 : 0;
            }
        } else {
            for (int i = 0; i < 16; i++) {
                result.selectors[i] = block.weights[i * 2];
                result.alphaSelectors[i] = block.weights[i * 2 + 1];
            }
        }
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode2Bc7ThreeAstcTwo(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(2);
        result.partition = BC7_3_ASTC_2_BC7[block.commonPattern];
        result.partitionMap = selectedBc7Partition3(result.partition);
        int commonK = BC7_3_ASTC_2_K[block.commonPattern];
        for (int bc7Part = 0; bc7Part < 3; bc7Part++) {
            int astcPart = bc7ConvertPartitionIndex3To2(bc7Part, commonK);
            for (int comp = 0; comp < 3; comp++) {
                int lo = dequantEndpoint(
                        block.endpoints[comp * 2 + astcPart * 6],
                        MODE_ENDPOINT_RANGES[block.mode]);
                int hi = dequantEndpoint(
                        block.endpoints[comp * 2 + astcPart * 6 + 1],
                        MODE_ENDPOINT_RANGES[block.mode]);
                result.low[bc7Part][comp] = (lo * 31 + 127) / 255;
                result.high[bc7Part][comp] = (hi * 31 + 127) / 255;
            }
        }
        System.arraycopy(block.weights, 0, result.selectors, 0, result.selectors.length);
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Solid(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result;
        int[] color = block.solidRgba;
        int err0 = 0;
        int err1 = 0;
        int[][] mode6P0 = new int[4][];
        int[][] mode6P1 = new int[4][];
        for (int comp = 0; comp < 4; comp++) {
            mode6P0[comp] = Bc7LdrBlockPacker.mode6OptimalEndpoint(color[comp], 0);
            mode6P1[comp] = Bc7LdrBlockPacker.mode6OptimalEndpoint(color[comp], 1);
            err0 += mode6P0[comp][2];
            err1 += mode6P1[comp][2];
        }
        if (err0 > 0 && err1 > 0) {
            result = new Bc7LdrBlockPacker.Block(5);
            for (int comp = 0; comp < 3; comp++) {
                int[] endpoint = Bc7LdrBlockPacker.mode5OptimalEndpoint(color[comp]);
                result.low[0][comp] = endpoint[0];
                result.high[0][comp] = endpoint[1];
            }
            result.low[0][3] = color[3];
            result.high[0][3] = color[3];
            for (int i = 0; i < 16; i++) {
                result.selectors[i] = 1;
            }
        } else {
            result = new Bc7LdrBlockPacker.Block(6);
            int pbit = err1 < err0 ? 1 : 0;
            int[][] endpoints = pbit == 0 ? mode6P0 : mode6P1;
            for (int comp = 0; comp < 4; comp++) {
                result.low[0][comp] = endpoints[comp][0];
                result.high[0][comp] = endpoints[comp][1];
            }
            result.pbits[0][0] = pbit;
            result.pbits[0][1] = pbit;
            for (int i = 0; i < 16; i++) {
                result.selectors[i] = 5;
            }
        }
        return result;
    }

    private static Bc7LdrBlockPacker.Block packBc7Mode7(UnpackedBlock block) {
        Bc7LdrBlockPacker.Block result = new Bc7LdrBlockPacker.Block(7);
        setupCommonPartition2(result, block);
        int totalComps = MODE_COMPS[block.mode];
        for (int subset = 0; subset < 2; subset++) {
            float[] low = new float[4];
            float[] high = new float[4];
            if (totalComps == 2) {
                low[0] = endpointFloat(block, subset * 4);
                high[0] = endpointFloat(block, subset * 4 + 1);
                low[1] = low[0];
                high[1] = high[0];
                low[2] = low[0];
                high[2] = high[0];
                low[3] = endpointFloat(block, subset * 4 + 2);
                high[3] = endpointFloat(block, subset * 4 + 3);
            } else {
                for (int comp = 0; comp < 4; comp++) {
                    low[comp] = endpointFloat(block, comp * 2 + subset * 8);
                    high[comp] = endpointFloat(block, comp * 2 + subset * 8 + 1);
                }
            }
            PbitChoice pbits = determineUniquePbits(4, 5, low, high);
            int bc7Subset = COMMON_PARTITION_2_INVERT[block.commonPattern] ? 1 - subset : subset;
            copyColor(result.low[bc7Subset], pbits.low);
            copyColor(result.high[bc7Subset], pbits.high);
            result.pbits[bc7Subset][0] = pbits.lowPbit;
            result.pbits[bc7Subset][1] = pbits.highPbit;
        }
        System.arraycopy(block.weights, 0, result.selectors, 0, result.selectors.length);
        return result;
    }

    private static void setupCommonPartition2(Bc7LdrBlockPacker.Block result, UnpackedBlock block) {
        result.partition = COMMON_PARTITION_2_BC7[block.commonPattern];
        result.partitionMap = selectedBc7Partition2(result.partition);
    }

    private static int[] selectedBc7Partition2(int partition) {
        if (partition >= 0 && partition < 64) {
            return Bc7PartitionTables.partition2Map(partition);
        }
        switch (partition) {
            case 0:
                return new int[] {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1};
            case 1:
                return new int[] {0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1};
            case 2:
                return new int[] {0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1};
            case 3:
                return new int[] {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1};
            case 4:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1};
            case 5:
                return new int[] {0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1};
            case 6:
                return new int[] {0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1};
            case 7:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1};
            case 8:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1};
            case 9:
                return new int[] {0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
            case 10:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1};
            case 11:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 1};
            case 12:
                return new int[] {0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
            case 13:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1};
            case 14:
                return new int[] {0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
            case 15:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1};
            case 17:
                return new int[] {0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0};
            case 18:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0};
            case 19:
                return new int[] {0, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0};
            case 20:
                return new int[] {0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0};
            case 21:
                return new int[] {0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0};
            case 22:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0};
            case 23:
                return new int[] {0, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1};
            case 24:
                return new int[] {0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0};
            case 25:
                return new int[] {0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0};
            case 26:
                return new int[] {0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0};
            case 29:
                return new int[] {0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0};
            case 32:
                return new int[] {0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1};
            case 33:
                return new int[] {0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1};
            case 52:
                return new int[] {0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1};
            default:
                throw new BasisDecodeException("Unsupported UASTC BC7 partition");
        }
    }

    private static int[] bc7PartitionMapFromAstc3(UnpackedBlock block) {
        int[] astcMap = partitionMap(block.partitionSeed, 3);
        int[] result = new int[16];
        int[] perm = ASTC_TO_BC7_PARTITION_PERM[COMMON_PARTITION_3_PERM[block.commonPattern]];
        for (int i = 0; i < astcMap.length; i++) {
            result[i] = perm[astcMap[i]];
        }
        return result;
    }

    private static int[] selectedBc7Partition3(int partition) {
        if (partition >= 0 && partition < 64) {
            return Bc7PartitionTables.partition3Map(partition);
        }
        switch (partition) {
            case 0:
                return new int[] {0, 0, 1, 1, 0, 0, 1, 1, 0, 2, 2, 1, 2, 2, 2, 2};
            case 1:
                return new int[] {0, 0, 0, 1, 0, 0, 1, 1, 2, 2, 1, 1, 2, 2, 2, 1};
            case 2:
                return new int[] {0, 0, 0, 0, 2, 0, 0, 1, 2, 2, 1, 1, 2, 2, 1, 1};
            case 3:
                return new int[] {0, 2, 2, 2, 0, 0, 2, 2, 0, 0, 1, 1, 0, 1, 1, 1};
            case 8:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2};
            case 10:
                return new int[] {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2};
            case 11:
                return new int[] {0, 0, 1, 2, 0, 0, 1, 2, 0, 0, 1, 2, 0, 0, 1, 2};
            case 13:
                return new int[] {0, 1, 2, 2, 0, 1, 2, 2, 0, 1, 2, 2, 0, 1, 2, 2};
            case 14:
                return new int[] {0, 0, 1, 1, 0, 1, 1, 2, 1, 1, 2, 2, 1, 2, 2, 2};
            case 20:
                return new int[] {0, 1, 1, 1, 0, 1, 1, 1, 0, 2, 2, 2, 0, 2, 2, 2};
            case 21:
                return new int[] {0, 0, 0, 1, 0, 0, 0, 1, 2, 2, 2, 1, 2, 2, 2, 1};
            case 31:
                return new int[] {0, 0, 0, 0, 2, 0, 0, 0, 2, 2, 1, 1, 2, 2, 2, 1};
            case 32:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 2, 1, 1, 2, 2, 1, 2, 2, 2};
            case 33:
                return new int[] {0, 2, 2, 2, 0, 0, 2, 2, 0, 0, 1, 2, 0, 0, 1, 1};
            case 34:
                return new int[] {0, 0, 1, 1, 0, 0, 1, 2, 0, 0, 2, 2, 0, 2, 2, 2};
            case 40:
                return new int[] {0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 0, 0, 0, 0, 1, 1};
            case 58:
                return new int[] {0, 0, 2, 2, 1, 1, 2, 2, 1, 1, 2, 2, 0, 0, 2, 2};
            case 59:
                return new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 1, 2};
            default:
                throw new BasisDecodeException("Unsupported UASTC BC7 partition");
        }
    }

    private static int bc7ConvertPartitionIndex3To2(int partition, int k) {
        int value = partition;
        switch (k >>> 1) {
            case 0:
                value = value <= 1 ? 0 : 1;
                break;
            case 1:
                value = value == 0 ? 0 : 1;
                break;
            case 2:
                value = value == 0 || value == 2 ? 0 : 1;
                break;
            default:
                break;
        }
        return (k & 1) != 0 ? 1 - value : value;
    }

    private static void mapSelectors(int[] output, int[] weights, int[] table, int stride) {
        for (int i = 0; i < output.length; i++) {
            output[i] = table[weights[i * stride]];
        }
    }

    private static float endpointFloat(UnpackedBlock block, int endpoint) {
        return dequantEndpoint(block.endpoints[endpoint], MODE_ENDPOINT_RANGES[block.mode]) / 255.0f;
    }

    private static void copyColor(int[] dst, int[] src) {
        System.arraycopy(src, 0, dst, 0, Math.min(dst.length, src.length));
    }

    private static PbitChoice determineSharedPbits(int totalComps, int compBits, float[] low, float[] high) {
        int totalBits = compBits + 1;
        int scaleInt = (1 << totalBits) - 1;
        float scale = scaleInt;
        float bestError = Float.MAX_VALUE;
        PbitChoice best = new PbitChoice();
        for (int pbit = 0; pbit < 2; pbit++) {
            int[] minColor = new int[4];
            int[] maxColor = new int[4];
            int[] scaledLow = new int[4];
            int[] scaledHigh = new int[4];
            for (int comp = 0; comp < 4; comp++) {
                minColor[comp] = quantizePbitEndpoint(low[comp], scale, pbit, scaleInt);
                maxColor[comp] = quantizePbitEndpoint(high[comp], scale, pbit, scaleInt);
                scaledLow[comp] = scalePbitEndpoint(minColor[comp], totalBits);
                scaledHigh[comp] = scalePbitEndpoint(maxColor[comp], totalBits);
            }
            float error = 0.0f;
            for (int comp = 0; comp < totalComps; comp++) {
                float lowDelta = scaledLow[comp] * (1.0f / 255.0f) - low[comp];
                float highDelta = scaledHigh[comp] * (1.0f / 255.0f) - high[comp];
                error += lowDelta * lowDelta + highDelta * highDelta;
            }
            if (error < bestError) {
                bestError = error;
                best.lowPbit = pbit;
                best.highPbit = pbit;
                for (int comp = 0; comp < 4; comp++) {
                    best.low[comp] = minColor[comp] >>> 1;
                    best.high[comp] = maxColor[comp] >>> 1;
                }
            }
        }
        return best;
    }

    private static PbitChoice determineUniquePbits(int totalComps, int compBits, float[] low, float[] high) {
        int totalBits = compBits + 1;
        int scaleInt = (1 << totalBits) - 1;
        float scale = scaleInt;
        float bestLowError = Float.MAX_VALUE;
        float bestHighError = Float.MAX_VALUE;
        PbitChoice best = new PbitChoice();
        for (int pbit = 0; pbit < 2; pbit++) {
            int[] minColor = new int[4];
            int[] maxColor = new int[4];
            int[] scaledLow = new int[4];
            int[] scaledHigh = new int[4];
            for (int comp = 0; comp < 4; comp++) {
                minColor[comp] = quantizePbitEndpoint(low[comp], scale, pbit, scaleInt);
                maxColor[comp] = quantizePbitEndpoint(high[comp], scale, pbit, scaleInt);
                scaledLow[comp] = scalePbitEndpoint(minColor[comp], totalBits);
                scaledHigh[comp] = scalePbitEndpoint(maxColor[comp], totalBits);
            }
            float lowError = 0.0f;
            float highError = 0.0f;
            for (int comp = 0; comp < totalComps; comp++) {
                float lowDelta = scaledLow[comp] - low[comp] * 255.0f;
                float highDelta = scaledHigh[comp] - high[comp] * 255.0f;
                lowError += lowDelta * lowDelta;
                highError += highDelta * highDelta;
            }
            if (lowError < bestLowError) {
                bestLowError = lowError;
                best.lowPbit = pbit;
                for (int comp = 0; comp < 4; comp++) {
                    best.low[comp] = minColor[comp] >>> 1;
                }
            }
            if (highError < bestHighError) {
                bestHighError = highError;
                best.highPbit = pbit;
                for (int comp = 0; comp < 4; comp++) {
                    best.high[comp] = maxColor[comp] >>> 1;
                }
            }
        }
        return best;
    }

    private static int quantizePbitEndpoint(float value, float scale, int pbit, int scaleInt) {
        int quantized = ((int) ((value * scale - pbit) / 2.0f + 0.5f)) * 2 + pbit;
        return clamp(quantized, pbit, scaleInt - 1 + pbit);
    }

    private static int scalePbitEndpoint(int value, int totalBits) {
        int scaled = value << (8 - totalBits);
        return scaled | (scaled >>> totalBits);
    }

    private static final class PbitChoice {
        final int[] low = new int[4];
        final int[] high = new int[4];
        int lowPbit;
        int highPbit;
    }

    private static void packBc1Hint0(byte[] output, int outputOffset, UnpackedBlock block) {
        int endpointRange = MODE_ENDPOINT_RANGES[block.mode];
        int totalComps = MODE_COMPS[block.mode];
        int lowColor;
        int highColor;
        if (totalComps == 2) {
            int low = dequantEndpoint(block.endpoints[0], endpointRange);
            int high = dequantEndpoint(block.endpoints[1], endpointRange);
            lowColor = Bc1LdrBlockPacker.packColor888(low, low, low);
            highColor = Bc1LdrBlockPacker.packColor888(high, high, high);
        } else {
            lowColor = Bc1LdrBlockPacker.packColor888(
                    dequantEndpoint(block.endpoints[0], endpointRange),
                    dequantEndpoint(block.endpoints[2], endpointRange),
                    dequantEndpoint(block.endpoints[4], endpointRange));
            highColor = Bc1LdrBlockPacker.packColor888(
                    dequantEndpoint(block.endpoints[1], endpointRange),
                    dequantEndpoint(block.endpoints[3], endpointRange),
                    dequantEndpoint(block.endpoints[5], endpointRange));
        }

        if (lowColor == highColor) {
            int mask = 0;
            if (highColor > 0) {
                highColor--;
            } else {
                highColor = 0;
                lowColor = 1;
                mask = 0x55;
            }
            Bc1LdrBlockPacker.writeBlock(output, outputOffset, lowColor, highColor, mask | (mask << 8)
                    | (mask << 16) | (mask << 24));
            return;
        }

        boolean invert = false;
        if (lowColor < highColor) {
            int temp = lowColor;
            lowColor = highColor;
            highColor = temp;
            invert = true;
        }

        int packedSelectors = packedBc1HintSelectors(block, invert);
        Bc1LdrBlockPacker.writeBlock(output, outputOffset, lowColor, highColor, packedSelectors);
    }

    private static void packEtc1Block(byte[] output, int offset, UnpackedBlock block, byte[] rgbaBlock) {
        if (block.mode == SOLID_MODE) {
            output[offset + 3] = (byte) ((block.etc1Diff ? 2 : 0)
                    | (block.etc1Intensity0 << 5)
                    | (block.etc1Intensity0 << 2));
            if (block.etc1Diff) {
                output[offset] = (byte) (block.etc1Red << 3);
                output[offset + 1] = (byte) (block.etc1Green << 3);
                output[offset + 2] = (byte) (block.etc1Blue << 3);
            } else {
                output[offset] = (byte) (block.etc1Red | (block.etc1Red << 4));
                output[offset + 1] = (byte) (block.etc1Green | (block.etc1Green << 4));
                output[offset + 2] = (byte) (block.etc1Blue | (block.etc1Blue << 4));
            }
            System.arraycopy(ETC1_SOLID_SELECTORS[block.etc1Selector], 0, output, offset + 4, 4);
            return;
        }

        int flip = block.etc1Flip ? 1 : 0;
        int diff = block.etc1Diff ? 1 : 0;
        output[offset + 3] = (byte) (flip | (diff << 1)
                | (block.etc1Intensity0 << 5)
                | (block.etc1Intensity1 << 2));

        int limit = block.etc1Diff ? 31 : 15;
        int[][] colors = new int[2][3];
        for (int subset = 0; subset < 2; subset++) {
            int[] avg = new int[3];
            for (int i = 0; i < 8; i++) {
                int[] coord = ETC1_PIXEL_COORDS[flip][subset][i];
                int pixel = (coord[1] * 4 + coord[0]) * 4;
                avg[0] += Byte.toUnsignedInt(rgbaBlock[pixel]);
                avg[1] += Byte.toUnsignedInt(rgbaBlock[pixel + 1]);
                avg[2] += Byte.toUnsignedInt(rgbaBlock[pixel + 2]);
            }
            for (int channel = 0; channel < 3; channel++) {
                colors[subset][channel] = (avg[channel] * limit + 1020) / (8 * 255);
            }
            if (MODE_HAS_ETC1_BIAS[block.mode]) {
                applyEtc1Bias(colors[subset], block.etc1Bias, limit, subset);
            }
        }

        if (block.etc1Diff) {
            int dr = clamp(colors[1][0] - colors[0][0], -4, 3);
            int dg = clamp(colors[1][1] - colors[0][1], -4, 3);
            int db = clamp(colors[1][2] - colors[0][2], -4, 3);
            output[offset] = (byte) ((colors[0][0] << 3) | encodeEtc1Delta(dr));
            output[offset + 1] = (byte) ((colors[0][1] << 3) | encodeEtc1Delta(dg));
            output[offset + 2] = (byte) ((colors[0][2] << 3) | encodeEtc1Delta(db));
        } else {
            output[offset] = (byte) (colors[1][0] | (colors[0][0] << 4));
            output[offset + 1] = (byte) (colors[1][1] | (colors[0][1] << 4));
            output[offset + 2] = (byte) (colors[1][2] | (colors[0][2] << 4));
        }
        determineEtc1Selectors(output, offset, rgbaBlock);
    }

    private static void determineEtc1Selectors(byte[] output, int offset, byte[] rgbaBlock) {
        int lowMask = 0;
        int highMask = 0;
        boolean flip = (output[offset + 3] & 1) != 0;
        for (int subset = 0; subset < 2; subset++) {
            int[][] blockColors = etc1BlockColors(output, offset, subset);
            int[] luminance = new int[4];
            for (int i = 0; i < 4; i++) {
                luminance[i] = blockColors[i][0] * 54 + blockColors[i][1] * 183 + blockColors[i][2] * 19;
            }
            int threshold01 = luminance[0] + luminance[1];
            int threshold12 = luminance[1] + luminance[2];
            int threshold23 = luminance[2] + luminance[3];

            if (flip) {
                int bitOffset = subset * 2;
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 4; x++) {
                        int pixel = ((subset * 2 + y) * 4 + x) * 4;
                        int luma = Byte.toUnsignedInt(rgbaBlock[pixel]) * 108
                                + Byte.toUnsignedInt(rgbaBlock[pixel + 1]) * 366
                                + Byte.toUnsignedInt(rgbaBlock[pixel + 2]) * 38;
                        int selector = etcSelectorForLuma(luma, threshold01, threshold12, threshold23);
                        lowMask |= (selector & 1) << bitOffset;
                        highMask |= (selector >>> 1) << bitOffset;
                        bitOffset += 4;
                    }
                    bitOffset += 1 - 16;
                }
            } else {
                int bitOffset = subset * 8;
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 4; y++) {
                        int pixel = (y * 4 + subset * 2 + x) * 4;
                        int luma = Byte.toUnsignedInt(rgbaBlock[pixel]) * 108
                                + Byte.toUnsignedInt(rgbaBlock[pixel + 1]) * 366
                                + Byte.toUnsignedInt(rgbaBlock[pixel + 2]) * 38;
                        int selector = etcSelectorForLuma(luma, threshold01, threshold12, threshold23);
                        lowMask |= (selector & 1) << bitOffset;
                        highMask |= (selector >>> 1) << bitOffset;
                        bitOffset++;
                    }
                }
            }
        }

        output[offset + 7] = (byte) lowMask;
        output[offset + 6] = (byte) (lowMask >>> 8);
        output[offset + 5] = (byte) highMask;
        output[offset + 4] = (byte) (highMask >>> 8);
    }

    private static int[][] etc1BlockColors(byte[] output, int offset, int subset) {
        int b0 = Byte.toUnsignedInt(output[offset]);
        int b1 = Byte.toUnsignedInt(output[offset + 1]);
        int b2 = Byte.toUnsignedInt(output[offset + 2]);
        int b3 = Byte.toUnsignedInt(output[offset + 3]);
        int baseR;
        int baseG;
        int baseB;
        if ((b3 & 2) != 0) {
            baseR = expand5(clamp((b0 >>> 3) + decodeEtc1Delta(b0 & 7), 0, 31));
            baseG = expand5(clamp((b1 >>> 3) + decodeEtc1Delta(b1 & 7), 0, 31));
            baseB = expand5(clamp((b2 >>> 3) + decodeEtc1Delta(b2 & 7), 0, 31));
            if (subset == 0) {
                baseR = expand5(b0 >>> 3);
                baseG = expand5(b1 >>> 3);
                baseB = expand5(b2 >>> 3);
            }
        } else if (subset == 0) {
            baseR = expand4(b0 >>> 4);
            baseG = expand4(b1 >>> 4);
            baseB = expand4(b2 >>> 4);
        } else {
            baseR = expand4(b0 & 15);
            baseG = expand4(b1 & 15);
            baseB = expand4(b2 & 15);
        }

        int intensity = subset == 0 ? b3 >>> 5 : (b3 >>> 2) & 7;
        int[][] colors = new int[4][3];
        for (int i = 0; i < 4; i++) {
            int modifier = ETC1_INTENSITY_TABLES[intensity][i];
            colors[i][0] = clamp255(baseR + modifier);
            colors[i][1] = clamp255(baseG + modifier);
            colors[i][2] = clamp255(baseB + modifier);
        }
        return colors;
    }

    private static int etcSelectorForLuma(int luma, int threshold01, int threshold12, int threshold23) {
        int index = (luma < threshold01 ? 1 : 0)
                + (luma < threshold12 ? 1 : 0)
                + (luma < threshold23 ? 1 : 0);
        return ETC1_LUMA_SELECTOR_TRANSLATION[index];
    }

    private static void applyEtc1Bias(int[] color, int bias, int limit, int subset) {
        for (int channel = 0; channel < 3; channel++) {
            int delta = etc1BiasDelta(bias, subset, channel);
            int value = color[channel];
            if (value == 0) {
                value += delta == -2 ? 3 : delta + 1;
            } else if (value == limit) {
                value += delta - 1;
            } else {
                value += delta;
                if (value < 0 || value > limit) {
                    value = value - delta - delta;
                }
            }
            color[channel] = value;
        }
    }

    private static int etc1BiasDelta(int bias, int subset, int channel) {
        switch (bias) {
            case 2:
                return subset != 0 ? 0 : channel == 0 ? -1 : 0;
            case 5:
                return subset != 0 ? 0 : channel == 1 ? -1 : 0;
            case 6:
                return subset != 0 ? 0 : channel == 2 ? -1 : 0;
            case 7:
                return subset != 0 ? 0 : channel == 0 ? 1 : 0;
            case 11:
                return subset != 0 ? 0 : channel == 1 ? 1 : 0;
            case 15:
                return subset != 0 ? 0 : channel == 2 ? 1 : 0;
            case 18:
                return subset != 0 && channel == 0 ? -1 : 0;
            case 19:
                return subset != 0 && channel == 1 ? -1 : 0;
            case 20:
                return subset != 0 && channel == 2 ? -1 : 0;
            case 21:
                return subset != 0 && channel == 0 ? 1 : 0;
            case 24:
                return subset != 0 && channel == 1 ? 1 : 0;
            case 8:
                return subset != 0 && channel == 2 ? 1 : 0;
            case 10:
                return -2;
            case 27:
                return subset != 0 ? 0 : -1;
            case 28:
                return subset != 0 ? -1 : 1;
            case 29:
                return subset != 0 ? 1 : 0;
            case 30:
                return subset != 0 ? -1 : 0;
            case 31:
                return subset != 0 ? 0 : 1;
            default:
                int divisor = channel == 0 ? 1 : channel == 1 ? 3 : 9;
                return (bias / divisor) % 3 - 1;
        }
    }

    private static int encodeEtc1Delta(int delta) {
        return delta < 0 ? delta + 8 : delta;
    }

    private static int decodeEtc1Delta(int delta) {
        return delta >= 4 ? delta - 8 : delta;
    }

    private static int expand4(int value) {
        return (value << 4) | value;
    }

    private static int expand5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static int clamp255(int value) {
        return clamp(value, 0, 255);
    }

    private static byte[] transcodeBlock(byte[] data, int offset) {
        UnpackedBlock block = unpack(data, offset);
        if (block.mode == SOLID_MODE) {
            return XuastcAstcBlockPacker.packSolidBlock(block.solidRgba);
        }
        return packAstcBlock(block);
    }

    private static byte[] decodeRgbaBlock(byte[] data, int offset) {
        UnpackedBlock block = unpack(data, offset);
        if (block.mode == SOLID_MODE) {
            return solidRgbaBlock(block.solidRgba);
        }
        return decodeRgbaBlock(block);
    }

    private static byte[] decodeRgbaBlock(UnpackedBlock block) {
        int subsets = MODE_SUBSETS[block.mode];
        int cem = MODE_CEM[block.mode];
        int endpointValues = XuastcAstcConstants.numCemEndpointValues(cem);
        int[] endpoints = new int[subsets * 8];
        for (int subset = 0; subset < subsets; subset++) {
            int[] decoded = XuastcAstcConstants.decodeLdrEndpoints(
                    cem,
                    block.endpoints,
                    subset * endpointValues,
                    MODE_ENDPOINT_RANGES[block.mode]);
            System.arraycopy(decoded, 0, endpoints, subset * 8, decoded.length);
        }

        int[] partitionMap = subsets == 1 ? null : partitionMap(block.partitionSeed, subsets);
        int planes = MODE_PLANES[block.mode];
        int weightRange = MODE_WEIGHT_RANGES[block.mode];
        byte[] rgba = new byte[BLOCK_BYTES * 4];
        for (int texel = 0; texel < 16; texel++) {
            int partition = partitionMap == null ? 0 : partitionMap[texel];
            int endpointOffset = partition * 8;
            int weight0 = dequantWeight(block.weights[texel * planes], weightRange);
            int dst = texel * 4;
            rgba[dst] = (byte) interpolateColor(
                    endpoints[endpointOffset],
                    endpoints[endpointOffset + 4],
                    weightForComponent(block, texel, planes, 0, weight0, weightRange));
            rgba[dst + 1] = (byte) interpolateColor(
                    endpoints[endpointOffset + 1],
                    endpoints[endpointOffset + 5],
                    weightForComponent(block, texel, planes, 1, weight0, weightRange));
            rgba[dst + 2] = (byte) interpolateColor(
                    endpoints[endpointOffset + 2],
                    endpoints[endpointOffset + 6],
                    weightForComponent(block, texel, planes, 2, weight0, weightRange));
            rgba[dst + 3] = (byte) interpolateColor(
                    endpoints[endpointOffset + 3],
                    endpoints[endpointOffset + 7],
                    weightForComponent(block, texel, planes, 3, weight0, weightRange));
        }
        return rgba;
    }

    private static byte[] solidRgbaBlock(int[] solidRgba) {
        byte[] rgba = new byte[BLOCK_BYTES * 4];
        for (int texel = 0; texel < 16; texel++) {
            int offset = texel * 4;
            rgba[offset] = (byte) solidRgba[0];
            rgba[offset + 1] = (byte) solidRgba[1];
            rgba[offset + 2] = (byte) solidRgba[2];
            rgba[offset + 3] = (byte) solidRgba[3];
        }
        return rgba;
    }

    private static int weightForComponent(
            UnpackedBlock block,
            int texel,
            int planes,
            int component,
            int weight0,
            int weightRange) {
        if (planes == 2 && block.colorComponentSelector == component) {
            return dequantWeight(block.weights[texel * planes + 1], weightRange);
        }
        return weight0;
    }

    private static int dequantWeight(int weight, int weightRange) {
        return XuastcAstcConstants.dequantBiseWeight(weight, weightRange);
    }

    private static int interpolateColor(int low, int high, int weight) {
        int low16 = (low << 8) | low;
        int high16 = (high << 8) | high;
        return (low16 * (64 - weight) + high16 * weight + 32) >> 14;
    }

    private static void copyRgbaBlock(
            byte[] block,
            byte[] output,
            int width,
            int height,
            int offsetX,
            int offsetY) {
        int copyWidth = Math.min(4, width - offsetX);
        int copyHeight = Math.min(4, height - offsetY);
        for (int y = 0; y < copyHeight; y++) {
            int src = y * 4 * 4;
            int dst = ((offsetY + y) * width + offsetX) * 4;
            System.arraycopy(block, src, output, dst, copyWidth * 4);
        }
    }

    private static UnpackedBlock unpack(byte[] data, int offset) {
        return unpack(data, offset, true);
    }

    private static UnpackedBlock unpack(byte[] data, int offset, boolean applyBlueContractionFix) {
        int mode = HUFF_MODE[Byte.toUnsignedInt(data[offset]) & 127];
        if (mode >= TOTAL_MODES) {
            throw new BasisDecodeException("Invalid UASTC mode");
        }
        int[] bitOffset = {HUFF_CODE[mode][1]};
        UnpackedBlock block = new UnpackedBlock(mode);
        if (mode == SOLID_MODE) {
            block.solidRgba = new int[] {
                readBits(data, offset, bitOffset, 8),
                readBits(data, offset, bitOffset, 8),
                readBits(data, offset, bitOffset, 8),
                readBits(data, offset, bitOffset, 8)
            };
            block.etc1Flip = false;
            block.etc1Diff = readBits(data, offset, bitOffset, 1) != 0;
            block.etc1Intensity0 = readBits(data, offset, bitOffset, 3);
            block.etc1Intensity1 = 0;
            block.etc1Selector = readBits(data, offset, bitOffset, 2);
            block.etc1Red = readBits(data, offset, bitOffset, 5);
            block.etc1Green = readBits(data, offset, bitOffset, 5);
            block.etc1Blue = readBits(data, offset, bitOffset, 5);
            block.etc1Bias = 0;
            block.etc2Hints = 0;
            return block;
        }

        readHints(data, offset, bitOffset, block);
        final int subsets = MODE_SUBSETS[mode];
        switch (mode) {
            case 2:
            case 4:
            case 7:
            case 9:
            case 16:
                block.commonPattern = readBits(data, offset, bitOffset, 5);
                break;
            case 3:
                block.commonPattern = readBits(data, offset, bitOffset, 4);
                break;
            default:
                break;
        }
        block.partitionSeed = partitionSeed(mode, block.commonPattern);
        int planes = MODE_PLANES[mode];
        switch (mode) {
            case 6:
            case 11:
            case 13:
                block.colorComponentSelector = readBits(data, offset, bitOffset, 2);
                break;
            case 17:
                block.colorComponentSelector = 3;
                break;
            default:
                block.colorComponentSelector = -1;
                break;
        }

        int totalComps = MODE_COMPS[mode];
        int totalValues = totalComps * 2 * subsets;
        int endpointRange = MODE_ENDPOINT_RANGES[mode];
        int endpointBits = XuastcAstcConstants.getIseBitCount(endpointRange);
        int endpointTrits = XuastcAstcConstants.getIseTritCount(endpointRange);
        int endpointQuints = XuastcAstcConstants.getIseQuintCount(endpointRange);
        int totalTq = 0;
        int bundleSize = 0;
        int multiplier = 0;
        if (endpointTrits != 0) {
            totalTq = (totalValues + 4) / 5;
            bundleSize = 5;
            multiplier = 3;
        } else if (endpointQuints != 0) {
            totalTq = (totalValues + 2) / 3;
            bundleSize = 3;
            multiplier = 5;
        }

        int[] tqValues = new int[8];
        for (int i = 0; i < totalTq; i++) {
            int bits = endpointTrits != 0 ? 8 : 7;
            if (i == totalTq - 1) {
                int remaining = totalValues - (totalTq - 1) * bundleSize;
                if (endpointTrits != 0) {
                    bits = endpointTritBitCount(bits, remaining);
                } else if (endpointQuints != 0) {
                    bits = remaining == 1 ? 3 : remaining == 2 ? 5 : bits;
                }
            }
            tqValues[i] = readBits(data, offset, bitOffset, bits);
        }

        int accum = 0;
        int accumRemaining = 0;
        int nextTq = 0;
        for (int i = 0; i < totalValues; i++) {
            int value = readBits(data, offset, bitOffset, endpointBits);
            if (totalTq != 0) {
                if (accumRemaining == 0) {
                    accum = tqValues[nextTq++];
                    accumRemaining = bundleSize;
                }
                int tq = accum % multiplier;
                accum /= multiplier;
                accumRemaining--;
                value |= tq << endpointBits;
            }
            block.endpoints[i] = value;
        }

        int weightBits = MODE_WEIGHT_BITS[mode];
        int weightMask = (1 << weightBits) - 1;
        int anchorMask = (1 << (weightBits - 1)) - 1;
        if (mode == 18) {
            for (int i = 0; i < 16; i++) {
                block.weights[i] = readBits(data, offset, bitOffset, i == 0 ? weightBits - 1 : weightBits);
            }
        } else {
            long bits = readBits64(data, offset, bitOffset[0], Math.min(64, 128 - bitOffset[0]));
            int pos = 0;
            if (planes == 2) {
                block.weights[0] = (int) ((bits >>> pos) & anchorMask);
                pos += weightBits - 1;
                block.weights[1] = (int) ((bits >>> pos) & anchorMask);
                pos += weightBits - 1;
                for (int i = 2; i < 32; i++) {
                    block.weights[i] = (int) ((bits >>> pos) & weightMask);
                    pos += weightBits;
                }
            } else if (subsets == 1) {
                block.weights[0] = (int) (bits & anchorMask);
                pos = weightBits - 1;
                for (int i = 1; i < 16; i++) {
                    block.weights[i] = (int) ((bits >>> pos) & weightMask);
                    pos += weightBits;
                }
            } else {
                int[] anchors = anchorIndices(block.partitionSeed, subsets);
                for (int i = 0; i < 16; i++) {
                    boolean anchor = i == anchors[0] || i == anchors[1] || i == anchors[2];
                    block.weights[i] = (int) ((bits >>> pos) & (anchor ? anchorMask : weightMask));
                    pos += anchor ? weightBits - 1 : weightBits;
                }
            }
        }
        if (applyBlueContractionFix) {
            fixBlueContraction(block);
        }
        return block;
    }

    private static void fixBlueContraction(UnpackedBlock block) {
        int totalComps = MODE_COMPS[block.mode];
        if (totalComps < 3) {
            return;
        }
        int subsets = MODE_SUBSETS[block.mode];
        int endpointRange = MODE_ENDPOINT_RANGES[block.mode];
        boolean[] invert = new boolean[3];
        boolean any = false;
        for (int subset = 0; subset < subsets; subset++) {
            int base = subset * totalComps * 2;
            int low = dequantEndpoint(block.endpoints[base], endpointRange)
                    + dequantEndpoint(block.endpoints[base + 2], endpointRange)
                    + dequantEndpoint(block.endpoints[base + 4], endpointRange);
            int high = dequantEndpoint(block.endpoints[base + 1], endpointRange)
                    + dequantEndpoint(block.endpoints[base + 3], endpointRange)
                    + dequantEndpoint(block.endpoints[base + 5], endpointRange);
            if (high < low) {
                for (int component = 0; component < totalComps; component++) {
                    int endpoint = base + component * 2;
                    int temp = block.endpoints[endpoint];
                    block.endpoints[endpoint] = block.endpoints[endpoint + 1];
                    block.endpoints[endpoint + 1] = temp;
                }
                invert[subset] = true;
                any = true;
            }
        }
        if (!any) {
            return;
        }
        int[] partitionMap = partitionMap(block.partitionSeed, subsets);
        int planes = MODE_PLANES[block.mode];
        int weightMask = (1 << MODE_WEIGHT_BITS[block.mode]) - 1;
        for (int i = 0; i < 16; i++) {
            if (invert[partitionMap[i]]) {
                block.weights[i * planes] = weightMask - block.weights[i * planes];
                if (planes == 2) {
                    block.weights[i * planes + 1] = weightMask - block.weights[i * planes + 1];
                }
            }
        }
    }

    private static byte[] packAstcBlock(UnpackedBlock block) {
        byte[] astc = new byte[BLOCK_BYTES];
        int astcMode = MODE_ASTC_BLOCK_MODE[block.mode];
        astc[0] = (byte) astcMode;
        astc[1] = (byte) (astcMode >>> 8);
        int[] bitPosition = {11};
        int subsets = MODE_SUBSETS[block.mode];
        XuastcAstcBlockPacker.setBits(astc, bitPosition, subsets - 1, 2);
        if (subsets == 1) {
            XuastcAstcBlockPacker.setBits(astc, bitPosition, MODE_CEM[block.mode], 4);
        } else {
            XuastcAstcBlockPacker.setBits(astc, bitPosition, block.partitionSeed, 10);
            XuastcAstcBlockPacker.setBits(astc, bitPosition, (MODE_CEM[block.mode] << 2) & 63, 6);
        }
        int weightBits = MODE_WEIGHT_BITS[block.mode];
        int totalWeights = MODE_PLANES[block.mode] == 2 ? 32 : 16;
        if (MODE_PLANES[block.mode] == 2) {
            int[] ccsPosition = {128 - totalWeights * weightBits - 2};
            XuastcAstcBlockPacker.setBits(astc, ccsPosition, block.colorComponentSelector, 2);
        }
        int endpointValueCount = (1 + (MODE_CEM[block.mode] >>> 2)) * subsets * 2;
        encodeBise(
                astc,
                block.endpoints,
                bitPosition[0],
                endpointValueCount,
                MODE_ENDPOINT_RANGES[block.mode]);
        packWeights(astc, block.weights, totalWeights, weightBits);
        return astc;
    }

    private static void packWeights(byte[] astc, int[] weights, int totalWeights, int bitsPerWeight) {
        for (int i = 0; i < totalWeights; i++) {
            int offset = 128 - bitsPerWeight - i * bitsPerWeight;
            int value = reverseBits(weights[i], bitsPerWeight) << (offset & 7);
            int index = offset >>> 3;
            astc[index] |= (byte) value;
            if (bitsPerWeight >= 3 && index + 1 < BLOCK_BYTES) {
                astc[index + 1] |= (byte) (value >>> 8);
            }
        }
    }

    private static void encodeBise(byte[] dst, int[] values, int bitOffset, int valueCount, int range) {
        if (XuastcAstcConstants.getIseQuintCount(range) == 0) {
            XuastcAstcBlockPacker.encodeBise(dst, values, bitOffset, valueCount, range);
            return;
        }
        byte[] temp = new byte[20];
        int[] position = {bitOffset};
        int bits = XuastcAstcConstants.getIseBitCount(range);
        int groups = (valueCount + 2) / 3;
        int levels = XuastcAstcConstants.getIseLevels(range);
        for (int group = 0; group < groups; group++) {
            int[] vals = new int[3];
            int limit = Math.min(3, valueCount - group * 3);
            for (int i = 0; i < limit; i++) {
                int value = values[group * 3 + i];
                if (value < 0 || value >= levels) {
                    throw new BasisDecodeException("UASTC ASTC BISE value is out of range");
                }
                vals[i] = value;
            }
            encodeQuints(temp, vals, position, bits);
        }
        for (int i = 0; i < BLOCK_BYTES; i++) {
            dst[i] |= temp[i];
        }
    }

    private static void encodeQuints(byte[] dst, int[] values, int[] bitPosition, int bits) {
        int bitMask = (1 << bits) - 1;
        int quints = 0;
        int multiplier = 1;
        int packed = 0;
        int packedBit = 0;
        for (int i = 0; i < 3; i++) {
            int value = values[i];
            quints += (value >>> bits) * multiplier;
            multiplier *= 5;
            packed |= (value & bitMask) << packedBit;
            packedBit += bits;
        }
        int t = QUINT_ENCODE[quints];
        int encoded = (packed & bitMask)
                | (((t >>> 0) & 0x7) << bits)
                | (((packed >>> bits) & bitMask) << (bits + 3))
                | (((t >>> 3) & 0x3) << (bits * 2 + 3))
                | (((packed >>> (bits * 2)) & bitMask) << (bits * 2 + 5))
                | (((t >>> 5) & 0x3) << (bits * 3 + 5));
        XuastcAstcBlockPacker.setBits(dst, bitPosition, encoded, 7 + bits * 3);
    }

    private static int endpointTritBitCount(int defaultBits, int remainingValues) {
        switch (remainingValues) {
            case 1:
                return 2;
            case 2:
                return 4;
            case 3:
                return 5;
            case 4:
                return 7;
            default:
                return defaultBits;
        }
    }

    private static int reverseBits(int value, int bits) {
        switch (bits) {
            case 1:
                return value;
            case 2:
                return REVERSE_BITS_2[value];
            case 3:
                return REVERSE_BITS_3[value];
            case 4:
                return REVERSE_BITS_4[value];
            case 5:
                return REVERSE_BITS_5[value];
            default:
                throw new BasisDecodeException("Unsupported UASTC ASTC weight width");
        }
    }

    private static int partitionSeed(int mode, int commonPattern) {
        if (mode == 3) {
            return readTable(COMMON_PARTITION_3_ASTC, commonPattern, "UASTC 3-subset partition");
        }
        if (mode == 7) {
            return readTable(BC7_3_ASTC_2_ASTC, commonPattern, "UASTC BC7/ASTC partition");
        }
        if (mode == 2 || mode == 4 || mode == 9 || mode == 16) {
            return readTable(COMMON_PARTITION_2_ASTC, commonPattern, "UASTC 2-subset partition");
        }
        return 0;
    }

    private static int[] anchorIndices(int seed, int subsets) {
        int[] map = partitionMap(seed, subsets);
        int[] anchors = {0, 0, 0};
        boolean[] seen = new boolean[3];
        for (int i = 0; i < map.length; i++) {
            int subset = map[i];
            if (subset >= 0 && subset < subsets && !seen[subset]) {
                anchors[subset] = i;
                seen[subset] = true;
            }
        }
        return anchors;
    }

    private static int[] partitionMap(int seed, int subsets) {
        if (subsets == 1) {
            return new int[16];
        }
        return XuastcAstcPartitioner.computePartitionMap(4, 4, seed, subsets);
    }

    private static int packedBc1HintSelectors(UnpackedBlock block) {
        return packedBc1HintSelectors(block, false);
    }

    private static int packedBc1HintSelectors(UnpackedBlock block, boolean invert) {
        int weightBits = MODE_WEIGHT_BITS[block.mode];
        int[] translation = UASTC_TO_BC1_WEIGHTS[weightBits];
        int planeShift = MODE_PLANES[block.mode] - 1;
        int selectors = 0;
        for (int i = 15; i >= 0; i--) {
            int selector = translation[block.weights[i << planeShift]];
            if (invert) {
                selector ^= 1;
            }
            selectors = (selectors << 2) | selector;
        }
        return selectors;
    }

    private static void readHints(byte[] data, int offset, int[] bitOffset, UnpackedBlock block) {
        int mode = block.mode;
        final int start = bitOffset[0];
        if (MODE_HAS_BC1_HINT0[mode]) {
            block.bc1Hint0 = readBits(data, offset, bitOffset, 1) != 0;
        }
        if (MODE_HAS_BC1_HINT1[mode]) {
            block.bc1Hint1 = readBits(data, offset, bitOffset, 1) != 0;
        }
        block.etc1Flip = readBits(data, offset, bitOffset, 1) != 0;
        block.etc1Diff = readBits(data, offset, bitOffset, 1) != 0;
        block.etc1Intensity0 = readBits(data, offset, bitOffset, 3);
        block.etc1Intensity1 = readBits(data, offset, bitOffset, 3);
        if (MODE_HAS_ETC1_BIAS[mode]) {
            block.etc1Bias = readBits(data, offset, bitOffset, 5);
        } else {
            block.etc1Bias = 0;
        }
        block.etc2Hints = MODE_HAS_ALPHA[mode] ? readBits(data, offset, bitOffset, 8) : 0;
        int consumed = bitOffset[0] - start;
        if (consumed != MODE_HINT_BITS[mode]) {
            throw new BasisDecodeException("Invalid UASTC hint layout");
        }
    }

    private static int dequantEndpoint(int endpoint, int range) {
        return XuastcAstcConstants.dequantBiseEndpoint(endpoint, range);
    }

    private static int readTable(int[] table, int index, String name) {
        if (index < 0 || index >= table.length) {
            throw new BasisDecodeException("Invalid " + name + " index");
        }
        return table[index];
    }

    private static int readBits(byte[] data, int offset, int[] bitOffset, int bits) {
        int value = 0;
        for (int i = 0; i < bits; i++) {
            int absolute = offset * 8 + bitOffset[0]++;
            value |= ((Byte.toUnsignedInt(data[absolute >>> 3]) >>> (absolute & 7)) & 1) << i;
        }
        return value;
    }

    private static long readBits64(byte[] data, int offset, int bitOffset, int bits) {
        long value = 0L;
        for (int i = 0; i < bits; i++) {
            int absolute = offset * 8 + bitOffset + i;
            value |= (long) ((Byte.toUnsignedInt(data[absolute >>> 3]) >>> (absolute & 7)) & 1) << i;
        }
        return value;
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static final class UnpackedBlock {
        private final int mode;
        private final int[] endpoints = new int[18];
        private final int[] weights = new int[32];
        private int commonPattern;
        private int partitionSeed;
        private int colorComponentSelector;
        private int etc2Hints;
        private int etc1Intensity0;
        private int etc1Intensity1;
        private int etc1Selector;
        private int etc1Red;
        private int etc1Green;
        private int etc1Blue;
        private int etc1Bias;
        private boolean etc1Flip;
        private boolean etc1Diff;
        private boolean bc1Hint0;
        private boolean bc1Hint1;
        private int[] solidRgba;

        private UnpackedBlock(int mode) {
            this.mode = mode;
        }
    }
}
