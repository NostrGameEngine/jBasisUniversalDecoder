package org.ngengine.basis;

/**
 * BC1 block packer used by LDR UASTC/XUASTC targets.
 */
final class Bc1LdrBlockPacker {
    private static final byte[] MATCH5_EQUALS_1 = createBc1MatchTable(31, 31, 1);
    private static final byte[] MATCH6_EQUALS_1 = createBc1MatchTable(63, 63, 1);
    private static final int[] SEL_TO_PACKED = {0, 2, 3, 1};
    private static final int[] PACKED_TO_SEL = {0, 3, 1, 2};
    private static final int[] LS_WEIGHT_VALS = {0x000009, 0x010204, 0x040201, 0x090000};

    private Bc1LdrBlockPacker() {
    }

    static byte[] packRgba(byte[] rgba, int width, int height) {
        if (rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new BasisDecodeException("RGBA data size does not match image dimensions");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), 8)];
        byte[] rgbaBlock = new byte[64];
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                extractBlock(rgba, width, height, blockX, blockY, rgbaBlock);
                packRgbaBlock(output, (blockY * blocksX + blockX) * 8, rgbaBlock);
            }
        }
        return output;
    }

    static void packRgbaBlock(byte[] output, int offset, byte[] rgbaBlock) {
        packRgbaBlock(output, offset, rgbaBlock, 0, false);
    }

    static void packRgbaBlockWithSelectors(byte[] output, int offset, byte[] rgbaBlock, int packedSelectors) {
        packRgbaBlock(output, offset, rgbaBlock, packedSelectors, true);
    }

    static void packSolidBlock(byte[] output, int offset, int red, int green, int blue) {
        int mask = 0xAA;
        int max16 = (matchHi(MATCH5_EQUALS_1, red) << 11)
                | (matchHi(MATCH6_EQUALS_1, green) << 5)
                | matchHi(MATCH5_EQUALS_1, blue);
        int min16 = (matchLo(MATCH5_EQUALS_1, red) << 11)
                | (matchLo(MATCH6_EQUALS_1, green) << 5)
                | matchLo(MATCH5_EQUALS_1, blue);

        if (min16 == max16) {
            mask = 0;
            if (min16 > 0) {
                min16--;
            } else {
                max16 = 1;
                min16 = 0;
                mask = 0x55;
            }
        }

        if (max16 < min16) {
            int temp = max16;
            max16 = min16;
            min16 = temp;
            mask ^= 0x55;
        }

        writeEndpoints(output, offset, max16, min16);
        fillSelectors(output, offset, mask);
    }

    static int packColor888(int red, int green, int blue) {
        int r = Math.min((red * 31 + 127) / 255, 31);
        int g = Math.min((green * 63 + 127) / 255, 63);
        int b = Math.min((blue * 31 + 127) / 255, 31);
        return packUnscaledColor(r, g, b);
    }

    static void writeBlock(byte[] output, int offset, int color0, int color1, int packedSelectors) {
        writeEndpoints(output, offset, color0, color1);
        output[offset + 4] = (byte) packedSelectors;
        output[offset + 5] = (byte) (packedSelectors >>> 8);
        output[offset + 6] = (byte) (packedSelectors >>> 16);
        output[offset + 7] = (byte) (packedSelectors >>> 24);
    }

    private static void packRgbaBlock(
            byte[] output,
            int offset,
            byte[] rgbaBlock,
            int packedSelectors,
            boolean useSelectors) {
        if (rgbaBlock.length != 64) {
            throw new BasisDecodeException("RGBA block data size mismatch");
        }

        int avgR = -1;
        int avgG = 0;
        int avgB = 0;
        int lr = 0;
        int lg = 0;
        int lb = 0;
        int hr = 0;
        int hg = 0;
        int hb = 0;
        int[] selectors = new int[16];

        if (useSelectors) {
            for (int i = 0; i < selectors.length; i++) {
                selectors[i] = PACKED_TO_SEL[(packedSelectors >>> (i * 2)) & 3];
            }
        } else {
            int fr = channel(rgbaBlock, 0, 0);
            int fg = channel(rgbaBlock, 0, 1);
            int fb = channel(rgbaBlock, 0, 2);
            int j = 1;
            while (j < 16
                    && channel(rgbaBlock, j, 0) == fr
                    && channel(rgbaBlock, j, 1) == fg
                    && channel(rgbaBlock, j, 2) == fb) {
                j++;
            }
            if (j == 16) {
                packSolidBlock(output, offset, fr, fg, fb);
                return;
            }

            int totalR = fr;
            int totalG = fg;
            int totalB = fb;
            int maxR = fr;
            int maxG = fg;
            int maxB = fb;
            int minR = fr;
            int minG = fg;
            int minB = fb;
            for (int i = 1; i < 16; i++) {
                int r = channel(rgbaBlock, i, 0);
                int g = channel(rgbaBlock, i, 1);
                int b = channel(rgbaBlock, i, 2);
                maxR = Math.max(maxR, r);
                maxG = Math.max(maxG, g);
                maxB = Math.max(maxB, b);
                minR = Math.min(minR, r);
                minG = Math.min(minG, g);
                minB = Math.min(minB, b);
                totalR += r;
                totalG += g;
                totalB += b;
            }

            avgR = (totalR + 8) >> 4;
            avgG = (totalG + 8) >> 4;
            avgB = (totalB + 8) >> 4;

            int[] icov = new int[6];
            for (int i = 0; i < 16; i++) {
                int r = channel(rgbaBlock, i, 0) - avgR;
                int g = channel(rgbaBlock, i, 1) - avgG;
                int b = channel(rgbaBlock, i, 2) - avgB;
                icov[0] += r * r;
                icov[1] += r * g;
                icov[2] += r * b;
                icov[3] += g * g;
                icov[4] += g * b;
                icov[5] += b * b;
            }

            float[] cov = new float[6];
            for (int i = 0; i < cov.length; i++) {
                cov[i] = icov[i] * (1.0f / 255.0f);
            }

            float xr = maxR - minR;
            float xg = maxG - minG;
            float xb = maxB - minB;
            for (int i = 0; i < 4; i++) {
                float r = xr * cov[0] + xg * cov[1] + xb * cov[2];
                float g = xr * cov[1] + xg * cov[3] + xb * cov[4];
                float b = xr * cov[2] + xg * cov[4] + xb * cov[5];
                xr = r;
                xg = g;
                xb = b;
            }

            float k = Math.max(Math.abs(xr), Math.max(Math.abs(xg), Math.abs(xb)));
            int axisR = 306;
            int axisG = 601;
            int axisB = 117;
            if (k >= 2.0f) {
                float m = 1024.0f / k;
                axisR = (int) (xr * m);
                axisG = (int) (xg * m);
                axisB = (int) (xb * m);
            }

            int lowDot = Integer.MAX_VALUE;
            int highDot = Integer.MIN_VALUE;
            int lowColor = 0;
            int highColor = 0;
            for (int i = 0; i < 16; i++) {
                int dot = channel(rgbaBlock, i, 0) * axisR
                        + channel(rgbaBlock, i, 1) * axisG
                        + channel(rgbaBlock, i, 2) * axisB;
                if (dot < lowDot) {
                    lowDot = dot;
                    lowColor = i;
                }
                if (dot > highDot) {
                    highDot = dot;
                    highColor = i;
                }
            }

            lr = to5(channel(rgbaBlock, lowColor, 0));
            lg = to6(channel(rgbaBlock, lowColor, 1));
            lb = to5(channel(rgbaBlock, lowColor, 2));
            hr = to5(channel(rgbaBlock, highColor, 0));
            hg = to6(channel(rgbaBlock, highColor, 1));
            hb = to5(channel(rgbaBlock, highColor, 2));

            findSelectors(rgbaBlock, lr, lg, lb, hr, hg, hb, selectors);
        }

        float[] low = new float[3];
        float[] high = new float[3];
        if (!computeLeastSquaresEndpoints(rgbaBlock, selectors, low, high)) {
            if (avgR < 0) {
                int totalR = 0;
                int totalG = 0;
                int totalB = 0;
                for (int i = 0; i < 16; i++) {
                    totalR += channel(rgbaBlock, i, 0);
                    totalG += channel(rgbaBlock, i, 1);
                    totalB += channel(rgbaBlock, i, 2);
                }
                avgR = (totalR + 8) >> 4;
                avgG = (totalG + 8) >> 4;
                avgB = (totalB + 8) >> 4;
            }
            lr = matchHi(MATCH5_EQUALS_1, avgR);
            lg = matchHi(MATCH6_EQUALS_1, avgG);
            lb = matchHi(MATCH5_EQUALS_1, avgB);
            hr = matchLo(MATCH5_EQUALS_1, avgR);
            hg = matchLo(MATCH6_EQUALS_1, avgG);
            hb = matchLo(MATCH5_EQUALS_1, avgB);
        } else {
            lr = quantizeEndpoint(low[0], 31);
            lg = quantizeEndpoint(low[1], 63);
            lb = quantizeEndpoint(low[2], 31);
            hr = quantizeEndpoint(high[0], 31);
            hg = quantizeEndpoint(high[1], 63);
            hb = quantizeEndpoint(high[2], 31);
        }

        findSelectors(rgbaBlock, lr, lg, lb, hr, hg, hb, selectors);
        writeEncodedSelectors(
                output,
                offset,
                packUnscaledColor(lr, lg, lb),
                packUnscaledColor(hr, hg, hb),
                selectors);
    }

    private static void writeEncodedSelectors(
            byte[] output,
            int offset,
            int lowColor,
            int highColor,
            int[] selectors) {
        if (lowColor == highColor) {
            int mask = 0;
            if (highColor > 0) {
                highColor--;
            } else {
                highColor = 0;
                lowColor = 1;
                mask = 0x55;
            }
            writeEndpoints(output, offset, lowColor, highColor);
            fillSelectors(output, offset, mask);
            return;
        }

        int invertMask = 0;
        if (lowColor < highColor) {
            int temp = lowColor;
            lowColor = highColor;
            highColor = temp;
            invertMask = 0x55;
        }

        int packed = 0;
        for (int i = 0; i < 16; i++) {
            packed |= SEL_TO_PACKED[selectors[i]] << (i * 2);
        }
        writeEndpoints(output, offset, lowColor, highColor);
        output[offset + 4] = (byte) (packed ^ invertMask);
        output[offset + 5] = (byte) ((packed >>> 8) ^ invertMask);
        output[offset + 6] = (byte) ((packed >>> 16) ^ invertMask);
        output[offset + 7] = (byte) ((packed >>> 24) ^ invertMask);
    }

    private static boolean computeLeastSquaresEndpoints(
            byte[] rgbaBlock,
            int[] selectors,
            float[] low,
            float[] high) {
        int uq00R = 0;
        int uq00G = 0;
        int uq00B = 0;
        int totalR = 0;
        int totalG = 0;
        int totalB = 0;
        int weightAccum = 0;
        for (int i = 0; i < 16; i++) {
            int r = channel(rgbaBlock, i, 0);
            int g = channel(rgbaBlock, i, 1);
            int b = channel(rgbaBlock, i, 2);
            final int selector = selectors[i];
            totalR += r;
            totalG += g;
            totalB += b;
            weightAccum += LS_WEIGHT_VALS[selector];
            uq00R += selector * r;
            uq00G += selector * g;
            uq00B += selector * b;
        }

        float q00R = uq00R;
        float q00G = uq00G;
        float q00B = uq00B;
        final float q10R = totalR * 3.0f - q00R;
        final float q10G = totalG * 3.0f - q00G;
        final float q10B = totalB * 3.0f - q00B;
        float z00 = (weightAccum >>> 16) & 0xFF;
        float z10 = (weightAccum >>> 8) & 0xFF;
        float z11 = weightAccum & 0xFF;
        float det = z00 * z11 - z10 * z10;
        if (Math.abs(det) < 1.0e-8f) {
            return false;
        }

        det = 3.0f / det;
        float iz00 = z11 * det;
        float iz01 = -z10 * det;
        float iz10 = -z10 * det;
        float iz11 = z00 * det;
        low[0] = Math.fma(iz00, q00R, iz01 * q10R);
        high[0] = Math.fma(iz10, q00R, iz11 * q10R);
        low[1] = Math.fma(iz00, q00G, iz01 * q10G);
        high[1] = Math.fma(iz10, q00G, iz11 * q10G);
        low[2] = Math.fma(iz00, q00B, iz01 * q10B);
        high[2] = Math.fma(iz10, q00B, iz11 * q10B);

        for (int component = 0; component < 3; component++) {
            if (low[component] < 0.0f || high[component] > 255.0f) {
                int lo = 255;
                int hi = 0;
                for (int i = 0; i < 16; i++) {
                    int value = channel(rgbaBlock, i, component);
                    lo = Math.min(lo, value);
                    hi = Math.max(hi, value);
                }
                if (lo == hi) {
                    low[component] = lo;
                    high[component] = hi;
                }
            }
        }
        return true;
    }

    private static void findSelectors(
            byte[] rgbaBlock,
            int lr,
            int lg,
            int lb,
            int hr,
            int hg,
            int hb,
            int[] selectors) {
        int[] blockR = new int[4];
        int[] blockG = new int[4];
        int[] blockB = new int[4];
        blockR[0] = expand5(lr);
        blockG[0] = expand6(lg);
        blockB[0] = expand5(lb);
        blockR[3] = expand5(hr);
        blockG[3] = expand6(hg);
        blockB[3] = expand5(hb);
        blockR[1] = (blockR[0] * 2 + blockR[3]) / 3;
        blockG[1] = (blockG[0] * 2 + blockG[3]) / 3;
        blockB[1] = (blockB[0] * 2 + blockB[3]) / 3;
        blockR[2] = (blockR[3] * 2 + blockR[0]) / 3;
        blockG[2] = (blockG[3] * 2 + blockG[0]) / 3;
        blockB[2] = (blockB[3] * 2 + blockB[0]) / 3;

        int ar = blockR[3] - blockR[0];
        int ag = blockG[3] - blockG[0];
        int ab = blockB[3] - blockB[0];
        int[] dots = new int[4];
        for (int i = 0; i < 4; i++) {
            dots[i] = blockR[i] * ar + blockG[i] * ag + blockB[i] * ab;
        }
        int t0 = dots[0] + dots[1];
        int t1 = dots[1] + dots[2];
        int t2 = dots[2] + dots[3];
        ar *= 2;
        ag *= 2;
        ab *= 2;
        int[] selectorMap = {3, 2, 1, 0};
        for (int i = 0; i < 16; i++) {
            int d = channel(rgbaBlock, i, 0) * ar
                    + channel(rgbaBlock, i, 1) * ag
                    + channel(rgbaBlock, i, 2) * ab;
            int index = (d <= t0 ? 1 : 0) + (d < t1 ? 1 : 0) + (d < t2 ? 1 : 0);
            selectors[i] = selectorMap[index];
        }
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
                System.arraycopy(rgba, src, block, dst, 4);
            }
        }
    }

    private static byte[] createBc1MatchTable(int maxLo, int maxHi, int selector) {
        byte[] result = new byte[256 * 2];
        for (int color = 0; color < 256; color++) {
            int lowestError = 256;
            for (int lo = 0; lo <= maxLo; lo++) {
                for (int hi = 0; hi <= maxHi; hi++) {
                    int loExpanded = maxHi == 63 ? expand6(lo) : expand5(lo);
                    int hiExpanded = maxHi == 63 ? expand6(hi) : expand5(hi);
                    int error;
                    if (selector == 1) {
                        error = Math.abs(((hiExpanded * 2 + loExpanded) / 3) - color);
                        error += (Math.abs(hiExpanded - loExpanded) * 3) / 100;
                    } else {
                        error = Math.abs(hiExpanded - color);
                    }
                    if (error < lowestError) {
                        result[color * 2] = (byte) hi;
                        result[color * 2 + 1] = (byte) lo;
                        lowestError = error;
                    }
                }
            }
        }
        return result;
    }

    private static void writeEndpoints(byte[] blocks, int blockOffset, int color0, int color1) {
        blocks[blockOffset] = (byte) color0;
        blocks[blockOffset + 1] = (byte) (color0 >>> 8);
        blocks[blockOffset + 2] = (byte) color1;
        blocks[blockOffset + 3] = (byte) (color1 >>> 8);
    }

    private static void fillSelectors(byte[] blocks, int blockOffset, int selectorByte) {
        blocks[blockOffset + 4] = (byte) selectorByte;
        blocks[blockOffset + 5] = (byte) selectorByte;
        blocks[blockOffset + 6] = (byte) selectorByte;
        blocks[blockOffset + 7] = (byte) selectorByte;
    }

    private static int packUnscaledColor(int red, int green, int blue) {
        return blue | (green << 5) | (red << 11);
    }

    private static int to5(int value) {
        int v = value * 31 + 128;
        return (v + (v >>> 8)) >>> 8;
    }

    private static int to6(int value) {
        int v = value * 63 + 128;
        return (v + (v >>> 8)) >>> 8;
    }

    private static int expand5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int expand6(int value) {
        return (value << 2) | (value >>> 4);
    }

    private static int matchHi(byte[] table, int color) {
        return Byte.toUnsignedInt(table[color * 2]);
    }

    private static int matchLo(byte[] table, int color) {
        return Byte.toUnsignedInt(table[color * 2 + 1]);
    }

    private static int channel(byte[] rgbaBlock, int texel, int channel) {
        return Byte.toUnsignedInt(rgbaBlock[texel * 4 + channel]);
    }

    private static int quantizeEndpoint(float value, int max) {
        return clamp((int) (value * (max / 255.0f) + 0.5f), 0, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
