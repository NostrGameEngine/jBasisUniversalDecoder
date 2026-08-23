package org.ngengine.basis;

/**
 * Rasterizes decoded XUASTC LDR blocks into RGBA8 pixels.
 */
final class XuastcLdrImageDecoder {
    private static final int RGBA_COMPONENTS = 4;

    private XuastcLdrImageDecoder() {
    }

    static byte[] decodeRgba(byte[] data, int offset, int length) {
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        XuastcLdrDecodedBlock[] blocks = XuastcLdrRawBlockConfigReader.readDecodedBlocks(
                data,
                offset,
                length);
        int blocksX = divideRoundUp(header.getWidth(), header.getBlockWidth());
        int blocksY = divideRoundUp(header.getHeight(), header.getBlockHeight());
        int expectedBlocks = blocksX * blocksY;
        if (blocks.length != expectedBlocks) {
            throw new BasisDecodeException("XUASTC decoded block count mismatch");
        }

        byte[] rgba = new byte[header.getWidth() * header.getHeight() * RGBA_COMPONENTS];
        for (XuastcLdrDecodedBlock block : blocks) {
            byte[] blockRgba = block.isSolid()
                    ? solidBlock(header, block.getSolidRgba())
                    : XuastcLdrBlockDecoder.decodeRgbaBlock(header, block.getConfig());
            copyBlock(header, block, blockRgba, rgba);
        }
        return rgba;
    }

    static DecodedImage decodeRgbaPaddedToVisibleHeight(byte[] data, int offset, int length) {
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        XuastcLdrDecodedBlock[] blocks = XuastcLdrRawBlockConfigReader.readDecodedBlocks(
                data,
                offset,
                length);
        int blocksX = divideRoundUp(header.getWidth(), header.getBlockWidth());
        int blocksY = divideRoundUp(header.getHeight(), header.getBlockHeight());
        int expectedBlocks = blocksX * blocksY;
        if (blocks.length != expectedBlocks) {
            throw new BasisDecodeException("XUASTC decoded block count mismatch");
        }

        int paddedWidth = blocksX * header.getBlockWidth();
        byte[] rgba = new byte[paddedWidth * header.getHeight() * RGBA_COMPONENTS];
        for (XuastcLdrDecodedBlock block : blocks) {
            byte[] blockRgba = block.isSolid()
                    ? solidBlock(header, block.getSolidRgba())
                    : XuastcLdrBlockDecoder.decodeRgbaBlock(header, block.getConfig());
            copyBlockPaddedToVisibleHeight(header, block, blockRgba, rgba, paddedWidth);
        }
        return new DecodedImage(rgba, paddedWidth, header.getHeight(), header.getWidth(), header.getHeight());
    }

    static DecodedImage decodeRgbaPaddedToBlockSize(byte[] data, int offset, int length) {
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        XuastcLdrDecodedBlock[] blocks = XuastcLdrRawBlockConfigReader.readDecodedBlocks(
                data,
                offset,
                length);
        int blocksX = divideRoundUp(header.getWidth(), header.getBlockWidth());
        int blocksY = divideRoundUp(header.getHeight(), header.getBlockHeight());
        int expectedBlocks = blocksX * blocksY;
        if (blocks.length != expectedBlocks) {
            throw new BasisDecodeException("XUASTC decoded block count mismatch");
        }

        int paddedWidth = blocksX * header.getBlockWidth();
        int paddedHeight = blocksY * header.getBlockHeight();
        byte[] rgba = new byte[paddedWidth * paddedHeight * RGBA_COMPONENTS];
        for (XuastcLdrDecodedBlock block : blocks) {
            byte[] blockRgba = block.isSolid()
                    ? solidBlock(header, block.getSolidRgba())
                    : XuastcLdrBlockDecoder.decodeRgbaBlock(header, block.getConfig());
            copyBlockPaddedToBlockSize(header, block, blockRgba, rgba, paddedWidth);
        }
        return new DecodedImage(rgba, paddedWidth, paddedHeight, header.getWidth(), header.getHeight());
    }

    private static byte[] solidBlock(XuastcLdrImageHeader header, int[] solidRgba) {
        byte[] block = new byte[header.getBlockWidth() * header.getBlockHeight() * RGBA_COMPONENTS];
        for (int i = 0; i < block.length; i += RGBA_COMPONENTS) {
            block[i] = (byte) solidRgba[0];
            block[i + 1] = (byte) solidRgba[1];
            block[i + 2] = (byte) solidRgba[2];
            block[i + 3] = (byte) solidRgba[3];
        }
        return block;
    }

    private static void copyBlock(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock block,
            byte[] blockRgba,
            byte[] imageRgba) {
        int blockWidth = header.getBlockWidth();
        int blockHeight = header.getBlockHeight();
        int baseX = block.getBlockX() * blockWidth;
        int baseY = block.getBlockY() * blockHeight;
        int copyWidth = Math.min(blockWidth, header.getWidth() - baseX);
        int copyHeight = Math.min(blockHeight, header.getHeight() - baseY);
        for (int y = 0; y < copyHeight; y++) {
            System.arraycopy(
                    blockRgba,
                    y * blockWidth * RGBA_COMPONENTS,
                    imageRgba,
                    ((baseY + y) * header.getWidth() + baseX) * RGBA_COMPONENTS,
                    copyWidth * RGBA_COMPONENTS);
        }
    }

    private static void copyBlockPaddedToVisibleHeight(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock block,
            byte[] blockRgba,
            byte[] imageRgba,
            int paddedWidth) {
        int blockWidth = header.getBlockWidth();
        int blockHeight = header.getBlockHeight();
        int baseX = block.getBlockX() * blockWidth;
        int baseY = block.getBlockY() * blockHeight;
        int copyHeight = Math.min(blockHeight, header.getHeight() - baseY);
        for (int y = 0; y < copyHeight; y++) {
            System.arraycopy(
                    blockRgba,
                    y * blockWidth * RGBA_COMPONENTS,
                    imageRgba,
                    ((baseY + y) * paddedWidth + baseX) * RGBA_COMPONENTS,
                    blockWidth * RGBA_COMPONENTS);
        }
    }

    private static void copyBlockPaddedToBlockSize(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock block,
            byte[] blockRgba,
            byte[] imageRgba,
            int paddedWidth) {
        int blockWidth = header.getBlockWidth();
        int blockHeight = header.getBlockHeight();
        int baseX = block.getBlockX() * blockWidth;
        int baseY = block.getBlockY() * blockHeight;
        for (int y = 0; y < blockHeight; y++) {
            System.arraycopy(
                    blockRgba,
                    y * blockWidth * RGBA_COMPONENTS,
                    imageRgba,
                    ((baseY + y) * paddedWidth + baseX) * RGBA_COMPONENTS,
                    blockWidth * RGBA_COMPONENTS);
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    static final class DecodedImage {
        private final byte[] rgba;
        private final int strideWidth;
        private final int sourceHeight;
        private final int visibleWidth;
        private final int visibleHeight;

        DecodedImage(byte[] rgba, int strideWidth, int sourceHeight, int visibleWidth, int visibleHeight) {
            this.rgba = rgba;
            this.strideWidth = strideWidth;
            this.sourceHeight = sourceHeight;
            this.visibleWidth = visibleWidth;
            this.visibleHeight = visibleHeight;
        }

        byte[] getRgba() {
            return rgba;
        }

        int getStrideWidth() {
            return strideWidth;
        }

        int getSourceHeight() {
            return sourceHeight;
        }

        int getVisibleWidth() {
            return visibleWidth;
        }

        int getVisibleHeight() {
            return visibleHeight;
        }
    }
}
