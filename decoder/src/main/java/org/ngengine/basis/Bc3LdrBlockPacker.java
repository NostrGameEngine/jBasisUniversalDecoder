package org.ngengine.basis;

/**
 * BC3/DXT5 block packer used by LDR UASTC/XUASTC targets.
 */
final class Bc3LdrBlockPacker {
    private Bc3LdrBlockPacker() {
    }

    static byte[] packRgba(byte[] rgba, int width, int height) {
        if (rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new BasisDecodeException("RGBA data size does not match image dimensions");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), 16)];
        byte[] rgbaBlock = new byte[64];
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                int blockOffset = (blockY * blocksX + blockX) * 16;
                extractBlock(rgba, width, height, blockX, blockY, rgbaBlock);
                packRgbaBlock(output, blockOffset, rgbaBlock);
            }
        }
        return output;
    }

    static void packRgbaBlock(byte[] output, int offset, byte[] rgbaBlock) {
        Bc4LdrBlockPacker.packRgbaBlockChannel(output, offset, rgbaBlock, 3);
        Bc1LdrBlockPacker.packRgbaBlock(output, offset + 8, rgbaBlock);
    }

    static void packSolidBlock(byte[] output, int offset, int red, int green, int blue, int alpha) {
        byte[] rgbaBlock = new byte[64];
        for (int i = 0; i < 16; i++) {
            int pixelOffset = i * 4;
            rgbaBlock[pixelOffset] = (byte) red;
            rgbaBlock[pixelOffset + 1] = (byte) green;
            rgbaBlock[pixelOffset + 2] = (byte) blue;
            rgbaBlock[pixelOffset + 3] = (byte) alpha;
        }
        Bc4LdrBlockPacker.packRgbaBlockChannel(output, offset, rgbaBlock, 3);
        Bc1LdrBlockPacker.packSolidBlock(output, offset + 8, red, green, blue);
    }

    private static void extractBlock(
            byte[] rgba,
            int width,
            int height,
            int blockX,
            int blockY,
            byte[] block) {
        for (int y = 0; y < 4; y++) {
            int srcY = Math.min(blockY * 4 + y, height - 1);
            for (int x = 0; x < 4; x++) {
                int srcX = Math.min(blockX * 4 + x, width - 1);
                int src = (srcY * width + srcX) * 4;
                int dst = (y * 4 + x) * 4;
                block[dst] = rgba[src];
                block[dst + 1] = rgba[src + 1];
                block[dst + 2] = rgba[src + 2];
                block[dst + 3] = rgba[src + 3];
            }
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
