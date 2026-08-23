package org.ngengine.basis;

/**
 * Adaptive binary model used by the XUASTC arithmetic decoder.
 */
final class XuastcArithmeticBitModel {
    private static final int LENGTH_SHIFT = 13;
    private static final int MAX_COUNT = 1 << LENGTH_SHIFT;

    int bit0Probability;
    int bit0Count;
    int bitCount;
    int bitsUntilUpdate;
    int updateInterval;

    XuastcArithmeticBitModel() {
        reset();
    }

    void reset() {
        bit0Count = 1;
        bitCount = 2;
        bit0Probability = 1 << (LENGTH_SHIFT - 1);
        updateInterval = 4;
        bitsUntilUpdate = updateInterval;
    }

    void update() {
        if (bitCount >= MAX_COUNT) {
            bitCount = (bitCount + 1) >>> 1;
            bit0Count = (bit0Count + 1) >>> 1;
            if (bit0Count == bitCount) {
                bitCount++;
            }
        }

        int scale = (int) (0x8000_0000L / bitCount);
        bit0Probability = (int) ((bit0Count * (long) scale) >>> (31 - LENGTH_SHIFT));
        updateInterval = clamp((5 * updateInterval) >>> 2, 4, 128);
        bitsUntilUpdate = updateInterval;
    }

    static int getLengthShift() {
        return LENGTH_SHIFT;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
