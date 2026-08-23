package org.ngengine.basis;

import java.util.Arrays;

/**
 * ETC1S selector palette entry.
 */
final class Etc1sSelector {
    private static final int[] SELECTOR_INDEX_TO_ETC1 = {3, 2, 0, 1};

    private final byte[] selectors = new byte[4];
    private final byte[] etc1Bytes = new byte[4];
    private int lowSelector;
    private int highSelector;
    private int uniqueSelectorCount;

    void setSelector(int x, int y, int value) {
        selectors[y] = (byte) ((selectors[y] & ~(3 << (x * 2))) | ((value & 3) << (x * 2)));

        int etc1BitIndex = x * 4 + y;
        int byteIndex = 3 - (etc1BitIndex >>> 3);
        int byteBitOffset = etc1BitIndex & 7;
        int mask = 1 << byteBitOffset;
        int etc1Value = SELECTOR_INDEX_TO_ETC1[value & 3];
        int lsb = etc1Value & 1;
        int msb = etc1Value >>> 1;

        etc1Bytes[byteIndex] = (byte) ((etc1Bytes[byteIndex] & ~mask) | (lsb << byteBitOffset));
        etc1Bytes[byteIndex - 2] = (byte) ((etc1Bytes[byteIndex - 2] & ~mask) | (msb << byteBitOffset));
    }

    int getSelector(int x, int y) {
        return (Byte.toUnsignedInt(selectors[y]) >>> (x * 2)) & 3;
    }

    byte[] getEtc1Bytes() {
        return Arrays.copyOf(etc1Bytes, etc1Bytes.length);
    }

    byte[] getSelectorBytes() {
        return Arrays.copyOf(selectors, selectors.length);
    }

    int getLowSelector() {
        return lowSelector;
    }

    int getHighSelector() {
        return highSelector;
    }

    int getUniqueSelectorCount() {
        return uniqueSelectorCount;
    }

    void initFlags() {
        int[] histogram = new int[4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                histogram[getSelector(x, y)]++;
            }
        }

        lowSelector = 3;
        highSelector = 0;
        uniqueSelectorCount = 0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] != 0) {
                uniqueSelectorCount++;
                lowSelector = Math.min(lowSelector, i);
                highSelector = Math.max(highSelector, i);
            }
        }
    }
}
