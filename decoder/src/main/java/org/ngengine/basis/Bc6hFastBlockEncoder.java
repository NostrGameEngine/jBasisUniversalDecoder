package org.ngengine.basis;

/**
 * Basis Universal fast unsigned BC6H encoder used by the ASTC HDR 6x6 paths.
 */
final class Bc6hFastBlockEncoder {
    private static final int ASTC_BLOCK_BYTES = 16;
    private static final int BLOCK_WIDTH = 6;
    private static final int BLOCK_HEIGHT = 6;
    private static final int MAX_HALF = 0x7BFF;
    private static final float MAX_HALF_FLOAT = 65504.0f;
    private static final float MIN_HALF_FLOAT = 0.00006103515625f;
    private static final float SMALL_FLOAT = 1.0e-10f;
    private static final int STD_DEV_THRESH = 256;
    private static final int COMPLEX_STD_DEV_THRESH = 512;
    private static final int VERY_COMPLEX_STD_DEV_THRESH = 2048;
    private static final int[] WEIGHTS_3 = {0, 9, 18, 27, 37, 46, 55, 64};
    private static final int[] WEIGHTS_4 = {0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64};
    private static final float[][] LS_WEIGHTS_3 = buildLsWeights3();
    private static final float[][] LS_WEIGHTS_4 = buildLsWeights4();

    private Bc6hFastBlockEncoder() {
    }

    static byte[] transcodeAstc6x6ToBc6h(byte[] astc, int width, int height) {
        return transcodeAstc6x6ToBc6h(astc, width, height, false);
    }

    static byte[] transcodeAstc6x6ToBc6h(byte[] astc, int width, int height, boolean highQuality) {
        int sourceBlocksX = AstcHdrBlockDecoder.divideRoundUp(width, BLOCK_WIDTH);
        int sourceBlocksY = AstcHdrBlockDecoder.divideRoundUp(height, BLOCK_HEIGHT);
        int sourceBlockCount = Math.multiplyExact(sourceBlocksX, sourceBlocksY);
        if (astc.length != Math.multiplyExact(sourceBlockCount, ASTC_BLOCK_BYTES)) {
            throw new BasisDecodeException("ASTC HDR 6x6 payload size mismatch");
        }
        int dstBlocksX = AstcHdrBlockDecoder.divideRoundUp(width, 4);
        int dstBlocksY = AstcHdrBlockDecoder.divideRoundUp(height, 4);
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(dstBlocksX, dstBlocksY), ASTC_BLOCK_BYTES)];
        int[] unpacked = new int[12 * 12 * 3];

        for (int srcBlockY = 0; srcBlockY < sourceBlocksY; srcBlockY += 2) {
            int innerBlocksY = Math.min(2, sourceBlocksY - srcBlockY);
            for (int srcBlockX = 0; srcBlockX < sourceBlocksX; srcBlockX += 2) {
                int innerBlocksX = Math.min(2, sourceBlocksX - srcBlockX);
                for (int iy = 0; iy < innerBlocksY; iy++) {
                    for (int ix = 0; ix < innerBlocksX; ix++) {
                        int blockIndex = (srcBlockX + ix) + (srcBlockY + iy) * sourceBlocksX;
                        int[] decoded = AstcHdrBlockDecoder.decodeBlockToRgbHalf(
                                astc,
                                blockIndex * ASTC_BLOCK_BYTES,
                                BLOCK_WIDTH,
                                BLOCK_HEIGHT);
                        copyDecodedBlock(decoded, unpacked, ix, iy);
                    }
                }

                int dstBlockX = (srcBlockX * BLOCK_WIDTH) >>> 2;
                int dstBlockY = (srcBlockY * BLOCK_HEIGHT) >>> 2;
                int innerDstBlocksX = Math.min(3, dstBlocksX - dstBlockX);
                int innerDstBlocksY = Math.min(3, dstBlocksY - dstBlockY);
                for (int dy = 0; dy < innerDstBlocksY; dy++) {
                    for (int dx = 0; dx < innerDstBlocksX; dx++) {
                        int[] pixels = new int[16 * 3];
                        for (int y = 0; y < 4; y++) {
                            int srcY = Math.min(dy * 4 + y, innerBlocksY * BLOCK_HEIGHT - 1);
                            for (int x = 0; x < 4; x++) {
                                int srcX = Math.min(dx * 4 + x, innerBlocksX * BLOCK_WIDTH - 1);
                                int src = (srcX + srcY * 12) * 3;
                                int dst = (x + y * 4) * 3;
                                pixels[dst] = unpacked[src];
                                pixels[dst + 1] = unpacked[src + 1];
                                pixels[dst + 2] = unpacked[src + 2];
                            }
                        }
                        int outputOffset = ((dstBlockX + dx) + (dstBlockY + dy) * dstBlocksX) * ASTC_BLOCK_BYTES;
                        encodeBlock(pixels, output, outputOffset, highQuality);
                    }
                }
            }
        }
        return output;
    }

    private static void copyDecodedBlock(int[] decoded, int[] unpacked, int blockX, int blockY) {
        for (int y = 0; y < BLOCK_HEIGHT; y++) {
            for (int x = 0; x < BLOCK_WIDTH; x++) {
                int src = (x + y * BLOCK_WIDTH) * 3;
                int dst = (blockX * BLOCK_WIDTH + x + (blockY * BLOCK_HEIGHT + y) * 12) * 3;
                unpacked[dst] = decoded[src];
                unpacked[dst + 1] = decoded[src + 1];
                unpacked[dst + 2] = decoded[src + 2];
            }
        }
    }

    private static void encodeBlock(int[] pixels, byte[] output, int offset, boolean highQuality) {
        Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block =
                new Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock();
        block.mode = Bc6hUastcHdr4x4Transcoder.BC6H_FIRST_ONE_SUBSET_MODE;

        int minR = Integer.MAX_VALUE;
        int minG = Integer.MAX_VALUE;
        int minB = Integer.MAX_VALUE;
        int maxR = 0;
        int maxG = 0;
        int maxB = 0;
        int totalR = 0;
        int totalG = 0;
        int totalB = 0;
        for (int i = 0; i < 16; i++) {
            int r = pixels[i * 3];
            int g = pixels[i * 3 + 1];
            int b = pixels[i * 3 + 2];
            totalR += r;
            totalG += g;
            totalB += b;
            minR = Math.min(minR, r);
            minG = Math.min(minG, g);
            minB = Math.min(minB, b);
            maxR = Math.max(maxR, r);
            maxG = Math.max(maxG, g);
            maxB = Math.max(maxB, b);
        }

        if (minR == maxR && minG == maxG && minB == maxB) {
            block.endpoints[0][0] = halfToBlog16(minR);
            block.endpoints[1][0] = halfToBlog16(minG);
            block.endpoints[2][0] = halfToBlog16(minB);
            block.mode = 13;
            Bc6hUastcHdr4x4Transcoder.packBlock(block, output, offset);
            return;
        }

        int meanR = (totalR + 8) / 16;
        int meanG = (totalG + 8) / 16;
        int meanB = (totalB + 8) / 16;
        long[] cov = covariance(pixels, meanR, meanG, meanB);
        long blockMaxVar = Math.max(cov[0], Math.max(cov[3], cov[5]));

        if (blockMaxVar < (long) STD_DEV_THRESH * STD_DEV_THRESH * 16) {
            int[] endpoints = {
                (maxR - minR) / 32 + minR,
                (maxG - minG) / 32 + minG,
                (maxB - minB) / 32 + minB,
                ((maxR - minR) * 31) / 32 + minR,
                ((maxG - minG) * 31) / 32 + minG,
                ((maxB - minB) * 31) / 32 + minB
            };
            quantDequantEndpoints(endpoints, 10);
            assignWeightsSimple4(pixels, block.weights, endpoints, blockMaxVar);
            setMode10Endpoints(block, endpoints);
            normalizeOneSubsetWeights(block);
            Bc6hUastcHdr4x4Transcoder.packBlock(block, output, offset);
            return;
        }

        encodeComplexBlock(pixels, block, cov, blockMaxVar, meanR, meanG, meanB, highQuality);
        Bc6hUastcHdr4x4Transcoder.packBlock(block, output, offset);
    }

    private static void encodeComplexBlock(
            int[] pixels,
            Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block,
            long[] icov,
            long blockMaxVar,
            int meanR,
            int meanG,
            int meanB,
            boolean highQuality) {
        float[] cov = new float[6];
        for (int i = 0; i < 6; i++) {
            cov[i] = (float) icov[i];
        }
        float scale = 1.0f / (float) blockMaxVar;
        float wx = scale * cov[0];
        float wy = scale * cov[3];
        float wz = scale * cov[5];
        float altXr = cov[0] * wx + cov[1] * wy + cov[2] * wz;
        float altXg = cov[1] * wx + cov[3] * wy + cov[4] * wz;
        float altXb = cov[2] * wx + cov[4] * wy + cov[5] * wz;
        float length = square(altXr) + square(altXg) + square(altXb);
        float axisR = 0.57735027f;
        float axisG = 0.57735027f;
        float axisB = 0.57735027f;
        if (Math.abs(length) >= SMALL_FLOAT) {
            float invLength = invSqrt(length);
            axisR = altXr * invLength;
            axisG = altXg * invLength;
            axisB = altXb * invLength;
        }
        float tr = axisR * cov[0] + axisG * cov[1] + axisB * cov[2];
        float tg = axisR * cov[1] + axisG * cov[3] + axisB * cov[4];
        float tb = axisR * cov[2] + axisG * cov[4] + axisB * cov[5];
        float principleAxisVar = tr * axisR + tg * axisG + tb * axisB;
        float invPrincipleAxisVar = 1.0f / (principleAxisVar + 1.0e-20f);
        axisR = tr * invPrincipleAxisVar;
        axisG = tg * invPrincipleAxisVar;
        axisB = tb * invPrincipleAxisVar;
        float totalVar = cov[0] + cov[3] + cov[5];
        boolean tryTwoSubsets = principleAxisVar < totalVar * 0.995f;

        int minIndex = 0;
        int maxIndex = 0;
        float minDot = Float.MAX_VALUE;
        float maxDot = -Float.MAX_VALUE;
        float[][] floatPixels = new float[16][3];
        float[] pixelScales = new float[16];
        for (int i = 0; i < 16; i++) {
            float r = pixels[i * 3];
            float g = pixels[i * 3 + 1];
            float b = pixels[i * 3 + 2];
            floatPixels[i][0] = halfToFloat(pixels[i * 3]);
            floatPixels[i][1] = halfToFloat(pixels[i * 3 + 1]);
            floatPixels[i][2] = halfToFloat(pixels[i * 3 + 2]);
            pixelScales[i] = 1.0f / (square(floatPixels[i][0])
                    + square(floatPixels[i][1])
                    + square(floatPixels[i][2])
                    + MIN_HALF_FLOAT);
            float dot = r * axisR + g * axisG + b * axisB;
            if (dot < minDot) {
                minDot = dot;
                minIndex = i;
            }
            if (dot > maxDot) {
                maxDot = dot;
                maxIndex = i;
            }
        }

        int[] endpoints = {
            pixels[minIndex * 3],
            pixels[minIndex * 3 + 1],
            pixels[minIndex * 3 + 2],
            pixels[maxIndex * 3],
            pixels[maxIndex * 3 + 1],
            pixels[maxIndex * 3 + 2]
        };
        quantDequantEndpoints(endpoints, 10);
        double currentError = assignWeights4(
                floatPixels,
                pixelScales,
                block.weights,
                endpoints,
                blockMaxVar,
                tryTwoSubsets);

        for (int pass = 0; pass < 2; pass++) {
            int[] trialEndpoints = leastSquaresEndpoints(pixels, block.weights);
            if (trialEndpoints == null) {
                break;
            }
            quantDequantEndpoints(trialEndpoints, 10);
            int[] trialWeights = new int[16];
            double trialError = assignWeights4(
                    floatPixels,
                    pixelScales,
                    trialWeights,
                    trialEndpoints,
                    blockMaxVar,
                    tryTwoSubsets);
            if (trialError < currentError) {
                currentError = trialError;
                endpoints = trialEndpoints;
                System.arraycopy(trialWeights, 0, block.weights, 0, 16);
            } else {
                break;
            }
        }

        setMode10Endpoints(block, endpoints);
        normalizeOneSubsetWeights(block);
        if (highQuality && tryTwoSubsets
                && blockMaxVar > (long) COMPLEX_STD_DEV_THRESH * COMPLEX_STD_DEV_THRESH * 16) {
            encodeBestTwoSubsetPattern(
                    pixels,
                    floatPixels,
                    pixelScales,
                    currentError,
                    block,
                    meanR,
                    meanG,
                    meanB,
                    axisR,
                    axisG,
                    axisB);
        }
    }

    private static void encodeBestTwoSubsetPattern(
            int[] pixels,
            float[][] floatPixels,
            float[] pixelScales,
            double currentError,
            Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block,
            int meanR,
            int meanG,
            int meanB,
            float axisR,
            float axisG,
            float axisB) {
        int desiredPatternBits = 0;
        for (int i = 0; i < 16; i++) {
            float projected = (pixels[i * 3] - meanR) * axisR
                    + (pixels[i * 3 + 1] - meanG) * axisG
                    + (pixels[i * 3 + 2] - meanB) * axisB;
            if (projected >= 0.0f) {
                desiredPatternBits |= 1 << i;
            }
        }

        int bestDiff = Integer.MAX_VALUE;
        int bestPattern = 0;
        for (int pattern = 0; pattern < 32; pattern++) {
            int patternBits = Bc7PartitionTables.partition2Mask(pattern);
            int diff = Integer.bitCount(patternBits ^ desiredPatternBits);
            int minDiff = (Math.min(diff, 16 - diff) << 8) | pattern;
            if (minDiff < bestDiff) {
                bestDiff = minDiff;
                bestPattern = pattern;
            }
        }

        encodeTwoSubsetPattern(pixels, floatPixels, pixelScales, currentError, block, meanR, meanG, meanB, bestPattern);
    }

    private static void encodeTwoSubsetPattern(
            int[] pixels,
            float[][] floatPixels,
            float[] pixelScales,
            double currentError,
            Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block,
            int meanR,
            int meanG,
            int meanB,
            int pattern) {
        long[][] subsetCov = new long[2][6];
        for (int i = 0; i < 16; i++) {
            int subset = Bc7PartitionTables.partition2(pattern, i);
            int r = pixels[i * 3] - meanR;
            int g = pixels[i * 3 + 1] - meanG;
            int b = pixels[i * 3 + 2] - meanB;
            subsetCov[subset][0] += (long) r * r;
            subsetCov[subset][1] += (long) r * g;
            subsetCov[subset][2] += (long) r * b;
            subsetCov[subset][3] += (long) g * g;
            subsetCov[subset][4] += (long) g * b;
            subsetCov[subset][5] += (long) b * b;
        }

        float[][] subsetAxis = new float[2][3];
        for (int subset = 0; subset < 2; subset++) {
            float c0 = subsetCov[subset][0];
            float c1 = subsetCov[subset][1];
            float c2 = subsetCov[subset][2];
            float c3 = subsetCov[subset][3];
            float c4 = subsetCov[subset][4];
            float c5 = subsetCov[subset][5];
            float scale = 1.0f / (Math.max(c0, Math.max(c3, c5)) + 1.0e-20f);
            float wx = scale * c0;
            float wy = scale * c3;
            float wz = scale * c5;
            float altR = c0 * wx + c1 * wy + c2 * wz;
            float altG = c1 * wx + c3 * wy + c4 * wz;
            float altB = c2 * wx + c4 * wy + c5 * wz;
            float length = square(altR) + square(altG) + square(altB);
            subsetAxis[subset][0] = 0.57735027f;
            subsetAxis[subset][1] = 0.57735027f;
            subsetAxis[subset][2] = 0.57735027f;
            if (Math.abs(length) >= SMALL_FLOAT) {
                float invLength = invSqrt(length);
                subsetAxis[subset][0] = altR * invLength;
                subsetAxis[subset][1] = altG * invLength;
                subsetAxis[subset][2] = altB * invLength;
            }
        }

        int[] minIndex = new int[2];
        int[] maxIndex = new int[2];
        float[] minDot = {Float.MAX_VALUE, Float.MAX_VALUE};
        float[] maxDot = {-Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < 16; i++) {
            int subset = Bc7PartitionTables.partition2(pattern, i);
            float dot = pixels[i * 3] * subsetAxis[subset][0]
                    + pixels[i * 3 + 1] * subsetAxis[subset][1]
                    + pixels[i * 3 + 2] * subsetAxis[subset][2];
            if (dot < minDot[subset]) {
                minDot[subset] = dot;
                minIndex[subset] = i;
            }
            if (dot > maxDot[subset]) {
                maxDot[subset] = dot;
                maxIndex[subset] = i;
            }
        }

        int[][] subsetMin = new int[2][3];
        int[][] subsetMax = new int[2][3];
        for (int subset = 0; subset < 2; subset++) {
            int minBase = minIndex[subset] * 3;
            int maxBase = maxIndex[subset] * 3;
            for (int c = 0; c < 3; c++) {
                subsetMin[subset][c] = pixels[minBase + c];
                subsetMax[subset][c] = pixels[maxBase + c];
            }
        }

        int[] trialWeights = new int[16];
        assignWeights3(trialWeights, pattern, subsetMin, subsetMax, floatPixels);
        leastSquaresEndpoints3(pixels, trialWeights, pattern, subsetMin, subsetMax);

        int mode = 9;
        int endpointBits = 6;
        int[][] blogEndpoints = new int[3][4];
        int[] modeOrder = {5, 1};
        for (int candidateMode : modeOrder) {
            int baseBits = Bc6hUastcHdr4x4Transcoder.MODE_SIG_BITS[candidateMode][0];
            fillQuantizedEndpoints(subsetMin, subsetMax, blogEndpoints, baseBits);
            if (canUseDeltaMode(candidateMode, blogEndpoints)) {
                mode = candidateMode;
                endpointBits = baseBits;
                break;
            }
        }
        if (mode == 9) {
            fillQuantizedEndpoints(subsetMin, subsetMax, blogEndpoints, endpointBits);
        }

        dequantizeEndpointPairs(blogEndpoints, subsetMin, subsetMax, endpointBits);
        double trialError = assignWeightsError3(
                trialWeights,
                pattern,
                subsetMin,
                subsetMax,
                floatPixels,
                pixelScales);
        if (trialError >= currentError) {
            return;
        }

        Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock trial =
                new Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock();
        trial.mode = mode;
        trial.partitionPattern = pattern;
        for (int c = 0; c < 3; c++) {
            System.arraycopy(blogEndpoints[c], 0, trial.endpoints[c], 0, 4);
        }
        System.arraycopy(trialWeights, 0, trial.weights, 0, 16);
        normalizeTwoSubsetWeights(trial, pattern);
        if (mode != 9) {
            convertTwoSubsetEndpointsToDeltas(trial);
        }
        copyBlock(trial, block);
    }

    private static long[] covariance(int[] pixels, int meanR, int meanG, int meanB) {
        long[] cov = new long[6];
        for (int i = 0; i < 16; i++) {
            int r = pixels[i * 3] - meanR;
            int g = pixels[i * 3 + 1] - meanG;
            int b = pixels[i * 3 + 2] - meanB;
            cov[0] += (long) r * r;
            cov[1] += (long) r * g;
            cov[2] += (long) r * b;
            cov[3] += (long) g * g;
            cov[4] += (long) g * b;
            cov[5] += (long) b * b;
        }
        return cov;
    }

    private static int[] leastSquaresEndpoints(int[] pixels, int[] weights) {
        float z00 = 0.0f;
        float z10 = 0.0f;
        float z11 = 0.0f;
        float q00R = 0.0f;
        float q00G = 0.0f;
        float q00B = 0.0f;
        float totalR = 0.0f;
        float totalG = 0.0f;
        float totalB = 0.0f;
        for (int i = 0; i < 16; i++) {
            int selector = weights[i];
            z00 += LS_WEIGHTS_4[selector][0];
            z10 += LS_WEIGHTS_4[selector][1];
            z11 += LS_WEIGHTS_4[selector][2];
            float w = LS_WEIGHTS_4[selector][3];
            float r = pixels[i * 3];
            float g = pixels[i * 3 + 1];
            float b = pixels[i * 3 + 2];
            q00R += w * r;
            q00G += w * g;
            q00B += w * b;
            totalR += r;
            totalG += g;
            totalB += b;
        }
        float q10R = totalR - q00R;
        float q10G = totalG - q00G;
        float q10B = totalB - q00B;
        float det = z00 * z11 - z10 * z10;
        if (Math.abs(det) < SMALL_FLOAT) {
            return null;
        }
        det = 1.0f / det;
        float iz00 = z11 * det;
        float iz01 = -z10 * det;
        float iz10 = -z10 * det;
        float iz11 = z00 * det;
        return new int[] {
            clamp(Math.round(iz10 * q00R + iz11 * q10R), 0, MAX_HALF),
            clamp(Math.round(iz10 * q00G + iz11 * q10G), 0, MAX_HALF),
            clamp(Math.round(iz10 * q00B + iz11 * q10B), 0, MAX_HALF),
            clamp(Math.round(iz00 * q00R + iz01 * q10R), 0, MAX_HALF),
            clamp(Math.round(iz00 * q00G + iz01 * q10G), 0, MAX_HALF),
            clamp(Math.round(iz00 * q00B + iz01 * q10B), 0, MAX_HALF)
        };
    }

    private static void assignWeightsSimple4(
            int[] pixels,
            int[] weights,
            int[] endpoints,
            long blockMaxVar) {
        float minR = halfToFloat(endpoints[0]);
        float minG = halfToFloat(endpoints[1]);
        float minB = halfToFloat(endpoints[2]);
        float maxR = halfToFloat(endpoints[3]);
        float maxG = halfToFloat(endpoints[4]);
        float maxB = halfToFloat(endpoints[5]);
        float dirR = maxR - minR;
        float dirG = maxG - minG;
        float dirB = maxB - minB;
        float length = invSqrt(dirR * dirR + dirG * dirG + dirB * dirB);
        if (length != 0.0f) {
            dirR *= length;
            dirG *= length;
            dirB *= length;
        }
        float low = minR * dirR + minG * dirG + minB * dirB;
        float high = maxR * dirR + maxG * dirG + maxB * dirB;
        if (low >= MAX_HALF_FLOAT || high >= MAX_HALF_FLOAT) {
            float[][] floatPixels = new float[16][3];
            float[] pixelScales = new float[16];
            for (int i = 0; i < 16; i++) {
                floatPixels[i][0] = halfToFloat(pixels[i * 3]);
                floatPixels[i][1] = halfToFloat(pixels[i * 3 + 1]);
                floatPixels[i][2] = halfToFloat(pixels[i * 3 + 2]);
                pixelScales[i] = 1.0f / (square(floatPixels[i][0])
                        + square(floatPixels[i][1])
                        + square(floatPixels[i][2])
                        + MIN_HALF_FLOAT);
            }
            assignWeights4(floatPixels, pixelScales, weights, endpoints, blockMaxVar, false);
            return;
        }
        float lowHalf = ftoh(low);
        float highHalf = ftoh(high);
        float scale = highHalf == lowHalf ? 0.0f : 14.93333f / (highHalf - lowHalf);
        lowHalf = (-lowHalf * scale) + 0.53333f;
        for (int i = 0; i < 16; i++) {
            float r = halfToFloat(pixels[i * 3]);
            float g = halfToFloat(pixels[i * 3 + 1]);
            float b = halfToFloat(pixels[i * 3 + 2]);
            float w = ftoh(Math.min(r * dirR + g * dirG + b * dirB, MAX_HALF_FLOAT));
            weights[i] = clamp((int) (w * scale + lowHalf), 0, 15);
        }
    }

    private static double assignWeights4(
            float[][] floatPixels,
            float[] pixelScales,
            int[] weights,
            int[] endpoints,
            long blockMaxVar,
            boolean tryTwoSubsets) {
        float[] cr = new float[16];
        float[] cg = new float[16];
        float[] cb = new float[16];
        for (int i = 0; i < 16; i++) {
            int w = WEIGHTS_4[i];
            cr[i] = halfToFloat((endpoints[0] * (64 - w) + endpoints[3] * w + 32) >>> 6);
            cg[i] = halfToFloat((endpoints[1] * (64 - w) + endpoints[4] * w + 32) >>> 6);
            cb[i] = halfToFloat((endpoints[2] * (64 - w) + endpoints[5] * w + 32) >>> 6);
        }
        float dirR = cr[15] - cr[0];
        float dirG = cg[15] - cg[0];
        float dirB = cb[15] - cb[0];
        float[] dots = new float[16];
        for (int i = 0; i < 16; i++) {
            dots[i] = cr[i] * dirR + cg[i] * dirG + cb[i] * dirB;
        }
        float[] midDots = new float[15];
        boolean increasing = true;
        for (int i = 0; i < 15; i++) {
            midDots[i] = (dots[i] + dots[i + 1]) * 0.5f;
            if (dots[i] > dots[i + 1]) {
                increasing = false;
            }
        }
        boolean checkMoreColors = blockMaxVar > (long) VERY_COMPLEX_STD_DEV_THRESH
                * VERY_COMPLEX_STD_DEV_THRESH * 16;
        double totalError = 0.0;
        for (int i = 0; i < 16; i++) {
            float qr = floatPixels[i][0];
            float qg = floatPixels[i][1];
            float qb = floatPixels[i][2];
            int bestIndex;
            if (!increasing) {
                float projected = qr * dirR + qg * dirG + qb * dirB;
                float best = Math.abs(projected - dots[0]);
                bestIndex = 0;
                for (int j = 1; j < 16; j++) {
                    float error = Math.abs(projected - dots[j]);
                    if (error < best) {
                        best = error;
                        bestIndex = j;
                    }
                }
            } else {
                bestIndex = binaryWeightIndex(qr * dirR + qg * dirG + qb * dirB, midDots);
                if (tryTwoSubsets && checkMoreColors) {
                    float err = colorError(qr, qg, qb, cr[bestIndex], cg[bestIndex], cb[bestIndex]);
                    int altIndex = bestIndex + 1;
                    if (altIndex > 15) {
                        altIndex = 13;
                    }
                    float altErr = colorError(qr, qg, qb, cr[altIndex], cg[altIndex], cb[altIndex]);
                    if (altErr < err) {
                        err = altErr;
                        bestIndex = altIndex;
                    }
                    int altIndex2 = bestIndex - 1;
                    if (altIndex2 < 0) {
                        altIndex2 = 2;
                    }
                    float altErr2 = colorError(qr, qg, qb, cr[altIndex2], cg[altIndex2], cb[altIndex2]);
                    if (altErr2 < err) {
                        bestIndex = altIndex2;
                    }
                }
            }
            weights[i] = bestIndex;
            totalError += colorError(qr, qg, qb, cr[bestIndex], cg[bestIndex], cb[bestIndex]) * pixelScales[i];
        }
        return totalError;
    }

    private static void assignWeights3(
            int[] weights,
            int pattern,
            int[][] subsetMin,
            int[][] subsetMax,
            float[][] floatPixels) {
        float[][] colorsR = new float[2][8];
        float[][] colorsG = new float[2][8];
        float[][] colorsB = new float[2][8];
        for (int subset = 0; subset < 2; subset++) {
            for (int j = 0; j < 8; j++) {
                int weight = WEIGHTS_3[j];
                colorsR[subset][j] = halfToFloat((subsetMin[subset][0] * (64 - weight)
                        + subsetMax[subset][0] * weight + 32) >>> 6);
                colorsG[subset][j] = halfToFloat((subsetMin[subset][1] * (64 - weight)
                        + subsetMax[subset][1] * weight + 32) >>> 6);
                colorsB[subset][j] = halfToFloat((subsetMin[subset][2] * (64 - weight)
                        + subsetMax[subset][2] * weight + 32) >>> 6);
            }
        }
        for (int i = 0; i < 16; i++) {
            int subset = Bc7PartitionTables.partition2(pattern, i);
            float qr = floatPixels[i][0];
            float qg = floatPixels[i][1];
            float qb = floatPixels[i][2];
            int best = 0;
            float bestError = colorError(qr, qg, qb, colorsR[subset][0], colorsG[subset][0], colorsB[subset][0]);
            for (int j = 1; j < 8; j++) {
                float error = colorError(qr, qg, qb, colorsR[subset][j], colorsG[subset][j], colorsB[subset][j]);
                if (error < bestError) {
                    bestError = error;
                    best = j;
                }
            }
            weights[i] = best;
        }
    }

    private static double assignWeightsError3(
            int[] weights,
            int pattern,
            int[][] subsetMin,
            int[][] subsetMax,
            float[][] floatPixels,
            float[] pixelScales) {
        float[][] colorsR = new float[2][8];
        float[][] colorsG = new float[2][8];
        float[][] colorsB = new float[2][8];
        for (int subset = 0; subset < 2; subset++) {
            for (int j = 0; j < 8; j++) {
                int weight = WEIGHTS_3[j];
                colorsR[subset][j] = halfToFloat((subsetMin[subset][0] * (64 - weight)
                        + subsetMax[subset][0] * weight + 32) >>> 6);
                colorsG[subset][j] = halfToFloat((subsetMin[subset][1] * (64 - weight)
                        + subsetMax[subset][1] * weight + 32) >>> 6);
                colorsB[subset][j] = halfToFloat((subsetMin[subset][2] * (64 - weight)
                        + subsetMax[subset][2] * weight + 32) >>> 6);
            }
        }
        double totalError = 0.0;
        for (int i = 0; i < 16; i++) {
            int subset = Bc7PartitionTables.partition2(pattern, i);
            float qr = floatPixels[i][0];
            float qg = floatPixels[i][1];
            float qb = floatPixels[i][2];
            int best = 0;
            float bestError = colorError(qr, qg, qb, colorsR[subset][0], colorsG[subset][0], colorsB[subset][0]);
            for (int j = 1; j < 8; j++) {
                float error = colorError(qr, qg, qb, colorsR[subset][j], colorsG[subset][j], colorsB[subset][j]);
                if (error < bestError) {
                    bestError = error;
                    best = j;
                }
            }
            weights[i] = best;
            totalError += bestError * pixelScales[i];
        }
        return totalError;
    }

    private static void leastSquaresEndpoints3(
            int[] pixels,
            int[] weights,
            int pattern,
            int[][] subsetMin,
            int[][] subsetMax) {
        float[] z00 = new float[2];
        float[] z10 = new float[2];
        float[] z11 = new float[2];
        float[] q00R = new float[2];
        float[] q00G = new float[2];
        float[] q00B = new float[2];
        float[] totalR = new float[2];
        float[] totalG = new float[2];
        float[] totalB = new float[2];
        for (int i = 0; i < 16; i++) {
            int subset = Bc7PartitionTables.partition2(pattern, i);
            int selector = weights[i];
            z00[subset] += LS_WEIGHTS_3[selector][0];
            z10[subset] += LS_WEIGHTS_3[selector][1];
            z11[subset] += LS_WEIGHTS_3[selector][2];
            float weight = LS_WEIGHTS_3[selector][3];
            float r = pixels[i * 3];
            float g = pixels[i * 3 + 1];
            float b = pixels[i * 3 + 2];
            q00R[subset] += weight * r;
            q00G[subset] += weight * g;
            q00B[subset] += weight * b;
            totalR[subset] += r;
            totalG[subset] += g;
            totalB[subset] += b;
        }
        for (int subset = 0; subset < 2; subset++) {
            float q10R = totalR[subset] - q00R[subset];
            float q10G = totalG[subset] - q00G[subset];
            float q10B = totalB[subset] - q00B[subset];
            float det = z00[subset] * z11[subset] - z10[subset] * z10[subset];
            if (Math.abs(det) < SMALL_FLOAT) {
                continue;
            }
            det = 1.0f / det;
            float iz00 = z11[subset] * det;
            float iz01 = -z10[subset] * det;
            float iz10 = -z10[subset] * det;
            float iz11 = z00[subset] * det;
            subsetMax[subset][0] = clamp(fastRoundfInt(iz00 * q00R[subset] + iz01 * q10R), 0, MAX_HALF);
            subsetMin[subset][0] = clamp(fastRoundfInt(iz10 * q00R[subset] + iz11 * q10R), 0, MAX_HALF);
            subsetMax[subset][1] = clamp(fastRoundfInt(iz00 * q00G[subset] + iz01 * q10G), 0, MAX_HALF);
            subsetMin[subset][1] = clamp(fastRoundfInt(iz10 * q00G[subset] + iz11 * q10G), 0, MAX_HALF);
            subsetMax[subset][2] = clamp(fastRoundfInt(iz00 * q00B[subset] + iz01 * q10B), 0, MAX_HALF);
            subsetMin[subset][2] = clamp(fastRoundfInt(iz10 * q00B[subset] + iz11 * q10B), 0, MAX_HALF);
        }
    }

    private static void fillQuantizedEndpoints(
            int[][] subsetMin,
            int[][] subsetMax,
            int[][] blogEndpoints,
            int bits) {
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 3; c++) {
                blogEndpoints[c][subset * 2] = Bc6hUastcHdr4x4Transcoder.halfToBlog(subsetMin[subset][c], bits);
                blogEndpoints[c][subset * 2 + 1] = Bc6hUastcHdr4x4Transcoder.halfToBlog(subsetMax[subset][c], bits);
            }
        }
    }

    private static boolean canUseDeltaMode(int mode, int[][] blogEndpoints) {
        for (int c = 0; c < 3; c++) {
            int deltaBits = Bc6hUastcHdr4x4Transcoder.MODE_SIG_BITS[mode][c + 1];
            int maxDelta = (1 << (deltaBits - 1)) - 1;
            int minDelta = -maxDelta;
            int delta0 = blogEndpoints[c][1] - blogEndpoints[c][0];
            int delta1 = blogEndpoints[c][2] - blogEndpoints[c][0];
            int delta2 = blogEndpoints[c][3] - blogEndpoints[c][0];
            int delta3 = blogEndpoints[c][2] - blogEndpoints[c][1];
            int delta4 = blogEndpoints[c][3] - blogEndpoints[c][1];
            if (delta0 < minDelta || delta0 > maxDelta
                    || delta1 < minDelta || delta1 > maxDelta
                    || delta2 < minDelta || delta2 > maxDelta
                    || delta3 < minDelta || delta3 > maxDelta
                    || delta4 < minDelta || delta4 > maxDelta) {
                return false;
            }
        }
        return true;
    }

    private static void dequantizeEndpointPairs(
            int[][] blogEndpoints,
            int[][] subsetMin,
            int[][] subsetMax,
            int bits) {
        for (int subset = 0; subset < 2; subset++) {
            for (int c = 0; c < 3; c++) {
                subsetMin[subset][c] = convertToHalf(dequantize(blogEndpoints[c][subset * 2], bits));
                subsetMax[subset][c] = convertToHalf(dequantize(blogEndpoints[c][subset * 2 + 1], bits));
            }
        }
    }

    private static void normalizeTwoSubsetWeights(
            Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block,
            int pattern) {
        if ((block.weights[0] & 4) != 0) {
            for (int c = 0; c < 3; c++) {
                swap(block.endpoints[c], 0, 1);
            }
            invertSubsetWeights(block.weights, pattern, 0);
        }
        int secondSubsetAnchor = Bc7LdrBlockPacker.colorSelectorAnchors(1, pattern)[1];
        if ((block.weights[secondSubsetAnchor] & 4) != 0) {
            for (int c = 0; c < 3; c++) {
                swap(block.endpoints[c], 2, 3);
            }
            invertSubsetWeights(block.weights, pattern, 1);
        }
    }

    private static void invertSubsetWeights(int[] weights, int pattern, int subset) {
        for (int i = 0; i < 16; i++) {
            if (Bc7PartitionTables.partition2(pattern, i) == subset) {
                weights[i] = 7 - weights[i];
            }
        }
    }

    private static void convertTwoSubsetEndpointsToDeltas(Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block) {
        for (int c = 0; c < 3; c++) {
            int deltaBits = Bc6hUastcHdr4x4Transcoder.MODE_SIG_BITS[block.mode][c + 1];
            int deltaMask = (1 << deltaBits) - 1;
            int base = block.endpoints[c][0];
            block.endpoints[c][1] = (block.endpoints[c][1] - base) & deltaMask;
            block.endpoints[c][2] = (block.endpoints[c][2] - base) & deltaMask;
            block.endpoints[c][3] = (block.endpoints[c][3] - base) & deltaMask;
        }
    }

    private static void copyBlock(
            Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock source,
            Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock target) {
        target.mode = source.mode;
        target.partitionPattern = source.partitionPattern;
        for (int c = 0; c < 3; c++) {
            System.arraycopy(source.endpoints[c], 0, target.endpoints[c], 0, source.endpoints[c].length);
        }
        System.arraycopy(source.weights, 0, target.weights, 0, source.weights.length);
    }

    private static int binaryWeightIndex(float dot, float[] midDots) {
        int low = 0;
        int mid = low + 7;
        if (dot >= midDots[mid]) {
            low = mid + 1;
        }
        mid = low + 3;
        if (mid < 15 && dot >= midDots[mid]) {
            low = mid + 1;
        }
        mid = low + 1;
        if (mid < 15 && dot >= midDots[mid]) {
            low = mid + 1;
        }
        mid = low;
        if (mid < 15 && dot >= midDots[mid]) {
            low = mid + 1;
        }
        return Math.min(low, 15);
    }

    private static void setMode10Endpoints(
            Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block,
            int[] endpoints) {
        block.endpoints[0][0] = Bc6hUastcHdr4x4Transcoder.halfToBlog(endpoints[0], 10);
        block.endpoints[0][1] = Bc6hUastcHdr4x4Transcoder.halfToBlog(endpoints[3], 10);
        block.endpoints[1][0] = Bc6hUastcHdr4x4Transcoder.halfToBlog(endpoints[1], 10);
        block.endpoints[1][1] = Bc6hUastcHdr4x4Transcoder.halfToBlog(endpoints[4], 10);
        block.endpoints[2][0] = Bc6hUastcHdr4x4Transcoder.halfToBlog(endpoints[2], 10);
        block.endpoints[2][1] = Bc6hUastcHdr4x4Transcoder.halfToBlog(endpoints[5], 10);
    }

    private static void normalizeOneSubsetWeights(Bc6hUastcHdr4x4Transcoder.LogicalBc6hBlock block) {
        if ((block.weights[0] & 8) == 0) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            block.weights[i] = 15 - block.weights[i];
        }
        for (int c = 0; c < 3; c++) {
            int temp = block.endpoints[c][0];
            block.endpoints[c][0] = block.endpoints[c][1];
            block.endpoints[c][1] = temp;
        }
    }

    private static void quantDequantEndpoints(int[] endpoints, int bits) {
        for (int i = 0; i < endpoints.length; i++) {
            endpoints[i] = convertToHalf(dequantize(Bc6hUastcHdr4x4Transcoder.halfToBlog(endpoints[i], bits), bits));
        }
    }

    private static int dequantize(int value, int bits) {
        if (bits >= 15) {
            return value;
        }
        if (value == 0) {
            return 0;
        }
        if (value == (1 << bits) - 1) {
            return 0xFFFF;
        }
        return ((value << 16) + 0x8000) >>> bits;
    }

    private static int convertToHalf(int value) {
        return (value * 31) >>> 6;
    }

    private static int halfToBlog16(int half) {
        return (half * 64 + 30) / 31;
    }

    private static float halfToFloat(int half) {
        return Float.intBitsToFloat(half << 13) * Float.intBitsToFloat(0x77800000);
    }

    private static float ftoh(float value) {
        int sign = value < 0.0f ? -1 : 1;
        return fastFloatToHalf(Math.abs(value)) * sign;
    }

    private static int fastFloatToHalf(float value) {
        int raw = Float.floatToRawIntBits(value * Float.intBitsToFloat(0x07800000));
        int half = (raw >>> 13) & 0x7FFF;
        int mantissa = raw & 8191;
        if (mantissa > 4096) {
            half++;
        }
        return Math.min(half, MAX_HALF);
    }

    private static float invSqrt(float value) {
        int bits = Float.floatToRawIntBits(value);
        bits = 0x5F1FFFF9 - (bits >>> 1);
        float approx = Float.intBitsToFloat(bits);
        return 0.703952253f * approx * (2.38924456f - value * (approx * approx));
    }

    private static float colorError(float r, float g, float b, float er, float eg, float eb) {
        return square(r - er) + square(g - eg) + square(b - eb);
    }

    private static float square(float value) {
        return value * value;
    }

    private static int clamp(int value, int low, int high) {
        if (value < low) {
            return low;
        }
        return Math.min(value, high);
    }

    private static int fastRoundfInt(float value) {
        return value >= 0.0f ? (int) (value + 0.5f) : (int) (value - 0.5f);
    }

    private static void swap(int[] values, int first, int second) {
        int temp = values[first];
        values[first] = values[second];
        values[second] = temp;
    }

    private static float[][] buildLsWeights3() {
        float[][] weights = new float[8][4];
        for (int i = 0; i < 8; i++) {
            float w = WEIGHTS_3[i] * (1.0f / 64.0f);
            weights[i][0] = w * w;
            weights[i][1] = (1.0f - w) * w;
            weights[i][2] = (1.0f - w) * (1.0f - w);
            weights[i][3] = w;
        }
        return weights;
    }

    private static float[][] buildLsWeights4() {
        float[][] weights = new float[16][4];
        for (int i = 0; i < 16; i++) {
            float w = WEIGHTS_4[i] * (1.0f / 64.0f);
            weights[i][0] = w * w;
            weights[i][1] = (1.0f - w) * w;
            weights[i][2] = (1.0f - w) * (1.0f - w);
            weights[i][3] = w;
        }
        return weights;
    }
}
