package org.ngengine.basis;

/**
 * Transcodes UASTC HDR 4x4 ASTC blocks to unsigned BC6H blocks using the
 * same constrained path as Basis Universal.
 */
final class Bc6hUastcHdr4x4Transcoder {
    private static final int ASTC_BLOCK_BYTES = 16;
    static final int BC6H_FIRST_ONE_SUBSET_MODE = 10;
    private static final int[] MODE_BITS = {0, 1, 2, 6, 10, 14, 18, 22, 26, 30, 3, 7, 11, 15};
    private static final int[] MODE_BIT_LENGTHS = {2, 2, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5};
    static final int[] MODE_ORDER = {2, 3, 4, 0, 5, 6, 7, 8, 1};

    static final int[][] MODE_SIG_BITS = {
            {10, 5, 5, 5},
            {7, 6, 6, 6},
            {11, 5, 4, 4},
            {11, 4, 5, 4},
            {11, 4, 4, 5},
            {9, 5, 5, 5},
            {8, 6, 5, 5},
            {8, 5, 6, 5},
            {8, 5, 5, 6},
            {6, 6, 6, 6},
            {10, 10, 10, 10},
            {11, 9, 9, 9},
            {12, 8, 8, 8},
            {16, 4, 4, 4},
    };

    private static final int[][][] BIT_LAYOUTS = {
            {
                    {1, 2, 4, -1},
                    {2, 2, 4, -1},
                    {2, 3, 4, -1},
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 4, 0},
                    {1, 3, 4, -1},
                    {1, 2, 3, 0},
                    {1, 1, 4, 0},
                    {2, 3, 0, -1},
                    {1, 3, 3, 0},
                    {2, 1, 4, 0},
                    {2, 3, 1, -1},
                    {2, 2, 3, 0},
                    {0, 2, 4, 0},
                    {2, 3, 2, -1},
                    {0, 3, 4, 0},
                    {2, 3, 3, -1},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {1, 2, 5, -1},
                    {1, 3, 4, -1},
                    {1, 3, 5, -1},
                    {0, 0, 6, 0},
                    {2, 3, 0, -1},
                    {2, 3, 1, -1},
                    {2, 2, 4, -1},
                    {1, 0, 6, 0},
                    {2, 2, 5, -1},
                    {2, 3, 2, -1},
                    {1, 2, 4, -1},
                    {2, 0, 6, 0},
                    {2, 3, 3, -1},
                    {2, 3, 5, -1},
                    {2, 3, 4, -1},
                    {0, 1, 5, 0},
                    {1, 2, 3, 0},
                    {1, 1, 5, 0},
                    {1, 3, 3, 0},
                    {2, 1, 5, 0},
                    {2, 2, 3, 0},
                    {0, 2, 5, 0},
                    {0, 3, 5, 0},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 4, 0},
                    {0, 0, 10, -1},
                    {1, 2, 3, 0},
                    {1, 1, 3, 0},
                    {1, 0, 10, -1},
                    {2, 3, 0, -1},
                    {1, 3, 3, 0},
                    {2, 1, 3, 0},
                    {2, 0, 10, -1},
                    {2, 3, 1, -1},
                    {2, 2, 3, 0},
                    {0, 2, 4, 0},
                    {2, 3, 2, -1},
                    {0, 3, 4, 0},
                    {2, 3, 3, -1},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 3, 0},
                    {0, 0, 10, -1},
                    {1, 3, 4, -1},
                    {1, 2, 3, 0},
                    {1, 1, 4, 0},
                    {1, 0, 10, -1},
                    {1, 3, 3, 0},
                    {2, 1, 3, 0},
                    {2, 0, 10, -1},
                    {2, 3, 1, -1},
                    {2, 2, 3, 0},
                    {0, 2, 3, 0},
                    {2, 3, 0, -1},
                    {2, 3, 2, -1},
                    {0, 3, 3, 0},
                    {1, 2, 4, -1},
                    {2, 3, 3, -1},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 3, 0},
                    {0, 0, 10, -1},
                    {2, 2, 4, -1},
                    {1, 2, 3, 0},
                    {1, 1, 3, 0},
                    {1, 0, 10, -1},
                    {2, 3, 0, -1},
                    {1, 3, 3, 0},
                    {2, 1, 4, 0},
                    {2, 0, 10, -1},
                    {2, 2, 3, 0},
                    {0, 2, 3, 0},
                    {2, 3, 1, -1},
                    {2, 3, 2, -1},
                    {0, 3, 3, 0},
                    {2, 3, 4, -1},
                    {2, 3, 3, -1},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 8, 0},
                    {2, 2, 4, -1},
                    {1, 0, 8, 0},
                    {1, 2, 4, -1},
                    {2, 0, 8, 0},
                    {2, 3, 4, -1},
                    {0, 1, 4, 0},
                    {1, 3, 4, -1},
                    {1, 2, 3, 0},
                    {1, 1, 4, 0},
                    {2, 3, 0, -1},
                    {1, 3, 3, 0},
                    {2, 1, 4, 0},
                    {2, 3, 1, -1},
                    {2, 2, 3, 0},
                    {0, 2, 4, 0},
                    {2, 3, 2, -1},
                    {0, 3, 4, 0},
                    {2, 3, 3, -1},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 7, 0},
                    {1, 3, 4, -1},
                    {2, 2, 4, -1},
                    {1, 0, 7, 0},
                    {2, 3, 2, -1},
                    {1, 2, 4, -1},
                    {2, 0, 7, 0},
                    {2, 3, 3, -1},
                    {2, 3, 4, -1},
                    {0, 1, 5, 0},
                    {1, 2, 3, 0},
                    {1, 1, 4, 0},
                    {2, 3, 0, -1},
                    {1, 3, 3, 0},
                    {2, 1, 4, 0},
                    {2, 3, 1, -1},
                    {2, 2, 3, 0},
                    {0, 2, 5, 0},
                    {0, 3, 5, 0},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 7, 0},
                    {2, 3, 0, -1},
                    {2, 2, 4, -1},
                    {1, 0, 7, 0},
                    {1, 2, 5, -1},
                    {1, 2, 4, -1},
                    {2, 0, 7, 0},
                    {1, 3, 5, -1},
                    {2, 3, 4, -1},
                    {0, 1, 4, 0},
                    {1, 3, 4, -1},
                    {1, 2, 3, 0},
                    {1, 1, 5, 0},
                    {1, 3, 3, 0},
                    {2, 1, 4, 0},
                    {2, 3, 1, -1},
                    {2, 2, 3, 0},
                    {0, 2, 4, 0},
                    {2, 3, 2, -1},
                    {0, 3, 4, 0},
                    {2, 3, 3, -1},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 7, 0},
                    {2, 3, 1, -1},
                    {2, 2, 4, -1},
                    {1, 0, 7, 0},
                    {2, 2, 5, -1},
                    {1, 2, 4, -1},
                    {2, 0, 7, 0},
                    {2, 3, 5, -1},
                    {2, 3, 4, -1},
                    {0, 1, 4, 0},
                    {1, 3, 4, -1},
                    {1, 2, 3, 0},
                    {1, 1, 4, 0},
                    {2, 3, 0, -1},
                    {1, 3, 3, 0},
                    {2, 1, 5, 0},
                    {2, 2, 3, 0},
                    {0, 2, 4, 0},
                    {2, 3, 2, -1},
                    {0, 3, 4, 0},
                    {2, 3, 3, -1},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 5, 0},
                    {1, 3, 4, -1},
                    {2, 3, 0, -1},
                    {2, 3, 1, -1},
                    {2, 2, 4, -1},
                    {1, 0, 5, 0},
                    {1, 2, 5, -1},
                    {2, 2, 5, -1},
                    {2, 3, 2, -1},
                    {1, 2, 4, -1},
                    {2, 0, 5, 0},
                    {1, 3, 5, -1},
                    {2, 3, 3, -1},
                    {2, 3, 5, -1},
                    {2, 3, 4, -1},
                    {0, 1, 5, 0},
                    {1, 2, 3, 0},
                    {1, 1, 5, 0},
                    {1, 3, 3, 0},
                    {2, 1, 5, 0},
                    {2, 2, 3, 0},
                    {0, 2, 5, 0},
                    {0, 3, 5, 0},
                    {3, -1, 4, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 9, 0},
                    {1, 1, 9, 0},
                    {2, 1, 9, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 8, 0},
                    {0, 0, 10, -1},
                    {1, 1, 8, 0},
                    {1, 0, 10, -1},
                    {2, 1, 8, 0},
                    {2, 0, 10, -1},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 7, 0},
                    {0, 0, 10, 11},
                    {1, 1, 7, 0},
                    {1, 0, 10, 11},
                    {2, 1, 7, 0},
                    {2, 0, 10, 11},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
            {
                    {0, 0, 9, 0},
                    {1, 0, 9, 0},
                    {2, 0, 9, 0},
                    {0, 1, 3, 0},
                    {0, 0, 10, 15},
                    {1, 1, 3, 0},
                    {1, 0, 10, 15},
                    {2, 1, 3, 0},
                    {2, 0, 10, 15},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
                    {-1, 0, 0, 0},
            },
    };

    private static final int[][] BC6H_2SUBSET_PATTERNS = {
            {128, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 129},
            {128, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 129},
            {128, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 129},
            {128, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 129},
            {128, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 129},
            {128, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 129},
            {128, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 129},
            {128, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 129},
            {128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 129},
            {128, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 129},
            {128, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 129},
            {128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 129},
            {128, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 129},
            {128, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 129},
            {128, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 129},
            {128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 129},
            {128, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 1, 1, 1, 129},
            {128, 1, 129, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0},
            {128, 0, 0, 0, 0, 0, 0, 0, 129, 0, 0, 0, 1, 1, 1, 0},
            {128, 1, 129, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0},
            {128, 0, 129, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0},
            {128, 0, 0, 0, 1, 0, 0, 0, 129, 1, 0, 0, 1, 1, 1, 0},
            {128, 0, 0, 0, 0, 0, 0, 0, 129, 0, 0, 0, 1, 1, 0, 0},
            {128, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 129},
            {128, 0, 129, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0},
            {128, 0, 0, 0, 1, 0, 0, 0, 129, 0, 0, 0, 1, 1, 0, 0},
            {128, 1, 129, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0},
            {128, 0, 129, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 0, 0},
            {128, 0, 0, 1, 0, 1, 1, 1, 129, 1, 1, 0, 1, 0, 0, 0},
            {128, 0, 0, 0, 1, 1, 1, 1, 129, 1, 1, 1, 0, 0, 0, 0},
            {128, 1, 129, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 0},
            {128, 0, 129, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0},
    };

    private static final int[][] COMMON_PARTITIONS2 = {
            {0, 28, 0},
            {1, 20, 0},
            {2, 16, 1},
            {3, 29, 0},
            {4, 91, 1},
            {5, 9, 0},
            {6, 107, 1},
            {7, 72, 1},
            {8, 149, 0},
            {9, 204, 1},
            {10, 50, 0},
            {11, 114, 1},
            {12, 496, 1},
            {13, 17, 1},
            {14, 78, 0},
            {15, 39, 1},
            {17, 252, 1},
            {18, 828, 1},
            {19, 43, 0},
            {20, 156, 0},
            {21, 116, 0},
            {22, 210, 1},
            {23, 476, 1},
            {24, 273, 0},
            {25, 684, 1},
            {26, 359, 0},
            {29, 246, 1},
            {32, 195, 1},
            {33, 694, 1},
            {52, 524, 1},
    };

    private Bc6hUastcHdr4x4Transcoder() {
    }

    static byte[] transcode(byte[] astc, int width, int height) {
        int blocksX = AstcHdrBlockDecoder.divideRoundUp(width, 4);
        int blocksY = AstcHdrBlockDecoder.divideRoundUp(height, 4);
        int blockCount = Math.multiplyExact(blocksX, blocksY);
        if (astc.length != Math.multiplyExact(blockCount, ASTC_BLOCK_BYTES)) {
            throw new BasisDecodeException("ASTC HDR payload size mismatch");
        }
        byte[] output = new byte[Math.multiplyExact(blockCount, ASTC_BLOCK_BYTES)];
        for (int block = 0; block < blockCount; block++) {
            AstcHdrBlockDecoder.LogicalBlock logical = AstcHdrBlockDecoder.unpackLogicalBlock(
                    astc,
                    block * ASTC_BLOCK_BYTES,
                    4,
                    4);
            if (!transcodeBlock(logical, output, block * ASTC_BLOCK_BYTES)) {
                throw new BasisDecodeException("UASTC HDR block cannot be transcoded to BC6H");
            }
        }
        return output;
    }

    private static boolean transcodeBlock(AstcHdrBlockDecoder.LogicalBlock block, byte[] output, int offset) {
        if (block.error || block.solidLdr) {
            return false;
        }
        if (block.solidHdr) {
            return encodeSolidColor(block.solidColor, output, offset);
        }
        if (block.gridWidth != 4 || block.gridHeight != 4 || block.dualPlane) {
            return false;
        }
        if (block.numPartitions == 1) {
            return transcodeOneSubset(block, output, offset);
        }
        if (block.numPartitions == 2) {
            int commonIndex = commonPartitionIndex(block.partitionId);
            return commonIndex >= 0 && transcodeTwoSubsets(commonIndex, block, output, offset);
        }
        return false;
    }

    private static boolean transcodeOneSubset(AstcHdrBlockDecoder.LogicalBlock block, byte[] output, int offset) {
        if (block.weightIseRange < 1 || block.weightIseRange > 8) {
            return false;
        }
        int cem = block.colorEndpointModes[0];
        if (cem == 7) {
            if (block.endpointIseRange != 20) {
                return false;
            }
        } else if (cem == 11) {
            if ((block.weightIseRange <= 7 && block.endpointIseRange != 20)
                    || (block.weightIseRange > 7 && block.endpointIseRange != 19)) {
                return false;
            }
        } else {
            return false;
        }
        int[][] qlogEndpoints = new int[4][2];
        if (!AstcHdrBlockDecoder.decodeQlogEndpoint(cem, block.endpointIseRange, block.endpoints, 0, qlogEndpoints)) {
            return false;
        }
        int[][] halfEndpoints = qlogToHalfEndpoints(qlogEndpoints);
        if (halfEndpoints == null) {
            return false;
        }
        if (block.weightIseRange == 5) {
            encodeOneSubset3Bit(halfEndpoints, copyWeights(block.weights), output, offset);
            return true;
        }
        int[] bc6hWeights = remapOneSubsetWeights(block);
        if (bc6hWeights == null) {
            return false;
        }
        encodeOneSubset4Bit(halfEndpoints, bc6hWeights, output, offset);
        return true;
    }

    private static boolean transcodeTwoSubsets(
            int commonIndex,
            AstcHdrBlockDecoder.LogicalBlock block,
            byte[] output,
            int offset) {
        if (block.colorEndpointModes[0] != block.colorEndpointModes[1]) {
            return false;
        }
        int cem = block.colorEndpointModes[0];
        if (cem != 7 && cem != 11) {
            return false;
        }
        if (cem == 7) {
            if (!((block.weightIseRange == 1 && block.endpointIseRange == 20)
                    || (block.weightIseRange == 2 && block.endpointIseRange == 20)
                    || (block.weightIseRange == 3 && block.endpointIseRange == 19)
                    || (block.weightIseRange == 4 && block.endpointIseRange == 17)
                    || (block.weightIseRange == 5 && block.endpointIseRange == 15))) {
                return false;
            }
        } else if (!((block.weightIseRange == 1 && block.endpointIseRange == 14)
                || (block.weightIseRange == 2 && block.endpointIseRange == 12))) {
            return false;
        }
        int[][][] halfEndpoints = new int[2][3][2];
        int endpointOffset = 0;
        for (int subset = 0; subset < 2; subset++) {
            int[][] qlogEndpoints = new int[4][2];
            if (!AstcHdrBlockDecoder.decodeQlogEndpoint(
                    cem,
                    block.endpointIseRange,
                    block.endpoints,
                    endpointOffset,
                    qlogEndpoints)) {
                return false;
            }
            int[][] subsetHalf = qlogToHalfEndpoints(qlogEndpoints);
            if (subsetHalf == null) {
                return false;
            }
            for (int c = 0; c < 3; c++) {
                halfEndpoints[subset][c][0] = subsetHalf[c][0];
                halfEndpoints[subset][c][1] = subsetHalf[c][1];
            }
            endpointOffset += XuastcAstcConstants.numCemEndpointValues(cem);
        }
        int[] weights = remapTwoSubsetWeights(block);
        if (weights == null) {
            return false;
        }
        encodeTwoSubset3Bit(commonIndex, halfEndpoints, weights, output, offset);
        return true;
    }

    private static int[][] qlogToHalfEndpoints(int[][] qlogEndpoints) {
        int[][] halfEndpoints = new int[3][2];
        for (int c = 0; c < 3; c++) {
            halfEndpoints[c][0] = AstcHdrBlockDecoder.qlogToHalf(qlogEndpoints[c][0], 12);
            halfEndpoints[c][1] = AstcHdrBlockDecoder.qlogToHalf(qlogEndpoints[c][1], 12);
            if (AstcHdrBlockDecoder.isInvalidHalf(halfEndpoints[c][0])
                    || AstcHdrBlockDecoder.isInvalidHalf(halfEndpoints[c][1])) {
                return null;
            }
        }
        return halfEndpoints;
    }

    private static int[] remapOneSubsetWeights(AstcHdrBlockDecoder.LogicalBlock block) {
        int[] weights = new int[16];
        int[] table;
        switch (block.weightIseRange) {
            case 1:
                table = new int[] {0, 8, 15};
                break;
            case 2:
                table = new int[] {0, 5, 10, 15};
                break;
            case 3:
                table = new int[] {0, 4, 7, 11, 15};
                break;
            case 4:
                table = new int[] {0, 15, 3, 12, 6, 9};
                break;
            case 6:
                table = new int[] {0, 15, 2, 13, 3, 12, 5, 10, 6, 9};
                break;
            case 7:
                table = new int[] {0, 15, 4, 11, 1, 14, 5, 10, 2, 13, 6, 9};
                break;
            case 8:
                return copyWeights(block.weights);
            default:
                return null;
        }
        for (int i = 0; i < 16; i++) {
            weights[i] = table[block.weights[i]];
        }
        return weights;
    }

    private static int[] remapTwoSubsetWeights(AstcHdrBlockDecoder.LogicalBlock block) {
        int[] table;
        switch (block.weightIseRange) {
            case 1:
                table = new int[] {0, 4, 7};
                break;
            case 2:
                table = new int[] {0, 2, 5, 7};
                break;
            case 3:
                table = new int[] {0, 2, 4, 5, 7};
                break;
            case 4:
                table = new int[] {0, 7, 1, 6, 3, 4};
                break;
            case 5:
                return copyWeights(block.weights);
            default:
                return null;
        }
        int[] weights = new int[16];
        for (int i = 0; i < 16; i++) {
            weights[i] = table[block.weights[i]];
        }
        return weights;
    }

    private static boolean encodeSolidColor(int[] color, byte[] output, int offset) {
        if (((color[0] | color[1] | color[2]) & 0x8000) != 0) {
            return false;
        }
        int[][] endpoints = new int[3][2];
        for (int c = 0; c < 3; c++) {
            endpoints[c][0] = color[c];
            endpoints[c][1] = color[c];
        }
        encodeOneSubset4Bit(endpoints, new int[16], output, offset);
        return true;
    }

    private static void encodeOneSubset4Bit(int[][] endpoints, int[] weights, byte[] output, int offset) {
        LogicalBc6hBlock block = new LogicalBc6hBlock();
        for (int mode = 13; mode > BC6H_FIRST_ONE_SUBSET_MODE; mode--) {
            int baseBits = MODE_SIG_BITS[mode][0];
            int deltaBits = MODE_SIG_BITS[mode][1];
            int[][] blogEndpoints = new int[3][2];
            for (int c = 0; c < 3; c++) {
                blogEndpoints[c][0] = halfToBlog(endpoints[c][0], baseBits);
                blogEndpoints[c][1] = halfToBlog(endpoints[c][1], baseBits);
            }
            System.arraycopy(weights, 0, block.weights, 0, 16);
            if ((block.weights[0] & 8) != 0) {
                invertWeights(block.weights, 15, null, -1);
                for (int c = 0; c < 3; c++) {
                    swap(blogEndpoints[c], 0, 1);
                }
            }
            int maxDelta = (1 << (deltaBits - 1)) - 1;
            int minDelta = -(maxDelta + 1);
            int deltaMask = (1 << deltaBits) - 1;
            boolean failed = false;
            for (int c = 0; c < 3; c++) {
                block.endpoints[c][0] = blogEndpoints[c][0];
                int delta = blogEndpoints[c][1] - blogEndpoints[c][0];
                if (delta < minDelta || delta > maxDelta) {
                    failed = true;
                    break;
                }
                block.endpoints[c][1] = delta & deltaMask;
            }
            if (!failed) {
                block.mode = mode;
                packBlock(block, output, offset);
                return;
            }
        }
        encodeMode10(endpoints, weights, output, offset);
    }

    private static void encodeMode10(int[][] endpoints, int[] weights, byte[] output, int offset) {
        LogicalBc6hBlock block = new LogicalBc6hBlock();
        for (int c = 0; c < 3; c++) {
            block.endpoints[c][0] = halfToBlog(endpoints[c][0], 10);
            block.endpoints[c][1] = halfToBlog(endpoints[c][1], 10);
        }
        System.arraycopy(weights, 0, block.weights, 0, 16);
        if ((block.weights[0] & 8) != 0) {
            invertWeights(block.weights, 15, null, -1);
            for (int c = 0; c < 3; c++) {
                swap(block.endpoints[c], 0, 1);
            }
        }
        block.mode = BC6H_FIRST_ONE_SUBSET_MODE;
        packBlock(block, output, offset);
    }

    private static void encodeOneSubset3Bit(int[][] endpoints, int[] weights, byte[] output, int offset) {
        int[][][] expanded = new int[2][3][2];
        for (int c = 0; c < 3; c++) {
            expanded[0][c][0] = endpoints[c][0];
            expanded[0][c][1] = endpoints[c][1];
            expanded[1][c][0] = endpoints[c][0];
            expanded[1][c][1] = endpoints[c][1];
        }
        encodeTwoSubset3BitWithCommon(0, false, expanded, weights, output, offset);
    }

    private static void encodeTwoSubset3Bit(
            int commonIndex,
            int[][][] endpoints,
            int[] weights,
            byte[] output,
            int offset) {
        encodeTwoSubset3BitWithCommon(
                COMMON_PARTITIONS2[commonIndex][0],
                COMMON_PARTITIONS2[commonIndex][2] != 0,
                endpoints,
                weights,
                output,
                offset);
    }

    private static void encodeTwoSubset3BitWithCommon(
            int bc7Pattern,
            boolean invertPartition,
            int[][][] endpoints,
            int[] weights,
            byte[] output,
            int offset) {
        LogicalBc6hBlock block = new LogicalBc6hBlock();
        for (int modeIter = 0; modeIter <= 8; modeIter++) {
            int mode = MODE_ORDER[modeIter];
            int baseBits = MODE_SIG_BITS[mode][0];
            int[] deltaBits = {MODE_SIG_BITS[mode][1], MODE_SIG_BITS[mode][2], MODE_SIG_BITS[mode][3]};
            int[][] blogEndpoints = new int[3][4];
            for (int subset = 0; subset < 2; subset++) {
                for (int c = 0; c < 3; c++) {
                    blogEndpoints[c][subset * 2] = halfToBlog(endpoints[subset][c][0], baseBits);
                    blogEndpoints[c][subset * 2 + 1] = halfToBlog(endpoints[subset][c][1], baseBits);
                }
            }
            if (invertPartition) {
                for (int c = 0; c < 3; c++) {
                    swap(blogEndpoints[c], 0, 2);
                    swap(blogEndpoints[c], 1, 3);
                }
            }
            System.arraycopy(weights, 0, block.weights, 0, 16);
            applyTwoSubsetWeightSwaps(blogEndpoints, block.weights, bc7Pattern);
            boolean failed = false;
            for (int c = 0; c < 3; c++) {
                int maxDelta = (1 << (deltaBits[c] - 1)) - 1;
                int minDelta = -(maxDelta + 1);
                int deltaMask = (1 << deltaBits[c]) - 1;
                block.endpoints[c][0] = blogEndpoints[c][0];
                for (int i = 1; i < 4; i++) {
                    int delta = blogEndpoints[c][i] - blogEndpoints[c][0];
                    if (delta < minDelta || delta > maxDelta) {
                        failed = true;
                        break;
                    }
                    block.endpoints[c][i] = delta & deltaMask;
                }
                if (failed) {
                    break;
                }
            }
            if (!failed) {
                block.mode = mode;
                block.partitionPattern = bc7Pattern;
                packBlock(block, output, offset);
                return;
            }
        }
        encodeTwoSubsetMode9(bc7Pattern, invertPartition, endpoints, weights, output, offset);
    }

    private static void encodeTwoSubsetMode9(
            int bc7Pattern,
            boolean invertPartition,
            int[][][] endpoints,
            int[] weights,
            byte[] output,
            int offset) {
        LogicalBc6hBlock block = new LogicalBc6hBlock();
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 3; c++) {
                block.endpoints[c][subset * 2] = halfToBlog(endpoints[subset][c][0], 6);
                block.endpoints[c][subset * 2 + 1] = halfToBlog(endpoints[subset][c][1], 6);
            }
        }
        if (invertPartition) {
            for (int c = 0; c < 3; c++) {
                swap(block.endpoints[c], 0, 2);
                swap(block.endpoints[c], 1, 3);
            }
        }
        System.arraycopy(weights, 0, block.weights, 0, 16);
        applyTwoSubsetWeightSwaps(block.endpoints, block.weights, bc7Pattern);
        block.mode = 9;
        block.partitionPattern = bc7Pattern;
        packBlock(block, output, offset);
    }

    private static void applyTwoSubsetWeightSwaps(int[][] endpoints, int[] weights, int pattern) {
        boolean[] swapFlags = new boolean[2];
        int[] partition = BC6H_2SUBSET_PATTERNS[pattern];
        for (int i = 0; i < 16; i++) {
            if ((partition[i] & 0x80) != 0 && (weights[i] & 4) != 0) {
                swapFlags[partition[i] & 1] = true;
            }
        }
        if (swapFlags[0]) {
            for (int c = 0; c < 3; c++) {
                swap(endpoints[c], 0, 1);
            }
            invertWeights(weights, 7, partition, 0);
        }
        if (swapFlags[1]) {
            for (int c = 0; c < 3; c++) {
                swap(endpoints[c], 2, 3);
            }
            invertWeights(weights, 7, partition, 1);
        }
    }

    static void packBlock(LogicalBc6hBlock block, byte[] output, int offset) {
        long low = Integer.toUnsignedLong(MODE_BITS[block.mode]);
        long high = 0;
        int[] bitPos = {MODE_BIT_LENGTHS[block.mode]};
        int numSubsets = block.mode >= BC6H_FIRST_ONE_SUBSET_MODE ? 1 : 2;
        for (int[] layout : BIT_LAYOUTS[block.mode]) {
            if (layout[0] == -1) {
                break;
            }
            int value = layout[0] == 3 ? block.partitionPattern : block.endpoints[layout[0]][layout[1]];
            if (layout[3] == -1) {
                long[] packed = writeBits((value >>> layout[2]) & 1, 1, bitPos[0], low, high);
                low = packed[0];
                high = packed[1];
                bitPos[0]++;
            } else {
                int totalBits = Math.abs(layout[2] - layout[3]) + 1;
                int shifted = value >>> Math.min(layout[2], layout[3]);
                shifted &= (1 << totalBits) - 1;
                long[] packed = layout[3] > layout[2]
                        ? writeReversedBits(shifted, totalBits, bitPos, low, high)
                        : writeSequentialBits(shifted, totalBits, bitPos, low, high);
                low = packed[0];
                high = packed[1];
            }
        }
        int selectorBits = numSubsets == 1 ? 4 : 3;
        int[] partition = BC6H_2SUBSET_PATTERNS[block.partitionPattern];
        for (int i = 0; i < 16; i++) {
            int bits = selectorBits;
            if (numSubsets == 2) {
                bits -= partition[i] >>> 7;
            } else if (i == 0) {
                bits--;
            }
            long[] packed = writeSequentialBits(block.weights[i], bits, bitPos, low, high);
            low = packed[0];
            high = packed[1];
        }
        AstcHdrBlockDecoder.writeIntLE(output, offset, (int) low);
        AstcHdrBlockDecoder.writeIntLE(output, offset + 4, (int) (low >>> 32));
        AstcHdrBlockDecoder.writeIntLE(output, offset + 8, (int) high);
        AstcHdrBlockDecoder.writeIntLE(output, offset + 12, (int) (high >>> 32));
    }

    private static long[] writeSequentialBits(int value, int count, int[] bitPos, long low, long high) {
        long[] packed = writeBits(value, count, bitPos[0], low, high);
        bitPos[0] += count;
        return packed;
    }

    private static long[] writeReversedBits(int value, int count, int[] bitPos, long low, long high) {
        for (int i = 0; i < count; i++) {
            long[] packed = writeBits((value >>> (count - 1 - i)) & 1, 1, bitPos[0], low, high);
            low = packed[0];
            high = packed[1];
            bitPos[0]++;
        }
        return new long[] {low, high};
    }

    private static long[] writeBits(int value, int count, int bitPos, long low, long high) {
        long unsignedValue = Integer.toUnsignedLong(value);
        if (bitPos < 64) {
            low |= unsignedValue << bitPos;
            if (bitPos + count > 64) {
                high |= unsignedValue >>> (64 - bitPos);
            }
        } else {
            high |= unsignedValue << (bitPos - 64);
        }
        return new long[] {low, high};
    }

    static int halfToBlog(int half, int bits) {
        return (half * 64 + 30) / (31 * (1 << (16 - bits)));
    }

    private static int[] copyWeights(int[] source) {
        int[] weights = new int[16];
        System.arraycopy(source, 0, weights, 0, 16);
        return weights;
    }

    private static void invertWeights(int[] weights, int max, int[] partition, int subset) {
        for (int i = 0; i < 16; i++) {
            if (partition == null || (partition[i] & 0x7F) == subset) {
                weights[i] = max - weights[i];
            }
        }
    }

    private static int commonPartitionIndex(int astcPartitionId) {
        for (int i = 0; i < COMMON_PARTITIONS2.length; i++) {
            if (COMMON_PARTITIONS2[i][1] == astcPartitionId) {
                return i;
            }
        }
        return -1;
    }

    private static void swap(int[] array, int first, int second) {
        int temp = array[first];
        array[first] = array[second];
        array[second] = temp;
    }

    static final class LogicalBc6hBlock {
        final int[][] endpoints = new int[3][4];
        final int[] weights = new int[16];
        int mode;
        int partitionPattern;
    }
}
