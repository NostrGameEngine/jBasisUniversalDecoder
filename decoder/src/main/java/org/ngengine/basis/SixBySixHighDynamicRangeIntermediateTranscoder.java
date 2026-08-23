package org.ngengine.basis;

import java.util.Arrays;

/**
 * Transcodes Basis Universal UASTC HDR 6x6 intermediate slices to physical ASTC HDR 6x6 blocks.
 */
final class SixBySixHighDynamicRangeIntermediateTranscoder {
    private static final int BLOCK_WIDTH = 6;
    private static final int BLOCK_HEIGHT = 6;
    private static final int BLOCK_BYTES = 16;
    private static final int SIGNATURE_ORIGINAL = 0xABCD;
    private static final int SIGNATURE_CURRENT = 0xABCE;
    private static final int END_MARKER = 0xA742;
    private static final int MAX_DIMENSION = 32768;
    private static final int REUSE_MAX_BUFFER_ROWS = 5;
    private static final int REUSE_XY_DELTA_BITS = 5;
    private static final int TOTAL_BLOCK_MODES = 75;
    private static final int TOTAL_ENDPOINT_MODES = 5;
    private static final int NUM_ENDPOINT_DELTA_BITS = 5;
    private static final int NUM_UNIQUE_PARTITIONS_TWO = 521;
    private static final int NUM_UNIQUE_PARTITIONS_THREE = 333;
    private static final int LEVEL_ZERO = 1;
    private static final int LEVEL_ONE = 2;
    private static final int LEVEL_TWO = 4;
    private static final int MAX_ENDPOINTS = 18;
    private static final int MAX_GRID_WEIGHTS = 72;

    private static final BlockModeDescription[] BLOCK_MODE_DESCRIPTIONS = {
        new BlockModeDescription(false, 11, 1, 6, 6, 20, 1, 20, 1,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 6, 6, 15, 2, 15, 2,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 6, 5, 16, 3, 16, 3,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 5, 6, 16, 3, 16, 3,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 6, 4, 15, 5, 15, 5,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 4, 6, 15, 5, 15, 5,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 6, 3, 15, 8, 15, 8,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 3, 6, 15, 8, 15, 8,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 5, 5, 14, 5, 14, 5,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 4, 4, 19, 8, 19, 8,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 1, 3, 3, 20, 8, 20, 8,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 1, 6, 6, 16, 3, 16, 3,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 1, 6, 6, 20, 1, 20, 1, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 1, 6, 6, 20, 2, 20, 2, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 1, 5, 6, 20, 4, 20, 4, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 1, 6, 5, 20, 4, 20, 4, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 1, 3, 6, 20, 9, 20, 9, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 1, 6, 3, 20, 9, 20, 9, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 2, 6, 6, 11, 0, 11, 0, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 2, 6, 3, 13, 1, 13, 1, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 2, 3, 6, 13, 1, 13, 1, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 2, 3, 6, 11, 2, 11, 2, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 2, 6, 3, 11, 2, 11, 2, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 11, 2, 4, 6, 11, 1, 11, 1, 0, 0),
        new BlockModeDescription(false, 11, 2, 6, 4, 11, 1, 11, 1, 0, 0),
        new BlockModeDescription(false, 7, 2, 5, 6, 15, 1, 15, 1, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 2, 6, 5, 15, 1, 15, 1, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 2, 4, 6, 15, 2, 15, 2, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 2, 6, 4, 15, 2, 15, 2, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 2, 6, 6, 11, 1, 11, 1, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 2, 6, 6, 19, 0, 19, 0, 0, 0),
        new BlockModeDescription(false, 7, 2, 5, 5, 14, 2, 14, 2, 0, 0),
        new BlockModeDescription(false, 7, 2, 3, 6, 13, 5, 13, 5, 0, 0),
        new BlockModeDescription(false, 7, 2, 6, 3, 13, 5, 13, 5, 0, 0),
        new BlockModeDescription(false, 7, 2, 3, 6, 15, 4, 15, 4, 0, 0),
        new BlockModeDescription(false, 7, 2, 6, 3, 15, 4, 15, 4, 0, 0),
        new BlockModeDescription(true, 11, 1, 3, 6, 14, 2, 14, 2,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(true, 11, 1, 3, 6, 14, 2, 14, 2,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 1),
        new BlockModeDescription(true, 11, 1, 3, 6, 14, 2, 14, 2,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 2),
        new BlockModeDescription(true, 11, 1, 6, 3, 14, 2, 14, 2,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(true, 11, 1, 6, 3, 14, 2, 14, 2,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 1),
        new BlockModeDescription(true, 11, 1, 6, 3, 14, 2, 14, 2,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 2),
        new BlockModeDescription(true, 11, 1, 3, 3, 14, 8, 14, 8, LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(true, 11, 1, 3, 3, 14, 8, 14, 8, LEVEL_ONE | LEVEL_TWO, 1),
        new BlockModeDescription(true, 11, 1, 3, 3, 14, 8, 14, 8, LEVEL_ONE | LEVEL_TWO, 2),
        new BlockModeDescription(true, 11, 1, 4, 4, 13, 3, 13, 3, LEVEL_TWO, 0),
        new BlockModeDescription(true, 11, 1, 4, 4, 13, 3, 13, 3, LEVEL_TWO, 1),
        new BlockModeDescription(true, 11, 1, 4, 4, 13, 3, 13, 3, LEVEL_TWO, 2),
        new BlockModeDescription(true, 11, 1, 5, 5, 20, 0, 20, 0, LEVEL_TWO, 0),
        new BlockModeDescription(true, 11, 1, 5, 5, 20, 0, 20, 0, LEVEL_TWO, 1),
        new BlockModeDescription(true, 11, 1, 5, 5, 20, 0, 20, 0, LEVEL_TWO, 2),
        new BlockModeDescription(true, 11, 1, 2, 2, 14, 2, 20, 5,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(true, 11, 1, 2, 2, 14, 2, 20, 5,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 1),
        new BlockModeDescription(true, 11, 1, 2, 2, 14, 2, 20, 5,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 2),
        new BlockModeDescription(false, 11, 1, 2, 2, 17, 0, 20, 1,
                LEVEL_ZERO | LEVEL_ONE | LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 6, 6, 11, 0, 11, 0, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 5, 5, 14, 0, 14, 0, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 4, 4, 14, 1, 14, 1, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 4, 4, 12, 2, 12, 2, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 4, 4, 11, 3, 11, 3, 0, 0),
        new BlockModeDescription(false, 7, 3, 3, 3, 14, 5, 14, 5, 0, 0),
        new BlockModeDescription(false, 7, 3, 6, 4, 14, 0, 14, 0, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 4, 6, 14, 0, 14, 0, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 6, 4, 11, 1, 11, 1, 0, 0),
        new BlockModeDescription(false, 7, 3, 4, 6, 11, 1, 11, 1, 0, 0),
        new BlockModeDescription(false, 7, 3, 6, 5, 13, 0, 13, 0, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 5, 6, 13, 0, 13, 0, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 6, 3, 13, 1, 13, 1, 0, 0),
        new BlockModeDescription(false, 7, 3, 3, 6, 13, 1, 13, 1, 0, 0),
        new BlockModeDescription(false, 7, 3, 6, 3, 11, 2, 11, 2, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 3, 6, 11, 2, 11, 2, LEVEL_TWO, 0),
        new BlockModeDescription(false, 7, 3, 6, 3, 10, 3, 10, 3, 0, 0),
        new BlockModeDescription(false, 7, 3, 3, 6, 10, 3, 10, 3, 0, 0),
        new BlockModeDescription(false, 7, 3, 5, 4, 12, 1, 12, 1, 0, 0),
        new BlockModeDescription(false, 7, 3, 4, 5, 12, 1, 12, 1, 0, 0)
    };

    private static final int[][] REUSE_DELTAS = {
        {-1, 0}, {-2, 0}, {-3, 0}, {-4, 0},
        {3, -1}, {2, -1}, {1, -1}, {0, -1}, {-1, -1}, {-2, -1}, {-3, -1}, {-4, -1},
        {3, -2}, {2, -2}, {1, -2}, {0, -2}, {-1, -2}, {-2, -2}, {-3, -2}, {-4, -2},
        {3, -3}, {2, -3}, {1, -3}, {0, -3}, {-1, -3}, {-2, -3}, {-3, -3}, {-4, -3},
        {3, -4}, {2, -4}, {1, -4}, {0, -4}
    };

    private static final int[] PARTITION_TWO_UNIQUE_INDEX_TO_SEED = {
        86, 959, 936, 476, 1007, 672, 447, 423, 488, 422, 273, 65, 267, 786, 585,
        195, 108, 731, 878, 812, 264, 125, 868, 581, 258, 390, 549, 872, 661,
        352, 645, 543, 988, 906, 903, 616, 482, 529, 3, 286, 272, 303, 151, 504,
        498, 260, 79, 66, 608, 769, 305, 610, 1014, 967, 835, 789, 7, 951, 691,
        15, 763, 976, 438, 314, 601, 673, 177, 252, 615, 436, 220, 899, 623, 433,
        674, 278, 797, 107, 847, 114, 470, 760, 821, 490, 329, 945, 387, 471, 225,
        172, 83, 418, 966, 439, 316, 247, 43, 343, 625, 798, 1, 61, 73, 307, 136,
        474, 42, 664, 1013, 249, 389, 227, 374, 121, 48, 538, 226, 309, 554, 802,
        834, 335, 495, 10, 955, 461, 293, 508, 153, 101, 63, 139, 31, 687, 132,
        174, 324, 545, 289, 39, 178, 594, 963, 854, 222, 323, 998, 964, 598, 475,
        720, 1019, 983, 91, 703, 614, 394, 612, 281, 207, 930, 758, 586, 128, 517,
        426, 306, 168, 713, 36, 458, 876, 368, 780, 5, 9, 214, 109, 553, 726, 175,
        103, 753, 684, 44, 665, 53, 500, 367, 611, 119, 732, 639, 326, 203, 156,
        686, 910, 255, 62, 392, 591, 112, 88, 213, 19, 1022, 478, 90, 486, 799,
        702, 730, 414, 99, 1008, 142, 886, 373, 216, 69, 393, 299, 648, 415, 822,
        912, 110, 567, 550, 693, 2, 138, 59, 271, 562, 295, 714, 719, 199, 893,
        831, 1006, 662, 235, 262, 78, 51, 902, 298, 190, 169, 583, 347, 890, 958,
        909, 49, 987, 696, 633, 480, 50, 764, 826, 1023, 1016, 437, 891, 774, 257,
        724, 791, 526, 593, 690, 638, 858, 895, 794, 995, 130, 87, 877, 819, 318,
        649, 376, 211, 284, 937, 370, 688, 229, 994, 115, 842, 60, 521, 95, 694,
        804, 146, 754, 487, 55, 17, 770, 450, 223, 4, 137, 911, 236, 683, 523, 47,
        181, 24, 270, 602, 736, 11, 355, 148, 351, 762, 1009, 16, 210, 619, 805,
        874, 807, 887, 403, 999, 810, 27, 402, 551, 135, 778, 33, 409, 993, 71,
        363, 159, 183, 77, 596, 670, 380, 968, 811, 404, 348, 539, 158, 578, 196,
        621, 68, 530, 193, 100, 167, 919, 353, 366, 327, 643, 948, 518, 756, 801,
        558, 28, 705, 116, 94, 898, 453, 622, 647, 231, 445, 652, 230, 191, 277,
        292, 254, 198, 766, 386, 232, 29, 70, 942, 740, 291, 607, 411, 496, 839, 8,
        675, 319, 742, 21, 547, 627, 716, 663, 23, 914, 631, 595, 499, 685, 950,
        510, 54, 587, 432, 45, 646, 25, 122, 947, 171, 862, 441, 808, 722, 14, 74,
        658, 129, 266, 1001, 534, 395, 527, 250, 206, 237, 67, 897, 634, 572, 569,
        533, 37, 341, 89, 463, 419, 75, 134, 283, 943, 519, 362, 144, 681, 407,
        954, 131, 455, 934, 46, 513, 339, 194, 361, 606, 852, 546, 655, 1015, 147,
        506, 240, 56, 836, 76, 98, 600, 430, 388, 980, 695, 817, 279, 58, 215,
        149, 170, 531, 870, 18, 727, 154, 26, 938, 929, 302, 697, 452, 218, 700,
        524, 828, 751, 869, 217, 440, 354
    };

    private static final int[] PARTITION_THREE_UNIQUE_INDEX_TO_SEED = {
        0, 8, 11, 14, 15, 17, 18, 19, 26, 31, 34, 35, 36, 38, 44, 47, 48, 49,
        51, 56, 59, 61, 70, 74, 76, 82, 88, 90, 96, 100, 103, 104, 108, 110,
        111, 117, 122, 123, 126, 127, 132, 133, 135, 139, 147, 150, 151, 152,
        156, 157, 163, 166, 168, 171, 175, 176, 179, 181, 182, 183, 186, 189,
        192, 199, 203, 205, 207, 210, 214, 216, 222, 247, 249, 250, 252, 254,
        260, 261, 262, 263, 266, 272, 273, 275, 276, 288, 291, 292, 293, 294,
        297, 302, 309, 310, 313, 314, 318, 327, 328, 331, 335, 337, 346, 356,
        357, 358, 363, 365, 368, 378, 381, 384, 386, 390, 391, 392, 396, 397,
        398, 399, 401, 410, 411, 419, 427, 430, 431, 437, 439, 440, 451, 455,
        457, 458, 459, 460, 462, 468, 470, 471, 472, 474, 475, 477, 479, 482,
        483, 488, 493, 495, 496, 502, 503, 504, 507, 510, 511, 512, 515, 516,
        518, 519, 522, 523, 525, 526, 527, 538, 543, 544, 546, 547, 549, 550,
        552, 553, 554, 562, 570, 578, 579, 581, 582, 588, 589, 590, 593, 595,
        600, 606, 611, 613, 618, 623, 625, 632, 637, 638, 645, 646, 650, 651,
        658, 659, 662, 666, 667, 669, 670, 678, 679, 685, 686, 687, 688, 691,
        694, 696, 698, 699, 700, 701, 703, 704, 707, 713, 714, 715, 717, 719,
        722, 724, 727, 730, 731, 734, 738, 739, 743, 747, 748, 750, 751, 753,
        758, 760, 764, 766, 769, 775, 776, 783, 784, 785, 787, 791, 793, 798,
        799, 802, 804, 805, 806, 807, 808, 809, 810, 813, 822, 823, 825, 831,
        835, 837, 838, 839, 840, 842, 845, 846, 848, 853, 854, 858, 859, 860,
        866, 874, 882, 884, 887, 888, 892, 894, 898, 902, 907, 914, 915, 918,
        919, 922, 923, 925, 927, 931, 932, 937, 938, 940, 943, 944, 945, 953,
        955, 958, 959, 963, 966, 971, 974, 979, 990, 991, 998, 999, 1007,
        1010, 1011, 1012, 1015, 1020, 1023
    };

    private SixBySixHighDynamicRangeIntermediateTranscoder() {
    }

    static byte[] transcodeToAstc(
            byte[] data,
            int offset,
            int length,
            int expectedWidth,
            int expectedHeight) {
        BasisBitReader reader = new BasisBitReader(data, offset, length);
        boolean originalBehavior = readHeaderAndCheckCompatibility(reader, expectedWidth, expectedHeight);

        int blocksWide = divideRoundUp(expectedWidth, BLOCK_WIDTH);
        int blocksHigh = divideRoundUp(expectedHeight, BLOCK_HEIGHT);
        int totalBlocks = Math.multiplyExact(blocksWide, blocksHigh);
        byte[] output = new byte[Math.multiplyExact(totalBlocks, BLOCK_BYTES)];
        LogicalBlock[] ring = new LogicalBlock[Math.multiplyExact(blocksWide, REUSE_MAX_BUFFER_ROWS)];

        int currentX = 0;
        int currentY = 0;
        int currentRowIndex = 0;
        while (currentY < blocksHigh) {
            if (reader.getBitsRemaining() < 1) {
                throw new BasisDecodeException("UASTC HDR 6x6 intermediate payload ended early");
            }

            int encodingType = readEncodingType(reader);
            DecodePosition position = new DecodePosition(currentX, currentY, currentRowIndex);
            switch (encodingType) {
                case 0:
                    position = decodeRun(reader, ring, output, blocksWide, blocksHigh, position);
                    break;
                case 1:
                    position = decodeSolid(reader, ring, output, blocksWide, position);
                    break;
                case 2:
                    position = decodeReuse(reader, ring, output, blocksWide, originalBehavior, position);
                    break;
                case 3:
                    position = decodeBlock(reader, ring, output, blocksWide, originalBehavior, position);
                    break;
                default:
                    throw new BasisDecodeException("Invalid UASTC HDR 6x6 intermediate encoding type");
            }
            currentX = position.blockX;
            currentY = position.blockY;
            currentRowIndex = position.rowIndex;
        }

        if (reader.getBits(16) != END_MARKER) {
            throw new BasisDecodeException("UASTC HDR 6x6 intermediate end marker not found");
        }
        return output;
    }

    private static boolean readHeaderAndCheckCompatibility(
            BasisBitReader reader,
            int expectedWidth,
            int expectedHeight) {
        int signature = reader.getBits(16);
        boolean originalBehavior = signature == SIGNATURE_ORIGINAL;
        if (!originalBehavior && signature != SIGNATURE_CURRENT) {
            throw new BasisDecodeException("Invalid UASTC HDR 6x6 intermediate signature");
        }

        int width = reader.getBits(16);
        int height = reader.getBits(16);
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new BasisDecodeException("Invalid UASTC HDR 6x6 intermediate dimensions");
        }
        if (width != expectedWidth || height != expectedHeight) {
            throw new BasisDecodeException("UASTC HDR 6x6 intermediate dimensions mismatch");
        }
        return originalBehavior;
    }

    private static int readEncodingType(BasisBitReader reader) {
        int first = reader.getBits(1);
        if (first != 0) {
            return 3;
        }
        int second = reader.getBits(1);
        if (second != 0) {
            return 2;
        }
        int third = reader.getBits(1);
        return third != 0 ? 1 : 0;
    }

    private static DecodePosition decodeRun(
            BasisBitReader reader,
            LogicalBlock[] ring,
            byte[] output,
            int blocksWide,
            int blocksHigh,
            DecodePosition position) {
        if (position.blockX == 0 && position.blockY == 0) {
            throw new BasisDecodeException("UASTC HDR 6x6 run cannot start at first block");
        }
        int runLength = reader.decodeVariableLengthCode(5) + 1;
        int remaining = Math.multiplyExact(blocksWide, blocksHigh)
                - (position.blockX + position.blockY * blocksWide);
        if (runLength > remaining) {
            throw new BasisDecodeException("UASTC HDR 6x6 run exceeds remaining blocks");
        }

        int previousX = position.blockX == 0 ? blocksWide - 1 : position.blockX - 1;
        int previousY = position.blockX == 0 ? position.blockY - 1 : position.blockY;
        LogicalBlock previousBlock = requireLogicalBlock(
                ring,
                previousX,
                rowIndex(position.blockY, previousY, position.rowIndex),
                blocksWide);
        byte[] previousAstc = readAstcBlock(output, previousX, previousY, blocksWide);

        DecodePosition current = position;
        for (int i = 0; i < runLength; i++) {
            storeLogicalBlock(ring, current.blockX, current.rowIndex, blocksWide, previousBlock.copy());
            writeAstcBlock(output, current.blockX, current.blockY, blocksWide, previousAstc);
            current = advance(current, blocksWide);
        }
        return current;
    }

    private static DecodePosition decodeSolid(
            BasisBitReader reader,
            LogicalBlock[] ring,
            byte[] output,
            int blocksWide,
            DecodePosition position) {
        LogicalBlock block = new LogicalBlock();
        block.userMode = 255;
        block.solidColorHigh = true;
        block.solidColor[0] = reader.getBits(15);
        block.solidColor[1] = reader.getBits(15);
        block.solidColor[2] = reader.getBits(15);
        block.solidColor[3] = 0x3C00;

        storeLogicalBlock(ring, position.blockX, position.rowIndex, blocksWide, block);
        writeAstcBlock(
                output,
                position.blockX,
                position.blockY,
                blocksWide,
                packSolidHighBlock(block.solidColor));
        return advance(position, blocksWide);
    }

    private static DecodePosition decodeReuse(
            BasisBitReader reader,
            LogicalBlock[] ring,
            byte[] output,
            int blocksWide,
            boolean originalBehavior,
            DecodePosition position) {
        if (position.blockX == 0 && position.blockY == 0) {
            throw new BasisDecodeException("UASTC HDR 6x6 reuse cannot start at first block");
        }
        int reuseIndex = reader.getBits(REUSE_XY_DELTA_BITS);
        int previousX = position.blockX + REUSE_DELTAS[reuseIndex][0];
        int previousY = position.blockY + REUSE_DELTAS[reuseIndex][1];
        if (previousX < 0 || previousX >= blocksWide || previousY < 0) {
            throw new BasisDecodeException("UASTC HDR 6x6 reuse block reference is out of range");
        }

        LogicalBlock block = requireLogicalBlock(
                ring,
                previousX,
                rowIndex(position.blockY, previousY, position.rowIndex),
                blocksWide).copy();
        if (block.solidColorHigh) {
            throw new BasisDecodeException("UASTC HDR 6x6 reuse cannot reference a solid block");
        }

        int totalWeights = block.gridWidth * block.gridHeight * (block.dualPlane ? 2 : 1);
        decodeValues(reader, totalWeights, block.weightIseRange, block.weights);
        byte[] physicalBlock = packTranscodeBlock(block, originalBehavior);
        storeLogicalBlock(ring, position.blockX, position.rowIndex, blocksWide, block);
        writeAstcBlock(output, position.blockX, position.blockY, blocksWide, physicalBlock);
        return advance(position, blocksWide);
    }

    private static DecodePosition decodeBlock(
            BasisBitReader reader,
            LogicalBlock[] ring,
            byte[] output,
            int blocksWide,
            boolean originalBehavior,
            DecodePosition position) {
        int blockMode = reader.decodeTruncatedBinary(TOTAL_BLOCK_MODES);
        int endpointMode = reader.decodeTruncatedBinary(TOTAL_ENDPOINT_MODES);
        switch (endpointMode) {
            case 0:
                return decodeRawBlock(
                        reader,
                        ring,
                        output,
                        blocksWide,
                        originalBehavior,
                        position,
                        blockMode);
            case 1:
            case 2:
                return decodeNeighborBlock(
                        reader,
                        ring,
                        output,
                        blocksWide,
                        originalBehavior,
                        position,
                        blockMode,
                        endpointMode == 1);
            case 3:
            case 4:
                return decodeNeighborDeltaBlock(
                        reader,
                        ring,
                        output,
                        blocksWide,
                        originalBehavior,
                        position,
                        blockMode,
                        endpointMode == 3);
            default:
                throw new BasisDecodeException("Invalid UASTC HDR 6x6 endpoint mode");
        }
    }

    private static DecodePosition decodeNeighborBlock(
            BasisBitReader reader,
            LogicalBlock[] ring,
            byte[] output,
            int blocksWide,
            boolean originalBehavior,
            DecodePosition position,
            int blockMode,
            boolean useLeft) {
        int neighborX = useLeft ? position.blockX - 1 : position.blockX;
        int neighborY = useLeft ? position.blockY : position.blockY - 1;
        LogicalBlock neighbor = requireNeighbor(ring, blocksWide, position, neighborX, neighborY);
        BlockModeDescription mode = BLOCK_MODE_DESCRIPTIONS[blockMode];
        final int endpointValues = endpointValueCount(mode.colorEndpointMode);
        if (mode.colorEndpointMode != neighbor.colorEndpointModes[0]) {
            throw new BasisDecodeException("UASTC HDR 6x6 neighbor CEM mismatch");
        }

        LogicalBlock block = logicalBlockForMode(blockMode, mode);
        block.numPartitions = 1;
        block.endpointIseRange = neighbor.endpointIseRange;
        System.arraycopy(neighbor.endpoints, 0, block.endpoints, 0, endpointValues);

        int totalWeights = mode.gridWidth * mode.gridHeight * (mode.dualPlane ? 2 : 1);
        decodeValues(reader, totalWeights, mode.weightIseRange, block.weights);
        byte[] physicalBlock = packTranscodeBlock(block, originalBehavior);
        storeLogicalBlock(ring, position.blockX, position.rowIndex, blocksWide, block);
        writeAstcBlock(output, position.blockX, position.blockY, blocksWide, physicalBlock);
        return advance(position, blocksWide);
    }

    private static DecodePosition decodeNeighborDeltaBlock(
            BasisBitReader reader,
            LogicalBlock[] ring,
            byte[] output,
            int blocksWide,
            boolean originalBehavior,
            DecodePosition position,
            int blockMode,
            boolean useLeft) {
        int neighborX = useLeft ? position.blockX - 1 : position.blockX;
        int neighborY = useLeft ? position.blockY : position.blockY - 1;
        LogicalBlock neighbor = requireNeighbor(ring, blocksWide, position, neighborX, neighborY);
        BlockModeDescription mode = BLOCK_MODE_DESCRIPTIONS[blockMode];
        final int endpointValues = endpointValueCount(mode.colorEndpointMode);
        if (mode.colorEndpointMode != neighbor.colorEndpointModes[0]) {
            throw new BasisDecodeException("UASTC HDR 6x6 neighbor-delta CEM mismatch");
        }

        LogicalBlock block = logicalBlockForMode(blockMode, mode);
        block.endpointIseRange = mode.endpointIseRange;
        requantizeEndpoints(
                mode.colorEndpointMode,
                neighbor.endpointIseRange,
                neighbor.endpoints,
                mode.endpointIseRange,
                block.endpoints,
                0);

        int lowDeltaLimit = -(1 << NUM_ENDPOINT_DELTA_BITS) / 2;
        int levels = XuastcAstcConstants.getIseLevels(block.endpointIseRange);
        for (int i = 0; i < endpointValues; i++) {
            int rank = XuastcAstcConstants.endpointIseToRank(block.endpoints[i], block.endpointIseRange);
            rank += reader.getBits(NUM_ENDPOINT_DELTA_BITS) + lowDeltaLimit;
            if (rank < 0 || rank >= levels) {
                throw new BasisDecodeException("UASTC HDR 6x6 endpoint delta is out of range");
            }
            block.endpoints[i] = XuastcAstcConstants.endpointRankToIse(rank, block.endpointIseRange);
        }

        int totalWeights = mode.gridWidth * mode.gridHeight * (mode.dualPlane ? 2 : 1);
        decodeValues(reader, totalWeights, mode.weightIseRange, block.weights);
        byte[] physicalBlock = packTranscodeBlock(block, originalBehavior);
        storeLogicalBlock(ring, position.blockX, position.rowIndex, blocksWide, block);
        writeAstcBlock(output, position.blockX, position.blockY, blocksWide, physicalBlock);
        return advance(position, blocksWide);
    }

    private static DecodePosition decodeRawBlock(
            BasisBitReader reader,
            LogicalBlock[] ring,
            byte[] output,
            int blocksWide,
            boolean originalBehavior,
            DecodePosition position,
            int blockMode) {
        BlockModeDescription mode = BLOCK_MODE_DESCRIPTIONS[blockMode];
        LogicalBlock block = logicalBlockForMode(blockMode, mode);
        if (mode.numPartitions == 2) {
            block.partitionId = PARTITION_TWO_UNIQUE_INDEX_TO_SEED[
                    reader.decodeTruncatedBinary(NUM_UNIQUE_PARTITIONS_TWO)];
        } else if (mode.numPartitions == 3) {
            block.partitionId = PARTITION_THREE_UNIQUE_INDEX_TO_SEED[
                    reader.decodeTruncatedBinary(NUM_UNIQUE_PARTITIONS_THREE)];
        }

        int endpointValues = endpointValueCount(mode.colorEndpointMode);
        decodeValues(
                reader,
                endpointValues * mode.numPartitions,
                mode.endpointIseRange,
                block.endpoints);
        int totalWeights = mode.gridWidth * mode.gridHeight * (mode.dualPlane ? 2 : 1);
        decodeValues(reader, totalWeights, mode.weightIseRange, block.weights);

        byte[] physicalBlock = packTranscodeBlock(block, originalBehavior);
        storeLogicalBlock(ring, position.blockX, position.rowIndex, blocksWide, block);
        writeAstcBlock(output, position.blockX, position.blockY, blocksWide, physicalBlock);
        return advance(position, blocksWide);
    }

    private static LogicalBlock requireNeighbor(
            LogicalBlock[] ring,
            int blocksWide,
            DecodePosition position,
            int neighborX,
            int neighborY) {
        if (neighborX < 0 || neighborY < 0) {
            throw new BasisDecodeException("UASTC HDR 6x6 neighbor reference is out of range");
        }
        LogicalBlock neighbor = requireLogicalBlock(
                ring,
                neighborX,
                rowIndex(position.blockY, neighborY, position.rowIndex),
                blocksWide);
        if (neighbor.colorEndpointModes[0] == 0) {
            throw new BasisDecodeException("UASTC HDR 6x6 neighbor endpoint mode is invalid");
        }
        return neighbor;
    }

    private static LogicalBlock logicalBlockForMode(int blockMode, BlockModeDescription mode) {
        LogicalBlock block = new LogicalBlock();
        block.userMode = blockMode;
        block.numPartitions = mode.numPartitions;
        block.endpointIseRange = mode.endpointIseRange;
        block.weightIseRange = mode.weightIseRange;
        block.gridWidth = mode.gridWidth;
        block.gridHeight = mode.gridHeight;
        block.dualPlane = mode.dualPlane;
        block.colorComponentSelector = mode.dualPlane ? mode.dualPlaneChannel : 0;
        Arrays.fill(block.colorEndpointModes, 0, mode.numPartitions, mode.colorEndpointMode);
        return block;
    }

    private static byte[] packTranscodeBlock(LogicalBlock block, boolean originalBehavior) {
        BlockModeDescription mode = BLOCK_MODE_DESCRIPTIONS[block.userMode];
        LogicalBlock transcodeBlock = new LogicalBlock();
        transcodeBlock.dualPlane = mode.dualPlane;
        transcodeBlock.colorComponentSelector = mode.dualPlane ? mode.dualPlaneChannel : 0;
        transcodeBlock.partitionId = block.partitionId;
        transcodeBlock.numPartitions = mode.numPartitions;
        Arrays.fill(transcodeBlock.colorEndpointModes, 0, mode.numPartitions, mode.colorEndpointMode);
        transcodeBlock.endpointIseRange = mode.transcodeEndpointIseRange;
        transcodeBlock.weightIseRange = mode.transcodeWeightIseRange;

        int endpointValues = endpointValueCount(mode.colorEndpointMode);
        for (int partition = 0; partition < mode.numPartitions; partition++) {
            requantizeEndpoints(
                    mode.colorEndpointMode,
                    block.endpointIseRange,
                    block.endpoints,
                    mode.transcodeEndpointIseRange,
                    transcodeBlock.endpoints,
                    partition * endpointValues);
        }

        int totalWeights = mode.gridWidth * mode.gridHeight * (mode.dualPlane ? 2 : 1);
        int[] weights = new int[Math.max(totalWeights, BLOCK_WIDTH * BLOCK_HEIGHT * 2)];
        requantizeWeights(
                totalWeights,
                block.weights,
                block.weightIseRange,
                weights,
                mode.transcodeWeightIseRange);
        copyWeightGrid(
                mode.dualPlane,
                mode.gridWidth,
                mode.gridHeight,
                weights,
                transcodeBlock,
                originalBehavior);
        return packLogicalBlock(transcodeBlock);
    }

    private static void decodeValues(BasisBitReader reader, int totalValues, int iseRange, int[] values) {
        int bitCount = XuastcAstcConstants.getIseBitCount(iseRange);
        boolean hasTrits = XuastcAstcConstants.getIseTritCount(iseRange) != 0;
        boolean hasQuints = XuastcAstcConstants.getIseQuintCount(iseRange) != 0;
        int totalGroups = 0;
        int bundleSize = 0;
        int multiplier = 0;
        if (hasTrits) {
            totalGroups = (totalValues + 4) / 5;
            bundleSize = 5;
            multiplier = 3;
        } else if (hasQuints) {
            totalGroups = (totalValues + 2) / 3;
            bundleSize = 3;
            multiplier = 5;
        }

        int[] groupValues = new int[totalGroups];
        for (int i = 0; i < totalGroups; i++) {
            int groupBits = hasTrits ? 8 : 7;
            if (i == totalGroups - 1) {
                groupBits = finalGroupBitCount(
                        groupBits,
                        hasTrits,
                        hasQuints,
                        totalValues,
                        totalGroups,
                        bundleSize);
            }
            groupValues[i] = reader.getBits(groupBits);
        }

        int accumulator = 0;
        int accumulatorRemaining = 0;
        int nextGroup = 0;
        for (int i = 0; i < totalValues; i++) {
            int value = reader.getBits(bitCount);
            if (totalGroups != 0) {
                if (accumulatorRemaining == 0) {
                    accumulator = groupValues[nextGroup++];
                    accumulatorRemaining = bundleSize;
                }
                int high = accumulator % multiplier;
                accumulator /= multiplier;
                accumulatorRemaining--;
                value |= high << bitCount;
            }
            values[i] = value;
        }
    }

    private static int finalGroupBitCount(
            int defaultBits,
            boolean hasTrits,
            boolean hasQuints,
            int totalValues,
            int totalGroups,
            int bundleSize) {
        int remaining = totalValues - (totalGroups - 1) * bundleSize;
        if (hasTrits) {
            switch (remaining) {
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
        if (hasQuints) {
            switch (remaining) {
                case 1:
                    return 3;
                case 2:
                    return 5;
                default:
                    return defaultBits;
            }
        }
        return defaultBits;
    }

    private static void requantizeWeights(
            int count,
            int[] source,
            int sourceRange,
            int[] destination,
            int destinationRange) {
        if (sourceRange == destinationRange) {
            System.arraycopy(source, 0, destination, 0, count);
            return;
        }
        for (int i = 0; i < count; i++) {
            int dequantized = XuastcAstcConstants.dequantBiseWeight(source[i], sourceRange);
            destination[i] = XuastcAstcConstants.nearestWeightIse(dequantized, destinationRange);
        }
    }

    private static void requantizeEndpoints(
            int colorEndpointMode,
            int sourceRange,
            int[] source,
            int destinationRange,
            int[] destination,
            int destinationOffset) {
        int count = endpointValueCount(colorEndpointMode);
        if (sourceRange == destinationRange) {
            System.arraycopy(source, destinationOffset, destination, destinationOffset, count);
            return;
        }

        int[] sourceValues = new int[count];
        for (int i = 0; i < count; i++) {
            sourceValues[i] = sourceRange == 20
                    ? source[destinationOffset + i]
                    : XuastcAstcConstants.dequantBiseEndpoint(source[destinationOffset + i], sourceRange);
        }
        if (destinationRange == 20) {
            System.arraycopy(sourceValues, 0, destination, destinationOffset, count);
            return;
        }

        if (colorEndpointMode == 11) {
            requantizeModeElevenEndpoints(sourceValues, destinationRange, destination, destinationOffset);
        } else if (colorEndpointMode == 7) {
            destination[destinationOffset] =
                    nearestEndpointIsePreservingMask(sourceValues[0], destinationRange, 0xC0);
            for (int i = 1; i < count; i++) {
                destination[destinationOffset + i] =
                        nearestEndpointIsePreservingMask(sourceValues[i], destinationRange, 0xE0);
            }
        } else {
            throw new BasisDecodeException("Unsupported UASTC HDR 6x6 endpoint CEM");
        }
    }

    private static void requantizeModeElevenEndpoints(
            int[] sourceValues,
            int destinationRange,
            int[] destination,
            int destinationOffset) {
        int majorComponent = ((sourceValues[4] >>> 7) & 1) | (((sourceValues[5] >>> 7) & 1) << 1);
        if (majorComponent == 3) {
            for (int i = 0; i < 6; i++) {
                destination[destinationOffset + i] = nearestEndpointIse(sourceValues[i], destinationRange);
            }
            return;
        }

        destination[destinationOffset] = nearestEndpointIse(sourceValues[0], destinationRange);
        destination[destinationOffset + 1] =
                nearestEndpointIsePreservingMask(sourceValues[1], destinationRange, 0xC0);
        destination[destinationOffset + 2] =
                nearestEndpointIsePreservingMask(sourceValues[2], destinationRange, 0xC0);
        destination[destinationOffset + 3] =
                nearestEndpointIsePreservingMask(sourceValues[3], destinationRange, 0xC0);
        destination[destinationOffset + 4] =
                nearestEndpointIsePreservingMask(sourceValues[4], destinationRange, 0xE0);
        destination[destinationOffset + 5] =
                nearestEndpointIsePreservingMask(sourceValues[5], destinationRange, 0xE0);
    }

    private static int nearestEndpointIsePreservingMask(int value, int range, int mask) {
        int clamped = clamp(value, 0, 255);
        int levels = XuastcAstcConstants.getIseLevels(range);
        int bestIndex = -1;
        int bestError = Integer.MAX_VALUE;
        for (int i = 0; i < levels; i++) {
            int dequantized = XuastcAstcConstants.dequantBiseEndpoint(i, range);
            if ((dequantized & mask) != (clamped & mask)) {
                continue;
            }
            int error = Math.abs(dequantized - clamped);
            if (error < bestError) {
                bestError = error;
                bestIndex = i;
            }
        }
        return bestIndex >= 0 ? bestIndex : nearestEndpointIse(clamped, range);
    }

    private static int nearestEndpointIse(int value, int range) {
        int clamped = clamp(value, 0, 255);
        int levels = XuastcAstcConstants.getIseLevels(range);
        int bestIndex = 0;
        int bestError = Integer.MAX_VALUE;
        for (int i = 0; i < levels; i++) {
            int error = Math.abs(XuastcAstcConstants.dequantBiseEndpoint(i, range) - clamped);
            if (error < bestError) {
                bestError = error;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static void copyWeightGrid(
            boolean dualPlane,
            int gridWidth,
            int gridHeight,
            int[] weights,
            LogicalBlock destination,
            boolean originalBehavior) {
        destination.weightIseRange = destination.weightIseRange;
        if (!dualPlane && gridWidth == 2 && gridHeight == 2) {
            destination.gridWidth = 4;
            destination.gridHeight = 4;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    WeightedSample sample = upsampleWeight(x, y, 4, 4, 2, 2);
                    int totalWeight = 8;
                    for (int yo = 0; yo < 2; yo++) {
                        for (int xo = 0; xo < 2; xo++) {
                            int sampleWeight = sample.weights[yo][xo];
                            if (sampleWeight == 0) {
                                continue;
                            }
                            int sourceIndex;
                            if (originalBehavior) {
                                int brokenIndex = (x + xo) + (y + yo) * gridWidth;
                                sourceIndex = brokenIndex >= 0 && brokenIndex < gridWidth * gridHeight
                                        ? 1
                                        : 0;
                            } else {
                                sourceIndex = (sample.sourceX + xo) + (sample.sourceY + yo) * gridWidth;
                            }
                            int dequantized = XuastcAstcConstants.dequantBiseWeight(
                                    weights[sourceIndex],
                                    destination.weightIseRange);
                            totalWeight += dequantized * sampleWeight;
                        }
                    }
                    destination.weights[x + y * 4] =
                            XuastcAstcConstants.nearestWeightIse(
                                    totalWeight >>> 4,
                                    destination.weightIseRange);
                }
            }
            return;
        }

        destination.gridWidth = gridWidth;
        destination.gridHeight = gridHeight;
        int totalWeights = gridWidth * gridHeight * (dualPlane ? 2 : 1);
        System.arraycopy(weights, 0, destination.weights, 0, totalWeights);
    }

    private static WeightedSample upsampleWeight(
            int x,
            int y,
            int blockWidth,
            int blockHeight,
            int gridWidth,
            int gridHeight) {
        int scaleX = (1024 + blockWidth / 2) / (blockWidth - 1);
        int scaleY = (1024 + blockHeight / 2) / (blockHeight - 1);
        int gridX = (scaleX * x * (gridWidth - 1) + 32) >>> 6;
        int gridY = (scaleY * y * (gridHeight - 1) + 32) >>> 6;
        int fractionX = gridX & 0xF;
        int fractionY = gridY & 0xF;
        int weight11 = (fractionX * fractionY + 8) >>> 4;
        int weight10 = fractionY - weight11;
        int weight01 = fractionX - weight11;
        int weight00 = 16 - fractionX - fractionY + weight11;
        return new WeightedSample(
                gridX >>> 4,
                gridY >>> 4,
                new int[][] {{weight00, weight01}, {weight10, weight11}});
    }

    private static byte[] packLogicalBlock(LogicalBlock block) {
        if (block.solidColorHigh) {
            return packSolidHighBlock(block.solidColor);
        }

        byte[] packed = new byte[BLOCK_BYTES];
        int[] bitPosition = {0};
        XuastcAstcBlockPacker.setBits(packed, bitPosition, configBits(block), 11);

        int totalWeights = block.gridWidth * block.gridHeight * (block.dualPlane ? 2 : 1);
        int totalWeightBits = iseSequenceBits(totalWeights, block.weightIseRange);
        if (totalWeights == 0 || totalWeights > MAX_GRID_WEIGHTS
                || totalWeightBits < 24 || totalWeightBits > 96) {
            throw new BasisDecodeException("Invalid UASTC HDR 6x6 ASTC weight-grid encoding");
        }

        XuastcAstcBlockPacker.setBits(packed, bitPosition, block.numPartitions - 1, 2);
        if (block.numPartitions > 1) {
            XuastcAstcBlockPacker.setBits(packed, bitPosition, block.partitionId, 10);
            XuastcAstcBlockPacker.setBits(packed, bitPosition, (block.colorEndpointModes[0] << 2) & 0x3F, 6);
        } else {
            if (block.partitionId != 0) {
                throw new BasisDecodeException("Invalid UASTC HDR 6x6 single-partition seed");
            }
            XuastcAstcBlockPacker.setBits(packed, bitPosition, block.colorEndpointModes[0], 4);
        }

        int extraBits = 0;
        if (block.dualPlane) {
            extraBits += 2;
            int[] selectorPosition = {128 - totalWeightBits - extraBits};
            XuastcAstcBlockPacker.setBits(packed, selectorPosition, block.colorComponentSelector, 2);
        }

        int remainingBits = 128 - bitPosition[0] - extraBits - totalWeightBits;
        int totalEndpointValues = 0;
        for (int i = 0; i < block.numPartitions; i++) {
            totalEndpointValues += endpointValueCount(block.colorEndpointModes[i]);
        }
        if (totalEndpointValues > MAX_ENDPOINTS) {
            throw new BasisDecodeException("UASTC HDR 6x6 ASTC block has too many endpoints");
        }

        int endpointRange = selectEndpointRange(totalEndpointValues, remainingBits);
        if (block.endpointIseRange != endpointRange) {
            throw new BasisDecodeException("UASTC HDR 6x6 endpoint range mismatch");
        }
        XuastcAstcBlockPacker.encodeBise(
                packed,
                block.endpoints,
                bitPosition[0],
                totalEndpointValues,
                endpointRange);

        byte[] encodedWeights = new byte[BLOCK_BYTES];
        XuastcAstcBlockPacker.encodeBise(
                encodedWeights,
                block.weights,
                0,
                totalWeights,
                block.weightIseRange);
        for (int i = 0; i < 4; i++) {
            int reversed = Integer.reverse(readLittleEndianInt(encodedWeights, (3 - i) * 4));
            int existing = readLittleEndianInt(packed, i * 4);
            writeLittleEndianInt(packed, i * 4, existing | reversed);
        }
        return packed;
    }

    private static byte[] packSolidHighBlock(int[] color) {
        byte[] block = new byte[BLOCK_BYTES];
        Arrays.fill(block, (byte) 0xFF);
        block[0] = (byte) 0xFC;
        for (int i = 0; i < 4; i++) {
            block[8 + i * 2] = (byte) color[i];
            block[9 + i * 2] = (byte) (color[i] >>> 8);
        }
        return block;
    }

    private static int configBits(LogicalBlock block) {
        int width = block.gridWidth;
        int height = block.gridHeight;
        int highPrecision = block.weightIseRange >= 6 ? 1 : 0;
        int dualPlanePrecision = ((block.dualPlane ? 1 : 0) << 1) | highPrecision;
        int precision = 2 + block.weightIseRange - (highPrecision != 0 ? 6 : 0);
        precision = (precision >>> 1) + ((precision & 1) << 2);

        if (isPackable(width - 4, 2) && isPackable(height - 2, 2)) {
            return (dualPlanePrecision << 9) | ((width - 4) << 7) | ((height - 2) << 5)
                    | ((precision & 4) << 2) | (precision & 3);
        }
        if (isPackable(width - 8, 2) && isPackable(height - 2, 2)) {
            return (dualPlanePrecision << 9) | ((width - 8) << 7) | ((height - 2) << 5)
                    | ((precision & 4) << 2) | 4 | (precision & 3);
        }
        if (isPackable(width - 2, 2) && isPackable(height - 8, 2)) {
            return (dualPlanePrecision << 9) | ((height - 8) << 7) | ((width - 2) << 5)
                    | ((precision & 4) << 2) | 8 | (precision & 3);
        }
        if (isPackable(width - 2, 2) && isPackable(height - 6, 1)) {
            return (dualPlanePrecision << 9) | ((height - 6) << 7) | ((width - 2) << 5)
                    | ((precision & 4) << 2) | 12 | (precision & 3);
        }
        if (isPackable(width - 2, 1) && isPackable(height - 2, 2)) {
            return (dualPlanePrecision << 9) | (width << 7) | ((height - 2) << 5)
                    | ((precision & 4) << 2) | 12 | (precision & 3);
        }
        if (width == 12 && isPackable(height - 2, 2)) {
            return (dualPlanePrecision << 9) | ((height - 2) << 5) | (precision << 2);
        }
        if (height == 12 && isPackable(width - 2, 2)) {
            return (dualPlanePrecision << 9) | (1 << 7) | ((width - 2) << 5) | (precision << 2);
        }
        if (width == 6 && height == 10) {
            return (dualPlanePrecision << 9) | (3 << 7) | (precision << 2);
        }
        if (width == 10 && height == 6) {
            return (dualPlanePrecision << 9) | (13 << 5) | (precision << 2);
        }
        if (dualPlanePrecision == 0 && isPackable(width - 6, 2) && isPackable(height - 6, 2)) {
            return ((height - 6) << 9) | 256 | ((width - 6) << 5) | (precision << 2);
        }
        throw new BasisDecodeException("Unsupported UASTC HDR 6x6 ASTC weight-grid dimensions");
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
        throw new BasisDecodeException("UASTC HDR 6x6 ASTC block has invalid endpoint ISE range");
    }

    private static int iseSequenceBits(int count, int range) {
        int totalBits = XuastcAstcConstants.getIseBitCount(range) * count;
        totalBits += (XuastcAstcConstants.getIseTritCount(range) * 8 * count + 4) / 5;
        totalBits += (XuastcAstcConstants.getIseQuintCount(range) * 7 * count + 2) / 3;
        return totalBits;
    }

    private static int endpointValueCount(int colorEndpointMode) {
        if (colorEndpointMode == 11) {
            return 6;
        }
        if (colorEndpointMode == 7) {
            return 4;
        }
        throw new BasisDecodeException("Unsupported UASTC HDR 6x6 endpoint mode");
    }

    private static LogicalBlock requireLogicalBlock(
            LogicalBlock[] ring,
            int x,
            int rowIndex,
            int blocksWide) {
        LogicalBlock block = ring[x + rowIndex * blocksWide];
        if (block == null) {
            throw new BasisDecodeException("UASTC HDR 6x6 referenced an unavailable prior block");
        }
        return block;
    }

    private static void storeLogicalBlock(
            LogicalBlock[] ring,
            int x,
            int rowIndex,
            int blocksWide,
            LogicalBlock block) {
        ring[x + rowIndex * blocksWide] = block;
    }

    private static int rowIndex(int currentY, int previousY, int currentRowIndex) {
        int deltaY = previousY - currentY;
        if (deltaY <= -REUSE_MAX_BUFFER_ROWS || deltaY > 0) {
            throw new BasisDecodeException("UASTC HDR 6x6 reuse row is out of range");
        }
        int rowIndex = currentRowIndex + deltaY;
        if (rowIndex < 0) {
            rowIndex += REUSE_MAX_BUFFER_ROWS;
        }
        return rowIndex;
    }

    private static DecodePosition advance(DecodePosition position, int blocksWide) {
        int blockX = position.blockX + 1;
        int blockY = position.blockY;
        int rowIndex = position.rowIndex;
        if (blockX == blocksWide) {
            blockX = 0;
            blockY++;
            rowIndex = (rowIndex + 1) % REUSE_MAX_BUFFER_ROWS;
        }
        return new DecodePosition(blockX, blockY, rowIndex);
    }

    private static byte[] readAstcBlock(byte[] output, int x, int y, int blocksWide) {
        byte[] block = new byte[BLOCK_BYTES];
        System.arraycopy(output, (x + y * blocksWide) * BLOCK_BYTES, block, 0, BLOCK_BYTES);
        return block;
    }

    private static void writeAstcBlock(byte[] output, int x, int y, int blocksWide, byte[] block) {
        System.arraycopy(block, 0, output, (x + y * blocksWide) * BLOCK_BYTES, BLOCK_BYTES);
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

    private static boolean isPackable(int value, int bits) {
        return value >= 0 && value < (1 << bits);
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static final class LogicalBlock {
        private final int[] colorEndpointModes = new int[4];
        private final int[] endpoints = new int[MAX_ENDPOINTS];
        private final int[] weights = new int[MAX_GRID_WEIGHTS];
        private final int[] solidColor = new int[4];
        private int userMode;
        private int numPartitions;
        private int endpointIseRange;
        private int weightIseRange;
        private int gridWidth;
        private int gridHeight;
        private boolean dualPlane;
        private int colorComponentSelector;
        private int partitionId;
        private boolean solidColorHigh;

        private LogicalBlock copy() {
            LogicalBlock copy = new LogicalBlock();
            System.arraycopy(colorEndpointModes, 0, copy.colorEndpointModes, 0, colorEndpointModes.length);
            System.arraycopy(endpoints, 0, copy.endpoints, 0, endpoints.length);
            System.arraycopy(weights, 0, copy.weights, 0, weights.length);
            System.arraycopy(solidColor, 0, copy.solidColor, 0, solidColor.length);
            copy.userMode = userMode;
            copy.numPartitions = numPartitions;
            copy.endpointIseRange = endpointIseRange;
            copy.weightIseRange = weightIseRange;
            copy.gridWidth = gridWidth;
            copy.gridHeight = gridHeight;
            copy.dualPlane = dualPlane;
            copy.colorComponentSelector = colorComponentSelector;
            copy.partitionId = partitionId;
            copy.solidColorHigh = solidColorHigh;
            return copy;
        }
    }

    private static final class BlockModeDescription {
        private final boolean dualPlane;
        private final int colorEndpointMode;
        private final int numPartitions;
        private final int gridWidth;
        private final int gridHeight;
        private final int endpointIseRange;
        private final int weightIseRange;
        private final int transcodeEndpointIseRange;
        private final int transcodeWeightIseRange;
        private final int flags;
        private final int dualPlaneChannel;

        private BlockModeDescription(
                boolean dualPlane,
                int colorEndpointMode,
                int numPartitions,
                int gridWidth,
                int gridHeight,
                int endpointIseRange,
                int weightIseRange,
                int transcodeEndpointIseRange,
                int transcodeWeightIseRange,
                int flags,
                int dualPlaneChannel) {
            this.dualPlane = dualPlane;
            this.colorEndpointMode = colorEndpointMode;
            this.numPartitions = numPartitions;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            this.endpointIseRange = endpointIseRange;
            this.weightIseRange = weightIseRange;
            this.transcodeEndpointIseRange = transcodeEndpointIseRange;
            this.transcodeWeightIseRange = transcodeWeightIseRange;
            this.flags = flags;
            this.dualPlaneChannel = dualPlaneChannel;
        }
    }

    private static final class DecodePosition {
        private final int blockX;
        private final int blockY;
        private final int rowIndex;

        private DecodePosition(int blockX, int blockY, int rowIndex) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.rowIndex = rowIndex;
        }
    }

    private static final class WeightedSample {
        private final int sourceX;
        private final int sourceY;
        private final int[][] weights;

        private WeightedSample(int sourceX, int sourceY, int[][] weights) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.weights = weights;
        }
    }
}
