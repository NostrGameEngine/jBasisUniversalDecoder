package org.ngengine.basis;

/**
 * Generic ETC1/ETC2 color block packer used by XUASTC LDR targets.
 */
final class Etc1LdrBlockPacker {
    private static final int[][] INTENSITY_TABLES = {
        {-8, -2, 2, 8},
        {-17, -5, 5, 17},
        {-29, -9, 9, 29},
        {-42, -13, 13, 42},
        {-60, -18, 18, 60},
        {-80, -24, 24, 80},
        {-106, -33, 33, 106},
        {-183, -47, 47, 183}
    };
    private static final int[] SELECTOR_INDEX_TO_ETC1 = {3, 2, 0, 1};
    private static final int[] VI = {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1};
    private static final int[] HI = {2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3};
    private static final int[][] SUBSETS = {
        {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1}
    };
    private static final int[][] SEL_BITMASKS = {
        {0x00FF, 0x00FF}, {0x0000, 0x00FF}, {0x0000, 0x0000}, {0x00FF, 0x0000},
        {0xFF00, 0xFF00}, {0x0000, 0xFF00}, {0x0000, 0x0000}, {0xFF00, 0x0000},
        {0x3333, 0x3333}, {0x0000, 0x3333}, {0x0000, 0x0000}, {0x3333, 0x0000},
        {0xCCCC, 0xCCCC}, {0x0000, 0xCCCC}, {0x0000, 0x0000}, {0xCCCC, 0x0000}
    };
    private static final int[] TRAN = {1, 0, 2, 3};
    private static final String MOD_TABS =
            "0000000000000000000000000000000000000011000000010000000000000000"
                    + "0000000000000000000000000000000000000001000000110000011100000111"
                    + "0000111100001111000011110000111100011111000111110001111100011112"
                    + "0001112200011122000112220011122200111222001112220011122200112222"
                    + "0011222200112222001122220011222200112223001122330012223300122233"
                    + "0012223301122333011223330112233301122333011223330112233301122333"
                    + "0112333301123333011233330122333301223334012233340122334401223344"
                    + "0122334401223344012233440122344401233444012334440123344401233444"
                    + "0123344401233444012334440123344401233444012334440123444401234445"
                    + "0123444501234445012344550123445501234455012344550223445502234455"
                    + "0223445502334555023345550233455502334555123345551233455512344555"
                    + "1234455512344555123445551234455512344555123445551234455512344556"
                    + "1234555612345556123455561234556612345566123455661234556612345566"
                    + "1234556612345566123455661234556612345666123456661234566612345666"
                    + "1234566612345666123456661234566612445666124456661244566612445666"
                    + "1245566612455666134556661345566613455666134556661345566613455666"
                    + "1345666613456666134566661345666613456666134566661345666613456666"
                    + "1345666613456666134566661345666613456666134566661345666613456666"
                    + "1345666613456666134566661345666713456667134566671345666713456667"
                    + "1345666713456667134566771345667713456677134566771345667713466677"
                    + "1356667713566677135666772356667723566677235666772356667723566777"
                    + "2356677723566777235667772356677723566777235667772356677724566777"
                    + "2456677724566777245667772456677724566777245667772456677724566777"
                    + "2456677724566777245667772456677724566777245667772456677724566777"
                    + "2456677724566777245667772456677724566777245677772456777724567777"
                    + "2456777724567777245677772456777724567777245677772456777724567777"
                    + "2456777724567777245677772456777724667777246677772466777724667777"
                    + "2466777724667777246677772466777724667777246677772466777724667777"
                    + "2466777724667777246677772466777724667777246677772466777724667777"
                    + "2466777724667777246677772466777724667777246677772466777724667777"
                    + "2466777724667777256677772566777725667777256677772566777725677777"
                    + "2567777725677777256777772567777725677777256777772567777725677777";

    private static final int[] NEAREST5 = new int[256];
    private static final int[] NEAREST4 = new int[256];
    private static final int[][][] SOLID8_5_BASE = new int[256][4][4];
    private static final int[][][] SOLID8_5_ERR = new int[256][4][4];
    private static final int[][][] SOLID8_4_BASE = new int[256][4][4];
    private static final int[][][] SOLID8_4_ERR = new int[256][4][4];
    private static final byte[][] SOLID_GRAYSCALE_BLOCKS = new byte[256][8];

    static {
        initTables();
    }

    private Etc1LdrBlockPacker() {
    }

    static byte[] packRgba(byte[] rgba, int width, int height, BasisTranscodeTarget target) {
        if (rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new BasisDecodeException("RGBA data size does not match image dimensions");
        }
        int blocksX = divideRoundUp(width, 4);
        int blocksY = divideRoundUp(height, 4);
        int bytesPerBlock = target == BasisTranscodeTarget.ETC2 ? 16 : 8;
        byte[] output = new byte[Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), bytesPerBlock)];
        byte[] rgbaBlock = new byte[64];
        State state = new State();
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                int outputOffset = (blockY * blocksX + blockX) * bytesPerBlock;
                extractBlock(rgba, width, height, blockX, blockY, rgbaBlock);
                if (target == BasisTranscodeTarget.ETC2) {
                    EacLdrBlockPacker.packRgbaBlockChannel(output, outputOffset, rgbaBlock, 3);
                    packRgbaBlock(output, outputOffset + 8, rgbaBlock, state);
                } else {
                    packRgbaBlock(output, outputOffset, rgbaBlock, state);
                }
            }
        }
        return output;
    }

    static void packRgbaBlock(byte[] output, int offset, byte[] rgbaBlock) {
        packRgbaBlock(output, offset, rgbaBlock, new State());
    }

    private static void packRgbaBlock(byte[] output, int offset, byte[] rgbaBlock, State state) {
        if (rgbaBlock.length != 64) {
            throw new BasisDecodeException("RGBA block data size mismatch");
        }
        if (isSolidRgb(rgbaBlock)) {
            packSolidBlock(output, offset, rgb(rgbaBlock, 0, 0), rgb(rgbaBlock, 0, 1), rgb(rgbaBlock, 0, 2),
                    state, false);
            return;
        }

        int[] accumY = new int[4];
        int[] accumY2 = new int[4];
        int[] accumC2 = new int[4];
        int totalC2 = 0;
        int maxC2 = 0;
        for (int i = 0; i < 16; i++) {
            int r = rgb(rgbaBlock, i, 0);
            int g = rgb(rgbaBlock, i, 1);
            int b = rgb(rgbaBlock, i, 2);
            int rg = r - g;
            int bg = b - g;
            int y = (r + g + b + 1) / 3;
            int c2 = rg * rg + bg * bg;
            totalC2 += c2;
            maxC2 = Math.max(maxC2, c2);
            accumY[VI[i]] += y;
            accumY2[VI[i]] += y * y;
            accumC2[VI[i]] += c2;
            accumY[HI[i]] += y;
            accumY2[HI[i]] += y * y;
            accumC2[HI[i]] += c2;
        }

        if (totalC2 < 300 && maxC2 < 32) {
            byte[] gray = new byte[16];
            for (int i = 0; i < 16; i++) {
                gray[i] = (byte) (totalC2 == 0 ? rgb(rgbaBlock, i, 0) : luma709(rgbaBlock, i));
            }
            packGrayscaleBlock(output, offset, gray, state);
            return;
        }

        int[] varYScaled = new int[4];
        float[] stdLuma = new float[4];
        float[] stdChroma = new float[4];
        for (int i = 0; i < 4; i++) {
            varYScaled[i] = Math.max(0, (accumY2[i] << 3) - accumY[i] * accumY[i]);
            stdLuma[i] = (float) Math.sqrt(varYScaled[i] * (1.0f / 64.0f));
            stdChroma[i] = (float) Math.sqrt(accumC2[i] * (1.0f / 8.0f));
        }

        float flip0Score = (stdLuma[0] + stdLuma[1]) * 2.0f + (stdChroma[0] + stdChroma[1]);
        float flip1Score = (stdLuma[2] + stdLuma[3]) * 2.0f + (stdChroma[2] + stdChroma[3]);
        int flip = flip1Score < flip0Score ? 1 : 0;

        int[] var8Y = new int[2];
        int[] mean8Y = new int[2];
        int[] mean8R = new int[2];
        int[] mean8G = new int[2];
        int[] mean8B = new int[2];
        int[] minY = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] maxY = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < 16; i++) {
            int r = rgb(rgbaBlock, i, 0);
            int g = rgb(rgbaBlock, i, 1);
            int b = rgb(rgbaBlock, i, 2);
            int y = (r + g + b + 1) / 3;
            int subset = SUBSETS[flip][i];
            var8Y[subset] += y * y;
            mean8Y[subset] += y;
            mean8R[subset] += r;
            mean8G[subset] += g;
            mean8B[subset] += b;
            minY[subset] = Math.min(minY[subset], y);
            maxY[subset] = Math.max(maxY[subset], y);
        }

        if (maxY[0] - minY[0] < 8 && maxY[1] - minY[1] < 8) {
            int[] subblockMeans = {
                ((mean8R[0] + 4) / 8 << 16) | ((mean8G[0] + 4) / 8 << 8) | (mean8B[0] + 4) / 8,
                ((mean8R[1] + 4) / 8 << 16) | ((mean8G[1] + 4) / 8 << 8) | (mean8B[1] + 4) / 8
            };
            if (subblockMeans[0] == subblockMeans[1]) {
                packSolidBlock(output, offset, (subblockMeans[0] >>> 16) & 255,
                        (subblockMeans[0] >>> 8) & 255, subblockMeans[0] & 255, state, false);
            } else {
                packSolidSubblocks(output, offset, subblockMeans, flip);
            }
            return;
        }

        finishColorBlock(output, offset, rgbaBlock, flip, var8Y, mean8Y, mean8R, mean8G, mean8B, minY, maxY);
    }

    private static void finishColorBlock(
            byte[] output,
            int offset,
            byte[] rgbaBlock,
            int flip,
            int[] var8Y,
            int[] mean8Y,
            int[] mean8R,
            int[] mean8G,
            int[] mean8B,
            int[] minY,
            int[] maxY) {
        int[] halfSpan8Y = new int[2];
        float[] stddevY = new float[2];
        for (int i = 0; i < 2; i++) {
            var8Y[i] = Math.max(0, (var8Y[i] << 3) - mean8Y[i] * mean8Y[i]);
            stddevY[i] = (float) Math.sqrt(var8Y[i]) * (1.0f / 8.0f);
            mean8Y[i] = (mean8Y[i] + 4) >> 3;
            mean8R[i] = (mean8R[i] + 4) >> 3;
            mean8G[i] = (mean8G[i] + 4) >> 3;
            mean8B[i] = (mean8B[i] + 4) >> 3;
            halfSpan8Y[i] = Math.max(maxY[i] - mean8Y[i], mean8Y[i] - minY[i]);
        }

        int[] modTab = new int[2];
        for (int i = 0; i < 2; i++) {
            int stddev = clamp((int) Math.ceil(9.0f * (stddevY[i] / Math.max(1, halfSpan8Y[i]))) - 1, 0, 7);
            modTab[i] = modTab(clamp(halfSpan8Y[i], 1, 255), stddev);
        }

        int[] mean5R = new int[2];
        int[] mean5G = new int[2];
        int[] mean5B = new int[2];
        for (int i = 0; i < 2; i++) {
            int[] rounded = corrRound555(mean8R[i], mean8G[i], mean8B[i]);
            mean5R[i] = rounded[0];
            mean5G[i] = rounded[1];
            mean5B[i] = rounded[2];
        }

        int delta5R = mean5R[1] - mean5R[0];
        int delta5G = mean5G[1] - mean5G[0];
        int delta5B = mean5B[1] - mean5B[0];
        boolean useAbsColors4 = delta5R < -4 || delta5R > 3
                || delta5G < -4 || delta5G > 3
                || delta5B < -4 || delta5B > 3;
        if (useAbsColors4) {
            int[] mean4R = new int[2];
            int[] mean4G = new int[2];
            int[] mean4B = new int[2];
            for (int i = 0; i < 2; i++) {
                int[] rounded = corrRound444(mean8R[i], mean8G[i], mean8B[i]);
                mean4R[i] = rounded[0];
                mean4G[i] = rounded[1];
                mean4B[i] = rounded[2];
            }
            output[offset] = (byte) (mean4R[1] | (mean4R[0] << 4));
            output[offset + 1] = (byte) (mean4G[1] | (mean4G[0] << 4));
            output[offset + 2] = (byte) (mean4B[1] | (mean4B[0] << 4));
            output[offset + 3] = (byte) (flip | (modTab[0] << 5) | (modTab[1] << 2));
        } else {
            output[offset] = (byte) ((delta5R & 7) | (mean5R[0] << 3));
            output[offset + 1] = (byte) ((delta5G & 7) | (mean5G[0] << 3));
            output[offset + 2] = (byte) ((delta5B & 7) | (mean5B[0] << 3));
            output[offset + 3] = (byte) (flip | 2 | (modTab[0] << 5) | (modTab[1] << 2));
        }

        int[][][] subblockColors = getBlockColors(output, offset);
        int lBitmask = 0;
        int hBitmask = 0;
        for (int subblock = 0; subblock < 2; subblock++) {
            int[] blockY = new int[4];
            for (int i = 0; i < 4; i++) {
                blockY[i] = subblockColors[subblock][i][0] * 54
                        + subblockColors[subblock][i][1] * 183
                        + subblockColors[subblock][i][2] * 19;
            }
            int y01 = blockY[0] + blockY[1];
            int y12 = blockY[1] + blockY[2];
            int y23 = blockY[2] + blockY[3];
            if (flip != 0) {
                int ofs = subblock * 2;
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 4; x++) {
                        int pixel = x + (subblock * 2 + y) * 4;
                        int l = rgb(rgbaBlock, pixel, 0) * 108
                                + rgb(rgbaBlock, pixel, 1) * 366
                                + rgb(rgbaBlock, pixel, 2) * 38;
                        int t = TRAN[(l < y01 ? 1 : 0) + (l < y12 ? 1 : 0) + (l < y23 ? 1 : 0)];
                        lBitmask |= (t & 1) << ofs;
                        hBitmask |= (t >>> 1) << ofs;
                        ofs += 4;
                    }
                    ofs = ofs + 1 - 16;
                }
            } else {
                int ofs = subblock * 8;
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 4; y++) {
                        int pixel = subblock * 2 + x + y * 4;
                        int l = rgb(rgbaBlock, pixel, 0) * 108
                                + rgb(rgbaBlock, pixel, 1) * 366
                                + rgb(rgbaBlock, pixel, 2) * 38;
                        int t = TRAN[(l < y01 ? 1 : 0) + (l < y12 ? 1 : 0) + (l < y23 ? 1 : 0)];
                        lBitmask |= (t & 1) << ofs;
                        hBitmask |= (t >>> 1) << ofs;
                        ofs++;
                    }
                }
            }
        }
        writeSelectorMasks(output, offset, lBitmask, hBitmask);
    }

    private static void packGrayscaleBlock(byte[] output, int offset, byte[] pixels, State state) {
        int first = Byte.toUnsignedInt(pixels[0]);
        if (first == Byte.toUnsignedInt(pixels[15])) {
            int k = 1;
            while (k < 15 && Byte.toUnsignedInt(pixels[k]) == first) {
                k++;
            }
            if (k == 15) {
                System.arraycopy(SOLID_GRAYSCALE_BLOCKS[first], 0, output, offset, 8);
                return;
            }
        }

        int[] accumY = new int[4];
        int[] accumY2 = new int[4];
        for (int i = 0; i < 16; i++) {
            int y = Byte.toUnsignedInt(pixels[i]);
            int y2 = y * y;
            accumY[VI[i]] += y;
            accumY2[VI[i]] += y2;
            accumY[HI[i]] += y;
            accumY2[HI[i]] += y2;
        }

        float[] stdLuma = new float[4];
        for (int i = 0; i < 4; i++) {
            int varYScaled = Math.max(0, (accumY2[i] << 3) - accumY[i] * accumY[i]);
            stdLuma[i] = (float) Math.sqrt(varYScaled * (1.0f / 64.0f));
        }
        int flip = stdLuma[2] + stdLuma[3] < stdLuma[0] + stdLuma[1] ? 1 : 0;

        int[] var8Y = new int[2];
        int[] mean8Y = new int[2];
        int[] minY = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] maxY = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < 16; i++) {
            int y = Byte.toUnsignedInt(pixels[i]);
            int subset = SUBSETS[flip][i];
            var8Y[subset] += y * y;
            mean8Y[subset] += y;
            minY[subset] = Math.min(minY[subset], y);
            maxY[subset] = Math.max(maxY[subset], y);
        }

        if (maxY[0] - minY[0] < 8 && maxY[1] - minY[1] < 8) {
            int[] subblockMeans = {(mean8Y[0] + 4) / 8, (mean8Y[1] + 4) / 8};
            if (subblockMeans[0] == subblockMeans[1]) {
                System.arraycopy(SOLID_GRAYSCALE_BLOCKS[subblockMeans[0]], 0, output, offset, 8);
            } else {
                packGrayscaleSolidSubblocks(output, offset, subblockMeans, flip);
            }
            return;
        }

        finishGrayscaleBlock(output, offset, pixels, flip, var8Y, mean8Y, minY, maxY);
    }

    private static void finishGrayscaleBlock(
            byte[] output,
            int offset,
            byte[] pixels,
            int flip,
            int[] var8Y,
            int[] mean8Y,
            int[] minY,
            int[] maxY) {
        int[] halfSpan8Y = new int[2];
        float[] stddevY = new float[2];
        for (int i = 0; i < 2; i++) {
            var8Y[i] = Math.max(0, (var8Y[i] << 3) - mean8Y[i] * mean8Y[i]);
            stddevY[i] = (float) Math.sqrt(var8Y[i]) * (1.0f / 8.0f);
            mean8Y[i] = (mean8Y[i] + 4) >> 3;
            halfSpan8Y[i] = Math.max(maxY[i] - mean8Y[i], mean8Y[i] - minY[i]);
        }

        int[] modTab = new int[2];
        for (int i = 0; i < 2; i++) {
            int stddev = clamp((int) Math.ceil(9.0f * (stddevY[i] / Math.max(1, halfSpan8Y[i]))) - 1, 0, 7);
            modTab[i] = modTab(clamp(halfSpan8Y[i], 1, 255), stddev);
        }

        int mean5Y0 = NEAREST5[mean8Y[0]];
        int mean5Y1 = NEAREST5[mean8Y[1]];
        int delta5Y = mean5Y1 - mean5Y0;
        if (delta5Y < -4 || delta5Y > 3) {
            int mean4Y0 = NEAREST4[mean8Y[0]];
            int mean4Y1 = NEAREST4[mean8Y[1]];
            output[offset] = output[offset + 1] = output[offset + 2] = (byte) (mean4Y1 | (mean4Y0 << 4));
            output[offset + 3] = (byte) (flip | (modTab[0] << 5) | (modTab[1] << 2));
        } else {
            output[offset] = output[offset + 1] = output[offset + 2] =
                    (byte) ((delta5Y & 7) | (mean5Y0 << 3));
            output[offset + 3] = (byte) (flip | 2 | (modTab[0] << 5) | (modTab[1] << 2));
        }

        int[][] subblockColors = getBlockColorsY(output, offset);
        int lBitmask = 0;
        int hBitmask = 0;
        for (int subblock = 0; subblock < 2; subblock++) {
            int y01 = subblockColors[subblock][0] + subblockColors[subblock][1];
            int y12 = subblockColors[subblock][1] + subblockColors[subblock][2];
            int y23 = subblockColors[subblock][2] + subblockColors[subblock][3];
            if (flip != 0) {
                int ofs = subblock * 2;
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 4; x++) {
                        int c = Byte.toUnsignedInt(pixels[x + (subblock * 2 + y) * 4]);
                        int t = TRAN[((c * 2) < y01 ? 1 : 0)
                                + ((c * 2) < y12 ? 1 : 0)
                                + ((c * 2) < y23 ? 1 : 0)];
                        lBitmask |= (t & 1) << ofs;
                        hBitmask |= (t >>> 1) << ofs;
                        ofs += 4;
                    }
                    ofs = ofs + 1 - 16;
                }
            } else {
                int ofs = subblock * 8;
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 4; y++) {
                        int c = Byte.toUnsignedInt(pixels[subblock * 2 + x + y * 4]);
                        int t = TRAN[((c * 2) < y01 ? 1 : 0)
                                + ((c * 2) < y12 ? 1 : 0)
                                + ((c * 2) < y23 ? 1 : 0)];
                        lBitmask |= (t & 1) << ofs;
                        hBitmask |= (t >>> 1) << ofs;
                        ofs++;
                    }
                }
            }
        }
        writeSelectorMasks(output, offset, lBitmask, hBitmask);
    }

    private static void packSolidSubblocks(byte[] output, int offset, int[] subblockMeans, int flip) {
        int[][] bestMod5 = new int[2][1];
        int[][] bestSel5 = new int[2][1];
        int[][] bestBase5 = new int[2][3];
        int[] bestErr5 = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[][] bestMod4 = new int[2][1];
        int[][] bestSel4 = new int[2][1];
        int[][] bestBase4 = new int[2][3];
        int[] bestErr4 = {Integer.MAX_VALUE, Integer.MAX_VALUE};

        for (int t = 0; t < 2; t++) {
            int r8 = (subblockMeans[t] >>> 16) & 255;
            int g8 = (subblockMeans[t] >>> 8) & 255;
            int b8 = subblockMeans[t] & 255;
            for (int mod = 0; mod < 4; mod++) {
                for (int sel = 0; sel < 4; sel++) {
                    int packedTie = (mod << 2) + sel;
                    int err5 = ((2 * SOLID8_5_ERR[r8][mod][sel]
                            + 4 * SOLID8_5_ERR[g8][mod][sel]
                            + SOLID8_5_ERR[b8][mod][sel]) << 5) + packedTie;
                    if (err5 < bestErr5[t]) {
                        bestErr5[t] = err5;
                    }
                    int err4 = ((2 * SOLID8_4_ERR[r8][mod][sel]
                            + 4 * SOLID8_4_ERR[g8][mod][sel]
                            + SOLID8_4_ERR[b8][mod][sel]) << 5) + packedTie;
                    if (err4 < bestErr4[t]) {
                        bestErr4[t] = err4;
                    }
                }
            }
            bestMod5[t][0] = (bestErr5[t] >>> 2) & 7;
            bestSel5[t][0] = bestErr5[t] & 3;
            bestErr5[t] >>>= 5;
            bestMod4[t][0] = (bestErr4[t] >>> 2) & 7;
            bestSel4[t][0] = bestErr4[t] & 3;
            bestErr4[t] >>>= 5;
            bestBase5[t][0] = SOLID8_5_BASE[r8][bestMod5[t][0]][bestSel5[t][0]];
            bestBase5[t][1] = SOLID8_5_BASE[g8][bestMod5[t][0]][bestSel5[t][0]];
            bestBase5[t][2] = SOLID8_5_BASE[b8][bestMod5[t][0]][bestSel5[t][0]];
            bestBase4[t][0] = SOLID8_4_BASE[r8][bestMod4[t][0]][bestSel4[t][0]];
            bestBase4[t][1] = SOLID8_4_BASE[g8][bestMod4[t][0]][bestSel4[t][0]];
            bestBase4[t][2] = SOLID8_4_BASE[b8][bestMod4[t][0]][bestSel4[t][0]];
        }

        int[] bestSels;
        if (bestErr4[0] + bestErr4[1] < bestErr5[0] + bestErr5[1]
                || bestBase5[1][0] - bestBase5[0][0] < -4
                || bestBase5[1][0] - bestBase5[0][0] > 3
                || bestBase5[1][1] - bestBase5[0][1] < -4
                || bestBase5[1][1] - bestBase5[0][1] > 3
                || bestBase5[1][2] - bestBase5[0][2] < -4
                || bestBase5[1][2] - bestBase5[0][2] > 3) {
            output[offset] = (byte) (bestBase4[1][0] | (bestBase4[0][0] << 4));
            output[offset + 1] = (byte) (bestBase4[1][1] | (bestBase4[0][1] << 4));
            output[offset + 2] = (byte) (bestBase4[1][2] | (bestBase4[0][2] << 4));
            output[offset + 3] = (byte) (flip | (bestMod4[0][0] << 5) | (bestMod4[1][0] << 2));
            bestSels = new int[] {bestSel4[0][0], bestSel4[1][0]};
        } else {
            output[offset] = (byte) (((bestBase5[1][0] - bestBase5[0][0]) & 7)
                    | (bestBase5[0][0] << 3));
            output[offset + 1] = (byte) (((bestBase5[1][1] - bestBase5[0][1]) & 7)
                    | (bestBase5[0][1] << 3));
            output[offset + 2] = (byte) (((bestBase5[1][2] - bestBase5[0][2]) & 7)
                    | (bestBase5[0][2] << 3));
            output[offset + 3] = (byte) (flip | 2 | (bestMod5[0][0] << 5) | (bestMod5[1][0] << 2));
            bestSels = new int[] {bestSel5[0][0], bestSel5[1][0]};
        }
        writeSolidSubblockSelectors(output, offset, bestSels, flip);
    }

    private static void packGrayscaleSolidSubblocks(
            byte[] output,
            int offset,
            int[] subblockMeans,
            int flip) {
        int[][] bestMod5 = new int[2][1];
        int[][] bestSel5 = new int[2][1];
        int[] bestBase5 = new int[2];
        int[] bestErr5 = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[][] bestMod4 = new int[2][1];
        int[][] bestSel4 = new int[2][1];
        int[] bestBase4 = new int[2];
        int[] bestErr4 = {Integer.MAX_VALUE, Integer.MAX_VALUE};

        for (int t = 0; t < 2; t++) {
            int y8 = subblockMeans[t];
            for (int mod = 0; mod < 4; mod++) {
                for (int sel = 0; sel < 4; sel++) {
                    int packedTie = (mod << 2) + sel;
                    int err5 = (SOLID8_5_ERR[y8][mod][sel] << 5) + packedTie;
                    if (err5 < bestErr5[t]) {
                        bestErr5[t] = err5;
                    }
                    int err4 = (SOLID8_4_ERR[y8][mod][sel] << 5) + packedTie;
                    if (err4 < bestErr4[t]) {
                        bestErr4[t] = err4;
                    }
                }
            }
            bestMod5[t][0] = (bestErr5[t] >>> 2) & 7;
            bestSel5[t][0] = bestErr5[t] & 3;
            bestErr5[t] >>>= 5;
            bestMod4[t][0] = (bestErr4[t] >>> 2) & 7;
            bestSel4[t][0] = bestErr4[t] & 3;
            bestErr4[t] >>>= 5;
            bestBase5[t] = SOLID8_5_BASE[y8][bestMod5[t][0]][bestSel5[t][0]];
            bestBase4[t] = SOLID8_4_BASE[y8][bestMod4[t][0]][bestSel4[t][0]];
        }

        int[] bestSels;
        if (bestErr4[0] + bestErr4[1] < bestErr5[0] + bestErr5[1]
                || bestBase5[1] - bestBase5[0] < -4
                || bestBase5[1] - bestBase5[0] > 3) {
            output[offset] = output[offset + 1] = output[offset + 2] =
                    (byte) (bestBase4[1] | (bestBase4[0] << 4));
            output[offset + 3] = (byte) (flip | (bestMod4[0][0] << 5) | (bestMod4[1][0] << 2));
            bestSels = new int[] {bestSel4[0][0], bestSel4[1][0]};
        } else {
            output[offset] = output[offset + 1] = output[offset + 2] =
                    (byte) (((bestBase5[1] - bestBase5[0]) & 7) | (bestBase5[0] << 3));
            output[offset + 3] = (byte) (flip | 2 | (bestMod5[0][0] << 5) | (bestMod5[1][0] << 2));
            bestSels = new int[] {bestSel5[0][0], bestSel5[1][0]};
        }
        writeSolidSubblockSelectors(output, offset, bestSels, flip);
    }

    private static void packSolidBlock(
            byte[] output,
            int offset,
            int r8,
            int g8,
            int b8,
            State state,
            boolean init) {
        if (!init) {
            if (r8 == g8 && r8 == b8) {
                System.arraycopy(SOLID_GRAYSCALE_BLOCKS[r8], 0, output, offset, 8);
                return;
            }
            if (state.prevR8 == r8 && state.prevG8 == g8 && state.prevB8 == b8) {
                System.arraycopy(state.prevBlock, 0, output, offset, 8);
                return;
            }
        }

        int bestErr = Integer.MAX_VALUE;
        int bestMod = 0;
        int bestSel = 0;
        boolean best4 = false;
        for (int mod = 0; mod < 4; mod++) {
            for (int sel = 0; sel < 4; sel++) {
                int totalErr5 = 2 * SOLID8_5_ERR[r8][mod][sel]
                        + 4 * SOLID8_5_ERR[g8][mod][sel]
                        + SOLID8_5_ERR[b8][mod][sel];
                if (totalErr5 < bestErr) {
                    bestErr = totalErr5;
                    bestMod = mod;
                    bestSel = sel;
                    best4 = false;
                    if (bestErr == 0) {
                        break;
                    }
                }
                int totalErr4 = 2 * SOLID8_4_ERR[r8][mod][sel]
                        + 4 * SOLID8_4_ERR[g8][mod][sel]
                        + SOLID8_4_ERR[b8][mod][sel];
                if (totalErr4 < bestErr) {
                    bestErr = totalErr4;
                    bestMod = mod;
                    bestSel = sel;
                    best4 = true;
                }
            }
            if (bestErr == 0) {
                break;
            }
        }

        if (best4) {
            int r4 = SOLID8_4_BASE[r8][bestMod][bestSel];
            int g4 = SOLID8_4_BASE[g8][bestMod][bestSel];
            int b4 = SOLID8_4_BASE[b8][bestMod][bestSel];
            output[offset] = (byte) (r4 | (r4 << 4));
            output[offset + 1] = (byte) (g4 | (g4 << 4));
            output[offset + 2] = (byte) (b4 | (b4 << 4));
            output[offset + 3] = (byte) ((bestMod << 5) | (bestMod << 2));
        } else {
            output[offset] = (byte) (SOLID8_5_BASE[r8][bestMod][bestSel] << 3);
            output[offset + 1] = (byte) (SOLID8_5_BASE[g8][bestMod][bestSel] << 3);
            output[offset + 2] = (byte) (SOLID8_5_BASE[b8][bestMod][bestSel] << 3);
            output[offset + 3] = (byte) (2 | (bestMod << 5) | (bestMod << 2));
        }

        int etc1Selector = SELECTOR_INDEX_TO_ETC1[bestSel];
        byte lowByte = (byte) ((etc1Selector & 2) != 0 ? 0xFF : 0);
        byte highByte = (byte) ((etc1Selector & 1) != 0 ? 0xFF : 0);
        output[offset + 4] = lowByte;
        output[offset + 5] = lowByte;
        output[offset + 6] = highByte;
        output[offset + 7] = highByte;

        state.prevR8 = r8;
        state.prevG8 = g8;
        state.prevB8 = b8;
        System.arraycopy(output, offset, state.prevBlock, 0, 8);
    }

    private static int[][][] getBlockColors(byte[] block, int offset) {
        int b0 = Byte.toUnsignedInt(block[offset]);
        int b1 = Byte.toUnsignedInt(block[offset + 1]);
        int b2 = Byte.toUnsignedInt(block[offset + 2]);
        int b3 = Byte.toUnsignedInt(block[offset + 3]);
        int[][] base = decodeBaseColors(b0, b1, b2, b3);
        int[][][] colors = new int[2][4][3];
        for (int subset = 0; subset < 2; subset++) {
            int table = subset == 0 ? b3 >>> 5 : (b3 >>> 2) & 7;
            for (int i = 0; i < 4; i++) {
                int modifier = INTENSITY_TABLES[table][i];
                colors[subset][i][0] = clamp255(base[subset][0] + modifier);
                colors[subset][i][1] = clamp255(base[subset][1] + modifier);
                colors[subset][i][2] = clamp255(base[subset][2] + modifier);
            }
        }
        return colors;
    }

    private static int[][] getBlockColorsY(byte[] block, int offset) {
        int b0 = Byte.toUnsignedInt(block[offset]);
        int b3 = Byte.toUnsignedInt(block[offset + 3]);
        int[] baseY = new int[2];
        if ((b3 & 2) != 0) {
            baseY[0] = expand5(b0 >>> 3);
            baseY[1] = expand5(clamp((b0 >>> 3) + dequantD3(b0 & 7), 0, 31));
        } else {
            baseY[0] = expand4(b0 >>> 4);
            baseY[1] = expand4(b0 & 15);
        }
        int[][] colors = new int[2][4];
        for (int subset = 0; subset < 2; subset++) {
            int table = subset == 0 ? b3 >>> 5 : (b3 >>> 2) & 7;
            for (int i = 0; i < 4; i++) {
                colors[subset][i] = clamp255(baseY[subset] + INTENSITY_TABLES[table][i]);
            }
        }
        return colors;
    }

    private static int[][] decodeBaseColors(int b0, int b1, int b2, int b3) {
        int[][] base = new int[2][3];
        if ((b3 & 2) != 0) {
            base[0][0] = expand5(b0 >>> 3);
            base[1][0] = expand5(clamp((b0 >>> 3) + dequantD3(b0 & 7), 0, 31));
            base[0][1] = expand5(b1 >>> 3);
            base[1][1] = expand5(clamp((b1 >>> 3) + dequantD3(b1 & 7), 0, 31));
            base[0][2] = expand5(b2 >>> 3);
            base[1][2] = expand5(clamp((b2 >>> 3) + dequantD3(b2 & 7), 0, 31));
        } else {
            base[0][0] = expand4(b0 >>> 4);
            base[1][0] = expand4(b0 & 15);
            base[0][1] = expand4(b1 >>> 4);
            base[1][1] = expand4(b1 & 15);
            base[0][2] = expand4(b2 >>> 4);
            base[1][2] = expand4(b2 & 15);
        }
        return base;
    }

    private static void writeSolidSubblockSelectors(byte[] output, int offset, int[] bestSels, int flip) {
        int lBitmask = 0;
        int hBitmask = 0;
        for (int subblock = 0; subblock < 2; subblock++) {
            int index = flip * 8 + subblock * 4 + bestSels[subblock];
            lBitmask |= SEL_BITMASKS[index][0];
            hBitmask |= SEL_BITMASKS[index][1];
        }
        writeSelectorMasks(output, offset, lBitmask, hBitmask);
    }

    private static void writeSelectorMasks(byte[] output, int offset, int lowBitmask, int highBitmask) {
        output[offset + 7] = (byte) lowBitmask;
        output[offset + 6] = (byte) (lowBitmask >>> 8);
        output[offset + 5] = (byte) highBitmask;
        output[offset + 4] = (byte) (highBitmask >>> 8);
    }

    private static int[] corrRound555(int red, int green, int blue) {
        return corrRound(red, green, blue, 31, 5);
    }

    private static int[] corrRound444(int red, int green, int blue) {
        return corrRound(red, green, blue, 15, 4);
    }

    private static int[] corrRound(int red, int green, int blue, int max, int bits) {
        int rLow = red * max / 255;
        int gLow = green * max / 255;
        int bLow = blue * max / 255;
        int rHigh = rLow < max ? rLow + 1 : max;
        int gHigh = gLow < max ? gLow + 1 : max;
        int bHigh = bLow < max ? bLow + 1 : max;
        int[] r8 = {expand(rLow, bits), expand(rHigh, bits)};
        int[] g8 = {expand(gLow, bits), expand(gHigh, bits)};
        int[] b8 = {expand(bLow, bits), expand(bHigh, bits)};
        int bestJ = correlatedError(red, green, blue, r8[0], g8[0], b8[0]);
        int br = r8[0];
        int bg = g8[0];
        int bb = b8[0];
        for (int mask = 1; mask < 8; mask++) {
            int tr = r8[mask & 1];
            int tg = g8[(mask >>> 1) & 1];
            int tb = b8[(mask >>> 2) & 1];
            int error = correlatedError(red, green, blue, tr, tg, tb);
            if (error < bestJ) {
                bestJ = error;
                br = tr;
                bg = tg;
                bb = tb;
            }
        }
        return new int[] {br >>> (8 - bits), bg >>> (8 - bits), bb >>> (8 - bits)};
    }

    private static int correlatedError(int red, int green, int blue, int tr, int tg, int tb) {
        int er = red - tr;
        int eg = green - tg;
        int eb = blue - tb;
        return square(er - eg) + square(eg - eb) + square(eb - er);
    }

    private static void initTables() {
        for (int i = 0; i < 256; i++) {
            int bestErr = Integer.MAX_VALUE;
            int bestIndex = 0;
            for (int s = 0; s < 32; s++) {
                int err = Math.abs(expand5(s) - i);
                if (err < bestErr) {
                    bestErr = err;
                    bestIndex = s;
                }
            }
            NEAREST5[i] = bestIndex;
        }

        for (int i = 0; i < 256; i++) {
            int bestErr = Integer.MAX_VALUE;
            int bestIndex = 0;
            for (int s = 0; s < 16; s++) {
                int err = Math.abs(expand4(s) - i);
                if (err < bestErr) {
                    bestErr = err;
                    bestIndex = s;
                }
            }
            NEAREST4[i] = bestIndex;
        }

        for (int desired8 = 0; desired8 < 256; desired8++) {
            for (int mod = 0; mod < 4; mod++) {
                for (int sel = 0; sel < 4; sel++) {
                    fillSolidTable(desired8, mod, sel, 32, true);
                    fillSolidTable(desired8, mod, sel, 16, false);
                }
            }
        }

        State state = new State();
        for (int i = 0; i < 256; i++) {
            packSolidBlock(SOLID_GRAYSCALE_BLOCKS[i], 0, i, i, i, state, true);
        }
    }

    private static void fillSolidTable(int desired8, int mod, int sel, int baseCount, boolean fiveBit) {
        int bestErr = Integer.MAX_VALUE;
        int bestBase = 0;
        for (int base = 0; base < baseCount; base++) {
            int value = clamp255((fiveBit ? expand5(base) : expand4(base)) + INTENSITY_TABLES[mod][sel]);
            int err = Math.abs(value - desired8);
            if (err < bestErr) {
                bestErr = err;
                bestBase = base;
                if (bestErr == 0) {
                    break;
                }
            }
        }
        if (fiveBit) {
            SOLID8_5_BASE[desired8][mod][sel] = bestBase;
            SOLID8_5_ERR[desired8][mod][sel] = Math.min(255, bestErr * bestErr);
        } else {
            SOLID8_4_BASE[desired8][mod][sel] = bestBase;
            SOLID8_4_ERR[desired8][mod][sel] = Math.min(255, bestErr * bestErr);
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
                block[dst] = rgba[src];
                block[dst + 1] = rgba[src + 1];
                block[dst + 2] = rgba[src + 2];
                block[dst + 3] = rgba[src + 3];
            }
        }
    }

    private static boolean isSolidRgb(byte[] rgbaBlock) {
        int firstR = rgb(rgbaBlock, 0, 0);
        int firstG = rgb(rgbaBlock, 0, 1);
        int firstB = rgb(rgbaBlock, 0, 2);
        if (firstR != rgb(rgbaBlock, 15, 0)
                || firstG != rgb(rgbaBlock, 15, 1)
                || firstB != rgb(rgbaBlock, 15, 2)) {
            return false;
        }
        int k = 1;
        while (k < 15
                && firstR == rgb(rgbaBlock, k, 0)
                && firstG == rgb(rgbaBlock, k, 1)
                && firstB == rgb(rgbaBlock, k, 2)) {
            k++;
        }
        return k == 15;
    }

    private static int rgb(byte[] rgbaBlock, int pixel, int channel) {
        return Byte.toUnsignedInt(rgbaBlock[pixel * 4 + channel]);
    }

    private static int luma709(byte[] rgbaBlock, int pixel) {
        return (13938 * rgb(rgbaBlock, pixel, 0)
                + 46869 * rgb(rgbaBlock, pixel, 1)
                + 4729 * rgb(rgbaBlock, pixel, 2)
                + 32768) >>> 16;
    }

    private static int modTab(int halfSpan, int stddev) {
        return MOD_TABS.charAt(halfSpan * 8 + stddev) - '0';
    }

    private static int dequantD3(int value) {
        return value >= 4 ? value - 8 : value;
    }

    private static int expand4(int value) {
        return (value << 4) | value;
    }

    private static int expand5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int expand(int value, int bits) {
        return bits == 5 ? expand5(value) : expand4(value);
    }

    private static int square(int value) {
        return value * value;
    }

    private static int clamp255(int value) {
        return clamp(value, 0, 255);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static final class State {
        private final byte[] prevBlock = new byte[8];
        private int prevR8 = -1;
        private int prevG8 = -1;
        private int prevB8 = -1;
    }
}
