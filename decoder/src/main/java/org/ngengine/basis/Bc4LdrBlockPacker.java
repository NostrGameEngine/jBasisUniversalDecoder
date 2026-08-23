package org.ngengine.basis;

/**
 * BC4/BC5 unsigned block packer used by LDR UASTC/XUASTC targets.
 */
final class Bc4LdrBlockPacker {
    private static final int[] SELECTOR_TRANSLATION = {1, 7, 6, 5, 4, 3, 2, 0};

    private Bc4LdrBlockPacker() {
    }

    static byte[] packRgba(byte[] rgba, int width, int height, BasisTranscodeTarget target) {
        if (rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new BasisDecodeException("RGBA data size does not match image dimensions");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int bytesPerBlock = target == BasisTranscodeTarget.BC5 ? 16 : 8;
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), bytesPerBlock)];
        int[] channelPixels = new int[16];
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                int blockOffset = (blockY * blocksX + blockX) * bytesPerBlock;
                extractChannel(rgba, width, height, blockX, blockY, 0, channelPixels);
                packBc4Block(output, blockOffset, channelPixels);
                if (target == BasisTranscodeTarget.BC5) {
                    extractChannel(rgba, width, height, blockX, blockY, 3, channelPixels);
                    packBc4Block(output, blockOffset + 8, channelPixels);
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
        packBc4Block(output, offset, channelPixels);
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

    private static void packBc4Block(byte[] output, int offset, int[] pixels) {
        int min = 255;
        int max = 0;
        for (int pixel : pixels) {
            min = Math.min(min, pixel);
            max = Math.max(max, pixel);
        }

        output[offset] = (byte) max;
        output[offset + 1] = (byte) min;
        if (min == max) {
            for (int i = 2; i < 8; i++) {
                output[offset + i] = 0;
            }
            return;
        }

        int delta = max - min;
        int[] thresholds = {
            delta * 13,
            delta * 11,
            delta * 9,
            delta * 7,
            delta * 5,
            delta * 3,
            delta
        };
        int bias = 4 - min * 14;
        long selectors = 0L;
        for (int i = 0; i < pixels.length; i++) {
            int value = pixels[i] * 14 + bias;
            int thresholdCount = 0;
            for (int threshold : thresholds) {
                if (value >= threshold) {
                    thresholdCount++;
                }
            }
            selectors |= (long) SELECTOR_TRANSLATION[thresholdCount] << (i * 3);
        }
        for (int i = 0; i < 6; i++) {
            output[offset + 2 + i] = (byte) (selectors >>> (i * 8));
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
