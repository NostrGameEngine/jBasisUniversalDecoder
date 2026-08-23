package org.ngengine.basis;

/**
 * BC7 mode 6 RGB packer for the low-variance path used by the reference bc7f encoder.
 */
final class Bc7Mode6RgbBlockPacker {
    private static final int[] WEIGHTS2 = {
        0, 21, 43, 64
    };
    private static final int[] WEIGHTS3 = {
        0, 9, 18, 27, 37, 46, 55, 64
    };
    private static final int[] WEIGHTS4 = {
        0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64
    };
    private static final float[][] LS_WEIGHTS2 = createLeastSquaresWeights(WEIGHTS2);
    private static final float[][] LS_WEIGHTS3 = createLeastSquaresWeights(WEIGHTS3);
    private static final float[][] LS_WEIGHTS4 = createLeastSquaresWeights();
    private static final int TRIVIAL_BLOCK_THRESH_RGB = 20 * 16;
    private static final int TRIVIAL_BLOCK_THRESH_RGBA = 2 * 16;
    private static final int DP_BLOCK_VAR_THRESH = 2 * 16;
    private static final int DP_BLOCK_VAR_THRESH_RGBA = 1 * 16;
    private static final int MIN_BLOCK_MAX_VAR_23SUBSETS = 100 * 16;
    private static final int MIN_BLOCK_MAX_VAR_3SUBSETS = 500 * 16;
    private static final int MIN_BLOCK_MAX_VAR_23SUBSETS_RGBA = 100 * 16;
    private static final float HIGH_ORTHO_ENERGY_THRESH = 1.0f * 16.0f;
    private static final float HIGH_ORTHO_ENERGY_THRESH_RGBA = 1.0f * 16.0f;
    private static final float ORTHO_RATIO_23SUBSET_RATIO_THRESH = .004f;
    private static final float ORTHO_RATIO_23SUBSET_RATIO_THRESH_RGBA = .004f;
    private static final float STRONG_CORR_THRESH = .85f;
    private static final float ALPHA_DECORR_THRESHOLD = .995f;
    private static final float STRONG_CORR_THRESH_RGBA = .85f;
    private static final float SMALL_FLOAT_VAL = .0000125f;
    private static final float UNIQUE_PBIT_DISCOUNT = .85f;
    private static final float SHARED_PBIT_DISCOUNT = .95f;

    private Bc7Mode6RgbBlockPacker() {
    }

    static byte[] packTrivialRgb(byte[] rgba, int width, int height) {
        return packRgba(rgba, width, height, true);
    }

    static byte[] packMode6Rgb(byte[] rgba, int width, int height) {
        return packRgba(rgba, width, height, false);
    }

    static byte[] packAutoRgb(byte[] rgba, int width, int height) {
        return packAutoRgb(rgba, width, height, false);
    }

    static byte[] packAutoRgb(byte[] rgba, int width, int height, boolean highQuality) {
        if (rgba.length != width * height * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        int blocksX = (width + 3) >>> 2;
        int blocksY = (height + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, width, height, bx * 4, by * 4, block);
                if (highQuality) {
                    packAutoRgbBlockHighQuality(block, output, outputOffset);
                } else {
                    packAutoRgbBlock(block, output, outputOffset);
                }
                outputOffset += 16;
            }
        }
        return output;
    }

    static byte[] packAutoRgba(byte[] rgba, int width, int height) {
        return packAutoRgba(rgba, width, height, false);
    }

    static byte[] packAutoRgba(byte[] rgba, int width, int height, boolean highQuality) {
        if (rgba.length != width * height * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        if (isOpaque(rgba)) {
            return packAutoRgb(rgba, width, height, highQuality);
        }
        int blocksX = (width + 3) >>> 2;
        int blocksY = (height + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, width, height, bx * 4, by * 4, block);
                if (highQuality) {
                    packAutoRgbaBlockHighQuality(block, output, outputOffset);
                } else {
                    packAutoRgbaBlock(block, output, outputOffset);
                }
                outputOffset += 16;
            }
        }
        return output;
    }

    static byte[] packAutoRgba(
            byte[] rgba,
            int strideWidth,
            int visibleWidth,
            int visibleHeight) {
        return packAutoRgba(rgba, strideWidth, visibleHeight, visibleWidth, visibleHeight, false);
    }

    static byte[] packAutoRgba(
            byte[] rgba,
            int strideWidth,
            int sourceHeight,
            int visibleWidth,
            int visibleHeight,
            boolean highQuality) {
        if (rgba.length != strideWidth * sourceHeight * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        int blocksX = (visibleWidth + 3) >>> 2;
        int blocksY = (visibleHeight + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        boolean opaque = isOpaque(rgba);
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, strideWidth, sourceHeight, bx * 4, by * 4, block);
                if (opaque) {
                    if (highQuality) {
                        packAutoRgbBlockHighQuality(block, output, outputOffset);
                    } else {
                        packAutoRgbBlock(block, output, outputOffset);
                    }
                } else {
                    if (highQuality) {
                        packAutoRgbaBlockHighQuality(block, output, outputOffset);
                    } else {
                        packAutoRgbaBlock(block, output, outputOffset);
                    }
                }
                outputOffset += 16;
            }
        }
        return output;
    }

    static byte[] packMode7Rgba(byte[] rgba, int width, int height) {
        if (rgba.length != width * height * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        int blocksX = (width + 3) >>> 2;
        int blocksY = (height + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, width, height, bx * 4, by * 4, block);
                packMode7RgbaBlock(block, output, outputOffset);
                outputOffset += 16;
            }
        }
        return output;
    }

    static byte[] packMode1Or3Rgb(byte[] rgba, int width, int height) {
        if (rgba.length != width * height * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        int blocksX = (width + 3) >>> 2;
        int blocksY = (height + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, width, height, bx * 4, by * 4, block);
                packMode1Or3RgbBlock(block, output, outputOffset);
                outputOffset += 16;
            }
        }
        return output;
    }

    static byte[] packMode0Or2Rgb(byte[] rgba, int width, int height) {
        if (rgba.length != width * height * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        int blocksX = (width + 3) >>> 2;
        int blocksY = (height + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, width, height, bx * 4, by * 4, block);
                packMode0Or2RgbBlock(block, output, outputOffset);
                outputOffset += 16;
            }
        }
        return output;
    }

    static byte[] packMode5DualPlaneRgb(byte[] rgba, int width, int height) {
        return packDualPlaneRgb(rgba, width, height, 5);
    }

    static byte[] packMode4DualPlaneRgb(byte[] rgba, int width, int height) {
        return packDualPlaneRgb(rgba, width, height, 4);
    }

    private static byte[] packDualPlaneRgb(byte[] rgba, int width, int height, int requiredMode) {
        if (rgba.length != width * height * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        int blocksX = (width + 3) >>> 2;
        int blocksY = (height + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, width, height, bx * 4, by * 4, block);
                packDualPlaneRgbBlock(block, output, outputOffset, requiredMode);
                outputOffset += 16;
            }
        }
        return output;
    }

    private static byte[] packRgba(byte[] rgba, int width, int height, boolean trivialOnly) {
        if (rgba.length != width * height * 4) {
            throw new BasisDecodeException("RGBA level size does not match dimensions");
        }
        int blocksX = (width + 3) >>> 2;
        int blocksY = (height + 3) >>> 2;
        byte[] output = new byte[blocksX * blocksY * 16];
        int[] block = new int[16 * 4];
        int outputOffset = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                copyBlock(rgba, width, height, bx * 4, by * 4, block);
                if (trivialOnly) {
                    packTrivialRgbBlock(block, output, outputOffset);
                } else {
                    packMode6RgbBlock(block, output, outputOffset);
                }
                outputOffset += 16;
            }
        }
        return output;
    }

    static void packTrivialRgbBlock(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int[] covariance = covariance(rgba);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }
        if (blockMaxVar >= TRIVIAL_BLOCK_THRESH_RGB
                || desiredDualPlaneChannel(covariance, blockMaxVar) != -1) {
            throw new BasisDecodeException("BC7 RGB block is outside the trivial mode 6 path");
        }
        packTrivialMode6RgbBlock(rgba, output, offset);
    }

    private static void packTrivialMode6RgbBlock(int[] rgba, byte[] output, int offset) {
        int lowIndex = 0;
        int highIndex = 0;
        int lowKey = Integer.MAX_VALUE;
        int highKey = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int key = 32 * rgba[base] + 64 * rgba[base + 1] + 16 * rgba[base + 2] + i;
            if (key < lowKey) {
                lowKey = key;
                lowIndex = i;
            }
            if (key > highKey) {
                highKey = key;
                highIndex = i;
            }
        }

        int[] low = determineUniquePbitEndpoint(rgba, lowIndex, true);
        int[] high = determineUniquePbitEndpoint(rgba, highIndex, false);
        int[] selectors = evalWeightsMode6Rgb(rgba, low, high);
        writeMode6(output, offset, low, high, selectors);
    }

    static void packMode5DualPlaneRgbBlock(int[] rgba, byte[] output, int offset) {
        packDualPlaneRgbBlock(rgba, output, offset, 5);
    }

    static void packMode4DualPlaneRgbBlock(int[] rgba, byte[] output, int offset) {
        packDualPlaneRgbBlock(rgba, output, offset, 4);
    }

    static void packMode4Or5DualPlaneRgbBlock(int[] rgba, byte[] output, int offset, int mode) {
        packDualPlaneRgbBlock(rgba, output, offset, mode);
    }

    private static void packDualPlaneRgbBlock(int[] rgba, byte[] output, int offset, int requiredMode) {
        int[] covariance = covariance(rgba);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        int dualPlaneChannel = desiredDualPlaneChannel(covariance, blockMaxVar);
        if (dualPlaneChannel == -1) {
            throw new BasisDecodeException("BC7 RGB block does not require the dual-plane path");
        }
        packDualPlaneBlock(rgba, output, offset, requiredMode, dualPlaneChannel);
    }

    private static void packDualPlaneBlock(
            int[] rgba,
            byte[] output,
            int offset,
            int requiredMode,
            int dualPlaneChannel) {
        int[] pixels = swapDualPlaneChannel(rgba, dualPlaneChannel);
        int[] totals = totals4(pixels);
        int[] min = {255, 255, 255, 255};
        int[] max = {0, 0, 0, 0};
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            for (int c = 0; c < 4; c++) {
                min[c] = Math.min(min[c], pixels[base + c]);
                max[c] = Math.max(max[c], pixels[base + c]);
            }
        }

        int[] cov3 = covariance(pixels, new int[] {totals[0], totals[1], totals[2]});
        int blockMaxVar3 = Math.max(cov3[0], Math.max(cov3[3], cov3[5]));
        float scale = blockMaxVar3 != 0 ? 1.0f / blockMaxVar3 : 0.0f;
        float wx = cov3[0] * scale;
        float wy = cov3[3] * scale;
        float wz = cov3[5] * scale;
        float axisR = axisComponent(cov3[0], cov3[1], cov3[2], wx, wy, wz);
        float axisG = axisComponent(cov3[1], cov3[3], cov3[4], wx, wy, wz);
        float axisB = axisComponent(cov3[2], cov3[4], cov3[5], wx, wy, wz);
        float lineSse = estimateSlamToLineSse3d(cov3, axisR, axisG, axisB);

        int[] minMax = projectMinMax(pixels, axisR, axisG, axisB);
        int lowIndex = minMax[0];
        int highIndex = minMax[1];
        int[] rgbSpans = {
            pixels[highIndex * 4] - pixels[lowIndex * 4],
            pixels[highIndex * 4 + 1] - pixels[lowIndex * 4 + 1],
            pixels[highIndex * 4 + 2] - pixels[lowIndex * 4 + 2],
            0
        };
        int alphaSpan = max[3] - min[3];

        float mode4Rgb3Alpha2 = lineSse
                + analyticalQuantEstSse(32, 8, 3, rgbSpans, 1.0f, 16)
                + analyticalQuantEstSse(64, 4, alphaSpan, 1.0f, 1.0f, 16);
        float mode4Rgb2Alpha3 = lineSse
                + analyticalQuantEstSse(32, 4, 3, rgbSpans, 1.0f, 16)
                + analyticalQuantEstSse(64, 8, alphaSpan, 1.0f, 1.0f, 16);
        float mode5 = lineSse
                + analyticalQuantEstSse(128, 4, 3, rgbSpans, 1.0f, 16)
                + analyticalQuantEstSse(256, 4, alphaSpan, 1.0f, 1.0f, 16);
        if (mode5 >= Math.min(mode4Rgb3Alpha2, mode4Rgb2Alpha3)) {
            if (requiredMode != 4) {
                throw new BasisDecodeException("BC7 RGB dual-plane block requires mode 4");
            }
            if (mode4Rgb3Alpha2 < mode4Rgb2Alpha3) {
                packMode4Rgb3Alpha2(
                        pixels, output, offset, dualPlaneChannel, totals, min, max, lowIndex, highIndex);
            } else {
                packMode4Rgb2Alpha3(
                        pixels, output, offset, dualPlaneChannel, totals, min, max, lowIndex, highIndex);
            }
            return;
        }
        if (requiredMode != 5) {
            throw new BasisDecodeException("BC7 RGB dual-plane block requires mode 5");
        }

        int lr = to7(pixels[lowIndex * 4]);
        int lg = to7(pixels[lowIndex * 4 + 1]);
        int lb = to7(pixels[lowIndex * 4 + 2]);
        int la = min[3];
        int hr = to7(pixels[highIndex * 4]);
        int hg = to7(pixels[highIndex * 4 + 1]);
        int hb = to7(pixels[highIndex * 4 + 2]);
        int ha = max[3];

        int[] rgbSelectors = evalWeightsMode5Rgb(pixels, lr, lg, lb, hr, hg, hb);
        float[][] rgbEndpoints = computeLeastSquaresEndpoints3d(pixels, rgbSelectors, totals, LS_WEIGHTS2);
        if (rgbEndpoints != null) {
            lr = fastRound(rgbEndpoints[0][0] * 127.0f);
            lg = fastRound(rgbEndpoints[0][1] * 127.0f);
            lb = fastRound(rgbEndpoints[0][2] * 127.0f);
            hr = fastRound(rgbEndpoints[1][0] * 127.0f);
            hg = fastRound(rgbEndpoints[1][1] * 127.0f);
            hb = fastRound(rgbEndpoints[1][2] * 127.0f);
            rgbSelectors = evalWeightsMode5Rgb(pixels, lr, lg, lb, hr, hg, hb);
        }

        int[] alphaSelectors = evalWeightsMode5Alpha(pixels, la, ha);
        float[] alphaEndpoints = computeLeastSquaresEndpoints1d(
                pixels,
                alphaSelectors,
                totals[3],
                LS_WEIGHTS2);
        if (alphaEndpoints != null) {
            la = fastRound(alphaEndpoints[0]);
            ha = fastRound(alphaEndpoints[1]);
            alphaSelectors = evalWeightsMode5Alpha(pixels, la, ha);
        }

        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(5);
        block.rotation = (dualPlaneChannel + 1) & 3;
        block.low[0][0] = lr;
        block.low[0][1] = lg;
        block.low[0][2] = lb;
        block.low[0][3] = la;
        block.high[0][0] = hr;
        block.high[0][1] = hg;
        block.high[0][2] = hb;
        block.high[0][3] = ha;
        System.arraycopy(rgbSelectors, 0, block.selectors, 0, rgbSelectors.length);
        System.arraycopy(alphaSelectors, 0, block.alphaSelectors, 0, alphaSelectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    static void packAutoRgbaBlock(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int[] totals = totals4(rgba);
        int[] covariance = covariance4(rgba, totals);
        int blockMaxVar = Math.max(
                Math.max(covariance[0], covariance[4]),
                Math.max(covariance[7], covariance[9]));
        int dualPlaneChannel = desiredDualPlaneChannelRgba(covariance, blockMaxVar);
        ModeEstimate mode6 = estimateMode6Rgba(rgba, covariance);
        if (dualPlaneChannel == -1 && blockMaxVar < TRIVIAL_BLOCK_THRESH_RGBA) {
            packTrivialMode6RgbaBlock(rgba, output, offset);
            return;
        }

        ModeEstimate mode7 = estimateMode7Rgba(rgba, covariance, totals);
        if (dualPlaneChannel >= 0) {
            ModeEstimate mode45 = estimateMode4Or5Rgb(rgba, dualPlaneChannel);
            if (mode45.sse < mode7.sse && mode45.sse < mode6.sse) {
                packDualPlaneBlock(rgba, output, offset, mode45.mode, dualPlaneChannel);
                return;
            }
        }

        if (mode7.sse < mode6.sse) {
            packMode7RgbaBlock(rgba, output, offset);
            return;
        }
        packMode6RgbaBlock(rgba, output, offset);
    }

    static void packAutoRgbaBlockHighQuality(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int[] totals = totals4(rgba);
        int[] covariance = covariance4(rgba, totals);
        int blockMaxVar = Math.max(
                Math.max(covariance[0], covariance[4]),
                Math.max(covariance[7], covariance[9]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int dualPlaneChannel = desiredDualPlaneChannelRgba(covariance, blockMaxVar);
        if (dualPlaneChannel == -1 && blockMaxVar < TRIVIAL_BLOCK_THRESH_RGBA) {
            packTrivialMode6RgbaBlock(rgba, output, offset);
            return;
        }

        byte[] mode6 = new byte[16];
        int mode6Sse = packMode6RgbaBlockPartialAnalytical(rgba, mode6, 0);
        int mode7Sse = Integer.MAX_VALUE;
        int mode45Sse = Integer.MAX_VALUE;
        byte[] mode7 = null;
        byte[] mode45 = null;

        if (mode6Sse != 0) {
            Axis4 axis = principalAxis4(covariance);
            SlamEstimate mode6Slam = estimateSlamToLineSse4dWithRatio(covariance, axis);
            if (blockMaxVar >= MIN_BLOCK_MAX_VAR_23SUBSETS_RGBA
                    && mode6Slam.orthoRatio > ORTHO_RATIO_23SUBSET_RATIO_THRESH_RGBA
                    && mode6Slam.sse >= HIGH_ORTHO_ENERGY_THRESH_RGBA) {
                mode7 = new byte[16];
                packMode7RgbaBlock(rgba, mode7, 0);
                mode7Sse = bc7Sse(mode7, rgba);
            }

            if (dualPlaneChannel >= 0) {
                ModeEstimate mode45Estimate = estimateMode4Or5Rgb(rgba, dualPlaneChannel);
                mode45 = new byte[16];
                packMode4Or5DualPlaneRgbBlock(
                        rgba,
                        mode45,
                        0,
                        mode45Estimate.mode);
                mode45Sse = bc7Sse(mode45, rgba);
            }
        }

        int best = Math.min(mode6Sse, Math.min(mode45Sse, mode7Sse));
        if (mode45 != null && mode45Sse == best) {
            System.arraycopy(mode45, 0, output, offset, 16);
        } else if (mode7 != null && mode7Sse == best) {
            System.arraycopy(mode7, 0, output, offset, 16);
        } else {
            System.arraycopy(mode6, 0, output, offset, 16);
        }
    }

    private static void packMode4Rgb3Alpha2(
            int[] pixels,
            byte[] output,
            int offset,
            int dualPlaneChannel,
            int[] totals,
            int[] min,
            int[] max,
            int lowIndex,
            int highIndex) {
        int lr = to5(pixels[lowIndex * 4]);
        int lg = to5(pixels[lowIndex * 4 + 1]);
        int lb = to5(pixels[lowIndex * 4 + 2]);
        int la = to6(min[3]);
        int hr = to5(pixels[highIndex * 4]);
        int hg = to5(pixels[highIndex * 4 + 1]);
        int hb = to5(pixels[highIndex * 4 + 2]);
        int ha = to6(max[3]);

        int[] rgbSelectors = evalWeightsMode4Rgb(pixels, lr, lg, lb, hr, hg, hb, 3);
        float[][] rgbEndpoints = computeLeastSquaresEndpoints3d(pixels, rgbSelectors, totals, LS_WEIGHTS3);
        if (rgbEndpoints != null) {
            lr = fastRound(rgbEndpoints[0][0] * 31.0f);
            lg = fastRound(rgbEndpoints[0][1] * 31.0f);
            lb = fastRound(rgbEndpoints[0][2] * 31.0f);
            hr = fastRound(rgbEndpoints[1][0] * 31.0f);
            hg = fastRound(rgbEndpoints[1][1] * 31.0f);
            hb = fastRound(rgbEndpoints[1][2] * 31.0f);
            rgbSelectors = evalWeightsMode4Rgb(pixels, lr, lg, lb, hr, hg, hb, 3);
        }

        int[] alphaSelectors = evalWeightsMode4Alpha(pixels, la, ha, 2);
        float[] alphaEndpoints = computeLeastSquaresEndpoints1d(
                pixels,
                alphaSelectors,
                totals[3],
                LS_WEIGHTS2);
        if (alphaEndpoints != null) {
            la = fastRound(alphaEndpoints[0] * (63.0f / 255.0f));
            ha = fastRound(alphaEndpoints[1] * (63.0f / 255.0f));
            alphaSelectors = evalWeightsMode4Alpha(pixels, la, ha, 2);
        }
        writeMode4(output, offset, dualPlaneChannel, 1, lr, lg, lb, la, hr, hg, hb, ha,
                rgbSelectors, alphaSelectors);
    }

    private static void packMode4Rgb2Alpha3(
            int[] pixels,
            byte[] output,
            int offset,
            int dualPlaneChannel,
            int[] totals,
            int[] min,
            int[] max,
            int lowIndex,
            int highIndex) {
        int lr = to5(pixels[lowIndex * 4]);
        int lg = to5(pixels[lowIndex * 4 + 1]);
        int lb = to5(pixels[lowIndex * 4 + 2]);
        int la = to6(min[3]);
        int hr = to5(pixels[highIndex * 4]);
        int hg = to5(pixels[highIndex * 4 + 1]);
        int hb = to5(pixels[highIndex * 4 + 2]);
        int ha = to6(max[3]);

        int[] rgbSelectors = evalWeightsMode4Rgb(pixels, lr, lg, lb, hr, hg, hb, 2);
        float[][] rgbEndpoints = computeLeastSquaresEndpoints3d(pixels, rgbSelectors, totals, LS_WEIGHTS2);
        if (rgbEndpoints != null) {
            lr = fastRound(rgbEndpoints[0][0] * 31.0f);
            lg = fastRound(rgbEndpoints[0][1] * 31.0f);
            lb = fastRound(rgbEndpoints[0][2] * 31.0f);
            hr = fastRound(rgbEndpoints[1][0] * 31.0f);
            hg = fastRound(rgbEndpoints[1][1] * 31.0f);
            hb = fastRound(rgbEndpoints[1][2] * 31.0f);
            rgbSelectors = evalWeightsMode4Rgb(pixels, lr, lg, lb, hr, hg, hb, 2);
        }

        int[] alphaSelectors = evalWeightsMode4Alpha(pixels, la, ha, 3);
        float[] alphaEndpoints = computeLeastSquaresEndpoints1d(
                pixels,
                alphaSelectors,
                totals[3],
                LS_WEIGHTS3);
        if (alphaEndpoints != null) {
            la = fastRound(alphaEndpoints[0] * (63.0f / 255.0f));
            ha = fastRound(alphaEndpoints[1] * (63.0f / 255.0f));
            alphaSelectors = evalWeightsMode4Alpha(pixels, la, ha, 3);
        }
        writeMode4(output, offset, dualPlaneChannel, 0, lr, lg, lb, la, hr, hg, hb, ha,
                rgbSelectors, alphaSelectors);
    }

    static void packMode6RgbBlock(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int[] totals = totals(rgba);
        int[] covariance = covariance(rgba, totals);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }
        int desiredDualPlaneChannel = desiredDualPlaneChannel(covariance, blockMaxVar);
        if (desiredDualPlaneChannel == -1 && blockMaxVar < TRIVIAL_BLOCK_THRESH_RGB) {
            packTrivialRgbBlock(rgba, output, offset);
            return;
        }

        float scale = 1.0f / blockMaxVar;
        float wx = covariance[0] * scale;
        float wy = covariance[3] * scale;
        float wz = covariance[5] * scale;
        float axisR = axisComponentMode6(covariance[0], covariance[1], covariance[2], wx, wy, wz);
        float axisG = axisComponentMode6(covariance[1], covariance[3], covariance[4], wx, wy, wz);
        float axisB = axisComponentMode6(covariance[2], covariance[4], covariance[5], wx, wy, wz);

        int scaledAxisR = 306;
        int scaledAxisG = 601;
        int scaledAxisB = 117;
        float maxAxis = Math.max(Math.abs(axisR), Math.max(Math.abs(axisG), Math.abs(axisB)));
        if (Math.abs(maxAxis) >= SMALL_FLOAT_VAL) {
            float m = 2048.0f / maxAxis;
            scaledAxisR = (int) (axisR * m);
            scaledAxisG = (int) (axisG * m);
            scaledAxisB = (int) (axisB * m);
        }
        scaledAxisR <<= 4;
        scaledAxisG <<= 4;
        scaledAxisB <<= 4;

        int lowDot = Integer.MAX_VALUE;
        int highDot = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int dot = rgba[base] * scaledAxisR
                    + rgba[base + 1] * scaledAxisG
                    + rgba[base + 2] * scaledAxisB
                    + i;
            lowDot = Math.min(lowDot, dot);
            highDot = Math.max(highDot, dot);
        }

        int[] low = determineUniquePbitEndpoint(rgba, lowDot & 15, true);
        int[] high = determineUniquePbitEndpoint(rgba, highDot & 15, false);
        int[] selectors = evalWeightsMode6Rgb(rgba, low, high);
        float[][] endpointPixels = computeLeastSquaresEndpointPixels3d(rgba, selectors, totals, LS_WEIGHTS4);
        if (endpointPixels != null) {
            float[][] endpoints = normalizeEndpoints(endpointPixels);
            low = determineUniquePbitEndpoint(endpoints[0]);
            high = determineUniquePbitEndpoint(endpoints[1]);
            selectors = evalWeightsMode6Rgb(rgba, low, high);
        }
        writeMode6(output, offset, low, high, selectors);
    }

    private static int packMode6RgbBlockPartialAnalytical(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return 0;
        }

        int[] totals = totals(rgba);
        int[] covariance = covariance(rgba, totals);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return 0;
        }

        float scale = 1.0f / blockMaxVar;
        float wx = covariance[0] * scale;
        float wy = covariance[3] * scale;
        float wz = covariance[5] * scale;
        float axisR = axisComponentMode6(covariance[0], covariance[1], covariance[2], wx, wy, wz);
        float axisG = axisComponentMode6(covariance[1], covariance[3], covariance[4], wx, wy, wz);
        float axisB = axisComponentMode6(covariance[2], covariance[4], covariance[5], wx, wy, wz);

        int scaledAxisR = 306;
        int scaledAxisG = 601;
        int scaledAxisB = 117;
        float maxAxis = Math.max(Math.abs(axisR), Math.max(Math.abs(axisG), Math.abs(axisB)));
        if (Math.abs(maxAxis) >= SMALL_FLOAT_VAL) {
            float m = 2048.0f / maxAxis;
            scaledAxisR = (int) (axisR * m);
            scaledAxisG = (int) (axisG * m);
            scaledAxisB = (int) (axisB * m);
        }
        scaledAxisR <<= 4;
        scaledAxisG <<= 4;
        scaledAxisB <<= 4;

        int lowDot = Integer.MAX_VALUE;
        int highDot = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int dot = rgba[base] * scaledAxisR
                    + rgba[base + 1] * scaledAxisG
                    + rgba[base + 2] * scaledAxisB
                    + i;
            lowDot = Math.min(lowDot, dot);
            highDot = Math.max(highDot, dot);
        }

        int[] low = determineUniquePbitEndpoint(rgba, lowDot & 15, true);
        int[] high = determineUniquePbitEndpoint(rgba, highDot & 15, false);
        int[] selectors = evalWeightsMode6Rgb(rgba, low, high);
        byte[] best = new byte[16];
        writeMode6(best, 0, low, high, selectors);
        int bestSse = bc7Sse(best, rgba);

        if (bestSse != 0) {
            float[][] endpointPixels = computeLeastSquaresEndpointPixels3d(
                    rgba,
                    selectors,
                    totals,
                    LS_WEIGHTS4);
            if (endpointPixels != null) {
                float[][] endpoints = normalizeEndpoints(endpointPixels);
                int[] trialLow = determineUniquePbitEndpoint(endpoints[0]);
                int[] trialHigh = determineUniquePbitEndpoint(endpoints[1]);
                int[] trialSelectors = evalWeightsMode6Rgb(rgba, trialLow, trialHigh);
                byte[] trial = new byte[16];
                writeMode6(trial, 0, trialLow, trialHigh, trialSelectors);
                int trialSse = bc7Sse(trial, rgba);
                if (trialSse < bestSse) {
                    best = trial;
                    bestSse = trialSse;
                }
            }
        }

        System.arraycopy(best, 0, output, offset, 16);
        return bestSse;
    }

    static void packAutoRgbBlock(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int[] totals = totals(rgba);
        int[] covariance = covariance(rgba, totals);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int desiredDualPlaneChannel = desiredDualPlaneChannel(covariance, blockMaxVar);
        if (desiredDualPlaneChannel == -1 && blockMaxVar < TRIVIAL_BLOCK_THRESH_RGB) {
            packTrivialRgbBlock(rgba, output, offset);
            return;
        }

        Axis axis = principalAxis(covariance, blockMaxVar);
        int[] spans = minMaxSpans(rgba);
        boolean needSseEstimates = true;
        SlamEstimate mode6Slam = estimateSlamToLineSse3dWithRatio(
                covariance, axis.red, axis.green, axis.blue);
        float mode6SseEstimate = needSseEstimates
                ? mode6Slam.sse + analyticalQuantEstSse(128, 16, 3, spans, 1.0f, 16)
                : 0.0f;

        if (blockMaxVar >= MIN_BLOCK_MAX_VAR_23SUBSETS
                && mode6Slam.orthoRatio > ORTHO_RATIO_23SUBSET_RATIO_THRESH
                && mode6Slam.sse >= HIGH_ORTHO_ENERGY_THRESH) {
            if (blockMaxVar >= MIN_BLOCK_MAX_VAR_3SUBSETS) {
                ModeEstimate mode02 = estimateMode0Or2Rgb(rgba, axis, totals);
                if (mode02 != null && mode02.sse < mode6SseEstimate) {
                    ModeEstimate mode13 = estimateMode1Or3Rgb(rgba, axis, totals);
                    if (mode13 != null && mode13.sse < mode02.sse) {
                        packMode1Or3RgbBlock(rgba, output, offset);
                    } else {
                        packMode0Or2RgbBlock(rgba, output, offset);
                    }
                    return;
                }
            }

            ModeEstimate mode13 = estimateMode1Or3Rgb(rgba, axis, totals);
            if (mode13 != null && mode13.sse < mode6SseEstimate) {
                packMode1Or3RgbBlock(rgba, output, offset);
                return;
            }
        }

        if (desiredDualPlaneChannel >= 0) {
            ModeEstimate mode45 = estimateMode4Or5Rgb(rgba, desiredDualPlaneChannel);
            if (mode45 != null && mode45.sse < mode6SseEstimate) {
                packMode4Or5DualPlaneRgbBlock(rgba, output, offset, mode45.mode);
                return;
            }
        }

        packMode6RgbBlock(rgba, output, offset);
    }

    static void packAutoRgbBlockHighQuality(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int[] totals = totals(rgba);
        int[] covariance = covariance(rgba, totals);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int desiredDualPlaneChannel = desiredDualPlaneChannel(covariance, blockMaxVar);
        if (desiredDualPlaneChannel == -1 && blockMaxVar < TRIVIAL_BLOCK_THRESH_RGB) {
            packTrivialRgbBlock(rgba, output, offset);
            return;
        }

        byte[] mode6 = new byte[16];
        int mode6Sse = packMode6RgbBlockPartialAnalytical(rgba, mode6, 0);
        int mode02Sse = Integer.MAX_VALUE;
        int mode13Sse = Integer.MAX_VALUE;
        int mode45Sse = Integer.MAX_VALUE;
        byte[] mode02 = null;
        byte[] mode13 = null;
        byte[] mode45 = null;

        if (mode6Sse != 0) {
            Axis axis = principalAxis(covariance, blockMaxVar);
            SlamEstimate mode6Slam = estimateSlamToLineSse3dWithRatio(
                    covariance, axis.red, axis.green, axis.blue);
            if (blockMaxVar >= MIN_BLOCK_MAX_VAR_23SUBSETS
                    && mode6Slam.orthoRatio > ORTHO_RATIO_23SUBSET_RATIO_THRESH
                    && mode6Slam.sse >= HIGH_ORTHO_ENERGY_THRESH) {
                if (blockMaxVar >= MIN_BLOCK_MAX_VAR_3SUBSETS) {
                    mode02 = new byte[16];
                    try {
                        packMode0Or2RgbBlock(rgba, mode02, 0);
                        mode02Sse = bc7Sse(mode02, rgba);
                    } catch (BasisDecodeException ignored) {
                        mode02 = null;
                    }
                }
                mode13 = new byte[16];
                packMode1Or3RgbBlock(rgba, mode13, 0);
                mode13Sse = bc7Sse(mode13, rgba);
            }

            if (desiredDualPlaneChannel >= 0) {
                ModeEstimate mode45Estimate = estimateMode4Or5Rgb(rgba, desiredDualPlaneChannel);
                mode45 = new byte[16];
                packMode4Or5DualPlaneRgbBlock(
                        rgba,
                        mode45,
                        0,
                        mode45Estimate.mode);
                mode45Sse = bc7Sse(mode45, rgba);
            }
        }

        int best = Math.min(Math.min(mode6Sse, mode02Sse), Math.min(mode13Sse, mode45Sse));
        if (mode45 != null && mode45Sse == best) {
            System.arraycopy(mode45, 0, output, offset, 16);
        } else if (mode02 != null && mode02Sse == best) {
            System.arraycopy(mode02, 0, output, offset, 16);
        } else if (mode13 != null && mode13Sse == best) {
            System.arraycopy(mode13, 0, output, offset, 16);
        } else {
            System.arraycopy(mode6, 0, output, offset, 16);
        }
    }

    static void packMode7RgbaBlock(int[] rgba, byte[] output, int offset) {
        if (isSolid(rgba)) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        int[] totals = totals4(rgba);
        int[] covariance = covariance4(rgba, totals);
        Axis4 axis = principalAxis4(covariance);
        int meanR = (totals[0] + 8) >> 4;
        int meanG = (totals[1] + 8) >> 4;
        int meanB = (totals[2] + 8) >> 4;
        int meanA = (totals[3] + 8) >> 4;

        int desiredPatternBits = 0;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float r = rgba[base] - meanR;
            float g = rgba[base + 1] - meanG;
            float b = rgba[base + 2] - meanB;
            float a = rgba[base + 3] - meanA;
            if (r * axis.red + g * axis.green + b * axis.blue + a * axis.alpha > 0.0f) {
                desiredPatternBits |= 1 << i;
            }
        }

        int bestDiff = Integer.MAX_VALUE;
        int bestPartition = 0;
        int bestPartitionBits = 0;
        for (int partition = 0; partition < 64; partition++) {
            int partitionBits = Bc7PartitionTables.partition2Mask(partition);
            int diff = Integer.bitCount(partitionBits ^ desiredPatternBits);
            int minDiff = (Math.min(diff, 16 - diff) << 8) | partition;
            if (minDiff < bestDiff) {
                bestDiff = minDiff;
                bestPartition = partition;
                bestPartitionBits = partitionBits;
            }
        }

        int[][] subsetTotals = new int[2][4];
        int[] subsetCounts = new int[2];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            for (int c = 0; c < 4; c++) {
                subsetTotals[subset][c] += rgba[base + c];
            }
            subsetCounts[subset]++;
        }

        int[][] subsetMeans = new int[2][4];
        for (int subset = 0; subset < 2; subset++) {
            int half = subsetCounts[subset] >>> 1;
            for (int c = 0; c < 4; c++) {
                subsetMeans[subset][c] = (subsetTotals[subset][c] + half) / subsetCounts[subset];
            }
        }

        int[][] subsetCovariance = new int[2][10];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            int r = rgba[base] - subsetMeans[subset][0];
            int g = rgba[base + 1] - subsetMeans[subset][1];
            int b = rgba[base + 2] - subsetMeans[subset][2];
            subsetCovariance[subset][0] += r * r;
            subsetCovariance[subset][1] += r * g;
            subsetCovariance[subset][2] += r * b;
            int a = rgba[base + 3] - subsetMeans[subset][3];
            subsetCovariance[subset][3] += r * a;
            subsetCovariance[subset][4] += g * g;
            subsetCovariance[subset][5] += g * b;
            subsetCovariance[subset][6] += g * a;
            subsetCovariance[subset][7] += b * b;
            subsetCovariance[subset][8] += b * a;
            subsetCovariance[subset][9] += a * a;
        }

        int[][] subsetAxis = new int[2][4];
        for (int subset = 0; subset < 2; subset++) {
            Axis4 subsetAxis4 = subsetAxis4(subsetCovariance[subset]);
            int[] scaled = scaledAxis(subsetAxis4);
            System.arraycopy(scaled, 0, subsetAxis[subset], 0, scaled.length);
        }

        int[] lowDot = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] highDot = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            int dot = rgba[base] * subsetAxis[subset][0]
                    + rgba[base + 1] * subsetAxis[subset][1]
                    + rgba[base + 2] * subsetAxis[subset][2]
                    + rgba[base + 3] * subsetAxis[subset][3]
                    + i;
            lowDot[subset] = Math.min(lowDot[subset], dot);
            highDot[subset] = Math.max(highDot[subset], dot);
        }

        int[][] low = new int[2][4];
        int[][] high = new int[2][4];
        int[][] pbits = new int[2][2];
        for (int subset = 0; subset < 2; subset++) {
            int[] endpoints = determineUniquePbits4(rgba, lowDot[subset] & 15, highDot[subset] & 15, 5);
            for (int c = 0; c < 4; c++) {
                low[subset][c] = endpoints[c];
                high[subset][c] = endpoints[c + 4];
            }
            pbits[subset][0] = endpoints[8];
            pbits[subset][1] = endpoints[9];
        }

        int[] selectors = evalWeightsMode7Rgba(rgba, low, high, pbits, bestPartitionBits);
        float[][] refined = computeSubsetLeastSquaresEndpoints4d(
                rgba, selectors, subsetTotals, bestPartitionBits, LS_WEIGHTS2);
        for (int subset = 0; subset < 2; subset++) {
            if (refined[subset * 2] == null) {
                continue;
            }
            int[] endpoints = determineUniquePbits4(refined[subset * 2], refined[subset * 2 + 1], 5);
            for (int c = 0; c < 4; c++) {
                low[subset][c] = endpoints[c];
                high[subset][c] = endpoints[c + 4];
            }
            pbits[subset][0] = endpoints[8];
            pbits[subset][1] = endpoints[9];
        }
        selectors = evalWeightsMode7Rgba(rgba, low, high, pbits, bestPartitionBits);

        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(7);
        block.partition = bestPartition;
        block.partitionMap = Bc7PartitionTables.partition2Map(bestPartition);
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 4; c++) {
                block.low[subset][c] = low[subset][c];
                block.high[subset][c] = high[subset][c];
            }
            block.pbits[subset][0] = pbits[subset][0];
            block.pbits[subset][1] = pbits[subset][1];
        }
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    static void packMode6RgbaBlock(int[] rgba, byte[] output, int offset) {
        int[] totals = totals4(rgba);
        Axis4 axis = principalAxis4(covariance4(rgba, totals));
        int[] scaled = scaledAxis(axis);
        int lowDot = Integer.MAX_VALUE;
        int highDot = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int dot = rgba[base] * scaled[0]
                    + rgba[base + 1] * scaled[1]
                    + rgba[base + 2] * scaled[2]
                    + rgba[base + 3] * scaled[3]
                    + i;
            lowDot = Math.min(lowDot, dot);
            highDot = Math.max(highDot, dot);
        }

        int[] endpoints = determineUniquePbits4(rgba, lowDot & 15, highDot & 15, 7);
        int[] low = {endpoints[0], endpoints[1], endpoints[2], endpoints[3], endpoints[8]};
        int[] high = {endpoints[4], endpoints[5], endpoints[6], endpoints[7], endpoints[9]};
        int[] selectors = evalWeightsMode6Rgba(rgba, low, high);
        float[][] refined = computeLeastSquaresEndpoints4d(rgba, selectors, totals);
        if (refined != null) {
            endpoints = determineUniquePbits4(refined[0], refined[1], 7);
            low = new int[] {endpoints[0], endpoints[1], endpoints[2], endpoints[3], endpoints[8]};
            high = new int[] {endpoints[4], endpoints[5], endpoints[6], endpoints[7], endpoints[9]};
            selectors = evalWeightsMode6Rgba(rgba, low, high);
        }
        writeMode6Rgba(output, offset, low, high, selectors);
    }

    private static int packMode6RgbaBlockPartialAnalytical(int[] rgba, byte[] output, int offset) {
        int[] totals = totals4(rgba);
        Axis4 axis = principalAxis4(covariance4(rgba, totals));
        int[] scaled = scaledAxis(axis);
        int lowDot = Integer.MAX_VALUE;
        int highDot = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int dot = rgba[base] * scaled[0]
                    + rgba[base + 1] * scaled[1]
                    + rgba[base + 2] * scaled[2]
                    + rgba[base + 3] * scaled[3]
                    + i;
            lowDot = Math.min(lowDot, dot);
            highDot = Math.max(highDot, dot);
        }

        int[] endpoints = determineUniquePbits4(rgba, lowDot & 15, highDot & 15, 7);
        int[] low = {endpoints[0], endpoints[1], endpoints[2], endpoints[3], endpoints[8]};
        int[] high = {endpoints[4], endpoints[5], endpoints[6], endpoints[7], endpoints[9]};
        int[] selectors = evalWeightsMode6Rgba(rgba, low, high);
        byte[] best = new byte[16];
        writeMode6Rgba(best, 0, low, high, selectors);
        int bestSse = bc7Sse(best, rgba);

        if (bestSse != 0) {
            float[][] refined = computeLeastSquaresEndpoints4d(rgba, selectors, totals);
            if (refined != null) {
                int[] trialEndpoints = determineUniquePbits4(refined[0], refined[1], 7);
                int[] trialLow = {
                    trialEndpoints[0],
                    trialEndpoints[1],
                    trialEndpoints[2],
                    trialEndpoints[3],
                    trialEndpoints[8]
                };
                int[] trialHigh = {
                    trialEndpoints[4],
                    trialEndpoints[5],
                    trialEndpoints[6],
                    trialEndpoints[7],
                    trialEndpoints[9]
                };
                int[] trialSelectors = evalWeightsMode6Rgba(rgba, trialLow, trialHigh);
                byte[] trial = new byte[16];
                writeMode6Rgba(trial, 0, trialLow, trialHigh, trialSelectors);
                int trialSse = bc7Sse(trial, rgba);
                if (trialSse < bestSse) {
                    best = trial;
                    bestSse = trialSse;
                }
            }
        }

        System.arraycopy(best, 0, output, offset, 16);
        return bestSse;
    }

    static void packTrivialMode6RgbaBlock(int[] rgba, byte[] output, int offset) {
        int lowDot = Integer.MAX_VALUE;
        int highDot = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int dot = 32 * rgba[base]
                    + 64 * rgba[base + 1]
                    + 16 * rgba[base + 2]
                    + 64 * rgba[base + 3]
                    + i;
            lowDot = Math.min(lowDot, dot);
            highDot = Math.max(highDot, dot);
        }

        int[] endpoints = determineUniquePbits4(rgba, lowDot & 15, highDot & 15, 7);
        int[] low = {endpoints[0], endpoints[1], endpoints[2], endpoints[3], endpoints[8]};
        int[] high = {endpoints[4], endpoints[5], endpoints[6], endpoints[7], endpoints[9]};
        writeMode6Rgba(output, offset, low, high, evalWeightsMode6Rgba(rgba, low, high));
    }

    static void packAstc4x4SingleSubsetRgbaBlock(
            int[] endpoints,
            int[] weights,
            byte[] output,
            int offset) {
        if (endpoints.length != 8 || weights.length != 16) {
            throw new BasisDecodeException("Invalid ASTC single-subset BC7 input");
        }

        float scale = 1.0f / 255.0f;
        float[] lowEndpoint = {
            endpoints[0] * scale,
            endpoints[1] * scale,
            endpoints[2] * scale,
            endpoints[3] * scale
        };
        float[] highEndpoint = {
            endpoints[4] * scale,
            endpoints[5] * scale,
            endpoints[6] * scale,
            endpoints[7] * scale
        };
        int[] quantized = determineUniquePbits4(lowEndpoint, highEndpoint, 7);
        int[] low = {quantized[0], quantized[1], quantized[2], quantized[3], quantized[8]};
        int[] high = {quantized[4], quantized[5], quantized[6], quantized[7], quantized[9]};
        int[] selectors = new int[16];
        for (int i = 0; i < selectors.length; i++) {
            selectors[i] = (weights[i] * 15 + 32) >> 6;
        }
        writeMode6Rgba(output, offset, low, high, selectors);
    }

    static void packAstcSingleSubsetRgbaBlock(
            int[] endpoints,
            int[] weights,
            int weightOffsetX,
            int weightOffsetY,
            int blockWidth,
            int blockHeight,
            byte[] output,
            int offset) {
        if (endpoints.length != 8 || weights.length != blockWidth * blockHeight
                || weightOffsetX < 0 || weightOffsetY < 0
                || weightOffsetX + 3 >= blockWidth || weightOffsetY + 3 >= blockHeight) {
            throw new BasisDecodeException("Invalid ASTC single-subset BC7 window input");
        }

        int[] windowWeights = new int[16];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                windowWeights[x + y * 4] = weights[(weightOffsetX + x) + (weightOffsetY + y) * blockWidth];
            }
        }
        packAstc4x4SingleSubsetRgbaBlock(endpoints, windowWeights, output, offset);
    }

    static void packAstcSameSingleSubsetEndpoints(
            int[] endpoints0,
            int[] weights0,
            int[] endpoints1,
            int[] weights1,
            int dx,
            int dy,
            int blockWidth,
            int blockHeight,
            byte[] output,
            int offset) {
        if (endpoints0.length != 8
                || endpoints1.length != 8
                || weights0.length != blockWidth * blockHeight
                || weights1.length != blockWidth * blockHeight) {
            throw new BasisDecodeException("Invalid ASTC same-endpoint BC7 input");
        }

        int[] averagedEndpoints = new int[8];
        for (int c = 0; c < 4; c++) {
            averagedEndpoints[c] = (endpoints0[c] + endpoints1[c] + 1) >> 1;
            averagedEndpoints[c + 4] = (endpoints0[c + 4] + endpoints1[c + 4] + 1) >> 1;
        }

        int[] windowWeights = new int[16];
        boolean sixBySix = blockWidth == 6 && blockHeight == 6;
        boolean topOrBottom = sixBySix && (dy == 0 || dy == 2);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int weight;
                if (sixBySix) {
                    if (topOrBottom) {
                        weight = x < 2
                                ? weights0[(x + 4) + (y + (dy == 2 ? 2 : 0)) * 6]
                                : weights1[(x - 2) + (y + (dy == 2 ? 2 : 0)) * 6];
                    } else {
                        weight = y < 2
                                ? weights0[(x + (dx == 2 ? 2 : 0)) + (y + 4) * 6]
                                : weights1[(x + (dx == 2 ? 2 : 0)) + (y - 2) * 6];
                    }
                } else {
                    weight = y < 2
                            ? weights0[(dx * 4 + x) + (y + 4) * 8]
                            : weights1[(dx * 4 + x) + (y - 2) * 8];
                }
                windowWeights[x + y * 4] = weight;
            }
        }
        packAstc4x4SingleSubsetRgbaBlock(averagedEndpoints, windowWeights, output, offset);
    }

    static void packAstcTwoSubsetDifferentEndpoints6x6(
            int[] endpoints0,
            int[] weights0,
            boolean solid0,
            int[] endpoints1,
            int[] weights1,
            boolean solid1,
            int dx,
            int dy,
            byte[] output,
            int offset) {
        int[] selectors = new int[16];
        boolean topOrBottom = dy == 0 || dy == 2;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int weight;
                if (topOrBottom) {
                    weight = x < 2
                            ? (solid0 ? 0 : weights0[(x + 4) + (y + (dy == 2 ? 2 : 0)) * 6])
                            : (solid1 ? 0 : weights1[(x - 2) + (y + (dy == 2 ? 2 : 0)) * 6]);
                } else {
                    weight = y < 2
                            ? (solid0 ? 0 : weights0[(x + (dx == 2 ? 2 : 0)) + (y + 4) * 6])
                            : (solid1 ? 0 : weights1[(x + (dx == 2 ? 2 : 0)) + (y - 2) * 6]);
                }
                selectors[x + y * 4] = (weight * 7 + 32) >> 6;
            }
        }
        packAstcTwoSubsetMode1(
                endpoints0,
                endpoints1,
                dx == 0 || dx == 2 ? 13 : 0,
                selectors,
                output,
                offset);
    }

    static void packAstcTwoSubsetDifferentEndpoints8x6Hq(
            int[] endpoints0,
            int[] weights0,
            boolean solid0,
            int[] endpoints1,
            int[] weights1,
            boolean solid1,
            int dx,
            boolean srgbDecode,
            byte[] output,
            int offset) {
        int[][] endpoints = {
            endpoints0.clone(),
            endpoints1.clone()
        };
        int[][] weights = {
            weights0,
            weights1
        };
        boolean[] solid = {
            solid0,
            solid1
        };
        int[] lowWeight = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] highWeight = {0, 0};
        for (int y = 0; y < 4; y++) {
            int subset = y < 2 ? 0 : 1;
            for (int x = 0; x < 4; x++) {
                int weight = solid[subset] ? 0 : astc8x6CrossWeight(weights[subset], dx, y, x);
                lowWeight[subset] = Math.min(lowWeight[subset], weight);
                highWeight[subset] = Math.max(highWeight[subset], weight);
            }
        }

        int lowStddev = 0;
        for (int subset = 0; subset < 2; subset++) {
            if (solid[subset]) {
                continue;
            }
            int[] original = endpoints[subset].clone();
            if (lowWeight[subset] > 0 || highWeight[subset] < 64) {
                for (int c = 0; c < 3; c++) {
                    endpoints[subset][c] = channelInterpolate(
                            original[c], original[c + 4], lowWeight[subset], srgbDecode);
                    endpoints[subset][c + 4] = channelInterpolate(
                            original[c], original[c + 4], highWeight[subset], srgbDecode);
                }
            }
            int dr = endpoints[subset][4] - endpoints[subset][0];
            int dg = endpoints[subset][5] - endpoints[subset][1];
            int db = endpoints[subset][6] - endpoints[subset][2];
            if (dr * dr + dg * dg + db * db < 60) {
                lowStddev++;
            }
        }
        if (lowStddev == 2 && !solid0 && !solid1) {
            int[] rgba = new int[16 * 4];
            for (int y = 0; y < 4; y++) {
                int subset = y < 2 ? 0 : 1;
                for (int x = 0; x < 4; x++) {
                    int weight = astc8x6CrossWeight(weights[subset], dx, y, x);
                    int base = (x + y * 4) * 4;
                    for (int c = 0; c < 3; c++) {
                        int low = srgbDecode
                                ? (endpoints[subset][c] << 8) | 0x80
                                : (endpoints[subset][c] << 8) | endpoints[subset][c];
                        int high = srgbDecode
                                ? (endpoints[subset][c + 4] << 8) | 0x80
                                : (endpoints[subset][c + 4] << 8) | endpoints[subset][c + 4];
                        rgba[base + c] = weightInterpolate(low, high, weight) >> 8;
                    }
                    rgba[base + 3] = 255;
                }
            }
            int[] covariance = covariance(rgba);
            int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
            if (isSolid(rgba) || blockMaxVar == 0) {
                Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            } else if (blockMaxVar < TRIVIAL_BLOCK_THRESH_RGB) {
                packTrivialMode6RgbBlock(rgba, output, offset);
            } else {
                packMode6RgbBlock(rgba, output, offset);
            }
            return;
        }

        int[] selectors = new int[16];
        for (int y = 0; y < 4; y++) {
            int subset = y < 2 ? 0 : 1;
            float scale = lowWeight[subset] == highWeight[subset]
                    ? 0.0f
                    : 7.0f / (highWeight[subset] - lowWeight[subset]);
            for (int x = 0; x < 4; x++) {
                int selector = 0;
                if (!solid[subset] && lowWeight[subset] != highWeight[subset]) {
                    float remapped = (astc8x6CrossWeight(weights[subset], dx, y, x)
                            - lowWeight[subset]) * scale;
                    selector = clamp((int) (remapped + .5f), 0, 7);
                }
                selectors[x + y * 4] = selector;
            }
        }
        packAstcTwoSubsetMode1(endpoints[0], endpoints[1], 13, selectors, output, offset);
    }

    static void packAstc6x6MiddleTwoSubsets(
            int[][][] endpoints,
            int[][][] weights,
            boolean[][] solid,
            boolean leftRight,
            byte[] output,
            int offset) {
        int[][] subsetEndpoints = new int[2][8];
        for (int subset = 0; subset < 2; subset++) {
            int firstX = leftRight ? subset : 0;
            int firstY = leftRight ? 0 : subset;
            int secondX = leftRight ? subset : 1;
            int secondY = leftRight ? 1 : subset;
            for (int c = 0; c < 3; c++) {
                subsetEndpoints[subset][c] = (endpoints[firstX][firstY][c]
                        + endpoints[secondX][secondY][c] + 1) >> 1;
                subsetEndpoints[subset][c + 4] = (endpoints[firstX][firstY][c + 4]
                        + endpoints[secondX][secondY][c + 4] + 1) >> 1;
            }
            subsetEndpoints[subset][3] = 255;
            subsetEndpoints[subset][7] = 255;
        }

        int[] selectors = new int[16];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int sourceX = x < 2 ? 0 : 1;
                int sourceY = y < 2 ? 0 : 1;
                int weight = solid[sourceX][sourceY]
                        ? 0
                        : weights[sourceX][sourceY][(x < 2 ? x + 4 : x - 2)
                                + (y < 2 ? y + 4 : y - 2) * 6];
                selectors[x + y * 4] = (weight * 7 + 32) >> 6;
            }
        }
        packAstcTwoSubsetMode1(
                subsetEndpoints[0],
                subsetEndpoints[1],
                leftRight ? 0 : 13,
                selectors,
                output,
                offset);
    }

    static void packMode1Or3RgbBlock(int[] rgba, byte[] output, int offset) {
        int[] totals = totals(rgba);
        int meanR = (totals[0] + 8) >> 4;
        int meanG = (totals[1] + 8) >> 4;
        int meanB = (totals[2] + 8) >> 4;
        int[] covariance = covariance(rgba, totals);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        Axis axis = principalAxis(covariance, blockMaxVar);
        packMode1Or3RgbBlock(rgba, output, offset, axis.red, axis.green, axis.blue, meanR, meanG, meanB);
    }

    static void packMode0Or2RgbBlock(int[] rgba, byte[] output, int offset) {
        int[] totals = totals(rgba);
        int meanR = (totals[0] + 8) >> 4;
        int meanG = (totals[1] + 8) >> 4;
        int meanB = (totals[2] + 8) >> 4;
        int[] covariance = covariance(rgba, totals);
        int blockMaxVar = Math.max(covariance[0], Math.max(covariance[3], covariance[5]));
        if (blockMaxVar == 0) {
            Bc7LdrBlockPacker.packMode5Solid(output, offset, rgba[0], rgba[1], rgba[2], rgba[3]);
            return;
        }

        float scale = 1.0f / blockMaxVar;
        float wx = covariance[0] * scale;
        float wy = covariance[3] * scale;
        float wz = covariance[5] * scale;
        float axisR = axisComponent(covariance[0], covariance[1], covariance[2], wx, wy, wz);
        float axisG = axisComponent(covariance[1], covariance[3], covariance[4], wx, wy, wz);
        float axisB = axisComponent(covariance[2], covariance[4], covariance[5], wx, wy, wz);

        int[] desiredSubsets = determine3Subsets(rgba, axisR, axisG, axisB, meanR, meanG, meanB);
        if (desiredSubsets == null) {
            throw new BasisDecodeException("BC7 RGB block is outside the three-subset path");
        }

        int[] partitions = pick3SubsetPartitionIndices(desiredSubsets);
        ThreeSubsetCandidate mode0 = createThreeSubsetCandidate(rgba, partitions[0], true);
        ThreeSubsetCandidate mode2 = createThreeSubsetCandidate(rgba, partitions[1], false);
        if (mode0.estimatedSse() < mode2.estimatedSse()) {
            writeMode0Rgb(rgba, output, offset, mode0);
        } else {
            writeMode2Rgb(rgba, output, offset, mode2);
        }
    }

    private static void packMode1Or3RgbBlock(
            int[] rgba,
            byte[] output,
            int offset,
            float blockAxisR,
            float blockAxisG,
            float blockAxisB,
            int blockMeanR,
            int blockMeanG,
            int blockMeanB) {
        int desiredPatternBits = 0;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float r = rgba[base] - blockMeanR;
            float g = rgba[base + 1] - blockMeanG;
            float b = rgba[base + 2] - blockMeanB;
            if (r * blockAxisR + g * blockAxisG + b * blockAxisB > 0.0f) {
                desiredPatternBits |= 1 << i;
            }
        }

        int bestDiff = Integer.MAX_VALUE;
        int bestPartition = 0;
        int bestPartitionBits = 0;
        for (int partition = 0; partition < 64; partition++) {
            int partitionBits = Bc7PartitionTables.partition2Mask(partition);
            int diff = Integer.bitCount(partitionBits ^ desiredPatternBits);
            int minDiff = (Math.min(diff, 16 - diff) << 8) | partition;
            if (minDiff < bestDiff) {
                bestDiff = minDiff;
                bestPartition = partition;
                bestPartitionBits = partitionBits;
            }
        }

        int[][] subsetTotals = new int[2][3];
        int[] subsetCounts = new int[2];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            subsetTotals[subset][0] += rgba[base];
            subsetTotals[subset][1] += rgba[base + 1];
            subsetTotals[subset][2] += rgba[base + 2];
            subsetCounts[subset]++;
        }

        int[][] subsetMeans = new int[2][3];
        for (int subset = 0; subset < 2; subset++) {
            int half = subsetCounts[subset] >>> 1;
            subsetMeans[subset][0] = (subsetTotals[subset][0] + half) / subsetCounts[subset];
            subsetMeans[subset][1] = (subsetTotals[subset][1] + half) / subsetCounts[subset];
            subsetMeans[subset][2] = (subsetTotals[subset][2] + half) / subsetCounts[subset];
        }

        int[][] subsetCovariance = new int[2][6];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            int r = rgba[base] - subsetMeans[subset][0];
            int g = rgba[base + 1] - subsetMeans[subset][1];
            int b = rgba[base + 2] - subsetMeans[subset][2];
            subsetCovariance[subset][0] += r * r;
            subsetCovariance[subset][1] += r * g;
            subsetCovariance[subset][2] += r * b;
            subsetCovariance[subset][3] += g * g;
            subsetCovariance[subset][4] += g * b;
            subsetCovariance[subset][5] += b * b;
        }

        float slamToLineSse = 0.0f;
        int[][] subsetAxis = new int[2][3];
        for (int subset = 0; subset < 2; subset++) {
            int blockMaxVar = Math.max(
                    subsetCovariance[subset][0],
                    Math.max(subsetCovariance[subset][3], subsetCovariance[subset][5]));
            float sc = 1.0f / (blockMaxVar + .0000125f);
            float wx = subsetCovariance[subset][0] * sc;
            float wy = subsetCovariance[subset][3] * sc;
            float wz = subsetCovariance[subset][5] * sc;
            boolean antiCorrelatedGb = subsetCovariance[subset][0] == 0
                    && subsetCovariance[subset][1] == 0
                    && subsetCovariance[subset][2] == 0
                    && subsetCovariance[subset][3] == subsetCovariance[subset][5]
                    && subsetCovariance[subset][4] == -subsetCovariance[subset][3];
            boolean balancedGb = subsetCovariance[subset][0] == 0
                    && subsetCovariance[subset][1] == 0
                    && subsetCovariance[subset][2] == 0
                    && subsetCovariance[subset][3] == subsetCovariance[subset][5]
                    && subsetCovariance[subset][4] < 0;
            boolean antiCorrelatedRb = subsetCovariance[subset][1] == 0
                    && subsetCovariance[subset][3] == 0
                    && subsetCovariance[subset][4] == 0
                    && subsetCovariance[subset][0] == subsetCovariance[subset][5]
                    && subsetCovariance[subset][2] == -subsetCovariance[subset][0];
            boolean tinySymmetricRg = subsetCovariance[subset][0] == 3
                    && subsetCovariance[subset][1] == 2
                    && subsetCovariance[subset][2] == 2
                    && subsetCovariance[subset][3] == 3
                    && subsetCovariance[subset][4] == 2
                    && subsetCovariance[subset][5] == 2;
            float axisR = subsetAxisComponent(
                    subsetCovariance[subset][0],
                    subsetCovariance[subset][1],
                    subsetCovariance[subset][2],
                    wx,
                    wy,
                    wz,
                    balancedGb || antiCorrelatedGb || antiCorrelatedRb,
                    tinySymmetricRg);
            float axisG = subsetAxisComponent(
                    subsetCovariance[subset][1],
                    subsetCovariance[subset][3],
                    subsetCovariance[subset][4],
                    wx,
                    wy,
                    wz,
                    balancedGb || antiCorrelatedGb || antiCorrelatedRb,
                    tinySymmetricRg);
            float axisB = subsetAxisComponent(
                    subsetCovariance[subset][2],
                    subsetCovariance[subset][4],
                    subsetCovariance[subset][5],
                    wx,
                    wy,
                    wz,
                    balancedGb || antiCorrelatedGb || antiCorrelatedRb,
                    tinySymmetricRg);
            slamToLineSse += estimateSlamToLineSse3d(subsetCovariance[subset], axisR, axisG, axisB);

            int scaledAxisR = 306;
            int scaledAxisG = 601;
            int scaledAxisB = 117;
            float maxAxis = Math.max(Math.abs(axisR), Math.max(Math.abs(axisG), Math.abs(axisB)));
            if (Math.abs(maxAxis) >= SMALL_FLOAT_VAL) {
                float m = 2048.0f / maxAxis;
                scaledAxisR = (int) (axisR * m);
                scaledAxisG = (int) (axisG * m);
                scaledAxisB = (int) (axisB * m);
            }
            subsetAxis[subset][0] = scaledAxisR << 4;
            subsetAxis[subset][1] = scaledAxisG << 4;
            subsetAxis[subset][2] = scaledAxisB << 4;
        }

        int[] lowDot = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] highDot = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            int dot = rgba[base] * subsetAxis[subset][0]
                    + rgba[base + 1] * subsetAxis[subset][1]
                    + rgba[base + 2] * subsetAxis[subset][2]
                    + i;
            lowDot[subset] = Math.min(lowDot[subset], dot);
            highDot[subset] = Math.max(highDot[subset], dot);
        }

        int[] lowPixel = {lowDot[0] & 15, lowDot[1] & 15};
        int[] highPixel = {highDot[0] & 15, highDot[1] & 15};
        float[] quantError = new float[2];
        int[] spans = new int[4];
        for (int subset = 0; subset < 2; subset++) {
            int lowBase = lowPixel[subset] * 4;
            int highBase = highPixel[subset] * 4;
            for (int c = 0; c < 3; c++) {
                spans[c] = rgba[highBase + c] - rgba[lowBase + c];
            }
            spans[3] = 0;
            quantError[0] += analyticalQuantEstSse(
                    64, 8, 3, spans, UNIQUE_PBIT_DISCOUNT, subsetCounts[subset]);
            quantError[1] += analyticalQuantEstSse(
                    128, 4, 3, spans, SHARED_PBIT_DISCOUNT, subsetCounts[subset]);
        }

        if (slamToLineSse + quantError[0] < slamToLineSse + quantError[1]) {
            writeMode1Rgb(rgba, output, offset, bestPartition, bestPartitionBits,
                    subsetTotals, lowPixel, highPixel);
        } else {
            writeMode3Rgb(rgba, output, offset, bestPartition, bestPartitionBits,
                    subsetTotals, lowPixel, highPixel);
        }
    }

    private static ModeEstimate estimateMode1Or3Rgb(int[] rgba, Axis axis, int[] blockTotals) {
        int blockMeanR = (blockTotals[0] + 8) >> 4;
        int blockMeanG = (blockTotals[1] + 8) >> 4;
        int blockMeanB = (blockTotals[2] + 8) >> 4;
        int desiredPatternBits = 0;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float r = rgba[base] - blockMeanR;
            float g = rgba[base + 1] - blockMeanG;
            float b = rgba[base + 2] - blockMeanB;
            if (r * axis.red + g * axis.green + b * axis.blue > 0.0f) {
                desiredPatternBits |= 1 << i;
            }
        }

        int bestDiff = Integer.MAX_VALUE;
        int bestPartitionBits = 0;
        for (int partition = 0; partition < 64; partition++) {
            int partitionBits = Bc7PartitionTables.partition2Mask(partition);
            int diff = Integer.bitCount(partitionBits ^ desiredPatternBits);
            int minDiff = (Math.min(diff, 16 - diff) << 8) | partition;
            if (minDiff < bestDiff) {
                bestDiff = minDiff;
                bestPartitionBits = partitionBits;
            }
        }

        int[][] subsetTotals = new int[2][3];
        int[] subsetCounts = new int[2];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            subsetTotals[subset][0] += rgba[base];
            subsetTotals[subset][1] += rgba[base + 1];
            subsetTotals[subset][2] += rgba[base + 2];
            subsetCounts[subset]++;
        }

        int[][] subsetMeans = new int[2][3];
        for (int subset = 0; subset < 2; subset++) {
            int half = subsetCounts[subset] >>> 1;
            subsetMeans[subset][0] = (subsetTotals[subset][0] + half) / subsetCounts[subset];
            subsetMeans[subset][1] = (subsetTotals[subset][1] + half) / subsetCounts[subset];
            subsetMeans[subset][2] = (subsetTotals[subset][2] + half) / subsetCounts[subset];
        }

        int[][] subsetCovariance = new int[2][6];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            int r = rgba[base] - subsetMeans[subset][0];
            int g = rgba[base + 1] - subsetMeans[subset][1];
            int b = rgba[base + 2] - subsetMeans[subset][2];
            subsetCovariance[subset][0] += r * r;
            subsetCovariance[subset][1] += r * g;
            subsetCovariance[subset][2] += r * b;
            subsetCovariance[subset][3] += g * g;
            subsetCovariance[subset][4] += g * b;
            subsetCovariance[subset][5] += b * b;
        }

        float slamToLineSse = 0.0f;
        int[][] subsetAxis = new int[2][3];
        for (int subset = 0; subset < 2; subset++) {
            int blockMaxVar = Math.max(
                    subsetCovariance[subset][0],
                    Math.max(subsetCovariance[subset][3], subsetCovariance[subset][5]));
            float sc = 1.0f / (blockMaxVar + .0000125f);
            float wx = subsetCovariance[subset][0] * sc;
            float wy = subsetCovariance[subset][3] * sc;
            float wz = subsetCovariance[subset][5] * sc;
            Axis subsetPrincipalAxis = new Axis(
                    axisComponent(
                            subsetCovariance[subset][0],
                            subsetCovariance[subset][1],
                            subsetCovariance[subset][2],
                            wx,
                            wy,
                            wz),
                    axisComponent(
                            subsetCovariance[subset][1],
                            subsetCovariance[subset][3],
                            subsetCovariance[subset][4],
                            wx,
                            wy,
                            wz),
                    axisComponent(
                            subsetCovariance[subset][2],
                            subsetCovariance[subset][4],
                            subsetCovariance[subset][5],
                            wx,
                            wy,
                            wz));
            slamToLineSse += estimateSlamToLineSse3d(
                    subsetCovariance[subset],
                    subsetPrincipalAxis.red,
                    subsetPrincipalAxis.green,
                    subsetPrincipalAxis.blue);
            int[] scaled = scaledAxis(subsetPrincipalAxis);
            subsetAxis[subset][0] = scaled[0];
            subsetAxis[subset][1] = scaled[1];
            subsetAxis[subset][2] = scaled[2];
        }

        int[] lowDot = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] highDot = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            int dot = rgba[base] * subsetAxis[subset][0]
                    + rgba[base + 1] * subsetAxis[subset][1]
                    + rgba[base + 2] * subsetAxis[subset][2]
                    + i;
            lowDot[subset] = Math.min(lowDot[subset], dot);
            highDot[subset] = Math.max(highDot[subset], dot);
        }

        float mode1Quant = 0.0f;
        float mode3Quant = 0.0f;
        int[] spans = new int[4];
        for (int subset = 0; subset < 2; subset++) {
            int lowBase = (lowDot[subset] & 15) * 4;
            int highBase = (highDot[subset] & 15) * 4;
            for (int c = 0; c < 3; c++) {
                spans[c] = rgba[highBase + c] - rgba[lowBase + c];
            }
            spans[3] = 0;
            mode1Quant += analyticalQuantEstSse(
                    64, 8, 3, spans, UNIQUE_PBIT_DISCOUNT, subsetCounts[subset]);
            mode3Quant += analyticalQuantEstSse(
                    128, 4, 3, spans, SHARED_PBIT_DISCOUNT, subsetCounts[subset]);
        }

        float mode1Sse = slamToLineSse + mode1Quant;
        float mode3Sse = slamToLineSse + mode3Quant;
        return mode1Sse < mode3Sse ? new ModeEstimate(1, mode1Sse) : new ModeEstimate(3, mode3Sse);
    }

    private static int[] determine3Subsets(
            int[] rgba,
            float blockAxisR,
            float blockAxisG,
            float blockAxisB,
            int blockMeanR,
            int blockMeanG,
            int blockMeanB) {
        int[] subsetIndices = new int[16];
        int[][] subsetMeans = new int[2][3];
        int[] subsetCounts = new int[2];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int rd = rgba[base] - blockMeanR;
            int gd = rgba[base + 1] - blockMeanG;
            int bd = rgba[base + 2] - blockMeanB;
            int subset = rd * blockAxisR + gd * blockAxisG + bd * blockAxisB > 0.0f ? 1 : 0;
            subsetIndices[i] = subset;
            subsetMeans[subset][0] += rgba[base];
            subsetMeans[subset][1] += rgba[base + 1];
            subsetMeans[subset][2] += rgba[base + 2];
            subsetCounts[subset]++;
        }

        for (int subset = 0; subset < 2; subset++) {
            int count = subsetCounts[subset];
            if (count == 0) {
                return null;
            }
            int half = count >>> 1;
            subsetMeans[subset][0] = (subsetMeans[subset][0] + half) / count;
            subsetMeans[subset][1] = (subsetMeans[subset][1] + half) / count;
            subsetMeans[subset][2] = (subsetMeans[subset][2] + half) / count;
        }

        int[] subsetSse = new int[2];
        for (int i = 0; i < 16; i++) {
            int subset = subsetIndices[i];
            int base = i * 4;
            subsetSse[subset] += dist3(
                    rgba[base],
                    rgba[base + 1],
                    rgba[base + 2],
                    subsetMeans[subset][0],
                    subsetMeans[subset][1],
                    subsetMeans[subset][2]);
        }

        int subsetToSplit = subsetSse[1] > subsetSse[0] ? 1 : 0;
        if (subsetCounts[subsetToSplit] < 2) {
            return null;
        }

        int lowY = Integer.MAX_VALUE;
        int highY = 0;
        for (int i = 0; i < 16; i++) {
            if (subsetIndices[i] != subsetToSplit) {
                continue;
            }
            int base = i * 4;
            int y = ((rgba[base] + rgba[base + 1] + rgba[base + 2]) << 4) + i;
            lowY = Math.min(lowY, y);
            highY = Math.max(highY, y);
        }

        int lowIndex = lowY & 15;
        int highIndex = highY & 15;
        if (lowIndex == highIndex) {
            return null;
        }

        int lowBase = lowIndex * 4;
        int highBase = highIndex * 4;
        int[] result = new int[16];
        java.util.Arrays.fill(result, 2);
        for (int i = 0; i < 16; i++) {
            if (subsetIndices[i] != subsetToSplit) {
                continue;
            }
            int base = i * 4;
            int distLow = dist3(
                    rgba[lowBase],
                    rgba[lowBase + 1],
                    rgba[lowBase + 2],
                    rgba[base],
                    rgba[base + 1],
                    rgba[base + 2]);
            int distHigh = dist3(
                    rgba[highBase],
                    rgba[highBase + 1],
                    rgba[highBase + 2],
                    rgba[base],
                    rgba[base + 1],
                    rgba[base + 2]);
            result[i] = distHigh > distLow ? 1 : 0;
        }
        return result;
    }

    private static int[] pick3SubsetPartitionIndices(int[] desiredSubsets) {
        int bestScore = -1;
        int bestPartition = 0;
        int bestFirst16 = 0;
        for (int partition = 0; partition < 64; partition++) {
            int[] partitionMap = Bc7PartitionTables.partition3Map(partition);
            int[][] counts = new int[3][3];
            for (int i = 0; i < 16; i++) {
                counts[desiredSubsets[i]][partitionMap[i]]++;
            }
            int s0 = counts[0][0] + counts[1][1] + counts[2][2];
            int s1 = counts[0][0] + counts[1][2] + counts[2][1];
            int s2 = counts[0][1] + counts[1][0] + counts[2][2];
            int s3 = counts[0][1] + counts[1][2] + counts[2][0];
            int s4 = counts[0][2] + counts[1][0] + counts[2][1];
            int s5 = counts[0][2] + counts[1][1] + counts[2][0];
            int score = Math.max(Math.max(Math.max(s0, s1), Math.max(s2, s3)), Math.max(s4, s5));
            if (score > bestScore) {
                bestScore = score;
                bestPartition = partition;
                if (score == 16) {
                    if (partition <= 15) {
                        bestFirst16 = bestPartition;
                    }
                    break;
                }
            }
            if (partition == 15) {
                bestFirst16 = bestPartition;
            }
        }
        return new int[] {bestFirst16, bestPartition};
    }

    private static ThreeSubsetCandidate createThreeSubsetCandidate(
            int[] rgba,
            int partition,
            boolean mode0) {
        int[] partitionMap = Bc7PartitionTables.partition3Map(partition);
        ThreeSubsetCandidate candidate = new ThreeSubsetCandidate(partition, partitionMap);
        for (int i = 0; i < 16; i++) {
            int subset = partitionMap[i];
            int base = i * 4;
            candidate.totals[subset][0] += rgba[base];
            candidate.totals[subset][1] += rgba[base + 1];
            candidate.totals[subset][2] += rgba[base + 2];
            candidate.counts[subset]++;
        }

        int[][] means = new int[3][3];
        for (int subset = 0; subset < 3; subset++) {
            int half = candidate.counts[subset] >>> 1;
            means[subset][0] = (candidate.totals[subset][0] + half) / candidate.counts[subset];
            means[subset][1] = (candidate.totals[subset][1] + half) / candidate.counts[subset];
            means[subset][2] = (candidate.totals[subset][2] + half) / candidate.counts[subset];
        }

        int[][] covariance = new int[3][6];
        for (int i = 0; i < 16; i++) {
            int subset = partitionMap[i];
            int base = i * 4;
            int r = rgba[base] - means[subset][0];
            int g = rgba[base + 1] - means[subset][1];
            int b = rgba[base + 2] - means[subset][2];
            covariance[subset][0] += r * r;
            covariance[subset][1] += r * g;
            covariance[subset][2] += r * b;
            covariance[subset][3] += g * g;
            covariance[subset][4] += g * b;
            covariance[subset][5] += b * b;
        }

        int[][] subsetAxis = new int[3][3];
        for (int subset = 0; subset < 3; subset++) {
            int blockMaxVar = Math.max(
                    covariance[subset][0],
                    Math.max(covariance[subset][3], covariance[subset][5]));
            float sc = 1.0f / (blockMaxVar + .0000125f);
            float wx = covariance[subset][0] * sc;
            float wy = covariance[subset][3] * sc;
            float wz = covariance[subset][5] * sc;
            boolean balancedGb = covariance[subset][0] == 0
                    && covariance[subset][1] == 0
                    && covariance[subset][2] == 0
                    && covariance[subset][3] == covariance[subset][5]
                    && covariance[subset][4] < 0;
            float axisR = balancedGb ? axisComponentMode6(
                    covariance[subset][0],
                    covariance[subset][1],
                    covariance[subset][2],
                    wx,
                    wy,
                    wz) : axisComponent(
                    covariance[subset][0],
                    covariance[subset][1],
                    covariance[subset][2],
                    wx,
                    wy,
                    wz);
            float axisG = balancedGb ? axisComponentMode6(
                    covariance[subset][1],
                    covariance[subset][3],
                    covariance[subset][4],
                    wx,
                    wy,
                    wz) : axisComponent(
                    covariance[subset][1],
                    covariance[subset][3],
                    covariance[subset][4],
                    wx,
                    wy,
                    wz);
            float axisB = balancedGb ? axisComponentMode6(
                    covariance[subset][2],
                    covariance[subset][4],
                    covariance[subset][5],
                    wx,
                    wy,
                    wz) : axisComponent(
                    covariance[subset][2],
                    covariance[subset][4],
                    covariance[subset][5],
                    wx,
                    wy,
                    wz);
            candidate.slamToLineSse += estimateSlamToLineSse3d(covariance[subset], axisR, axisG, axisB);

            int scaledAxisR = 306;
            int scaledAxisG = 601;
            int scaledAxisB = 117;
            float maxAxis = Math.max(Math.abs(axisR), Math.max(Math.abs(axisG), Math.abs(axisB)));
            if (Math.abs(maxAxis) >= SMALL_FLOAT_VAL) {
                float m = 2048.0f / maxAxis;
                scaledAxisR = (int) (axisR * m);
                scaledAxisG = (int) (axisG * m);
                scaledAxisB = (int) (axisB * m);
            }
            subsetAxis[subset][0] = scaledAxisR << 4;
            subsetAxis[subset][1] = scaledAxisG << 4;
            subsetAxis[subset][2] = scaledAxisB << 4;
        }

        int[] lowDot = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] highDot = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < 16; i++) {
            int subset = partitionMap[i];
            int base = i * 4;
            int dot = rgba[base] * subsetAxis[subset][0]
                    + rgba[base + 1] * subsetAxis[subset][1]
                    + rgba[base + 2] * subsetAxis[subset][2]
                    + i;
            lowDot[subset] = Math.min(lowDot[subset], dot);
            highDot[subset] = Math.max(highDot[subset], dot);
        }

        int[] spans = new int[4];
        for (int subset = 0; subset < 3; subset++) {
            candidate.lowPixel[subset] = lowDot[subset] & 15;
            candidate.highPixel[subset] = highDot[subset] & 15;
            int lowBase = candidate.lowPixel[subset] * 4;
            int highBase = candidate.highPixel[subset] * 4;
            for (int c = 0; c < 3; c++) {
                spans[c] = rgba[highBase + c] - rgba[lowBase + c];
            }
            spans[3] = 0;
            if (mode0) {
                candidate.quantSse += analyticalQuantEstSse(
                        16, 8, 3, spans, UNIQUE_PBIT_DISCOUNT, candidate.counts[subset]);
            } else {
                candidate.quantSse += analyticalQuantEstSse(
                        32, 4, 3, spans, 1.0f, candidate.counts[subset]);
            }
        }
        return candidate;
    }

    private static ModeEstimate estimateMode0Or2Rgb(int[] rgba, Axis axis, int[] totals) {
        int meanR = (totals[0] + 8) >> 4;
        int meanG = (totals[1] + 8) >> 4;
        int meanB = (totals[2] + 8) >> 4;
        int[] desiredSubsets = determine3Subsets(rgba, axis.red, axis.green, axis.blue, meanR, meanG, meanB);
        if (desiredSubsets == null) {
            return null;
        }
        int[] partitions = pick3SubsetPartitionIndices(desiredSubsets);
        ThreeSubsetCandidate mode0 = createThreeSubsetCandidate(rgba, partitions[0], true);
        ThreeSubsetCandidate mode2 = createThreeSubsetCandidate(rgba, partitions[1], false);
        return mode0.estimatedSse() < mode2.estimatedSse()
                ? new ModeEstimate(0, mode0.estimatedSse())
                : new ModeEstimate(2, mode2.estimatedSse());
    }

    private static void writeMode1Rgb(
            int[] rgba,
            byte[] output,
            int offset,
            int partition,
            int partitionBits,
            int[][] subsetTotals,
            int[] lowPixel,
            int[] highPixel) {
        int[][] low = new int[2][3];
        int[][] high = new int[2][3];
        int[] pbits = new int[2];
        for (int subset = 0; subset < 2; subset++) {
            int[] endpoints = determineSharedPbits(rgba, lowPixel[subset], highPixel[subset], 6);
            low[subset][0] = endpoints[0];
            low[subset][1] = endpoints[1];
            low[subset][2] = endpoints[2];
            high[subset][0] = endpoints[3];
            high[subset][1] = endpoints[4];
            high[subset][2] = endpoints[5];
            pbits[subset] = endpoints[6];
        }

        int[] selectors = evalWeightsMode1Rgb(rgba, low, high, pbits, partitionBits);
        float[][] refined = computeSubsetLeastSquaresEndpoints3d(
                rgba, selectors, subsetTotals, partitionBits, LS_WEIGHTS3);
        for (int subset = 0; subset < 2; subset++) {
            if (refined[subset * 2] == null) {
                continue;
            }
            int[] endpoints = determineSharedPbits(refined[subset * 2], refined[subset * 2 + 1], 6);
            low[subset][0] = endpoints[0];
            low[subset][1] = endpoints[1];
            low[subset][2] = endpoints[2];
            high[subset][0] = endpoints[3];
            high[subset][1] = endpoints[4];
            high[subset][2] = endpoints[5];
            pbits[subset] = endpoints[6];
        }
        selectors = evalWeightsMode1Rgb(rgba, low, high, pbits, partitionBits);

        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(1);
        block.partition = partition;
        block.partitionMap = Bc7PartitionTables.partition2Map(partition);
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 3; c++) {
                block.low[subset][c] = low[subset][c];
                block.high[subset][c] = high[subset][c];
            }
            block.pbits[subset][0] = pbits[subset];
        }
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    private static void packAstcTwoSubsetMode1(
            int[] endpoints0,
            int[] endpoints1,
            int partition,
            int[] selectors,
            byte[] output,
            int offset) {
        float scale = 1.0f / 255.0f;
        int[][] low = new int[2][3];
        int[][] high = new int[2][3];
        int[] pbits = new int[2];
        int[][] endpoints = {
            endpoints0,
            endpoints1
        };
        for (int subset = 0; subset < 2; subset++) {
            float[] lowEndpoint = {
                endpoints[subset][0] * scale,
                endpoints[subset][1] * scale,
                endpoints[subset][2] * scale
            };
            float[] highEndpoint = {
                endpoints[subset][4] * scale,
                endpoints[subset][5] * scale,
                endpoints[subset][6] * scale
            };
            int[] quantized = determineSharedPbits(lowEndpoint, highEndpoint, 6);
            low[subset][0] = quantized[0];
            low[subset][1] = quantized[1];
            low[subset][2] = quantized[2];
            high[subset][0] = quantized[3];
            high[subset][1] = quantized[4];
            high[subset][2] = quantized[5];
            pbits[subset] = quantized[6];
        }

        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(1);
        block.partition = partition;
        block.partitionMap = Bc7PartitionTables.partition2Map(partition);
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 3; c++) {
                block.low[subset][c] = low[subset][c];
                block.high[subset][c] = high[subset][c];
            }
            block.pbits[subset][0] = pbits[subset];
        }
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    private static int astc8x6CrossWeight(int[] weights, int dx, int y, int x) {
        return y < 2
                ? weights[(dx * 4 + x) + (y + 4) * 8]
                : weights[(dx * 4 + x) + (y - 2) * 8];
    }

    private static int channelInterpolate(int low, int high, int weight, boolean srgbDecode) {
        int scaledLow = srgbDecode ? (low << 8) | 0x80 : (low << 8) | low;
        int scaledHigh = srgbDecode ? (high << 8) | 0x80 : (high << 8) | high;
        return weightInterpolate(scaledLow, scaledHigh, weight) >> 8;
    }

    private static int weightInterpolate(int low, int high, int weight) {
        return (low * (64 - weight) + high * weight + 32) >> 6;
    }

    private static void writeMode3Rgb(
            int[] rgba,
            byte[] output,
            int offset,
            int partition,
            int partitionBits,
            int[][] subsetTotals,
            int[] lowPixel,
            int[] highPixel) {
        int[][] low = new int[2][3];
        int[][] high = new int[2][3];
        int[][] pbits = new int[2][2];
        for (int subset = 0; subset < 2; subset++) {
            int[] endpoints = determineUniquePbits(rgba, lowPixel[subset], highPixel[subset], 7);
            low[subset][0] = endpoints[0];
            low[subset][1] = endpoints[1];
            low[subset][2] = endpoints[2];
            high[subset][0] = endpoints[3];
            high[subset][1] = endpoints[4];
            high[subset][2] = endpoints[5];
            pbits[subset][0] = endpoints[6];
            pbits[subset][1] = endpoints[7];
        }

        int[] selectors = evalWeightsMode3Rgb(rgba, low, high, pbits, partition, partitionBits);
        float[][] refined = computeSubsetLeastSquaresEndpoints3d(
                rgba, selectors, subsetTotals, partitionBits, LS_WEIGHTS2);
        for (int subset = 0; subset < 2; subset++) {
            if (refined[subset * 2] == null) {
                continue;
            }
            int[] endpoints = determineUniquePbits(refined[subset * 2], refined[subset * 2 + 1], 7);
            low[subset][0] = endpoints[0];
            low[subset][1] = endpoints[1];
            low[subset][2] = endpoints[2];
            high[subset][0] = endpoints[3];
            high[subset][1] = endpoints[4];
            high[subset][2] = endpoints[5];
            pbits[subset][0] = endpoints[6];
            pbits[subset][1] = endpoints[7];
        }
        selectors = evalWeightsMode3Rgb(rgba, low, high, pbits, partition, partitionBits);

        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(3);
        block.partition = partition;
        block.partitionMap = Bc7PartitionTables.partition2Map(partition);
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 3; c++) {
                block.low[subset][c] = low[subset][c];
                block.high[subset][c] = high[subset][c];
            }
            block.pbits[subset][0] = pbits[subset][0];
            block.pbits[subset][1] = pbits[subset][1];
        }
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    private static void writeMode0Rgb(
            int[] rgba,
            byte[] output,
            int offset,
            ThreeSubsetCandidate candidate) {
        int[][] low = new int[3][3];
        int[][] high = new int[3][3];
        int[][] pbits = new int[3][2];
        for (int subset = 0; subset < 3; subset++) {
            int[] endpoints = determineUniquePbits(
                    rgba, candidate.lowPixel[subset], candidate.highPixel[subset], 4);
            low[subset][0] = endpoints[0];
            low[subset][1] = endpoints[1];
            low[subset][2] = endpoints[2];
            high[subset][0] = endpoints[3];
            high[subset][1] = endpoints[4];
            high[subset][2] = endpoints[5];
            pbits[subset][0] = endpoints[6];
            pbits[subset][1] = endpoints[7];
        }

        int[] selectors = evalWeightsMode0Rgb(rgba, low, high, pbits, candidate.partitionMap);
        float[][] refined = computeSubsetLeastSquaresEndpoints3d(
                rgba, selectors, candidate.totals, candidate.partitionMap, LS_WEIGHTS3);
        for (int subset = 0; subset < 3; subset++) {
            if (refined[subset * 2] == null) {
                continue;
            }
            int[] endpoints = determineUniquePbits(refined[subset * 2], refined[subset * 2 + 1], 4);
            low[subset][0] = endpoints[0];
            low[subset][1] = endpoints[1];
            low[subset][2] = endpoints[2];
            high[subset][0] = endpoints[3];
            high[subset][1] = endpoints[4];
            high[subset][2] = endpoints[5];
            pbits[subset][0] = endpoints[6];
            pbits[subset][1] = endpoints[7];
        }
        selectors = evalWeightsMode0Rgb(rgba, low, high, pbits, candidate.partitionMap);

        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(0);
        block.partition = candidate.partition;
        block.partitionMap = candidate.partitionMap;
        for (int subset = 0; subset < 3; subset++) {
            for (int c = 0; c < 3; c++) {
                block.low[subset][c] = low[subset][c];
                block.high[subset][c] = high[subset][c];
            }
            block.pbits[subset][0] = pbits[subset][0];
            block.pbits[subset][1] = pbits[subset][1];
        }
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    private static void writeMode2Rgb(
            int[] rgba,
            byte[] output,
            int offset,
            ThreeSubsetCandidate candidate) {
        int[][] low = new int[3][3];
        int[][] high = new int[3][3];
        for (int subset = 0; subset < 3; subset++) {
            int lowBase = candidate.lowPixel[subset] * 4;
            low[subset][0] = to5(rgba[lowBase]);
            low[subset][1] = to5(rgba[lowBase + 1]);
            low[subset][2] = to5(rgba[lowBase + 2]);
            int highBase = candidate.highPixel[subset] * 4;
            high[subset][0] = to5(rgba[highBase]);
            high[subset][1] = to5(rgba[highBase + 1]);
            high[subset][2] = to5(rgba[highBase + 2]);
        }

        int[] selectors = evalWeightsMode2Rgb(rgba, low, high, candidate.partitionMap);
        float[][] refined = computeSubsetLeastSquaresEndpoints3d(
                rgba, selectors, candidate.totals, candidate.partitionMap, LS_WEIGHTS2);
        for (int subset = 0; subset < 3; subset++) {
            if (refined[subset * 2] == null) {
                continue;
            }
            low[subset][0] = to5Clamp(refined[subset * 2][0] * 255.0f);
            low[subset][1] = to5Clamp(refined[subset * 2][1] * 255.0f);
            low[subset][2] = to5Clamp(refined[subset * 2][2] * 255.0f);
            high[subset][0] = to5Clamp(refined[subset * 2 + 1][0] * 255.0f);
            high[subset][1] = to5Clamp(refined[subset * 2 + 1][1] * 255.0f);
            high[subset][2] = to5Clamp(refined[subset * 2 + 1][2] * 255.0f);
        }
        selectors = evalWeightsMode2Rgb(rgba, low, high, candidate.partitionMap);

        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(2);
        block.partition = candidate.partition;
        block.partitionMap = candidate.partitionMap;
        for (int subset = 0; subset < 3; subset++) {
            for (int c = 0; c < 3; c++) {
                block.low[subset][c] = low[subset][c];
                block.high[subset][c] = high[subset][c];
            }
        }
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    private static void copyBlock(byte[] rgba, int width, int height, int startX, int startY, int[] block) {
        int out = 0;
        for (int y = 0; y < 4; y++) {
            int sourceY = Math.min(startY + y, height - 1);
            for (int x = 0; x < 4; x++) {
                int sourceX = Math.min(startX + x, width - 1);
                int input = (sourceY * width + sourceX) * 4;
                block[out++] = Byte.toUnsignedInt(rgba[input]);
                block[out++] = Byte.toUnsignedInt(rgba[input + 1]);
                block[out++] = Byte.toUnsignedInt(rgba[input + 2]);
                block[out++] = Byte.toUnsignedInt(rgba[input + 3]);
            }
        }
    }

    private static boolean isSolid(int[] rgba) {
        int r = rgba[0];
        int g = rgba[1];
        int b = rgba[2];
        int a = rgba[3];
        for (int i = 1; i < 16; i++) {
            int base = i * 4;
            if (rgba[base] != r || rgba[base + 1] != g || rgba[base + 2] != b || rgba[base + 3] != a) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpaque(byte[] rgba) {
        for (int i = 3; i < rgba.length; i += 4) {
            if (Byte.toUnsignedInt(rgba[i]) != 255) {
                return false;
            }
        }
        return true;
    }

    private static int[] covariance(int[] rgba) {
        return covariance(rgba, totals(rgba));
    }

    private static int[] totals(int[] rgba) {
        int[] totals = new int[3];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            totals[0] += rgba[base];
            totals[1] += rgba[base + 1];
            totals[2] += rgba[base + 2];
        }
        return totals;
    }

    private static int[] totals4(int[] rgba) {
        int[] totals = new int[4];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            totals[0] += rgba[base];
            totals[1] += rgba[base + 1];
            totals[2] += rgba[base + 2];
            totals[3] += rgba[base + 3];
        }
        return totals;
    }

    private static int[] covariance(int[] rgba, int[] totals) {
        int meanR = (totals[0] + 8) >> 4;
        int meanG = (totals[1] + 8) >> 4;
        int meanB = (totals[2] + 8) >> 4;
        int[] cov = new int[6];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int r = rgba[base] - meanR;
            int g = rgba[base + 1] - meanG;
            int b = rgba[base + 2] - meanB;
            cov[0] += r * r;
            cov[1] += r * g;
            cov[2] += r * b;
            cov[3] += g * g;
            cov[4] += g * b;
            cov[5] += b * b;
        }
        return cov;
    }

    private static int[] covariance4(int[] rgba, int[] totals) {
        int meanR = (totals[0] + 8) >> 4;
        int meanG = (totals[1] + 8) >> 4;
        int meanB = (totals[2] + 8) >> 4;
        int meanA = (totals[3] + 8) >> 4;
        int[] cov = new int[10];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int r = rgba[base] - meanR;
            int g = rgba[base + 1] - meanG;
            int b = rgba[base + 2] - meanB;
            cov[0] += r * r;
            cov[1] += r * g;
            cov[2] += r * b;
            int a = rgba[base + 3] - meanA;
            cov[3] += r * a;
            cov[4] += g * g;
            cov[5] += g * b;
            cov[6] += g * a;
            cov[7] += b * b;
            cov[8] += b * a;
            cov[9] += a * a;
        }
        return cov;
    }

    private static int desiredDualPlaneChannel(int[] cov, int blockMaxVar) {
        if (blockMaxVar < DP_BLOCK_VAR_THRESH) {
            return -1;
        }
        boolean hasR = cov[0] > 16;
        boolean hasG = cov[3] > 16;
        boolean hasB = cov[5] > 16;
        int activeChannels = (hasR ? 1 : 0) + (hasG ? 1 : 0) + (hasB ? 1 : 0);
        if (activeChannels < 2) {
            return -1;
        }

        float rVar = cov[0];
        float gVar = cov[3];
        float bVar = cov[5];
        float rgCorr = hasR && hasG ? correlation(cov[1], rVar, gVar) : 1.0f;
        float rbCorr = hasR && hasB ? correlation(cov[2], rVar, bVar) : 1.0f;
        float gbCorr = hasG && hasB ? correlation(cov[4], gVar, bVar) : 1.0f;
        if (Math.min(rgCorr, Math.min(rbCorr, gbCorr)) >= STRONG_CORR_THRESH) {
            return -1;
        }
        if (activeChannels == 2) {
            if (!hasR) {
                return 1;
            }
            return !hasG ? 0 : 0;
        }
        if (rgCorr < gbCorr && rbCorr < gbCorr) {
            return 0;
        }
        if (rgCorr < rbCorr && gbCorr < rbCorr) {
            return 1;
        }
        return 2;
    }

    private static float correlation(int covariance, float firstVariance, float secondVariance) {
        return Math.abs((float) covariance / (float) Math.sqrt(firstVariance * secondVariance));
    }

    private static int desiredDualPlaneChannelRgba(int[] cov, int blockMaxVar) {
        if (blockMaxVar < DP_BLOCK_VAR_THRESH_RGBA) {
            return -1;
        }
        float rVar = cov[0];
        float gVar = cov[4];
        float bVar = cov[7];
        float aVar = cov[9];
        if (cov[9] > 0) {
            float raCorr = cov[0] != 0 ? correlation(cov[3], rVar, aVar) : 1.0f;
            float gaCorr = cov[4] != 0 ? correlation(cov[6], gVar, aVar) : 1.0f;
            float baCorr = cov[7] != 0 ? correlation(cov[8], bVar, aVar) : 1.0f;
            if (Math.min(raCorr, Math.min(gaCorr, baCorr)) < ALPHA_DECORR_THRESHOLD) {
                return 3;
            }
        }

        boolean hasR = cov[0] > 16;
        boolean hasG = cov[4] > 16;
        boolean hasB = cov[7] > 16;
        int activeRgbChannels = (hasR ? 1 : 0) + (hasG ? 1 : 0) + (hasB ? 1 : 0);
        if (activeRgbChannels < 2) {
            return -1;
        }

        float rgCorr = hasR && hasG ? correlation(cov[1], rVar, gVar) : 1.0f;
        float rbCorr = hasR && hasB ? correlation(cov[2], rVar, bVar) : 1.0f;
        float gbCorr = hasG && hasB ? correlation(cov[5], gVar, bVar) : 1.0f;
        if (Math.min(rgCorr, Math.min(rbCorr, gbCorr)) >= STRONG_CORR_THRESH_RGBA) {
            return -1;
        }
        if (activeRgbChannels == 2) {
            if (!hasR) {
                return 1;
            }
            return !hasG ? 0 : 0;
        }
        if (rgCorr < gbCorr && rbCorr < gbCorr) {
            return 0;
        }
        if (rgCorr < rbCorr && gbCorr < rbCorr) {
            return 1;
        }
        return 2;
    }

    private static ModeEstimate estimateMode4Or5Rgb(int[] rgba, int dualPlaneChannel) {
        int[] pixels = swapDualPlaneChannel(rgba, dualPlaneChannel);
        int[] totals = totals4(pixels);
        int[] min = {255, 255, 255, 255};
        int[] max = {0, 0, 0, 0};
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            for (int c = 0; c < 4; c++) {
                min[c] = Math.min(min[c], pixels[base + c]);
                max[c] = Math.max(max[c], pixels[base + c]);
            }
        }

        int[] cov3 = covariance(pixels, new int[] {totals[0], totals[1], totals[2]});
        int blockMaxVar3 = Math.max(cov3[0], Math.max(cov3[3], cov3[5]));
        Axis axis = principalAxis(cov3, blockMaxVar3);
        float lineSse = estimateSlamToLineSse3d(cov3, axis.red, axis.green, axis.blue);

        int[] minMax = projectMinMax(pixels, axis.red, axis.green, axis.blue);
        int lowIndex = minMax[0];
        int highIndex = minMax[1];
        int[] rgbSpans = {
            pixels[highIndex * 4] - pixels[lowIndex * 4],
            pixels[highIndex * 4 + 1] - pixels[lowIndex * 4 + 1],
            pixels[highIndex * 4 + 2] - pixels[lowIndex * 4 + 2],
            0
        };
        int alphaSpan = max[3] - min[3];

        float mode4Rgb3Alpha2 = lineSse
                + analyticalQuantEstSse(32, 8, 3, rgbSpans, 1.0f, 16)
                + analyticalQuantEstSse(64, 4, alphaSpan, 1.0f, 1.0f, 16);
        float mode4Rgb2Alpha3 = lineSse
                + analyticalQuantEstSse(32, 4, 3, rgbSpans, 1.0f, 16)
                + analyticalQuantEstSse(64, 8, alphaSpan, 1.0f, 1.0f, 16);
        float mode5 = lineSse
                + analyticalQuantEstSse(128, 4, 3, rgbSpans, 1.0f, 16)
                + analyticalQuantEstSse(256, 4, alphaSpan, 1.0f, 1.0f, 16);
        if (mode5 < Math.min(mode4Rgb3Alpha2, mode4Rgb2Alpha3)) {
            return new ModeEstimate(5, mode5);
        }
        float mode4 = Math.min(mode4Rgb3Alpha2, mode4Rgb2Alpha3);
        return new ModeEstimate(4, mode4);
    }

    private static ModeEstimate estimateMode6Rgba(int[] rgba, int[] covariance) {
        Axis4 axis = principalAxis4(covariance);
        int[] spans = minMaxSpans4(rgba);
        float sse = estimateSlamToLineSse4d(covariance, axis)
                + analyticalQuantEstSse(128, 16, 4, spans, UNIQUE_PBIT_DISCOUNT, 16);
        return new ModeEstimate(6, sse);
    }

    private static ModeEstimate estimateMode7Rgba(int[] rgba, int[] covariance, int[] totals) {
        Axis4 axis = principalAxis4(covariance);
        int meanR = (totals[0] + 8) >> 4;
        int meanG = (totals[1] + 8) >> 4;
        int meanB = (totals[2] + 8) >> 4;
        int meanA = (totals[3] + 8) >> 4;
        int desiredPatternBits = 0;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float r = rgba[base] - meanR;
            float g = rgba[base + 1] - meanG;
            float b = rgba[base + 2] - meanB;
            float a = rgba[base + 3] - meanA;
            if (r * axis.red + g * axis.green + b * axis.blue + a * axis.alpha > 0.0f) {
                desiredPatternBits |= 1 << i;
            }
        }

        int bestDiff = Integer.MAX_VALUE;
        int bestPartitionBits = 0;
        for (int partition = 0; partition < 64; partition++) {
            int partitionBits = Bc7PartitionTables.partition2Mask(partition);
            int diff = Integer.bitCount(partitionBits ^ desiredPatternBits);
            int minDiff = (Math.min(diff, 16 - diff) << 8) | partition;
            if (minDiff < bestDiff) {
                bestDiff = minDiff;
                bestPartitionBits = partitionBits;
            }
        }

        int[][] subsetTotals = new int[2][4];
        int[] subsetCounts = new int[2];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            for (int c = 0; c < 4; c++) {
                subsetTotals[subset][c] += rgba[base + c];
            }
            subsetCounts[subset]++;
        }

        int[][] subsetMeans = new int[2][4];
        for (int subset = 0; subset < 2; subset++) {
            int half = subsetCounts[subset] >>> 1;
            for (int c = 0; c < 4; c++) {
                subsetMeans[subset][c] = (subsetTotals[subset][c] + half) / subsetCounts[subset];
            }
        }

        int[][] subsetCovariance = new int[2][10];
        for (int i = 0; i < 16; i++) {
            int subset = (bestPartitionBits >>> i) & 1;
            int base = i * 4;
            int r = rgba[base] - subsetMeans[subset][0];
            int g = rgba[base + 1] - subsetMeans[subset][1];
            int b = rgba[base + 2] - subsetMeans[subset][2];
            subsetCovariance[subset][0] += r * r;
            subsetCovariance[subset][1] += r * g;
            subsetCovariance[subset][2] += r * b;
            int a = rgba[base + 3] - subsetMeans[subset][3];
            subsetCovariance[subset][3] += r * a;
            subsetCovariance[subset][4] += g * g;
            subsetCovariance[subset][5] += g * b;
            subsetCovariance[subset][6] += g * a;
            subsetCovariance[subset][7] += b * b;
            subsetCovariance[subset][8] += b * a;
            subsetCovariance[subset][9] += a * a;
        }

        float sse = 0.0f;
        for (int subset = 0; subset < 2; subset++) {
            Axis4 subsetAxis = subsetAxis4(subsetCovariance[subset]);
            sse += estimateSlamToLineSse4d(subsetCovariance[subset], subsetAxis);
            int[] scaled = scaledAxis(subsetAxis);
            int lowDot = Integer.MAX_VALUE;
            int highDot = Integer.MIN_VALUE;
            for (int i = 0; i < 16; i++) {
                if (((bestPartitionBits >>> i) & 1) != subset) {
                    continue;
                }
                int base = i * 4;
                int dot = rgba[base] * scaled[0]
                        + rgba[base + 1] * scaled[1]
                        + rgba[base + 2] * scaled[2]
                        + rgba[base + 3] * scaled[3]
                        + i;
                lowDot = Math.min(lowDot, dot);
                highDot = Math.max(highDot, dot);
            }
            int lowBase = (lowDot & 15) * 4;
            int highBase = (highDot & 15) * 4;
            int[] spans = {
                rgba[highBase] - rgba[lowBase],
                rgba[highBase + 1] - rgba[lowBase + 1],
                rgba[highBase + 2] - rgba[lowBase + 2],
                rgba[highBase + 3] - rgba[lowBase + 3]
            };
            sse += analyticalQuantEstSse(32, 4, 4, spans, UNIQUE_PBIT_DISCOUNT, subsetCounts[subset]);
        }
        return new ModeEstimate(7, sse);
    }

    private static int[] determineUniquePbitEndpoint(int[] rgba, int pixelIndex, boolean lowEndpoint) {
        int base = pixelIndex * 4;
        float[] color = {
            rgba[base] * (1.0f / 255.0f),
            rgba[base + 1] * (1.0f / 255.0f),
            rgba[base + 2] * (1.0f / 255.0f)
        };
        float bestError = Float.MAX_VALUE;
        int bestPbit = 0;
        int[] bestColor = new int[3];
        for (int pbit = 0; pbit < 2; pbit++) {
            int[] quantized = new int[3];
            int[] scaled = new int[3];
            for (int c = 0; c < 3; c++) {
                int value = (int) ((color[c] * 255.0f - pbit) * 0.5f + .5f);
                quantized[c] = clamp(value * 2 + pbit, pbit, 254 + pbit);
                scaled[c] = quantized[c];
            }

            float error = 0.0f;
            for (int c = 0; c < 3; c++) {
                float delta = Math.fma(-color[c], 255.0f, scaled[c]);
                error += delta * delta;
            }
            if (error < bestError) {
                bestError = error;
                bestPbit = pbit;
                for (int c = 0; c < 3; c++) {
                    bestColor[c] = quantized[c] >>> 1;
                }
            }
        }
        return new int[] {bestColor[0], bestColor[1], bestColor[2], bestPbit};
    }

    private static int[] determineUniquePbitEndpoint(float[] color) {
        float bestError = Float.MAX_VALUE;
        int bestPbit = 0;
        int[] bestColor = new int[3];
        for (int pbit = 0; pbit < 2; pbit++) {
            int[] quantized = new int[3];
            int[] scaled = new int[3];
            for (int c = 0; c < 3; c++) {
                int value = (int) ((color[c] * 255.0f - pbit) * 0.5f + .5f);
                quantized[c] = clamp(value * 2 + pbit, pbit, 254 + pbit);
                scaled[c] = quantized[c];
            }

            float error = 0.0f;
            for (int c = 0; c < 3; c++) {
                float delta = Math.fma(-color[c], 255.0f, scaled[c]);
                error += delta * delta;
            }
            if (error < bestError) {
                bestError = error;
                bestPbit = pbit;
                for (int c = 0; c < 3; c++) {
                    bestColor[c] = quantized[c] >>> 1;
                }
            }
        }
        return new int[] {bestColor[0], bestColor[1], bestColor[2], bestPbit};
    }

    private static int[] determineUniquePbits(int[] rgba, int lowPixel, int highPixel, int compBits) {
        return determineUniquePbits(
                normalizedRgb(rgba, lowPixel),
                normalizedRgb(rgba, highPixel),
                compBits);
    }

    private static int[] determineUniquePbits(float[] lowColor, float[] highColor, int compBits) {
        int totalBits = compBits + 1;
        int scaleMax = (1 << totalBits) - 1;
        float scale = scaleMax;
        float bestLowError = Float.MAX_VALUE;
        float bestHighError = Float.MAX_VALUE;
        int bestLowPbit = 0;
        int bestHighPbit = 0;
        int[] bestLow = new int[3];
        int[] bestHigh = new int[3];
        for (int pbit = 0; pbit < 2; pbit++) {
            int[] lowQuantized = new int[3];
            int[] highQuantized = new int[3];
            int[] lowScaled = new int[3];
            int[] highScaled = new int[3];
            for (int c = 0; c < 3; c++) {
                lowQuantized[c] = clamp(
                        (int) ((lowColor[c] * scale - pbit) * 0.5f + .5f) * 2 + pbit,
                        pbit,
                        scaleMax - 1 + pbit);
                highQuantized[c] = clamp(
                        (int) ((highColor[c] * scale - pbit) * 0.5f + .5f) * 2 + pbit,
                        pbit,
                        scaleMax - 1 + pbit);
                lowScaled[c] = scaleEndpoint(lowQuantized[c], totalBits);
                highScaled[c] = scaleEndpoint(highQuantized[c], totalBits);
            }

            float lowError = 0.0f;
            float highError = 0.0f;
            for (int c = 0; c < 3; c++) {
                float lowDelta = Math.fma(-lowColor[c], 255.0f, lowScaled[c]);
                float highDelta = Math.fma(-highColor[c], 255.0f, highScaled[c]);
                lowError += lowDelta * lowDelta;
                highError += highDelta * highDelta;
            }
            if (lowError < bestLowError) {
                bestLowError = lowError;
                bestLowPbit = pbit;
                for (int c = 0; c < 3; c++) {
                    bestLow[c] = lowQuantized[c] >>> 1;
                }
            }
            if (highError < bestHighError) {
                bestHighError = highError;
                bestHighPbit = pbit;
                for (int c = 0; c < 3; c++) {
                    bestHigh[c] = highQuantized[c] >>> 1;
                }
            }
        }
        return new int[] {
            bestLow[0], bestLow[1], bestLow[2],
            bestHigh[0], bestHigh[1], bestHigh[2],
            bestLowPbit, bestHighPbit
        };
    }

    private static int[] determineUniquePbits4(int[] rgba, int lowPixel, int highPixel, int compBits) {
        return determineUniquePbits4(
                normalizedRgba(rgba, lowPixel),
                normalizedRgba(rgba, highPixel),
                compBits);
    }

    private static int[] determineUniquePbits4(float[] lowColor, float[] highColor, int compBits) {
        int totalBits = compBits + 1;
        int scaleMax = (1 << totalBits) - 1;
        float scale = scaleMax;
        float bestLowError = Float.MAX_VALUE;
        float bestHighError = Float.MAX_VALUE;
        int bestLowPbit = 0;
        int bestHighPbit = 0;
        int[] bestLow = new int[4];
        int[] bestHigh = new int[4];
        for (int pbit = 0; pbit < 2; pbit++) {
            int[] lowQuantized = new int[4];
            int[] highQuantized = new int[4];
            int[] lowScaled = new int[4];
            int[] highScaled = new int[4];
            for (int c = 0; c < 4; c++) {
                lowQuantized[c] = clamp(
                        (int) ((lowColor[c] * scale - pbit) * 0.5f + .5f) * 2 + pbit,
                        pbit,
                        scaleMax - 1 + pbit);
                highQuantized[c] = clamp(
                        (int) ((highColor[c] * scale - pbit) * 0.5f + .5f) * 2 + pbit,
                        pbit,
                        scaleMax - 1 + pbit);
                lowScaled[c] = scaleEndpoint(lowQuantized[c], totalBits);
                highScaled[c] = scaleEndpoint(highQuantized[c], totalBits);
            }

            float lowError = 0.0f;
            float highError = 0.0f;
            for (int c = 0; c < 4; c++) {
                float lowDelta = Math.fma(-lowColor[c], 255.0f, lowScaled[c]);
                float highDelta = Math.fma(-highColor[c], 255.0f, highScaled[c]);
                lowError += lowDelta * lowDelta;
                highError += highDelta * highDelta;
            }
            if (lowError < bestLowError) {
                bestLowError = lowError;
                bestLowPbit = pbit;
                for (int c = 0; c < 4; c++) {
                    bestLow[c] = lowQuantized[c] >>> 1;
                }
            }
            if (highError < bestHighError) {
                bestHighError = highError;
                bestHighPbit = pbit;
                for (int c = 0; c < 4; c++) {
                    bestHigh[c] = highQuantized[c] >>> 1;
                }
            }
        }
        return new int[] {
            bestLow[0], bestLow[1], bestLow[2], bestLow[3],
            bestHigh[0], bestHigh[1], bestHigh[2], bestHigh[3],
            bestLowPbit, bestHighPbit
        };
    }

    private static int[] determineSharedPbits(int[] rgba, int lowPixel, int highPixel, int compBits) {
        return determineSharedPbits(
                normalizedRgb(rgba, lowPixel),
                normalizedRgb(rgba, highPixel),
                compBits);
    }

    private static int[] determineSharedPbits(float[] lowColor, float[] highColor, int compBits) {
        int totalBits = compBits + 1;
        int scaleMax = (1 << totalBits) - 1;
        float scale = scaleMax;
        float bestError = Float.MAX_VALUE;
        int bestPbit = 0;
        int[] bestLow = new int[3];
        int[] bestHigh = new int[3];
        for (int pbit = 0; pbit < 2; pbit++) {
            int[] lowQuantized = new int[3];
            int[] highQuantized = new int[3];
            int[] lowScaled = new int[3];
            int[] highScaled = new int[3];
            for (int c = 0; c < 3; c++) {
                lowQuantized[c] = clamp(
                        (int) ((lowColor[c] * scale - pbit) * 0.5f + .5f) * 2 + pbit,
                        pbit,
                        scaleMax - 1 + pbit);
                highQuantized[c] = clamp(
                        (int) ((highColor[c] * scale - pbit) * 0.5f + .5f) * 2 + pbit,
                        pbit,
                        scaleMax - 1 + pbit);
                lowScaled[c] = scaleEndpoint(lowQuantized[c], totalBits);
                highScaled[c] = scaleEndpoint(highQuantized[c], totalBits);
            }

            float inverse255 = 1.0f / 255.0f;
            float error = 0.0f;
            for (int c = 0; c < 3; c++) {
                float lowDelta = Math.fma(lowScaled[c], inverse255, -lowColor[c]);
                float highDelta = Math.fma(highScaled[c], inverse255, -highColor[c]);
                error += lowDelta * lowDelta + highDelta * highDelta;
            }
            if (error < bestError) {
                bestError = error;
                bestPbit = pbit;
                for (int c = 0; c < 3; c++) {
                    bestLow[c] = lowQuantized[c] >>> 1;
                    bestHigh[c] = highQuantized[c] >>> 1;
                }
            }
        }
        return new int[] {
            bestLow[0], bestLow[1], bestLow[2],
            bestHigh[0], bestHigh[1], bestHigh[2],
            bestPbit, bestPbit
        };
    }

    private static float[] normalizedRgb(int[] rgba, int pixel) {
        int base = pixel * 4;
        return new float[] {
            rgba[base] * (1.0f / 255.0f),
            rgba[base + 1] * (1.0f / 255.0f),
            rgba[base + 2] * (1.0f / 255.0f)
        };
    }

    private static float[] normalizedRgba(int[] rgba, int pixel) {
        int base = pixel * 4;
        return new float[] {
            rgba[base] * (1.0f / 255.0f),
            rgba[base + 1] * (1.0f / 255.0f),
            rgba[base + 2] * (1.0f / 255.0f),
            rgba[base + 3] * (1.0f / 255.0f)
        };
    }

    private static int scaleEndpoint(int value, int totalBits) {
        int scaled = value << (8 - totalBits);
        return scaled | (scaled >>> totalBits);
    }

    private static int[] evalWeightsMode6Rgb(int[] rgba, int[] low, int[] high) {
        int lr = from7(low[0], low[3]);
        int lg = from7(low[1], low[3]);
        int lb = from7(low[2], low[3]);
        int hr = from7(high[0], high[3]);
        int hg = from7(high[1], high[3]);
        int hb = from7(high[2], high[3]);

        int dr = hr - lr;
        int dg = hg - lg;
        int db = hb - lb;
        float f = 15.0f / (dr * dr + dg * dg + db * db + .00000125f);
        int offset = -(lr * dr + lg * dg + lb * db);
        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float dot = rgba[base] * dr + rgba[base + 1] * dg + rgba[base + 2] * db + offset;
            int selector = (int) (dot * f + .5f);
            selectors[i] = clamp(selector, 0, 15);
        }
        return selectors;
    }

    private static int[] evalWeightsMode6Rgba(int[] rgba, int[] low, int[] high) {
        int lr = from7(low[0], low[4]);
        int lg = from7(low[1], low[4]);
        int lb = from7(low[2], low[4]);
        int la = from7(low[3], low[4]);
        int hr = from7(high[0], high[4]);
        int hg = from7(high[1], high[4]);
        int hb = from7(high[2], high[4]);
        int ha = from7(high[3], high[4]);

        int dr = hr - lr;
        int dg = hg - lg;
        int db = hb - lb;
        int da = ha - la;
        float f = 15.0f / (dr * dr + dg * dg + db * db + da * da + .00000125f);
        int offset = -(lr * dr + lg * dg + lb * db + la * da);
        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float dot = rgba[base] * dr
                    + rgba[base + 1] * dg
                    + rgba[base + 2] * db
                    + rgba[base + 3] * da
                    + offset;
            selectors[i] = clamp((int) (dot * f + .5f), 0, 15);
        }
        return selectors;
    }

    private static int[] evalWeightsMode1Rgb(
            int[] rgba,
            int[][] low,
            int[][] high,
            int[] pbits,
            int partitionBits) {
        int[][] low8 = new int[2][3];
        int[][] high8 = new int[2][3];
        int[][] delta = new int[2][3];
        float[] scale = new float[2];
        int[] offset = new int[2];
        for (int subset = 0; subset < 2; subset++) {
            low8[subset][0] = from6(low[subset][0], pbits[subset]);
            low8[subset][1] = from6(low[subset][1], pbits[subset]);
            low8[subset][2] = from6(low[subset][2], pbits[subset]);
            high8[subset][0] = from6(high[subset][0], pbits[subset]);
            high8[subset][1] = from6(high[subset][1], pbits[subset]);
            high8[subset][2] = from6(high[subset][2], pbits[subset]);
            delta[subset][0] = high8[subset][0] - low8[subset][0];
            delta[subset][1] = high8[subset][1] - low8[subset][1];
            delta[subset][2] = high8[subset][2] - low8[subset][2];
            scale[subset] = 7.0f / (delta[subset][0] * delta[subset][0]
                    + delta[subset][1] * delta[subset][1]
                    + delta[subset][2] * delta[subset][2] + .00000125f);
            offset[subset] = low8[subset][0] * delta[subset][0]
                    + low8[subset][1] * delta[subset][1]
                    + low8[subset][2] * delta[subset][2];
        }

        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int subset = (partitionBits >>> i) & 1;
            int base = i * 4;
            float dot = rgba[base] * delta[subset][0]
                    + rgba[base + 1] * delta[subset][1]
                    + rgba[base + 2] * delta[subset][2]
                    - offset[subset];
            selectors[i] = clamp((int) (dot * scale[subset] + .5f), 0, 7);
        }
        return selectors;
    }

    private static int[] evalWeightsMode0Rgb(
            int[] rgba,
            int[][] low,
            int[][] high,
            int[][] pbits,
            int[] partitionMap) {
        int[][] low8 = new int[3][3];
        int[][] high8 = new int[3][3];
        int[][] delta = new int[3][3];
        float[] scale = new float[3];
        int[] offset = new int[3];
        for (int subset = 0; subset < 3; subset++) {
            low8[subset][0] = from4(low[subset][0], pbits[subset][0]);
            low8[subset][1] = from4(low[subset][1], pbits[subset][0]);
            low8[subset][2] = from4(low[subset][2], pbits[subset][0]);
            high8[subset][0] = from4(high[subset][0], pbits[subset][1]);
            high8[subset][1] = from4(high[subset][1], pbits[subset][1]);
            high8[subset][2] = from4(high[subset][2], pbits[subset][1]);
            delta[subset][0] = high8[subset][0] - low8[subset][0];
            delta[subset][1] = high8[subset][1] - low8[subset][1];
            delta[subset][2] = high8[subset][2] - low8[subset][2];
            scale[subset] = 7.0f / (delta[subset][0] * delta[subset][0]
                    + delta[subset][1] * delta[subset][1]
                    + delta[subset][2] * delta[subset][2] + .00000125f);
            offset[subset] = low8[subset][0] * delta[subset][0]
                    + low8[subset][1] * delta[subset][1]
                    + low8[subset][2] * delta[subset][2];
        }

        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int subset = partitionMap[i];
            int base = i * 4;
            float dot = rgba[base] * delta[subset][0]
                    + rgba[base + 1] * delta[subset][1]
                    + rgba[base + 2] * delta[subset][2]
                    - offset[subset];
            selectors[i] = clamp((int) (dot * scale[subset] + .5f), 0, 7);
        }
        return selectors;
    }

    private static int[] evalWeightsMode2Rgb(
            int[] rgba,
            int[][] low,
            int[][] high,
            int[] partitionMap) {
        int[][] low8 = new int[3][3];
        int[][] high8 = new int[3][3];
        int[][] delta = new int[3][3];
        float[] scale = new float[3];
        int[] offset = new int[3];
        for (int subset = 0; subset < 3; subset++) {
            low8[subset][0] = from5(low[subset][0]);
            low8[subset][1] = from5(low[subset][1]);
            low8[subset][2] = from5(low[subset][2]);
            high8[subset][0] = from5(high[subset][0]);
            high8[subset][1] = from5(high[subset][1]);
            high8[subset][2] = from5(high[subset][2]);
            delta[subset][0] = high8[subset][0] - low8[subset][0];
            delta[subset][1] = high8[subset][1] - low8[subset][1];
            delta[subset][2] = high8[subset][2] - low8[subset][2];
            scale[subset] = 3.0f / (delta[subset][0] * delta[subset][0]
                    + delta[subset][1] * delta[subset][1]
                    + delta[subset][2] * delta[subset][2] + .00000125f);
            offset[subset] = low8[subset][0] * delta[subset][0]
                    + low8[subset][1] * delta[subset][1]
                    + low8[subset][2] * delta[subset][2];
        }

        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int subset = partitionMap[i];
            int base = i * 4;
            float dot = rgba[base] * delta[subset][0]
                    + rgba[base + 1] * delta[subset][1]
                    + rgba[base + 2] * delta[subset][2]
                    - offset[subset];
            selectors[i] = clamp((int) (dot * scale[subset] + .5f), 0, 3);
        }
        return selectors;
    }

    private static int[] evalWeightsMode3Rgb(
            int[] rgba,
            int[][] low,
            int[][] high,
            int[][] pbits,
            int partition,
            int partitionBits) {
        int[][] low8 = new int[2][3];
        int[][] high8 = new int[2][3];
        int[][] delta = new int[2][3];
        float[] scale = new float[2];
        int[] offset = new int[2];
        for (int subset = 0; subset < 2; subset++) {
            low8[subset][0] = from7(low[subset][0], pbits[subset][0]);
            low8[subset][1] = from7(low[subset][1], pbits[subset][0]);
            low8[subset][2] = from7(low[subset][2], pbits[subset][0]);
            high8[subset][0] = from7(high[subset][0], pbits[subset][1]);
            high8[subset][1] = from7(high[subset][1], pbits[subset][1]);
            high8[subset][2] = from7(high[subset][2], pbits[subset][1]);
            delta[subset][0] = high8[subset][0] - low8[subset][0];
            delta[subset][1] = high8[subset][1] - low8[subset][1];
            delta[subset][2] = high8[subset][2] - low8[subset][2];
            scale[subset] = 3.0f / (delta[subset][0] * delta[subset][0]
                    + delta[subset][1] * delta[subset][1]
                    + delta[subset][2] * delta[subset][2] + .00000125f);
            offset[subset] = low8[subset][0] * delta[subset][0]
                    + low8[subset][1] * delta[subset][1]
                    + low8[subset][2] * delta[subset][2];
        }

        int[] anchors = Bc7LdrBlockPacker.colorSelectorAnchors(3, partition);
        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int subset = (partitionBits >>> i) & 1;
            int base = i * 4;
            float dot = rgba[base] * delta[subset][0]
                    + rgba[base + 1] * delta[subset][1]
                    + rgba[base + 2] * delta[subset][2]
                    - offset[subset];
            float selectorValue = dot * scale[subset] + .5f;
            int selector = (int) selectorValue;
            if (i != anchors[0]
                    && i != anchors[1]
                    && isOpposingTwoChannelSelectorTie(delta[subset], selectorValue, selector)) {
                selector--;
            }
            selectors[i] = clamp(selector, 0, 3);
        }
        return selectors;
    }

    private static int[] evalWeightsMode7Rgba(
            int[] rgba,
            int[][] low,
            int[][] high,
            int[][] pbits,
            int partitionBits) {
        int[][] low8 = new int[2][4];
        int[][] high8 = new int[2][4];
        int[][] delta = new int[2][4];
        float[] scale = new float[2];
        int[] offset = new int[2];
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 4; c++) {
                low8[subset][c] = from5(low[subset][c], pbits[subset][0]);
                high8[subset][c] = from5(high[subset][c], pbits[subset][1]);
                delta[subset][c] = high8[subset][c] - low8[subset][c];
            }
            scale[subset] = 3.0f / (delta[subset][0] * delta[subset][0]
                    + delta[subset][1] * delta[subset][1]
                    + delta[subset][2] * delta[subset][2]
                    + delta[subset][3] * delta[subset][3] + .00000125f);
            offset[subset] = low8[subset][0] * delta[subset][0]
                    + low8[subset][1] * delta[subset][1]
                    + low8[subset][2] * delta[subset][2]
                    + low8[subset][3] * delta[subset][3];
        }

        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int subset = (partitionBits >>> i) & 1;
            int base = i * 4;
            int dot = rgba[base] * delta[subset][0]
                    + rgba[base + 1] * delta[subset][1]
                    + rgba[base + 2] * delta[subset][2]
                    + rgba[base + 3] * delta[subset][3]
                    - offset[subset];
            selectors[i] = clamp((int) ((float) dot * scale[subset] + .5f), 0, 3);
        }
        return selectors;
    }

    private static int[] evalWeightsMode5Rgb(
            int[] rgba,
            int lr,
            int lg,
            int lb,
            int hr,
            int hg,
            int hb) {
        lr = from7(lr);
        lg = from7(lg);
        lb = from7(lb);
        hr = from7(hr);
        hg = from7(hg);
        hb = from7(hb);

        int dr = hr - lr;
        int dg = hg - lg;
        int db = hb - lb;
        float f = 3.0f / (dr * dr + dg * dg + db * db + .00000125f);
        int offset = lr * dr + lg * dg + lb * db;
        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float dot = rgba[base] * dr + rgba[base + 1] * dg + rgba[base + 2] * db - offset;
            selectors[i] = clamp((int) (dot * f + .5f), 0, 3);
        }
        return selectors;
    }

    private static int[] evalWeightsMode5Alpha(int[] rgba, int lowAlpha, int highAlpha) {
        int delta = highAlpha - lowAlpha;
        float f = 3.0f / (delta + .00000125f);
        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            selectors[i] = clamp((int) ((rgba[base + 3] - lowAlpha) * f + .5f), 0, 3);
        }
        return selectors;
    }

    private static int[] evalWeightsMode4Rgb(
            int[] rgba,
            int lr,
            int lg,
            int lb,
            int hr,
            int hg,
            int hb,
            int selectorBits) {
        lr = from5(lr);
        lg = from5(lg);
        lb = from5(lb);
        hr = from5(hr);
        hg = from5(hg);
        hb = from5(hb);

        int dr = hr - lr;
        int dg = hg - lg;
        int db = hb - lb;
        int maxSelector = (1 << selectorBits) - 1;
        float f = maxSelector / (dr * dr + dg * dg + db * db + .00000125f);
        int offset = lr * dr + lg * dg + lb * db;
        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            float dot = rgba[base] * dr + rgba[base + 1] * dg + rgba[base + 2] * db - offset;
            selectors[i] = clamp((int) (dot * f + .5f), 0, maxSelector);
        }
        return selectors;
    }

    private static int[] evalWeightsMode4Alpha(int[] rgba, int lowAlpha, int highAlpha, int selectorBits) {
        int low = from6(lowAlpha);
        int high = from6(highAlpha);
        int delta = high - low;
        int maxSelector = (1 << selectorBits) - 1;
        float f = maxSelector / (delta + .00000125f);
        int[] selectors = new int[16];
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            selectors[i] = clamp((int) ((rgba[base + 3] - low) * f + .5f), 0, maxSelector);
        }
        return selectors;
    }

    private static float[][] computeLeastSquaresEndpoints3d(int[] rgba, int[] selectors, int[] totals) {
        return computeLeastSquaresEndpoints3d(rgba, selectors, totals, LS_WEIGHTS4);
    }

    private static float[][] computeLeastSquaresEndpoints3d(
            int[] rgba,
            int[] selectors,
            int[] totals,
            float[][] selectorWeights) {
        float[][] endpoints = computeLeastSquaresEndpointPixels3d(rgba, selectors, totals, selectorWeights);
        if (endpoints == null) {
            return null;
        }
        return normalizeEndpoints(endpoints);
    }

    private static float[][] normalizeEndpoints(float[][] endpoints) {
        return new float[][] {
            {
                normalizeEndpoint(endpoints[0][0]),
                normalizeEndpoint(endpoints[0][1]),
                normalizeEndpoint(endpoints[0][2])
            },
            {
                normalizeEndpoint(endpoints[1][0]),
                normalizeEndpoint(endpoints[1][1]),
                normalizeEndpoint(endpoints[1][2])
            }
        };
    }

    private static float[][] computeLeastSquaresEndpointPixels3d(
            int[] rgba,
            int[] selectors,
            int[] totals,
            float[][] selectorWeights) {
        float z00 = 0.0f;
        float z10 = 0.0f;
        float z11 = 0.0f;
        float q00R = 0.0f;
        float q00G = 0.0f;
        float q00B = 0.0f;
        for (int i = 0; i < 16; i++) {
            int selector = selectors[i];
            z00 += selectorWeights[selector][0];
            z10 += selectorWeights[selector][1];
            z11 += selectorWeights[selector][2];

            float w = selectorWeights[selector][3];
            int base = i * 4;
            q00R += w * rgba[base];
            q00G += w * rgba[base + 1];
            q00B += w * rgba[base + 2];
        }

        float q10R = totals[0] - q00R;
        float q10G = totals[1] - q00G;
        float q10B = totals[2] - q00B;
        float det = Math.fma(z00, z11, -(z10 * z10));
        if (Math.abs(det) < 1.0e-8f) {
            return null;
        }

        det = 1.0f / det;
        final float iz00 = z11 * det;
        final float iz01 = -z10 * det;
        final float iz10 = -z10 * det;
        final float iz11 = z00 * det;
        float[] high = {
            clamp(Math.fma(iz00, q00R, iz01 * q10R), 0.0f, 255.0f),
            clamp(Math.fma(iz00, q00G, iz01 * q10G), 0.0f, 255.0f),
            clamp(Math.fma(iz00, q00B, iz01 * q10B), 0.0f, 255.0f)
        };
        float[] low = {
            clamp(Math.fma(iz10, q00R, iz11 * q10R), 0.0f, 255.0f),
            clamp(Math.fma(iz10, q00G, iz11 * q10G), 0.0f, 255.0f),
            clamp(Math.fma(iz10, q00B, iz11 * q10B), 0.0f, 255.0f)
        };
        return new float[][] {low, high};
    }

    private static float normalizeEndpoint(float value) {
        float clamped = clamp(value, 0.0f, 255.0f);
        return clamped * (1.0f / 255.0f);
    }

    private static float[][] computeLeastSquaresEndpoints4d(int[] rgba, int[] selectors, int[] totals) {
        float z00 = 0.0f;
        float z10 = 0.0f;
        float z11 = 0.0f;
        float[] q00 = new float[4];
        for (int i = 0; i < 16; i++) {
            int selector = selectors[i];
            z00 += LS_WEIGHTS4[selector][0];
            z10 += LS_WEIGHTS4[selector][1];
            z11 += LS_WEIGHTS4[selector][2];

            float w = LS_WEIGHTS4[selector][3];
            int base = i * 4;
            for (int c = 0; c < 4; c++) {
                q00[c] += w * rgba[base + c];
            }
        }

        float det = Math.fma(z00, z11, -(z10 * z10));
        if (Math.abs(det) < 1e-8f) {
            return null;
        }

        det = 1.0f / det;
        float iz00 = z11 * det;
        float iz01 = -z10 * det;
        float iz10 = -z10 * det;
        float iz11 = z00 * det;
        float[] low = new float[4];
        float[] high = new float[4];
        for (int c = 0; c < 4; c++) {
            float q10 = totals[c] - q00[c];
            high[c] = clamp(Math.fma(iz00, q00[c], iz01 * q10), 0.0f, 255.0f) * (1.0f / 255.0f);
            low[c] = clamp(Math.fma(iz10, q00[c], iz11 * q10), 0.0f, 255.0f) * (1.0f / 255.0f);
        }
        return new float[][] {low, high};
    }

    private static float[][] computeSubsetLeastSquaresEndpoints3d(
            int[] rgba,
            int[] selectors,
            int[][] subsetTotals,
            int partitionBits,
            float[][] selectorWeights) {
        int[] partitionMap = new int[16];
        for (int i = 0; i < partitionMap.length; i++) {
            partitionMap[i] = (partitionBits >>> i) & 1;
        }
        return computeSubsetLeastSquaresEndpoints3d(
                rgba, selectors, subsetTotals, partitionMap, selectorWeights);
    }

    private static float[][] computeSubsetLeastSquaresEndpoints3d(
            int[] rgba,
            int[] selectors,
            int[][] subsetTotals,
            int[] partitionMap,
            float[][] selectorWeights) {
        int subsetCount = subsetTotals.length;
        float[][] result = new float[subsetCount * 2][];
        float[] z00 = new float[subsetCount];
        float[] z10 = new float[subsetCount];
        float[] z11 = new float[subsetCount];
        float[] q00R = new float[subsetCount];
        float[] q00G = new float[subsetCount];
        float[] q00B = new float[subsetCount];
        for (int i = 0; i < 16; i++) {
            int subset = partitionMap[i];
            int selector = selectors[i];
            z00[subset] += selectorWeights[selector][0];
            z10[subset] += selectorWeights[selector][1];
            z11[subset] += selectorWeights[selector][2];

            float w = selectorWeights[selector][3];
            int base = i * 4;
            q00R[subset] += w * rgba[base];
            q00G[subset] += w * rgba[base + 1];
            q00B[subset] += w * rgba[base + 2];
        }

        for (int subset = 0; subset < subsetCount; subset++) {
            float q10R = subsetTotals[subset][0] - q00R[subset];
            float q10G = subsetTotals[subset][1] - q00G[subset];
            float q10B = subsetTotals[subset][2] - q00B[subset];
            float det = Math.fma(z00[subset], z11[subset], -(z10[subset] * z10[subset]));
            if (Math.abs(det) < 1e-8f) {
                continue;
            }

            det = 1.0f / det;
            float iz00 = z11[subset] * det;
            float iz01 = -z10[subset] * det;
            float iz10 = -z10[subset] * det;
            float iz11 = z00[subset] * det;
            result[subset * 2] = new float[] {
                clamp(Math.fma(iz10, q00R[subset], iz11 * q10R), 0.0f, 255.0f) * (1.0f / 255.0f),
                clamp(Math.fma(iz10, q00G[subset], iz11 * q10G), 0.0f, 255.0f) * (1.0f / 255.0f),
                clamp(Math.fma(iz10, q00B[subset], iz11 * q10B), 0.0f, 255.0f) * (1.0f / 255.0f)
            };
            result[subset * 2 + 1] = new float[] {
                clamp(Math.fma(iz00, q00R[subset], iz01 * q10R), 0.0f, 255.0f) * (1.0f / 255.0f),
                clamp(Math.fma(iz00, q00G[subset], iz01 * q10G), 0.0f, 255.0f) * (1.0f / 255.0f),
                clamp(Math.fma(iz00, q00B[subset], iz01 * q10B), 0.0f, 255.0f) * (1.0f / 255.0f)
            };
        }
        return result;
    }

    private static float[][] computeSubsetLeastSquaresEndpoints4d(
            int[] rgba,
            int[] selectors,
            int[][] subsetTotals,
            int partitionBits,
            float[][] selectorWeights) {
        int[] partitionMap = new int[16];
        for (int i = 0; i < partitionMap.length; i++) {
            partitionMap[i] = (partitionBits >>> i) & 1;
        }
        int subsetCount = subsetTotals.length;
        float[][] result = new float[subsetCount * 2][];
        float[] z00 = new float[subsetCount];
        float[] z10 = new float[subsetCount];
        float[] z11 = new float[subsetCount];
        float[][] q00 = new float[subsetCount][4];
        for (int i = 0; i < 16; i++) {
            int subset = partitionMap[i];
            int selector = selectors[i];
            z00[subset] += selectorWeights[selector][0];
            z10[subset] += selectorWeights[selector][1];
            z11[subset] += selectorWeights[selector][2];

            float w = selectorWeights[selector][3];
            int base = i * 4;
            for (int c = 0; c < 4; c++) {
                q00[subset][c] += w * rgba[base + c];
            }
        }

        for (int subset = 0; subset < subsetCount; subset++) {
            float det = Math.fma(z00[subset], z11[subset], -(z10[subset] * z10[subset]));
            if (Math.abs(det) < 1e-8f) {
                continue;
            }

            det = 1.0f / det;
            float iz00 = z11[subset] * det;
            float iz01 = -z10[subset] * det;
            float iz10 = -z10[subset] * det;
            float iz11 = z00[subset] * det;
            float[] low = new float[4];
            float[] high = new float[4];
            for (int c = 0; c < 4; c++) {
                float q10 = subsetTotals[subset][c] - q00[subset][c];
                low[c] = clamp(Math.fma(iz10, q00[subset][c], iz11 * q10), 0.0f, 255.0f)
                        * (1.0f / 255.0f);
                high[c] = clamp(Math.fma(iz00, q00[subset][c], iz01 * q10), 0.0f, 255.0f)
                        * (1.0f / 255.0f);
            }
            result[subset * 2] = low;
            result[subset * 2 + 1] = high;
        }
        return result;
    }

    private static float[] computeLeastSquaresEndpoints1d(
            int[] rgba,
            int[] selectors,
            int total,
            float[][] selectorWeights) {
        float z00 = 0.0f;
        float z10 = 0.0f;
        float z11 = 0.0f;
        float q00 = 0.0f;
        for (int i = 0; i < 16; i++) {
            int selector = selectors[i];
            z00 += selectorWeights[selector][0];
            z10 += selectorWeights[selector][1];
            z11 += selectorWeights[selector][2];
            q00 += selectorWeights[selector][3] * rgba[i * 4 + 3];
        }

        float q10 = total - q00;
        float det = Math.fma(z00, z11, -(z10 * z10));
        if (Math.abs(det) < 1e-8f) {
            return null;
        }

        det = 1.0f / det;
        float iz00 = z11 * det;
        float iz01 = -z10 * det;
        float iz10 = -z10 * det;
        float iz11 = z00 * det;
        float high = clamp(Math.fma(iz00, q00, iz01 * q10), 0.0f, 255.0f);
        float low = clamp(Math.fma(iz10, q00, iz11 * q10), 0.0f, 255.0f);
        return new float[] {low, high};
    }

    private static int[] swapDualPlaneChannel(int[] rgba, int channel) {
        int[] pixels = rgba.clone();
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int tmp = pixels[base + channel];
            pixels[base + channel] = pixels[base + 3];
            pixels[base + 3] = tmp;
        }
        return pixels;
    }

    private static int[] projectMinMax(int[] rgba, float axisR, float axisG, float axisB) {
        int scaledAxisR = 306;
        int scaledAxisG = 601;
        int scaledAxisB = 117;
        float maxAxis = Math.max(Math.abs(axisR), Math.max(Math.abs(axisG), Math.abs(axisB)));
        if (Math.abs(maxAxis) >= SMALL_FLOAT_VAL) {
            float m = 2048.0f / maxAxis;
            scaledAxisR = (int) (axisR * m);
            scaledAxisG = (int) (axisG * m);
            scaledAxisB = (int) (axisB * m);
        }
        scaledAxisR <<= 4;
        scaledAxisG <<= 4;
        scaledAxisB <<= 4;

        int lowDot = Integer.MAX_VALUE;
        int highDot = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            int dot = rgba[base] * scaledAxisR
                    + rgba[base + 1] * scaledAxisG
                    + rgba[base + 2] * scaledAxisB
                    + i;
            lowDot = Math.min(lowDot, dot);
            highDot = Math.max(highDot, dot);
        }
        return new int[] {lowDot & 15, highDot & 15};
    }

    private static Axis principalAxis(int[] cov, int blockMaxVar) {
        float scale = blockMaxVar != 0 ? 1.0f / blockMaxVar : 0.0f;
        float wx = cov[0] * scale;
        float wy = cov[3] * scale;
        float wz = cov[5] * scale;
        return new Axis(
                Math.fma(cov[2], wz, Math.fma(cov[1], wy, cov[0] * wx)),
                Math.fma(cov[4], wz, Math.fma(cov[3], wy, cov[1] * wx)),
                Math.fma(cov[5], wz, Math.fma(cov[4], wy, cov[2] * wx)));
    }

    private static float axisComponent(int c0, int c1, int c2, float wx, float wy, float wz) {
        return Math.fma(c0, wx, Math.fma(c1, wy, c2 * wz));
    }

    private static float axisComponentMode6(int c0, int c1, int c2, float wx, float wy, float wz) {
        // Match the reference bc7f contraction order for balanced G/B near-ties.
        return Math.fma(c2, wz, Math.fma(c1, wy, c0 * wx));
    }

    private static float subsetAxisComponent(
            int c0,
            int c1,
            int c2,
            float wx,
            float wy,
            float wz,
            boolean antiCorrelatedGb,
            boolean tinySymmetricRg) {
        if (antiCorrelatedGb) {
            return axisComponentMode6(c0, c1, c2, wx, wy, wz);
        }
        if (tinySymmetricRg) {
            return (c0 * wx + c1 * wy) + c2 * wz;
        }
        return axisComponent(c0, c1, c2, wx, wy, wz);
    }

    private static boolean isOpposingTwoChannelSelectorTie(int[] delta, float selectorValue, int selector) {
        return selector > 0
                && selector < 3
                && selectorValue == selector
                && delta[0] == 0
                && delta[1] < 0
                && delta[1] == -delta[2];
    }

    private static Axis4 principalAxis4(int[] cov) {
        int blockMaxVar = Math.max(Math.max(cov[0], cov[4]), Math.max(cov[7], cov[9]));
        float scale = blockMaxVar != 0 ? 1.0f / blockMaxVar : 0.0f;
        float wx = cov[0] * scale;
        float wy = cov[4] * scale;
        float wz = cov[7] * scale;
        float wa = cov[9] * scale;
        float x = 0.25f;
        float y = 0.25f;
        float z = 0.25f;
        float a = 0.25f;
        for (int i = 0; i < 4; i++) {
            x = cov[0] * wx + cov[1] * wy + cov[2] * wz + cov[3] * wa;
            y = cov[1] * wx + cov[4] * wy + cov[5] * wz + cov[6] * wa;
            z = cov[2] * wx + cov[5] * wy + cov[7] * wz + cov[8] * wa;
            a = cov[3] * wx + cov[6] * wy + cov[8] * wz + cov[9] * wa;
            float length = (float) Math.sqrt(x * x + y * y + z * z + a * a);
            if (length > SMALL_FLOAT_VAL) {
                length = 1.0f / length;
                x *= length;
                y *= length;
                z *= length;
                a *= length;
            } else {
                x = 0.25f;
                y = 0.25f;
                z = 0.25f;
                a = 0.25f;
            }
            wx = x;
            wy = y;
            wz = z;
            wa = a;
        }
        return new Axis4(x, y, z, a);
    }

    private static Axis4 subsetAxis4(int[] cov) {
        int blockMaxVar = Math.max(Math.max(cov[0], cov[4]), Math.max(cov[7], cov[9]));
        float scale = blockMaxVar != 0 ? 1.0f / blockMaxVar : 0.0f;
        float wx = cov[0] * scale;
        float wy = cov[4] * scale;
        float wz = cov[7] * scale;
        float wa = cov[9] * scale;
        float x0 = cov[0] * wx + cov[1] * wy + cov[2] * wz + cov[3] * wa;
        float y0 = cov[1] * wx + cov[4] * wy + cov[5] * wz + cov[6] * wa;
        float z0 = cov[2] * wx + cov[5] * wy + cov[7] * wz + cov[8] * wa;
        float a0 = cov[3] * wx + cov[6] * wy + cov[8] * wz + cov[9] * wa;
        return new Axis4(
                cov[0] * x0 + cov[1] * y0 + cov[2] * z0 + cov[3] * a0,
                cov[1] * x0 + cov[4] * y0 + cov[5] * z0 + cov[6] * a0,
                cov[2] * x0 + cov[5] * y0 + cov[7] * z0 + cov[8] * a0,
                cov[3] * x0 + cov[6] * y0 + cov[8] * z0 + cov[9] * a0);
    }

    private static int[] scaledAxis(Axis axis) {
        int scaledAxisR = 306;
        int scaledAxisG = 601;
        int scaledAxisB = 117;
        float maxAxis = Math.max(Math.abs(axis.red), Math.max(Math.abs(axis.green), Math.abs(axis.blue)));
        if (Math.abs(maxAxis) >= SMALL_FLOAT_VAL) {
            float m = 2048.0f / maxAxis;
            scaledAxisR = (int) (axis.red * m);
            scaledAxisG = (int) (axis.green * m);
            scaledAxisB = (int) (axis.blue * m);
        }
        return new int[] {scaledAxisR << 4, scaledAxisG << 4, scaledAxisB << 4};
    }

    private static int[] scaledAxis(Axis4 axis) {
        int scaledAxisR = 256;
        int scaledAxisG = 256;
        int scaledAxisB = 256;
        int scaledAxisA = 256;
        float maxAxis = Math.max(
                Math.max(Math.abs(axis.red), Math.abs(axis.green)),
                Math.max(Math.abs(axis.blue), Math.abs(axis.alpha)));
        if (Math.abs(maxAxis) >= SMALL_FLOAT_VAL) {
            float m = 2048.0f / maxAxis;
            scaledAxisR = (int) (axis.red * m);
            scaledAxisG = (int) (axis.green * m);
            scaledAxisB = (int) (axis.blue * m);
            scaledAxisA = (int) (axis.alpha * m);
        }
        return new int[] {scaledAxisR << 4, scaledAxisG << 4, scaledAxisB << 4, scaledAxisA << 4};
    }

    private static int[] minMaxSpans(int[] rgba) {
        int minR = 255;
        int minG = 255;
        int minB = 255;
        int maxR = 0;
        int maxG = 0;
        int maxB = 0;
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            minR = Math.min(minR, rgba[base]);
            minG = Math.min(minG, rgba[base + 1]);
            minB = Math.min(minB, rgba[base + 2]);
            maxR = Math.max(maxR, rgba[base]);
            maxG = Math.max(maxG, rgba[base + 1]);
            maxB = Math.max(maxB, rgba[base + 2]);
        }
        return new int[] {maxR - minR, maxG - minG, maxB - minB, 0};
    }

    private static int[] minMaxSpans4(int[] rgba) {
        int[] min = {255, 255, 255, 255};
        int[] max = {0, 0, 0, 0};
        for (int i = 0; i < 16; i++) {
            int base = i * 4;
            for (int c = 0; c < 4; c++) {
                min[c] = Math.min(min[c], rgba[base + c]);
                max[c] = Math.max(max[c], rgba[base + c]);
            }
        }
        return new int[] {
            max[0] - min[0],
            max[1] - min[1],
            max[2] - min[2],
            max[3] - min[3]
        };
    }

    private static float estimateSlamToLineSse3d(int[] cov, float axisR, float axisG, float axisB) {
        return estimateSlamToLineSse3dWithRatio(cov, axisR, axisG, axisB).sse;
    }

    private static SlamEstimate estimateSlamToLineSse3dWithRatio(
            int[] cov,
            float axisR,
            float axisG,
            float axisB) {
        float totalVariance = cov[0] + cov[3] + cov[5];
        float length = (float) Math.sqrt(axisR * axisR + axisG * axisG + axisB * axisB);
        if (length < SMALL_FLOAT_VAL) {
            axisR = 0.577350269f;
            axisG = 0.577350269f;
            axisB = 0.577350269f;
        } else {
            length = 1.0f / length;
            axisR *= length;
            axisG *= length;
            axisB *= length;
        }

        float xr2 = cov[0] * axisR + cov[1] * axisG + cov[2] * axisB;
        float xg2 = cov[1] * axisR + cov[3] * axisG + cov[4] * axisB;
        float xb2 = cov[2] * axisR + cov[4] * axisG + cov[5] * axisB;
        float principalAxisVariance = xr2 * axisR + xg2 * axisG + xb2 * axisB;
        float orthoVariance = Math.max(0.0f, totalVariance - principalAxisVariance);
        float orthoRatio = totalVariance > SMALL_FLOAT_VAL ? orthoVariance / totalVariance : 0.0f;
        return new SlamEstimate(orthoVariance, orthoRatio);
    }

    private static float estimateSlamToLineSse4d(int[] cov, Axis4 axis) {
        return estimateSlamToLineSse4dWithRatio(cov, axis).sse;
    }

    private static SlamEstimate estimateSlamToLineSse4dWithRatio(int[] cov, Axis4 axis) {
        float totalVariance = cov[0] + cov[4] + cov[7] + cov[9];
        float axisR = axis.red;
        float axisG = axis.green;
        float axisB = axis.blue;
        float axisA = axis.alpha;
        float length = (float) Math.sqrt(axisR * axisR + axisG * axisG + axisB * axisB + axisA * axisA);
        if (length < SMALL_FLOAT_VAL) {
            axisR = 0.5f;
            axisG = 0.5f;
            axisB = 0.5f;
            axisA = 0.5f;
        } else {
            length = 1.0f / length;
            axisR *= length;
            axisG *= length;
            axisB *= length;
            axisA *= length;
        }

        float xr2 = cov[0] * axisR + cov[1] * axisG + cov[2] * axisB + cov[3] * axisA;
        float xg2 = cov[1] * axisR + cov[4] * axisG + cov[5] * axisB + cov[6] * axisA;
        float xb2 = cov[2] * axisR + cov[5] * axisG + cov[7] * axisB + cov[8] * axisA;
        float xa2 = cov[3] * axisR + cov[6] * axisG + cov[8] * axisB + cov[9] * axisA;
        float principalAxisVariance = xr2 * axisR + xg2 * axisG + xb2 * axisB + xa2 * axisA;
        float orthoVariance = Math.max(0.0f, totalVariance - principalAxisVariance);
        float orthoRatio = totalVariance > SMALL_FLOAT_VAL ? orthoVariance / totalVariance : 0.0f;
        return new SlamEstimate(orthoVariance, orthoRatio);
    }

    private static float analyticalQuantEstSse(
            int endpointLevels,
            int weightLevels,
            int channelCount,
            int[] spans,
            float endpointWeightScale,
            int pixelCount) {
        float endpointDelta = 1.0f / (endpointLevels - 1);
        float weightDelta = 1.0f / (weightLevels - 1);
        float levels = weightLevels;
        float endpointSum = (2.0f * levels - 1.0f) / (3.0f * (levels - 1.0f));
        float pixelSse = endpointLevels == 256
                ? 0.0f
                : endpointDelta * endpointDelta * ((1.0f / 12.0f) * endpointSum * 255.0f * 255.0f)
                * channelCount * endpointWeightScale;
        float weightScale = weightDelta * weightDelta * (1.0f / 12.0f);
        for (int i = 0; i < channelCount; i++) {
            pixelSse += weightScale * spans[i] * spans[i];
        }
        return pixelSse * pixelCount;
    }

    private static float analyticalQuantEstSse(
            int endpointLevels,
            int weightLevels,
            int span,
            float spanWeight,
            float endpointWeightScale,
            int pixelCount) {
        float endpointDelta = 1.0f / (endpointLevels - 1);
        float weightDelta = 1.0f / (weightLevels - 1);
        float levels = weightLevels;
        float endpointSum = (2.0f * levels - 1.0f) / (3.0f * (levels - 1.0f));
        float pixelSse = endpointLevels == 256
                ? 0.0f
                : endpointDelta * endpointDelta * ((1.0f / 12.0f) * endpointSum * 255.0f * 255.0f)
                * endpointWeightScale;
        pixelSse += weightDelta * weightDelta * (1.0f / 12.0f) * span * span * spanWeight;
        return pixelSse * pixelCount;
    }

    private static void writeMode6(byte[] output, int offset, int[] low, int[] high, int[] selectors) {
        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(6);
        block.low[0][0] = low[0];
        block.low[0][1] = low[1];
        block.low[0][2] = low[2];
        block.low[0][3] = 127;
        block.high[0][0] = high[0];
        block.high[0][1] = high[1];
        block.high[0][2] = high[2];
        block.high[0][3] = 127;
        block.pbits[0][0] = low[3];
        block.pbits[0][1] = high[3];
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    private static void writeMode6Rgba(byte[] output, int offset, int[] low, int[] high, int[] selectors) {
        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(6);
        for (int c = 0; c < 4; c++) {
            block.low[0][c] = low[c];
            block.high[0][c] = high[c];
        }
        block.pbits[0][0] = low[4];
        block.pbits[0][1] = high[4];
        System.arraycopy(selectors, 0, block.selectors, 0, selectors.length);
        Bc7LdrBlockPacker.writeBlock(output, offset, block);
    }

    private static void writeMode4(
            byte[] output,
            int offset,
            int dualPlaneChannel,
            int indexFlag,
            int lr,
            int lg,
            int lb,
            int la,
            int hr,
            int hg,
            int hb,
            int ha,
            int[] rgbSelectors,
            int[] alphaSelectors) {
        int rotation = (dualPlaneChannel + 1) & 3;
        int[] p2BitWeights = indexFlag != 0 ? alphaSelectors : rgbSelectors;
        int[] p3BitWeights = indexFlag != 0 ? rgbSelectors : alphaSelectors;
        int colorInvert = 0;
        int alphaInvert = 0;

        if ((p3BitWeights[0] & 4) != 0) {
            colorInvert = 7;
            if (indexFlag != 0) {
                int tmp = lr;
                lr = hr;
                hr = tmp;
                tmp = lg;
                lg = hg;
                hg = tmp;
                tmp = lb;
                lb = hb;
                hb = tmp;
            } else {
                int tmp = la;
                la = ha;
                ha = tmp;
            }
        }

        if ((p2BitWeights[0] & 2) != 0) {
            alphaInvert = 3;
            if (indexFlag != 0) {
                int tmp = la;
                la = ha;
                ha = tmp;
            } else {
                int tmp = lr;
                lr = hr;
                hr = tmp;
                tmp = lg;
                lg = hg;
                hg = tmp;
                tmp = lb;
                lb = hb;
                hb = tmp;
            }
        }

        output[offset] = (byte) (0b10000 | (rotation << 5) | (indexFlag << 7));
        long bits = lr | ((long) hr << 5);
        bits |= ((long) lg << 10) | ((long) hg << 15);
        bits |= ((long) lb << 20) | ((long) hb << 25);
        bits |= ((long) la << 30) | ((long) ha << 36);

        output[offset + 1] = (byte) bits;
        output[offset + 2] = (byte) (bits >>> 8);
        output[offset + 3] = (byte) (bits >>> 16);
        output[offset + 4] = (byte) (bits >>> 24);
        output[offset + 5] = (byte) (bits >>> 32);

        bits >>>= 40;
        int bitOffset = 2;
        for (int i = 0; i < 16; i++) {
            long selector = p2BitWeights[i] ^ alphaInvert;
            bits |= selector << bitOffset;
            bitOffset += 2 - (i == 0 ? 1 : 0);
        }
        output[offset + 6] = (byte) bits;
        output[offset + 7] = (byte) (bits >>> 8);
        output[offset + 8] = (byte) (bits >>> 16);
        output[offset + 9] = (byte) (bits >>> 24);

        bits >>>= 32;
        bitOffset = 1;
        for (int i = 0; i < 16; i++) {
            long selector = p3BitWeights[i] ^ colorInvert;
            bits |= selector << bitOffset;
            bitOffset += 3 - (i == 0 ? 1 : 0);
        }
        output[offset + 10] = (byte) bits;
        output[offset + 11] = (byte) (bits >>> 8);
        output[offset + 12] = (byte) (bits >>> 16);
        output[offset + 13] = (byte) (bits >>> 24);
        output[offset + 14] = (byte) (bits >>> 32);
        output[offset + 15] = (byte) (bits >>> 40);
    }

    private static int from7(int value, int pbit) {
        return (value << 1) | pbit;
    }

    private static int from4(int value, int pbit) {
        int expanded = (value << 1) | pbit;
        return (expanded << 3) | (expanded >>> 2);
    }

    private static int from5(int value, int pbit) {
        int expanded = (value << 1) | pbit;
        return (expanded << 2) | (expanded >>> 4);
    }

    private static int from7(int value) {
        return (value << 1) | (value >>> 6);
    }

    private static int to7(int value) {
        return (value * 127 + 127) / 255;
    }

    private static int from5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int from6(int value) {
        return (value << 2) | (value >>> 4);
    }

    private static int from6(int value, int pbit) {
        int expanded = (value << 1) | pbit;
        return (expanded << 1) | (expanded >>> 6);
    }

    private static int to5(int value) {
        return (value * 31 + 127) / 255;
    }

    private static int to5Clamp(float value) {
        return clamp(fastRound(value * (31.0f / 255.0f)), 0, 31);
    }

    private static int to6(int value) {
        return (value * 63 + 127) / 255;
    }

    private static int dist3(int ar, int ag, int ab, int br, int bg, int bb) {
        int dr = ar - br;
        int dg = ag - bg;
        int db = ab - bb;
        return dr * dr + dg * dg + db * db;
    }

    private static int bc7Sse(byte[] bc7Block, int[] rgba) {
        int[] decoded = new int[16 * 4];
        Bc7LdrBlockPacker.decodeBlock(bc7Block, 0, decoded);
        int sse = 0;
        for (int i = 0; i < decoded.length; i++) {
            int delta = decoded[i] - rgba[i];
            sse += delta * delta;
        }
        return sse;
    }

    private static int fastRound(float value) {
        return value >= 0.0f ? (int) (value + 0.5f) : (int) (value - 0.5f);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float[][] createLeastSquaresWeights() {
        return createLeastSquaresWeights(WEIGHTS4);
    }

    private static float[][] createLeastSquaresWeights(int[] weights) {
        float[][] table = new float[weights.length][4];
        for (int i = 0; i < weights.length; i++) {
            float w = weights[i] * (1.0f / 64.0f);
            table[i][0] = w * w;
            table[i][1] = (1.0f - w) * w;
            table[i][2] = (1.0f - w) * (1.0f - w);
            table[i][3] = w;
        }
        return table;
    }


    private static final class ThreeSubsetCandidate {
        final int partition;
        final int[] partitionMap;
        final int[][] totals = new int[3][3];
        final int[] counts = new int[3];
        final int[] lowPixel = new int[3];
        final int[] highPixel = new int[3];
        float slamToLineSse;
        float quantSse;

        ThreeSubsetCandidate(int partition, int[] partitionMap) {
            this.partition = partition;
            this.partitionMap = partitionMap;
        }

        float estimatedSse() {
            return slamToLineSse + quantSse;
        }
    }

    private static final class Axis {
        final float red;
        final float green;
        final float blue;

        Axis(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    private static final class Axis4 {
        final float red;
        final float green;
        final float blue;
        final float alpha;

        Axis4(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
    }

    private static final class SlamEstimate {
        final float sse;
        final float orthoRatio;

        SlamEstimate(float sse, float orthoRatio) {
            this.sse = sse;
            this.orthoRatio = orthoRatio;
        }
    }

    private static final class ModeEstimate {
        final int mode;
        final float sse;

        ModeEstimate(int mode, float sse) {
            this.mode = mode;
            this.sse = sse;
        }
    }
}
