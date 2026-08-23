package org.ngengine.basis;

/**
 * ASTC partition selection for logical XUASTC blocks.
 */
final class XuastcAstcPartitioner {
    private XuastcAstcPartitioner() {
    }

    static int[] computePartitionMap(int blockWidth, int blockHeight, int seed, int numberOfPartitions) {
        int[] partitions = new int[blockWidth * blockHeight];
        boolean smallBlock = blockWidth * blockHeight < 31;
        for (int y = 0; y < blockHeight; y++) {
            for (int x = 0; x < blockWidth; x++) {
                partitions[x + y * blockWidth] =
                        computeTexelPartition(seed, x, y, numberOfPartitions, smallBlock);
            }
        }
        return partitions;
    }

    private static int computeTexelPartition(
            int seedInput,
            int inputX,
            int inputY,
            int numberOfPartitions,
            boolean smallBlock) {
        final int x = smallBlock ? inputX << 1 : inputX;
        final int y = smallBlock ? inputY << 1 : inputY;
        final int seed = seedInput + 1024 * (numberOfPartitions - 1);
        final int random = hash52(seed);
        int[] seeds = {
                random & 0xF,
                (random >>> 4) & 0xF,
                (random >>> 8) & 0xF,
                (random >>> 12) & 0xF,
                (random >>> 16) & 0xF,
                (random >>> 20) & 0xF,
                (random >>> 24) & 0xF,
                (random >>> 28) & 0xF,
                (random >>> 18) & 0xF,
                (random >>> 22) & 0xF,
                (random >>> 26) & 0xF,
                ((random >>> 30) | (random << 2)) & 0xF
        };

        for (int i = 0; i < seeds.length; i++) {
            seeds[i] *= seeds[i];
        }

        int shiftA = (seed & 2) != 0 ? 4 : 5;
        int shiftB = numberOfPartitions == 3 ? 6 : 5;
        int shift1 = (seed & 1) != 0 ? shiftA : shiftB;
        int shift2 = (seed & 1) != 0 ? shiftB : shiftA;
        final int shift3 = (seed & 0x10) != 0 ? shift1 : shift2;

        shiftSeeds(seeds, shift1, shift2, shift3);

        int a = 0x3F & (seeds[0] * x + seeds[1] * y + (random >>> 14));
        int b = 0x3F & (seeds[2] * x + seeds[3] * y + (random >>> 10));
        int c = numberOfPartitions >= 3
                ? 0x3F & (seeds[4] * x + seeds[5] * y + (random >>> 6))
                : 0;
        int d = numberOfPartitions >= 4
                ? 0x3F & (seeds[6] * x + seeds[7] * y + (random >>> 2))
                : 0;
        if (a >= b && a >= c && a >= d) {
            return 0;
        }
        if (b >= c && b >= d) {
            return 1;
        }
        return c >= d ? 2 : 3;
    }

    private static void shiftSeeds(int[] seeds, int shift1, int shift2, int shift3) {
        seeds[0] >>>= shift1;
        seeds[1] >>>= shift2;
        seeds[2] >>>= shift1;
        seeds[3] >>>= shift2;
        seeds[4] >>>= shift1;
        seeds[5] >>>= shift2;
        seeds[6] >>>= shift1;
        seeds[7] >>>= shift2;
        seeds[8] >>>= shift3;
        seeds[9] >>>= shift3;
        seeds[10] >>>= shift3;
        seeds[11] >>>= shift3;
    }

    private static int hash52(int value) {
        int p = value;
        p ^= p >>> 15;
        p -= p << 17;
        p += p << 7;
        p += p << 4;
        p ^= p >>> 5;
        p += p << 16;
        p ^= p >>> 7;
        p ^= p >>> 3;
        p ^= p << 6;
        p ^= p >>> 17;
        return p;
    }
}
