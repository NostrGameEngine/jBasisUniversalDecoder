package org.ngengine.basis;

/**
 * Huffman tables decoded from ETC1S BasisLZ table data.
 */
final class Etc1sCodebookTables {
    private final HuffmanDecodingTable endpointPredictionTable;
    private final HuffmanDecodingTable endpointDeltaTable;
    private final HuffmanDecodingTable selectorTable;
    private final HuffmanDecodingTable selectorHistoryRunLengthTable;
    private final int selectorHistoryBufferSize;

    private Etc1sCodebookTables(
            HuffmanDecodingTable endpointPredictionTable,
            HuffmanDecodingTable endpointDeltaTable,
            HuffmanDecodingTable selectorTable,
            HuffmanDecodingTable selectorHistoryRunLengthTable,
            int selectorHistoryBufferSize) {
        this.endpointPredictionTable = endpointPredictionTable;
        this.endpointDeltaTable = endpointDeltaTable;
        this.selectorTable = selectorTable;
        this.selectorHistoryRunLengthTable = selectorHistoryRunLengthTable;
        this.selectorHistoryBufferSize = selectorHistoryBufferSize;
    }

    static Etc1sCodebookTables decode(byte[] data, int offset, int length) {
        BasisBitReader reader = new BasisBitReader(data, offset, length);
        HuffmanDecodingTable endpointPredictionTable = readRequiredTable(reader, "endpoint prediction");
        HuffmanDecodingTable endpointDeltaTable = readRequiredTable(reader, "endpoint delta");
        HuffmanDecodingTable selectorTable = readRequiredTable(reader, "selector");
        HuffmanDecodingTable selectorHistoryRunLengthTable =
                readRequiredTable(reader, "selector history run-length");
        int selectorHistoryBufferSize = reader.getBits(13);
        if (selectorHistoryBufferSize == 0) {
            throw new BasisDecodeException("ETC1S selector history buffer size is zero");
        }
        return new Etc1sCodebookTables(
                endpointPredictionTable,
                endpointDeltaTable,
                selectorTable,
                selectorHistoryRunLengthTable,
                selectorHistoryBufferSize);
    }

    int getEndpointPredictionSymbolCount() {
        return endpointPredictionTable.getSymbolCount();
    }

    int getEndpointDeltaSymbolCount() {
        return endpointDeltaTable.getSymbolCount();
    }

    int getSelectorSymbolCount() {
        return selectorTable.getSymbolCount();
    }

    int getSelectorHistoryRunLengthSymbolCount() {
        return selectorHistoryRunLengthTable.getSymbolCount();
    }

    int getSelectorHistoryBufferSize() {
        return selectorHistoryBufferSize;
    }

    HuffmanDecodingTable getEndpointPredictionTable() {
        return endpointPredictionTable;
    }

    HuffmanDecodingTable getEndpointDeltaTable() {
        return endpointDeltaTable;
    }

    HuffmanDecodingTable getSelectorTable() {
        return selectorTable;
    }

    HuffmanDecodingTable getSelectorHistoryRunLengthTable() {
        return selectorHistoryRunLengthTable;
    }

    private static HuffmanDecodingTable readRequiredTable(BasisBitReader reader, String name) {
        HuffmanDecodingTable table = new HuffmanDecodingTable();
        if (!reader.readHuffmanTable(table) || !table.isValid()) {
            throw new BasisDecodeException("Invalid ETC1S " + name + " Huffman table");
        }
        return table;
    }
}
