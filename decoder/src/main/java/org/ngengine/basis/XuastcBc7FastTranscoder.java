package org.ngengine.basis;

/**
 * Fast XUASTC LDR to BC7 paths matching the reference direct transcode cases.
 */
final class XuastcBc7FastTranscoder {
    private static final int RGBA_COMPONENTS = 4;
    private static final int BC7_BYTES_PER_BLOCK = 16;

    private XuastcBc7FastTranscoder() {
    }

    static byte[] packImage(byte[] data, int offset, int length) {
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        XuastcLdrDecodedBlock[] blocks = XuastcLdrRawBlockConfigReader.readDecodedBlocks(
                data,
                offset,
                length);
        int sourceBlocksX = divideRoundUp(header.getWidth(), header.getBlockWidth());
        int sourceBlocksY = divideRoundUp(header.getHeight(), header.getBlockHeight());
        if (blocks.length != sourceBlocksX * sourceBlocksY) {
            throw new BasisDecodeException("XUASTC decoded block count mismatch");
        }
        XuastcLdrDecodedBlock[][] grid = toBlockGrid(blocks, sourceBlocksX, sourceBlocksY);
        if (header.getBlockWidth() == 4 && header.getBlockHeight() == 4) {
            return pack4x4Image(header, grid);
        }
        if (header.getBlockWidth() == 8 && header.getBlockHeight() == 6) {
            return pack8x6Image(header, grid);
        }
        if (header.getBlockWidth() == 6 && header.getBlockHeight() == 6) {
            return pack6x6Image(header, grid);
        }
        throw new BasisDecodeException("XUASTC fast BC7 transcode requires 4x4, 6x6, or 8x6 blocks");
    }

    private static byte[] pack4x4Image(XuastcLdrImageHeader header, XuastcLdrDecodedBlock[][] blocks) {
        int blocksX = divideRoundUp(header.getWidth(), 4);
        int blocksY = divideRoundUp(header.getHeight(), 4);
        byte[] output = new byte[blocksX * blocksY * BC7_BYTES_PER_BLOCK];
        for (XuastcLdrDecodedBlock[] row : blocks) {
            for (XuastcLdrDecodedBlock block : row) {
                int outputOffset = (block.getBlockY() * blocksX + block.getBlockX()) * BC7_BYTES_PER_BLOCK;
                if (block.isSolid()) {
                    packSolid(block, output, outputOffset);
                } else if (isFastSingleSubset(block.getConfig())) {
                    packSingleSubsetBlock(block.getConfig(), 4, 4, 0, 0, output, outputOffset);
                } else {
                    packDecoded4x4(header, block, output, outputOffset);
                }
            }
        }
        return output;
    }

    private static byte[] pack8x6Image(XuastcLdrImageHeader header, XuastcLdrDecodedBlock[][] blocks) {
        int sourceBlocksX = blocks[0].length;
        int sourceBlocksY = blocks.length;
        int dstBlocksX = divideRoundUp(header.getWidth(), 4);
        int dstBlocksY = divideRoundUp(header.getHeight(), 4);
        byte[] output = new byte[dstBlocksX * dstBlocksY * BC7_BYTES_PER_BLOCK];
        for (int sourceY = 0; sourceY < sourceBlocksY; sourceY += 2) {
            int rows = Math.min(2, sourceBlocksY - sourceY);
            int totalDstRows = (rows * 6 + 3) >>> 2;
            int dstBaseY = (sourceY * 6) >>> 2;
            for (int sourceX = 0; sourceX < sourceBlocksX; sourceX++) {
                XuastcLdrDecodedBlock[] group = {
                    blocks[sourceY][sourceX],
                    blocks[Math.min(sourceY + 1, sourceBlocksY - 1)][sourceX]
                };
                if (pack8x6AllSolid(group, output, dstBlocksX, dstBlocksY, dstBaseY, sourceX, totalDstRows)) {
                    continue;
                }
                int[][] weights = new int[2][];
                int[][] endpoints = new int[2][];
                int[][] decoded = new int[2][];
                for (int i = 0; i < 2; i++) {
                    endpoints[i] = endpoints(group[i]);
                    weights[i] = group[i].isSolid() ? null : upsampledWeights(group[i].getConfig(), 8, 6);
                }
                for (int dy = 0; dy < totalDstRows; dy++) {
                    int dstY = dstBaseY + dy;
                    if (dstY >= dstBlocksY) {
                        break;
                    }
                    for (int dx = 0; dx < 2; dx++) {
                        int dstX = sourceX * 2 + dx;
                        if (dstX >= dstBlocksX) {
                            break;
                        }
                        int outputOffset = (dstY * dstBlocksX + dstX) * BC7_BYTES_PER_BLOCK;
                        int topBlockY = (dy * 4) / 6;
                        int bottomBlockY = (dy * 4 + 3) / 6;
                        XuastcLdrDecodedBlock top = group[topBlockY];
                        XuastcLdrDecodedBlock bottom = group[bottomBlockY];
                        if (isComplex(top) || isComplex(bottom)) {
                            pack8x6DecodedWindow(header, group, decoded, dx, dy, output, outputOffset);
                        } else if (topBlockY == bottomBlockY) {
                            if (top.isSolid()) {
                                packSolid(top, output, outputOffset);
                            } else {
                                Bc7Mode6RgbBlockPacker.packAstcSingleSubsetRgbaBlock(
                                        endpoints[topBlockY], weights[topBlockY],
                                        dx * 4, (dy * 4) % 6, 8, 6, output, outputOffset);
                            }
                        } else if (sameSingleSubsetEndpoints(top, bottom, 0)) {
                            Bc7Mode6RgbBlockPacker.packAstcSameSingleSubsetEndpoints(
                                    endpoints[topBlockY], weights[topBlockY],
                                    endpoints[bottomBlockY], weights[bottomBlockY],
                                    dx, dy, 8, 6, output, outputOffset);
                        } else if (!hasAlpha(top) && !hasAlpha(bottom)) {
                            Bc7Mode6RgbBlockPacker.packAstcTwoSubsetDifferentEndpoints8x6Hq(
                                    endpoints[topBlockY], weights[topBlockY], top.isSolid(),
                                    endpoints[bottomBlockY], weights[bottomBlockY], bottom.isSolid(),
                                    dx, header.isSrgbDecodeProfile(), output, outputOffset);
                        } else {
                            pack8x6DecodedWindow(header, group, decoded, dx, dy, output, outputOffset);
                        }
                    }
                }
            }
        }
        return output;
    }

    private static byte[] pack6x6Image(XuastcLdrImageHeader header, XuastcLdrDecodedBlock[][] blocks) {
        int sourceBlocksX = blocks[0].length;
        int sourceBlocksY = blocks.length;
        int dstBlocksX = divideRoundUp(header.getWidth(), 4);
        int dstBlocksY = divideRoundUp(header.getHeight(), 4);
        byte[] output = new byte[dstBlocksX * dstBlocksY * BC7_BYTES_PER_BLOCK];
        for (int sourceY = 0; sourceY < sourceBlocksY; sourceY += 2) {
            int rows = Math.min(2, sourceBlocksY - sourceY);
            int totalDstRows = (rows * 6 + 3) >>> 2;
            int dstBaseY = (sourceY * 6) >>> 2;
            for (int sourceX = 0; sourceX < sourceBlocksX; sourceX += 2) {
                XuastcLdrDecodedBlock[][] group = source6x6Group(blocks, sourceX, sourceY);
                if (pack6x6AllSolid(group, output, dstBlocksX, dstBlocksY, dstBaseY, sourceX, totalDstRows)) {
                    continue;
                }
                int hardBlocks = countComplex(group);
                int[][][] weights = weights6x6(group);
                int[][][] endpoints = endpoints6x6(group);
                int[][] decoded = new int[4][];
                for (int dy = 0; dy < totalDstRows; dy++) {
                    int dstY = dstBaseY + dy;
                    if (dstY >= dstBlocksY) {
                        break;
                    }
                    for (int dx = 0; dx < 3; dx++) {
                        int dstX = ((sourceX * 6) >>> 2) + dx;
                        if (dstX >= dstBlocksX) {
                            break;
                        }
                        int outputOffset = (dstY * dstBlocksX + dstX) * BC7_BYTES_PER_BLOCK;
                        if (hardBlocks == 4 || (dx == 1 && dy == 1)) {
                            if (dx == 1 && dy == 1 && hardBlocks != 4) {
                                pack6x6CenterBlock(
                                        header, group, endpoints, weights, decoded, output,
                                        dstBlocksX, dstBlocksY, (sourceX * 6) >>> 2, dstBaseY);
                            } else {
                                pack6x6DecodedWindow(header, group, decoded, dx, dy, output, outputOffset);
                            }
                        } else {
                            pack6x6NonCenterBlock(
                                    header, group, endpoints, weights, decoded, dx, dy, output, outputOffset);
                        }
                    }
                }
            }
        }
        return output;
    }

    private static XuastcLdrDecodedBlock[][] source6x6Group(
            XuastcLdrDecodedBlock[][] blocks,
            int sourceX,
            int sourceY) {
        int maxX = blocks[0].length - 1;
        int maxY = blocks.length - 1;
        XuastcLdrDecodedBlock[][] group = new XuastcLdrDecodedBlock[2][2];
        group[0][0] = blocks[sourceY][sourceX];
        group[1][0] = blocks[sourceY][Math.min(sourceX + 1, maxX)];
        group[0][1] = blocks[Math.min(sourceY + 1, maxY)][sourceX];
        group[1][1] = blocks[Math.min(sourceY + 1, maxY)][Math.min(sourceX + 1, maxX)];
        return group;
    }

    private static boolean isFastSingleSubset(XuastcLdrRawBlockConfig config) {
        XuastcTrialMode mode = config.getTrialMode();
        XuastcLdrWeightGrid weightGrid = config.getWeightGrid();
        return mode.getNumberOfPartitions() == 1
                && weightGrid.getPlaneCount() == 1
                && weightGrid.getGridWidth() <= 4
                && weightGrid.getGridHeight() <= 4;
    }

    private static void packSingleSubsetBlock(
            XuastcLdrRawBlockConfig config,
            int blockWidth,
            int blockHeight,
            int weightOffsetX,
            int weightOffsetY,
            byte[] output,
            int outputOffset) {
        int[] endpoints = endpoints(XuastcLdrDecodedBlock.config(
                config.getBlockX(),
                config.getBlockY(),
                config));
        int[] weights = upsampledWeights(config, blockWidth, blockHeight);
        Bc7Mode6RgbBlockPacker.packAstcSingleSubsetRgbaBlock(
                endpoints,
                weights,
                weightOffsetX,
                weightOffsetY,
                blockWidth,
                blockHeight,
                output,
                outputOffset);
    }

    private static void pack6x6NonCenterBlock(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[][] group,
            int[][][] endpoints,
            int[][][] weights,
            int[][] decoded,
            int dx,
            int dy,
            byte[] output,
            int outputOffset) {
        int topX = (dx * 4) / 6;
        int topY = (dy * 4) / 6;
        int bottomX = (dx * 4 + 3) / 6;
        int bottomY = (dy * 4 + 3) / 6;
        XuastcLdrDecodedBlock first = group[topX][topY];
        XuastcLdrDecodedBlock second = group[bottomX][bottomY];
        if (isComplex(first) || isComplex(second)) {
            pack6x6DecodedWindow(header, group, decoded, dx, dy, output, outputOffset);
        } else if (topX == bottomX && topY == bottomY) {
            if (first.isSolid()) {
                packSolid(first, output, outputOffset);
            } else {
                Bc7Mode6RgbBlockPacker.packAstcSingleSubsetRgbaBlock(
                        endpoints[topX][topY], weights[topX][topY],
                        (dx * 4) % 6, (dy * 4) % 6, 6, 6, output, outputOffset);
            }
        } else if (sameSolidColors(first, second, 0)) {
            packSolid(first, output, outputOffset);
        } else if (sameSingleSubsetEndpoints(first, second, 1)) {
            Bc7Mode6RgbBlockPacker.packAstcSameSingleSubsetEndpoints(
                    endpoints[topX][topY], weights[topX][topY],
                    endpoints[bottomX][bottomY], weights[bottomX][bottomY],
                    dx, dy, 6, 6, output, outputOffset);
        } else if (!hasAlpha(first) && !hasAlpha(second)) {
            Bc7Mode6RgbBlockPacker.packAstcTwoSubsetDifferentEndpoints6x6(
                    endpoints[topX][topY], weights[topX][topY], first.isSolid(),
                    endpoints[bottomX][bottomY], weights[bottomX][bottomY], second.isSolid(),
                    dx, dy, output, outputOffset);
        } else {
            pack6x6DecodedWindow(header, group, decoded, dx, dy, output, outputOffset);
        }
    }

    private static void pack6x6CenterBlock(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[][] group,
            int[][][] endpoints,
            int[][][] weights,
            int[][] decoded,
            byte[] output,
            int dstBlocksX,
            int dstBlocksY,
            int dstBaseX,
            int dstBaseY) {
        int dstX = dstBaseX + 1;
        int dstY = dstBaseY + 1;
        if (dstX >= dstBlocksX || dstY >= dstBlocksY) {
            return;
        }
        int outputOffset = (dstY * dstBlocksX + dstX) * BC7_BYTES_PER_BLOCK;
        if (countComplex(group) == 0) {
            boolean top = (sameSolidColors(group[0][0], group[1][0], 1)
                    || sameSingleSubsetEndpoints(group[0][0], group[1][0], 1))
                    && !hasAlpha(group[0][0]) && !hasAlpha(group[1][0]);
            boolean bottom = (sameSolidColors(group[0][1], group[1][1], 1)
                    || sameSingleSubsetEndpoints(group[0][1], group[1][1], 1))
                    && !hasAlpha(group[0][1]) && !hasAlpha(group[1][1]);
            boolean left = (sameSolidColors(group[0][0], group[0][1], 1)
                    || sameSingleSubsetEndpoints(group[0][0], group[0][1], 1))
                    && !hasAlpha(group[0][0]) && !hasAlpha(group[0][1]);
            boolean right = (sameSolidColors(group[1][0], group[1][1], 1)
                    || sameSingleSubsetEndpoints(group[1][0], group[1][1], 1))
                    && !hasAlpha(group[1][0]) && !hasAlpha(group[1][1]);
            if (top && bottom) {
                Bc7Mode6RgbBlockPacker.packAstc6x6MiddleTwoSubsets(
                        endpoints, weights, solidFlags(group), false, output, outputOffset);
                return;
            }
            if (left && right) {
                Bc7Mode6RgbBlockPacker.packAstc6x6MiddleTwoSubsets(
                        endpoints, weights, solidFlags(group), true, output, outputOffset);
                return;
            }
        }
        pack6x6DecodedWindow(header, group, decoded, 1, 1, output, outputOffset);
    }

    private static boolean pack8x6AllSolid(
            XuastcLdrDecodedBlock[] group,
            byte[] output,
            int dstBlocksX,
            int dstBlocksY,
            int dstBaseY,
            int sourceX,
            int totalDstRows) {
        if (!sameSolidColors(group[0], group[1], 0)) {
            return false;
        }
        byte[] block = new byte[BC7_BYTES_PER_BLOCK];
        packSolid(group[0], block, 0);
        for (int dy = 0; dy < totalDstRows; dy++) {
            int dstY = dstBaseY + dy;
            if (dstY >= dstBlocksY) {
                break;
            }
            for (int dx = 0; dx < 2; dx++) {
                int dstX = sourceX * 2 + dx;
                if (dstX >= dstBlocksX) {
                    break;
                }
                int outputOffset = (dstY * dstBlocksX + dstX) * BC7_BYTES_PER_BLOCK;
                System.arraycopy(block, 0, output, outputOffset, block.length);
            }
        }
        return true;
    }

    private static boolean pack6x6AllSolid(
            XuastcLdrDecodedBlock[][] group,
            byte[] output,
            int dstBlocksX,
            int dstBlocksY,
            int dstBaseY,
            int sourceX,
            int totalDstRows) {
        if (!sameSolidColors(group[0][0], group[1][0], 0)
                || !sameSolidColors(group[0][0], group[0][1], 0)
                || !sameSolidColors(group[0][0], group[1][1], 0)) {
            return false;
        }
        byte[] block = new byte[BC7_BYTES_PER_BLOCK];
        packSolid(group[0][0], block, 0);
        for (int dy = 0; dy < totalDstRows; dy++) {
            int dstY = dstBaseY + dy;
            if (dstY >= dstBlocksY) {
                break;
            }
            for (int dx = 0; dx < 3; dx++) {
                int dstX = ((sourceX * 6) >>> 2) + dx;
                if (dstX >= dstBlocksX) {
                    break;
                }
                int outputOffset = (dstY * dstBlocksX + dstX) * BC7_BYTES_PER_BLOCK;
                System.arraycopy(block, 0, output, outputOffset, block.length);
            }
        }
        return true;
    }

    private static void pack8x6DecodedWindow(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[] group,
            int[][] decoded,
            int dx,
            int dy,
            byte[] output,
            int outputOffset) {
        int[] rgba = new int[16 * RGBA_COMPONENTS];
        for (int y = 0; y < 4; y++) {
            int sourceY = dy * 4 + y;
            int[] source = decoded8x6Block(header, group, decoded, sourceY / 6);
            for (int x = 0; x < 4; x++) {
                copyPixel(source, dx * 4 + x + (sourceY % 6) * 8, rgba, x + y * 4);
            }
        }
        packRgbaBlock(header, rgba, output, outputOffset);
    }

    private static void pack6x6DecodedWindow(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[][] group,
            int[][] decoded,
            int dx,
            int dy,
            byte[] output,
            int outputOffset) {
        int[] rgba = new int[16 * RGBA_COMPONENTS];
        for (int y = 0; y < 4; y++) {
            int sourceY = dy * 4 + y;
            for (int x = 0; x < 4; x++) {
                int sourceX = dx * 4 + x;
                int[] source = decoded6x6Block(header, group, decoded, sourceX / 6, sourceY / 6);
                copyPixel(source, (sourceX % 6) + (sourceY % 6) * 6, rgba, x + y * 4);
            }
        }
        packRgbaBlock(header, rgba, output, outputOffset);
    }

    private static void packDecoded4x4(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock block,
            byte[] output,
            int outputOffset) {
        packRgbaBlock(header, decodeLogicalBlock(header, block, 4, 4), output, outputOffset);
    }

    private static void packRgbaBlock(
            XuastcLdrImageHeader header,
            int[] rgba,
            byte[] output,
            int outputOffset) {
        if (header.hasAlpha()) {
            Bc7Mode6RgbBlockPacker.packAutoRgbaBlock(rgba, output, outputOffset);
        } else {
            Bc7Mode6RgbBlockPacker.packAutoRgbBlock(rgba, output, outputOffset);
        }
    }

    private static int[] decoded8x6Block(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[] group,
            int[][] decoded,
            int sourceBlockY) {
        if (decoded[sourceBlockY] == null) {
            decoded[sourceBlockY] = decodeLogicalBlock(header, group[sourceBlockY], 8, 6);
        }
        return decoded[sourceBlockY];
    }

    private static int[] decoded6x6Block(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[][] group,
            int[][] decoded,
            int sourceBlockX,
            int sourceBlockY) {
        int index = sourceBlockX + sourceBlockY * 2;
        if (decoded[index] == null) {
            decoded[index] = decodeLogicalBlock(header, group[sourceBlockX][sourceBlockY], 6, 6);
        }
        return decoded[index];
    }

    private static int[] decodeLogicalBlock(
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock block,
            int width,
            int height) {
        if (block.isSolid()) {
            return solidBlock(block, width, height);
        }
        return toIntRgba(XuastcLdrBlockDecoder.decodeRgbaBlock(header, block.getConfig()));
    }

    private static int[] solidBlock(XuastcLdrDecodedBlock block, int width, int height) {
        int[] solid = block.getSolidRgba();
        int[] rgba = new int[width * height * RGBA_COMPONENTS];
        for (int i = 0; i < width * height; i++) {
            System.arraycopy(solid, 0, rgba, i * RGBA_COMPONENTS, RGBA_COMPONENTS);
        }
        return rgba;
    }

    private static void copyPixel(int[] source, int sourcePixel, int[] destination, int destinationPixel) {
        System.arraycopy(
                source,
                sourcePixel * RGBA_COMPONENTS,
                destination,
                destinationPixel * RGBA_COMPONENTS,
                RGBA_COMPONENTS);
    }

    private static int countComplex(XuastcLdrDecodedBlock[][] group) {
        int count = 0;
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                if (isComplex(group[x][y])) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int[][][] weights6x6(XuastcLdrDecodedBlock[][] group) {
        int[][][] weights = new int[2][2][];
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                weights[x][y] = group[x][y].isSolid()
                        ? null
                        : upsampledWeights(group[x][y].getConfig(), 6, 6);
            }
        }
        return weights;
    }

    private static int[][][] endpoints6x6(XuastcLdrDecodedBlock[][] group) {
        int[][][] endpoints = new int[2][2][];
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                endpoints[x][y] = endpoints(group[x][y]);
            }
        }
        return endpoints;
    }

    private static boolean[][] solidFlags(XuastcLdrDecodedBlock[][] group) {
        boolean[][] solid = new boolean[2][2];
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                solid[x][y] = group[x][y].isSolid();
            }
        }
        return solid;
    }

    private static boolean isComplex(XuastcLdrDecodedBlock block) {
        if (block.isSolid()) {
            return false;
        }
        XuastcLdrRawBlockConfig config = block.getConfig();
        return config.getWeightGrid().getPlaneCount() > 1
                || config.getTrialMode().getNumberOfPartitions() > 1;
    }

    private static int[] endpoints(XuastcLdrDecodedBlock block) {
        if (block.isSolid()) {
            int[] solid = block.getSolidRgba();
            return new int[] {
                solid[0], solid[1], solid[2], solid[3],
                solid[0], solid[1], solid[2], solid[3]
            };
        }
        XuastcLdrRawBlockConfig config = block.getConfig();
        return XuastcAstcConstants.decodeLdrEndpoints(
                config.getActualColorEndpointMode(),
                config.getEndpoints(),
                0,
                config.getTrialMode().getEndpointIseRange());
    }

    private static int[] upsampledWeights(XuastcLdrRawBlockConfig config, int blockWidth, int blockHeight) {
        return dequantizeAndUpsampleWeights(
                config.getWeightGrid(),
                config.getTrialMode().getWeightIseRange(),
                blockWidth,
                blockHeight);
    }

    private static int[] dequantizeAndUpsampleWeights(
            XuastcLdrWeightGrid weightGrid,
            int weightIseRange,
            int blockWidth,
            int blockHeight) {
        int totalWeights = weightGrid.getGridWidth() * weightGrid.getGridHeight();
        int[] weightSymbols = weightGrid.getWeights();
        int planeCount = weightGrid.getPlaneCount();
        if (weightSymbols.length != totalWeights * planeCount) {
            throw new BasisDecodeException("Invalid XUASTC single-plane weight-grid length");
        }
        int[] dequantized = new int[totalWeights];
        for (int i = 0; i < totalWeights; i++) {
            dequantized[i] = XuastcAstcConstants.dequantBiseWeight(
                    weightSymbols[i * planeCount],
                    weightIseRange);
        }
        return XuastcLdrBlockDecoder.upsampleWeights(
                blockWidth,
                blockHeight,
                weightGrid.getGridWidth(),
                weightGrid.getGridHeight(),
                dequantized);
    }

    private static void packSolid(XuastcLdrDecodedBlock block, byte[] output, int outputOffset) {
        int[] solid = block.getSolidRgba();
        Bc7LdrBlockPacker.packMode5Solid(output, outputOffset, solid[0], solid[1], solid[2], solid[3]);
    }

    private static boolean sameSolidColors(
            XuastcLdrDecodedBlock first,
            XuastcLdrDecodedBlock second,
            int tolerance) {
        if (!first.isSolid() || !second.isSolid()) {
            return false;
        }
        int[] a = first.getSolidRgba();
        int[] b = second.getSolidRgba();
        for (int i = 0; i < RGBA_COMPONENTS; i++) {
            if (Math.abs(a[i] - b[i]) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameSingleSubsetEndpoints(
            XuastcLdrDecodedBlock first,
            XuastcLdrDecodedBlock second,
            int tolerance) {
        if (first.isSolid() || second.isSolid() || isComplex(first) || isComplex(second)) {
            return false;
        }
        XuastcLdrRawBlockConfig firstConfig = first.getConfig();
        XuastcLdrRawBlockConfig secondConfig = second.getConfig();
        if (firstConfig.getActualColorEndpointMode() != secondConfig.getActualColorEndpointMode()
                || firstConfig.getTrialMode().getEndpointIseRange()
                        != secondConfig.getTrialMode().getEndpointIseRange()) {
            return false;
        }
        if (tolerance == 0) {
            int endpointValues = XuastcAstcConstants.numCemEndpointValues(
                    firstConfig.getActualColorEndpointMode());
            int[] firstEndpoints = firstConfig.getEndpoints();
            int[] secondEndpoints = secondConfig.getEndpoints();
            for (int i = 0; i < endpointValues; i++) {
                if (firstEndpoints[i] != secondEndpoints[i]) {
                    return false;
                }
            }
            return true;
        }
        int[] firstEndpoints = endpoints(first);
        int[] secondEndpoints = endpoints(second);
        for (int i = 0; i < 8; i++) {
            if (Math.abs(firstEndpoints[i] - secondEndpoints[i]) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAlpha(XuastcLdrDecodedBlock block) {
        if (block.isSolid()) {
            return block.getSolidRgba()[3] != 255;
        }
        int cem = block.getConfig().getActualColorEndpointMode();
        return cem == XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET;
    }

    private static XuastcLdrDecodedBlock[][] toBlockGrid(
            XuastcLdrDecodedBlock[] blocks,
            int blocksX,
            int blocksY) {
        XuastcLdrDecodedBlock[][] grid = new XuastcLdrDecodedBlock[blocksY][blocksX];
        for (XuastcLdrDecodedBlock block : blocks) {
            grid[block.getBlockY()][block.getBlockX()] = block;
        }
        return grid;
    }

    private static int[] toIntRgba(byte[] rgba) {
        int[] result = new int[rgba.length];
        for (int i = 0; i < rgba.length; i++) {
            result[i] = Byte.toUnsignedInt(rgba[i]);
        }
        return result;
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
