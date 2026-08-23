package org.ngengine.basis;

import java.util.Arrays;

/**
 * Decoded ETC1S endpoint and selector palettes.
 */
final class Etc1sPalettes {
    private static final int COLOR5_PAL0_PREV_HI = 9;
    private static final int COLOR5_PAL1_PREV_HI = 21;

    private final Etc1sEndpoint[] endpoints;
    private final Etc1sSelector[] selectors;

    private Etc1sPalettes(Etc1sEndpoint[] endpoints, Etc1sSelector[] selectors) {
        this.endpoints = Arrays.copyOf(endpoints, endpoints.length);
        this.selectors = Arrays.copyOf(selectors, selectors.length);
    }

    static Etc1sPalettes decode(
            int endpointCount,
            byte[] endpointData,
            int endpointOffset,
            int endpointLength,
            int selectorCount,
            byte[] selectorData,
            int selectorOffset,
            int selectorLength) {
        Etc1sEndpoint[] endpoints = decodeEndpoints(
                endpointCount,
                endpointData,
                endpointOffset,
                endpointLength);
        Etc1sSelector[] selectors = decodeSelectors(
                selectorCount,
                selectorData,
                selectorOffset,
                selectorLength);
        return new Etc1sPalettes(endpoints, selectors);
    }

    int getEndpointCount() {
        return endpoints.length;
    }

    int getSelectorCount() {
        return selectors.length;
    }

    Etc1sEndpoint getEndpoint(int index) {
        return endpoints[index];
    }

    Etc1sSelector getSelector(int index) {
        return selectors[index];
    }

    private static Etc1sEndpoint[] decodeEndpoints(
            int endpointCount,
            byte[] data,
            int offset,
            int length) {
        BasisBitReader reader = new BasisBitReader(data, offset, length);
        HuffmanDecodingTable colorDelta0 = readOptionalTable(reader, "color delta 0");
        HuffmanDecodingTable colorDelta1 = readOptionalTable(reader, "color delta 1");
        HuffmanDecodingTable colorDelta2 = readOptionalTable(reader, "color delta 2");
        HuffmanDecodingTable intensityDelta = readOptionalTable(reader, "intensity delta");
        if (!colorDelta0.isValid() || !colorDelta1.isValid() || !colorDelta2.isValid()
                || !intensityDelta.isValid()) {
            throw new BasisDecodeException("ETC1S endpoint palette has invalid Huffman tables");
        }

        boolean grayscale = reader.getBits(1) != 0;
        Etc1sEndpoint[] endpoints = new Etc1sEndpoint[endpointCount];
        int[] previousColor = {16, 16, 16};
        int previousIntensity = 0;

        for (int i = 0; i < endpointCount; i++) {
            int intensity = (reader.decodeHuffman(intensityDelta) + previousIntensity) & 7;
            previousIntensity = intensity;

            int componentCount = grayscale ? 1 : 3;
            for (int c = 0; c < componentCount; c++) {
                int delta = reader.decodeHuffman(selectColorDeltaTable(previousColor[c],
                        colorDelta0,
                        colorDelta1,
                        colorDelta2));
                previousColor[c] = (previousColor[c] + delta) & 31;
            }
            if (grayscale) {
                previousColor[1] = previousColor[0];
                previousColor[2] = previousColor[0];
            }
            endpoints[i] = new Etc1sEndpoint(
                    previousColor[0],
                    previousColor[1],
                    previousColor[2],
                    intensity);
        }
        return endpoints;
    }

    private static Etc1sSelector[] decodeSelectors(
            int selectorCount,
            byte[] data,
            int offset,
            int length) {
        BasisBitReader reader = new BasisBitReader(data, offset, length);
        if (reader.getBits(1) != 0) {
            throw new BasisDecodeException("ETC1S global selector codebooks are not supported");
        }
        if (reader.getBits(1) != 0) {
            throw new BasisDecodeException("ETC1S hybrid selector codebooks are not supported");
        }

        Etc1sSelector[] selectors = new Etc1sSelector[selectorCount];
        boolean rawEncoding = reader.getBits(1) != 0;
        if (rawEncoding) {
            for (int i = 0; i < selectorCount; i++) {
                selectors[i] = readRawSelector(reader);
            }
        } else {
            HuffmanDecodingTable selectorDelta = new HuffmanDecodingTable();
            if (!reader.readHuffmanTable(selectorDelta) || (selectorCount > 1 && !selectorDelta.isValid())) {
                throw new BasisDecodeException("ETC1S selector palette has invalid delta Huffman table");
            }
            readDeltaSelectors(reader, selectorDelta, selectors);
        }
        return selectors;
    }

    private static void readDeltaSelectors(
            BasisBitReader reader,
            HuffmanDecodingTable selectorDelta,
            Etc1sSelector[] selectors) {
        int[] previousBytes = new int[4];
        for (int i = 0; i < selectors.length; i++) {
            Etc1sSelector selector = new Etc1sSelector();
            for (int row = 0; row < 4; row++) {
                int currentByte;
                if (i == 0) {
                    currentByte = reader.getBits(8);
                } else {
                    currentByte = reader.decodeHuffman(selectorDelta) ^ previousBytes[row];
                }
                previousBytes[row] = currentByte & 0xFF;
                setSelectorRow(selector, row, currentByte);
            }
            selector.initFlags();
            selectors[i] = selector;
        }
    }

    private static Etc1sSelector readRawSelector(BasisBitReader reader) {
        Etc1sSelector selector = new Etc1sSelector();
        for (int row = 0; row < 4; row++) {
            setSelectorRow(selector, row, reader.getBits(8));
        }
        selector.initFlags();
        return selector;
    }

    private static void setSelectorRow(Etc1sSelector selector, int row, int packedSelectors) {
        for (int x = 0; x < 4; x++) {
            selector.setSelector(x, row, (packedSelectors >>> (x * 2)) & 3);
        }
    }

    private static HuffmanDecodingTable selectColorDeltaTable(
            int previousValue,
            HuffmanDecodingTable colorDelta0,
            HuffmanDecodingTable colorDelta1,
            HuffmanDecodingTable colorDelta2) {
        if (previousValue <= COLOR5_PAL0_PREV_HI) {
            return colorDelta0;
        }
        if (previousValue <= COLOR5_PAL1_PREV_HI) {
            return colorDelta1;
        }
        return colorDelta2;
    }

    private static HuffmanDecodingTable readOptionalTable(BasisBitReader reader, String name) {
        HuffmanDecodingTable table = new HuffmanDecodingTable();
        if (!reader.readHuffmanTable(table)) {
            throw new BasisDecodeException("Invalid ETC1S " + name + " Huffman table");
        }
        return table;
    }
}
