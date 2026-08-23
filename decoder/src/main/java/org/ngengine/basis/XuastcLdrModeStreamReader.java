package org.ngengine.basis;

/**
 * Incremental XUASTC LDR arithmetic mode stream reader.
 */
final class XuastcLdrModeStreamReader {
    private static final int MODE_COUNT = 6;

    private XuastcLdrModeStreamReader() {
    }

    static XuastcLdrModeStreamSummary summarize(byte[] data, int offset, int length) {
        XuastcLdrPayload payload = XuastcLdrPayload.parse(data, offset, length);
        if (payload.getSyntax() != XuastcLdrSyntax.FULL_ARITH) {
            throw new BasisDecodeException("XUASTC mode stream reader currently requires FULL_ARITH");
        }

        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        XuastcArithmeticDecoder decoder = new XuastcArithmeticDecoder(data, offset + 1, length - 1);
        consumeImageHeader(decoder);

        int blocksX = divideRoundUp(header.getWidth(), header.getBlockWidth());
        int blocksY = divideRoundUp(header.getHeight(), header.getBlockHeight());
        int totalBlocks = Math.multiplyExact(blocksX, blocksY);
        int[] explicitModeCounts = new int[MODE_COUNT];

        XuastcArithmeticDataModel modeModel = new XuastcArithmeticDataModel(MODE_COUNT);
        XuastcArithmeticDataModel[] solidColorModels = new XuastcArithmeticDataModel[] {
                new XuastcArithmeticDataModel(256, true),
                new XuastcArithmeticDataModel(256, true),
                new XuastcArithmeticDataModel(256, true),
                new XuastcArithmeticDataModel(256, true)
        };
        XuastcArithmeticGammaContext runLengthContext = new XuastcArithmeticGammaContext();

        int decodedBlocks = 0;
        int solidBlocks = 0;
        int runCopiedBlocks = 0;
        int currentRunLength = 0;
        XuastcLdrMode unsupportedMode = null;
        int unsupportedX = -1;
        int unsupportedY = -1;

        for (int by = 0; by < blocksY && unsupportedMode == null; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                if (currentRunLength > 0) {
                    decodedBlocks++;
                    runCopiedBlocks++;
                    currentRunLength--;
                    continue;
                }

                XuastcLdrMode mode = XuastcLdrMode.fromCode(decoder.decodeSymbol(modeModel));
                explicitModeCounts[mode.getCode()]++;
                if (mode == XuastcLdrMode.SOLID) {
                    decoder.decodeSymbol(solidColorModels[0]);
                    decoder.decodeSymbol(solidColorModels[1]);
                    decoder.decodeSymbol(solidColorModels[2]);
                    if (header.hasAlpha()) {
                        decoder.decodeSymbol(solidColorModels[3]);
                    }
                    decodedBlocks++;
                    solidBlocks++;
                } else if (mode == XuastcLdrMode.RUN) {
                    if (bx == 0 && by == 0) {
                        throw new BasisDecodeException("XUASTC run mode cannot appear at the first block");
                    }
                    currentRunLength = decoder.decodeGamma(runLengthContext);
                    if (currentRunLength <= 0 || currentRunLength > blocksX - bx) {
                        throw new BasisDecodeException("Invalid XUASTC run length");
                    }
                    decodedBlocks++;
                    runCopiedBlocks++;
                    currentRunLength--;
                } else {
                    unsupportedMode = mode;
                    unsupportedX = bx;
                    unsupportedY = by;
                    break;
                }
            }
        }

        return new XuastcLdrModeStreamSummary(
                header,
                totalBlocks,
                decodedBlocks,
                solidBlocks,
                runCopiedBlocks,
                explicitModeCounts,
                unsupportedMode,
                unsupportedX,
                unsupportedY);
    }

    private static void consumeImageHeader(XuastcArithmeticDecoder decoder) {
        int marker = decoder.getBits(5);
        if (marker != 1) {
            throw new BasisDecodeException("XUASTC image header marker mismatch");
        }
        decoder.getBits(4);
        decoder.getBit();
        decoder.getBits(16);
        decoder.getBits(16);
        decoder.getBit();
        boolean usesDct = decoder.getBits(1) != 0;
        if (usesDct) {
            decoder.getBits(8);
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
