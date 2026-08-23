package org.ngengine.basis;

import java.util.Arrays;

/**
 * Adaptive symbol model used by the XUASTC arithmetic decoder.
 */
final class XuastcArithmeticDataModel {
    private static final int LENGTH_SHIFT = 15;
    private static final int MAX_SYMBOLS = 2048;
    private static final int MAX_COUNT = 1 << LENGTH_SHIFT;

    int numberOfSymbols;
    int[] symbolFrequencies;
    int[] cumulativeSymbolFrequencies;
    int totalSymbolFrequency;
    int updateInterval;
    int symbolsUntilUpdate;

    XuastcArithmeticDataModel(int numberOfSymbols) {
        this(numberOfSymbols, false);
    }

    XuastcArithmeticDataModel(int numberOfSymbols, boolean fasterUpdate) {
        init(numberOfSymbols, fasterUpdate);
    }

    void init(int symbols, boolean fasterUpdate) {
        if (symbols < 2 || symbols > MAX_SYMBOLS) {
            throw new BasisDecodeException("Invalid arithmetic model symbol count");
        }
        numberOfSymbols = symbols;
        symbolFrequencies = new int[symbols];
        cumulativeSymbolFrequencies = new int[symbols + 1];
        reset(fasterUpdate);
    }

    void reset(boolean fasterUpdate) {
        Arrays.fill(symbolFrequencies, 1);
        totalSymbolFrequency = numberOfSymbols;
        updateInterval = numberOfSymbols;
        symbolsUntilUpdate = 0;
        update();
        if (fasterUpdate) {
            updateInterval = clamp((numberOfSymbols + 7) / 8, 4, (numberOfSymbols + 6) << 3);
            symbolsUntilUpdate = updateInterval;
        }
    }

    void update() {
        while (totalSymbolFrequency >= MAX_COUNT) {
            totalSymbolFrequency = 0;
            for (int i = 0; i < numberOfSymbols; i++) {
                symbolFrequencies[i] = (symbolFrequencies[i] + 1) >>> 1;
                totalSymbolFrequency += symbolFrequencies[i];
            }
        }

        int scale = (int) (0x8000_0000L / totalSymbolFrequency);
        int sum = 0;
        for (int i = 0; i < numberOfSymbols; i++) {
            cumulativeSymbolFrequencies[i] = (int) ((scale * (long) sum) >>> (31 - LENGTH_SHIFT));
            sum += symbolFrequencies[i];
        }
        cumulativeSymbolFrequencies[numberOfSymbols] = MAX_COUNT;
        updateInterval = clamp((5 * updateInterval) >>> 2, 4, (numberOfSymbols + 6) << 3);
        symbolsUntilUpdate = updateInterval;
    }

    static int getLengthShift() {
        return LENGTH_SHIFT;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
