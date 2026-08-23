package org.ngengine.basis;

/**
 * One decoded XUASTC LDR logical block before final RGBA rasterization.
 */
final class XuastcLdrDecodedBlock {
    private final int blockX;
    private final int blockY;
    private final XuastcLdrRawBlockConfig config;
    private final int[] solidRgba;

    private XuastcLdrDecodedBlock(
            int blockX,
            int blockY,
            XuastcLdrRawBlockConfig config,
            int[] solidRgba) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.config = config;
        this.solidRgba = solidRgba == null ? null : solidRgba.clone();
    }

    static XuastcLdrDecodedBlock config(int blockX, int blockY, XuastcLdrRawBlockConfig config) {
        return new XuastcLdrDecodedBlock(blockX, blockY, config, null);
    }

    static XuastcLdrDecodedBlock solid(int blockX, int blockY, int[] solidRgba) {
        return new XuastcLdrDecodedBlock(blockX, blockY, null, solidRgba);
    }

    XuastcLdrDecodedBlock copyTo(int blockX, int blockY) {
        return new XuastcLdrDecodedBlock(blockX, blockY, config, solidRgba);
    }

    int getBlockX() {
        return blockX;
    }

    int getBlockY() {
        return blockY;
    }

    boolean isSolid() {
        return config == null;
    }

    XuastcLdrRawBlockConfig getConfig() {
        return config;
    }

    int[] getSolidRgba() {
        return solidRgba == null ? null : solidRgba.clone();
    }
}
