package org.ngengine.basis;

import java.util.Arrays;

/**
 * Lazy ETC1S-to-BC7 mode 5 conversion tables.
 */
final class Bc7Mode5Tables {
    private static final int[][] SELECTOR_RANGES = {
        {0, 3},
        {1, 3},
        {0, 2},
        {1, 2},
        {2, 3},
        {0, 1}
    };
    private static final int[][] SELECTOR_MAPPINGS = {
        {0, 0, 1, 1},
        {0, 0, 1, 2},
        {0, 0, 1, 3},
        {0, 0, 2, 3},
        {0, 1, 1, 1},
        {0, 1, 2, 2},
        {0, 1, 2, 3},
        {0, 2, 3, 3},
        {1, 2, 2, 2},
        {1, 2, 3, 3}
    };
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
    private static final int COLOR_ENTRY_COUNT =
            8 * 32 * SELECTOR_RANGES.length * SELECTOR_MAPPINGS.length;
    private static final int ALPHA_ENTRY_COUNT = 8 * 32 * SELECTOR_RANGES.length;
    private static final int[] COLOR_CACHE = new int[COLOR_ENTRY_COUNT];
    private static final int[] ALPHA_CACHE = new int[ALPHA_ENTRY_COUNT];

    static {
        Arrays.fill(COLOR_CACHE, -1);
        Arrays.fill(ALPHA_CACHE, -1);
    }

    private Bc7Mode5Tables() {
    }

    static int color(int index) {
        int cached = COLOR_CACHE[index];
        if (cached >= 0) {
            return cached;
        }
        int computed = computeColor(index);
        COLOR_CACHE[index] = computed;
        return computed;
    }

    static int alpha(int index) {
        int cached = ALPHA_CACHE[index];
        if (cached >= 0) {
            return cached;
        }
        int computed = computeAlpha(index);
        ALPHA_CACHE[index] = computed;
        return computed;
    }

    private static int computeColor(int index) {
        int mapping = index % SELECTOR_MAPPINGS.length;
        int rangeIndex = (index / SELECTOR_MAPPINGS.length) % SELECTOR_RANGES.length;
        int colorBase = index / (SELECTOR_MAPPINGS.length * SELECTOR_RANGES.length);
        int base5 = colorBase % 32;
        int intensity = colorBase / 32;
        int[] blockColors = scalarBlockColors(base5, intensity);
        int lowSelector = SELECTOR_RANGES[rangeIndex][0];
        int highSelector = SELECTOR_RANGES[rangeIndex][1];
        int bestLow = 0;
        int bestHigh = 0;
        int bestError = Integer.MAX_VALUE;

        for (int high = 0; high <= 127; high++) {
            for (int low = 0; low <= 127; low++) {
                int[] colors = bc7Mode5Colors(low, high);
                int totalError = 0;
                for (int selector = lowSelector; selector <= highSelector; selector++) {
                    int error = blockColors[selector] - colors[SELECTOR_MAPPINGS[mapping][selector]];
                    int scale = colorErrorScale(intensity, lowSelector, highSelector, selector);
                    totalError += error * error * scale;
                }
                if (totalError < bestError) {
                    bestError = totalError;
                    bestLow = low;
                    bestHigh = high;
                }
            }
        }
        return bestLow | (bestHigh << 8) | (Math.min(bestError, 0xFFFF) << 16);
    }

    private static int computeAlpha(int index) {
        int rangeIndex = index % SELECTOR_RANGES.length;
        int colorBase = index / SELECTOR_RANGES.length;
        int base5 = colorBase % 32;
        int intensity = colorBase / 32;
        int[] blockColors = scalarBlockColors(base5, intensity);
        int lowSelector = SELECTOR_RANGES[rangeIndex][0];
        int highSelector = SELECTOR_RANGES[rangeIndex][1];
        int bestLow = 0;
        int bestHigh = 0;
        int bestTransform = 0;
        int bestError = Integer.MAX_VALUE;

        for (int high = 0; high <= 255; high++) {
            for (int low = 0; low <= 255; low++) {
                int[] colors = bc7AlphaColors(low, high);
                int totalError = 0;
                int transform = 0;
                for (int selector = lowSelector; selector <= highSelector; selector++) {
                    int bestMappingError = Integer.MAX_VALUE;
                    int bestSelector = 0;
                    for (int candidate = 0; candidate < 4; candidate++) {
                        int error = blockColors[selector] - colors[candidate];
                        int scaledError = error * error
                                * colorErrorScale(intensity, lowSelector, highSelector, selector);
                        if (scaledError < bestMappingError) {
                            bestMappingError = scaledError;
                            bestSelector = candidate;
                        }
                    }
                    totalError += bestMappingError;
                    transform |= bestSelector << (selector * 2);
                }
                if (totalError < bestError) {
                    bestError = totalError;
                    bestLow = low;
                    bestHigh = high;
                    bestTransform = transform;
                }
            }
        }
        return bestLow | (bestHigh << 8) | (bestTransform << 16);
    }

    private static int[] scalarBlockColors(int base5, int intensity) {
        int base = expand5(base5);
        int[] modifiers = ETC1_INTENSITY_TABLES[intensity];
        int[] colors = new int[4];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = clamp255(base + modifiers[i]);
        }
        return colors;
    }

    private static int[] bc7Mode5Colors(int low, int high) {
        int[] colors = new int[4];
        colors[0] = expand7(low);
        colors[3] = expand7(high);
        colors[1] = interpolate(colors[0], colors[3], 21);
        colors[2] = interpolate(colors[0], colors[3], 43);
        return colors;
    }

    private static int[] bc7AlphaColors(int low, int high) {
        int[] colors = new int[4];
        colors[0] = low;
        colors[3] = high;
        colors[1] = interpolate(colors[0], colors[3], 21);
        colors[2] = interpolate(colors[0], colors[3], 43);
        return colors;
    }

    private static int interpolate(int low, int high, int weight) {
        return (low * (64 - weight) + high * weight + 32) / 64;
    }

    private static int colorErrorScale(int intensity, int lowSelector, int highSelector, int selector) {
        if (intensity == 7 && lowSelector == 0 && highSelector == 3
                && (selector == 0 || selector == 3)) {
            return 5;
        }
        return 1;
    }

    private static int expand5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int expand7(int value) {
        return (value << 1) | (value >>> 6);
    }

    private static int clamp255(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }
}
