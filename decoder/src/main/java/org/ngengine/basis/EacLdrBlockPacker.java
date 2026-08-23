package org.ngengine.basis;

/**
 * ETC2 EAC unsigned R11/RG11 block packer used by LDR UASTC/XUASTC targets.
 */
final class EacLdrBlockPacker {
    private static final int[][] EAC_MODIFIER_TABLE = {
        {-3, -6, -9, -15, 2, 5, 8, 14},
        {-3, -7, -10, -13, 2, 6, 9, 12},
        {-2, -5, -8, -13, 1, 4, 7, 12},
        {-2, -4, -6, -13, 1, 3, 5, 12},
        {-3, -6, -8, -12, 2, 5, 7, 11},
        {-3, -7, -9, -11, 2, 6, 8, 10},
        {-4, -7, -8, -11, 3, 6, 7, 10},
        {-3, -5, -8, -11, 2, 4, 7, 10},
        {-2, -6, -8, -10, 1, 5, 7, 9},
        {-2, -5, -8, -10, 1, 4, 7, 9},
        {-2, -4, -8, -10, 1, 3, 7, 9},
        {-2, -5, -7, -10, 1, 4, 6, 9},
        {-3, -4, -7, -10, 2, 3, 6, 9},
        {-1, -2, -3, -10, 0, 1, 2, 9},
        {-4, -6, -8, -9, 3, 5, 7, 8},
        {-3, -5, -7, -9, 2, 4, 6, 8}
    };
    private static final int[] ETC2_EAC_BIT_OFFSETS = {
        45, 33, 21, 9, 42, 30, 18, 6, 39, 27, 15, 3, 36, 24, 12, 0
    };
    private static final int[] FAST_TABLES = {2, 8, 11, 13};
    private static final int MIN_SELECTOR = 3;
    private static final int MAX_SELECTOR = 7;

    private EacLdrBlockPacker() {
    }

    static byte[] packRgba(byte[] rgba, int width, int height, BasisTranscodeTarget target) {
        if (rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new BasisDecodeException("RGBA data size does not match image dimensions");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int bytesPerBlock = target == BasisTranscodeTarget.ETC2_EAC_RG11 ? 16 : 8;
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), bytesPerBlock)];
        int[] channelPixels = new int[16];
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                int blockOffset = (blockY * blocksX + blockX) * bytesPerBlock;
                extractChannel(rgba, width, height, blockX, blockY, 0, channelPixels);
                packEacBlock(output, blockOffset, channelPixels);
                if (target == BasisTranscodeTarget.ETC2_EAC_RG11) {
                    extractChannel(rgba, width, height, blockX, blockY, 3, channelPixels);
                    packEacBlock(output, blockOffset + 8, channelPixels);
                }
            }
        }
        return output;
    }

    static void packRgbaBlockChannel(byte[] output, int offset, byte[] rgbaBlock, int channel) {
        if (rgbaBlock.length != 64) {
            throw new BasisDecodeException("RGBA block data size mismatch");
        }
        int[] channelPixels = new int[16];
        for (int i = 0; i < channelPixels.length; i++) {
            channelPixels[i] = Byte.toUnsignedInt(rgbaBlock[i * 4 + channel]);
        }
        packEacBlock(output, offset, channelPixels);
    }

    static void packUastcAlphaHintBlock(
            byte[] output,
            int offset,
            byte[] rgbaBlock,
            boolean modeHasAlpha,
            int etc2Hints) {
        if (rgbaBlock.length != 64) {
            throw new BasisDecodeException("RGBA block data size mismatch");
        }
        if (!modeHasAlpha) {
            packConstantAlphaHintBlock(output, offset, 255);
            return;
        }

        int min = 255;
        int max = 0;
        for (int i = 0; i < 16; i++) {
            int alpha = Byte.toUnsignedInt(rgbaBlock[i * 4 + 3]);
            min = Math.min(min, alpha);
            max = Math.max(max, alpha);
        }
        if (min == max) {
            packConstantAlphaHintBlock(output, offset, min);
            return;
        }

        int table = etc2Hints & 15;
        int multiplier = etc2Hints >>> 4;
        if (multiplier < 1) {
            throw new BasisDecodeException("Invalid UASTC ETC2 EAC alpha hint multiplier");
        }

        output[offset + 1] = (byte) ((multiplier << 4) | table);
        float range = EAC_MODIFIER_TABLE[table][MAX_SELECTOR] - EAC_MODIFIER_TABLE[table][MIN_SELECTOR];
        float t = (float) (0 - EAC_MODIFIER_TABLE[table][MIN_SELECTOR]) / range;
        int center = Math.round(lerp(min, max, t));
        output[offset] = (byte) center;

        long selectorBits = 0;
        for (int i = 0; i < 16; i++) {
            int alpha = Byte.toUnsignedInt(rgbaBlock[((i & 3) * 4 + (i >>> 2)) * 4 + 3]);
            int best = Integer.MAX_VALUE;
            for (int selector = 0; selector < 8; selector++) {
                int value = clamp255(center + EAC_MODIFIER_TABLE[table][selector] * multiplier);
                best = Math.min(best, (Math.abs(value - alpha) << 3) | selector);
            }
            selectorBits |= (long) (best & 7) << (45 - i * 3);
        }
        writeSelectorBits(output, offset, selectorBits);
    }

    private static void extractChannel(
            byte[] rgba,
            int width,
            int height,
            int blockX,
            int blockY,
            int channel,
            int[] pixels) {
        for (int y = 0; y < 4; y++) {
            int srcY = Math.min(blockY * 4 + y, height - 1);
            for (int x = 0; x < 4; x++) {
                int srcX = Math.min(blockX * 4 + x, width - 1);
                pixels[y * 4 + x] = Byte.toUnsignedInt(rgba[(srcY * width + srcX) * 4 + channel]);
            }
        }
    }

    private static void packEacBlock(byte[] output, int offset, int[] pixels) {
        int min = 255;
        int max = 0;
        for (int pixel : pixels) {
            min = Math.min(min, pixel);
            max = Math.max(max, pixel);
        }

        if (min == max) {
            packSolidBlock(output, offset, min);
            return;
        }

        int range = max - min;
        if (range <= 5) {
            packNarrowRangeBlock(output, offset, pixels, max);
            return;
        }

        int[] base = new int[FAST_TABLES.length];
        int[] multiplier = new int[FAST_TABLES.length];
        int multiplierOr = 0;
        for (int i = 0; i < FAST_TABLES.length; i++) {
            int table = FAST_TABLES[i];
            float tableRange = EAC_MODIFIER_TABLE[table][MAX_SELECTOR]
                    - EAC_MODIFIER_TABLE[table][MIN_SELECTOR];
            float t = (float) (0 - EAC_MODIFIER_TABLE[table][MIN_SELECTOR]) / tableRange;
            base[i] = clamp255(Math.round(lerp(min, max, t)));
            multiplier[i] = clamp(Math.round(range / tableRange), 1, 15);
            multiplierOr |= multiplier[i];
        }

        int[] totalError = new int[FAST_TABLES.length];
        int[][] selectors = new int[FAST_TABLES.length][16];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            for (int tableIndex = 0; tableIndex < FAST_TABLES.length; tableIndex++) {
                int table = FAST_TABLES[tableIndex];
                int best = Integer.MAX_VALUE;
                if (pixel < 7 || pixel > 248) {
                    for (int selector = 0; selector < 8; selector++) {
                        int value = clamp255(multiplier[tableIndex]
                                * EAC_MODIFIER_TABLE[table][selector] + base[tableIndex]);
                        best = Math.min(best, (Math.abs(value - pixel) << 3) | selector);
                    }
                } else if (multiplierOr == 1) {
                    int delta = base[tableIndex] - pixel;
                    for (int selector = 0; selector < 8; selector++) {
                        int value = EAC_MODIFIER_TABLE[table][selector] + delta;
                        best = Math.min(best, (Math.abs(value) << 3) | selector);
                    }
                } else {
                    int delta = base[tableIndex] - pixel;
                    for (int selector = 0; selector < 8; selector++) {
                        int value = multiplier[tableIndex] * EAC_MODIFIER_TABLE[table][selector] + delta;
                        best = Math.min(best, (Math.abs(value) << 3) | selector);
                    }
                }
                selectors[tableIndex][i] = best & 7;
                int error = best >>> 3;
                totalError[tableIndex] += error * error;
            }
        }

        int bestIndex = 0;
        for (int i = 1; i < totalError.length; i++) {
            if (totalError[i] < totalError[bestIndex]) {
                bestIndex = i;
            }
        }

        output[offset] = (byte) base[bestIndex];
        output[offset + 1] = (byte) ((multiplier[bestIndex] << 4) | FAST_TABLES[bestIndex]);
        writeSelectors(output, offset, selectors[bestIndex]);
    }

    static void packSolidBlock(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = 0x0D;
        writeSolidSelectors(output, offset);
    }

    private static void packConstantAlphaHintBlock(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = 0x1D;
        writeSolidSelectors(output, offset);
    }

    private static void writeSolidSelectors(byte[] output, int offset) {
        output[offset + 2] = (byte) 0x92;
        output[offset + 3] = 0x49;
        output[offset + 4] = 0x24;
        output[offset + 5] = (byte) 0x92;
        output[offset + 6] = 0x49;
        output[offset + 7] = 0x24;
    }

    private static void packNarrowRangeBlock(byte[] output, int offset, int[] pixels, int max) {
        int base = clamp255(max - 2);
        output[offset] = (byte) base;
        output[offset + 1] = 0x1D;

        int selectorBase = base - 3;
        int[] selectors = new int[16];
        int[] selectorMap = {2, 1, 0, 4, 5, 6};
        for (int i = 0; i < pixels.length; i++) {
            selectors[i] = selectorMap[pixels[i] - selectorBase];
        }
        writeSelectors(output, offset, selectors);
    }

    private static void writeSelectors(byte[] output, int offset, int[] selectors) {
        long selectorBits = 0;
        for (int i = 0; i < selectors.length; i++) {
            selectorBits |= (long) selectors[i] << ETC2_EAC_BIT_OFFSETS[i];
        }
        writeSelectorBits(output, offset, selectorBits);
    }

    private static void writeSelectorBits(byte[] output, int offset, long selectorBits) {
        for (int i = 0; i < 6; i++) {
            output[offset + 2 + i] = (byte) (selectorBits >>> (40 - i * 8));
        }
    }

    private static float lerp(float low, float high, float t) {
        return low + (high - low) * t;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp255(int value) {
        return clamp(value, 0, 255);
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
