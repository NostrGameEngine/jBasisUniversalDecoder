package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TestBasisBitReader {

    @Test
    public void readsBitsLeastSignificantBitFirst() {
        BasisBitReader reader = new BasisBitReader(new byte[] {(byte) 0b1011_0010, 0b0000_0011}, 0, 2);

        assertEquals(0, reader.getBits(1));
        assertEquals(1, reader.getBits(1));
        assertEquals(0b1100, reader.getBits(4));
        assertEquals(0b1110, reader.getBits(4));
    }

    @Test
    public void decodesCanonicalHuffmanSymbols() {
        HuffmanDecodingTable table = new HuffmanDecodingTable();
        assertTrue(table.init(2, new byte[] {1, 1}));
        BasisBitReader reader = new BasisBitReader(new byte[] {0b0000_0010}, 0, 1);

        assertEquals(0, reader.decodeHuffman(table));
        assertEquals(1, reader.decodeHuffman(table));
    }

    @Test
    public void rejectsInvalidHuffmanCodeSizes() {
        HuffmanDecodingTable table = new HuffmanDecodingTable();

        assertFalse(table.init(1, new byte[] {32}));
    }

    @Test
    public void decodesVariableLengthHelpers() {
        BasisBitReader truncated = new BasisBitReader(new byte[] {0b0000_0110}, 0, 1);
        assertEquals(2, truncated.decodeTruncatedBinary(5));

        BasisBitReader vlc = new BasisBitReader(new byte[] {0b0000_1111}, 0, 1);
        assertEquals(7, vlc.decodeVariableLengthCode(2));
    }
}
