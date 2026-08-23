package org.ngengine.basis;

/**
 * LSB-first bit reader used by BasisLZ and UASTC payloads.
 */
final class BasisBitReader {
    private final byte[] data;
    private final int end;
    private int position;
    private int bitBuffer;
    private int bitBufferSize;

    BasisBitReader(byte[] data, int offset, int length) {
        if (data == null) {
            throw new BasisDecodeException("Bitstream data must not be null");
        }
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new BasisDecodeException("Bitstream range extends beyond input buffer");
        }
        this.data = data;
        this.position = offset;
        this.end = offset + length;
    }

    int peekBits(int bitCount) {
        if (bitCount < 0 || bitCount > 25) {
            throw new BasisDecodeException("Can only peek 0..25 bits");
        }
        if (bitCount == 0) {
            return 0;
        }
        fill(bitCount);
        return bitBuffer & ((1 << bitCount) - 1);
    }

    void removeBits(int bitCount) {
        if (bitCount < 0 || bitCount > bitBufferSize) {
            throw new BasisDecodeException("Cannot remove more bits than currently buffered");
        }
        bitBuffer >>>= bitCount;
        bitBufferSize -= bitCount;
    }

    int getBits(int bitCount) {
        if (bitCount < 0 || bitCount > 32) {
            throw new BasisDecodeException("Can only read 0..32 bits");
        }
        if (bitCount > 25) {
            int low = peekBits(25);
            removeBits(25);
            int highBits = bitCount - 25;
            int high = peekBits(highBits);
            removeBits(highBits);
            return low | (high << 25);
        }
        int bits = peekBits(bitCount);
        removeBits(bitCount);
        return bits;
    }

    int decodeTruncatedBinary(int n) {
        if (n < 2) {
            throw new BasisDecodeException("Truncated binary range must be >= 2");
        }
        int k = floorLog2(n);
        int u = (1 << (k + 1)) - n;
        int result = getBits(k);
        if (result >= u) {
            result = ((result << 1) | getBits(1)) - u;
        }
        return result;
    }

    int decodeRice(int m) {
        if (m <= 0) {
            throw new BasisDecodeException("Rice parameter must be positive");
        }
        int q = 0;
        while (true) {
            int value = peekBits(16);
            int run = 0;
            while ((value & 1) != 0) {
                run++;
                value >>>= 1;
            }
            q += run;
            removeBits(run);
            if (run < 16) {
                break;
            }
        }
        return (q << m) + (getBits(m + 1) >>> 1);
    }

    int decodeVariableLengthCode(int chunkBits) {
        if (chunkBits <= 0 || chunkBits >= 32) {
            throw new BasisDecodeException("Invalid variable-length code chunk size");
        }
        int chunkSize = 1 << chunkBits;
        int chunkMask = chunkSize - 1;
        int value = 0;
        int offset = 0;
        while (true) {
            int bits = getBits(chunkBits + 1);
            value |= (bits & chunkMask) << offset;
            offset += chunkBits;
            if ((bits & chunkSize) == 0) {
                return value;
            }
            if (offset >= 32) {
                throw new BasisDecodeException("Variable-length code overflow");
            }
        }
    }

    int decodeHuffman(HuffmanDecodingTable table) {
        if (table == null || !table.isValid()) {
            throw new BasisDecodeException("Huffman table is not initialized");
        }
        fill(16);
        int[] lookup = table.getLookup();
        int symbol = lookup[bitBuffer & ((1 << HuffmanDecodingTable.FAST_LOOKUP_BITS) - 1)];
        int codeLength;
        if (symbol >= 0) {
            codeLength = symbol >>> 16;
            symbol &= 0xFFFF;
        } else {
            codeLength = HuffmanDecodingTable.FAST_LOOKUP_BITS;
            short[] tree = table.getTree();
            do {
                int treeIndex = ~symbol + ((bitBuffer >>> codeLength) & 1);
                if (treeIndex < 0 || treeIndex >= tree.length) {
                    throw new BasisDecodeException("Huffman tree lookup exceeded table bounds");
                }
                symbol = tree[treeIndex];
                codeLength++;
            } while (symbol < 0);
        }
        bitBuffer >>>= codeLength;
        bitBufferSize -= codeLength;
        return symbol;
    }

    boolean readHuffmanTable(HuffmanDecodingTable table) {
        table.clear();
        int totalUsedSymbols = getBits(HuffmanDecodingTable.MAX_SYMS_LOG2);
        if (totalUsedSymbols == 0) {
            return true;
        }
        if (totalUsedSymbols > HuffmanDecodingTable.MAX_SYMS) {
            return false;
        }

        byte[] codeLengthCodeSizes = new byte[HuffmanDecodingTable.TOTAL_CODELENGTH_CODES];
        int codeLengthCodeCount = getBits(5);
        if (codeLengthCodeCount < 1
                || codeLengthCodeCount > HuffmanDecodingTable.TOTAL_CODELENGTH_CODES) {
            return false;
        }

        for (int i = 0; i < codeLengthCodeCount; i++) {
            int symbol = HuffmanDecodingTable.SORTED_CODELENGTH_CODES[i];
            codeLengthCodeSizes[symbol] = (byte) getBits(3);
        }

        HuffmanDecodingTable codeLengthTable = new HuffmanDecodingTable();
        if (!codeLengthTable.init(HuffmanDecodingTable.TOTAL_CODELENGTH_CODES, codeLengthCodeSizes)
                || !codeLengthTable.isValid()) {
            return false;
        }

        byte[] codeSizes = new byte[totalUsedSymbols];
        int current = 0;
        while (current < totalUsedSymbols) {
            int code = decodeHuffman(codeLengthTable);
            if (code <= 16) {
                codeSizes[current++] = (byte) code;
            } else if (code == HuffmanDecodingTable.SMALL_ZERO_RUN_CODE) {
                current += getBits(HuffmanDecodingTable.SMALL_ZERO_RUN_EXTRA_BITS)
                        + HuffmanDecodingTable.SMALL_ZERO_RUN_SIZE_MIN;
            } else if (code == HuffmanDecodingTable.BIG_ZERO_RUN_CODE) {
                current += getBits(HuffmanDecodingTable.BIG_ZERO_RUN_EXTRA_BITS)
                        + HuffmanDecodingTable.BIG_ZERO_RUN_SIZE_MIN;
            } else {
                int repeatCount = repeatCount(code);
                if (!readRepeat(codeSizes, current, repeatCount)) {
                    return false;
                }
                current = repeatEnd(codeSizes, current, repeatCount);
            }
            if (current > totalUsedSymbols) {
                return false;
            }
        }
        return table.init(totalUsedSymbols, codeSizes);
    }

    int getBitsRemaining() {
        return (end - position) * 8 + bitBufferSize;
    }

    private boolean readRepeat(byte[] codeSizes, int current, int repeatCount) {
        if (current == 0 || codeSizes[current - 1] == 0) {
            return false;
        }
        return current + repeatCount <= codeSizes.length;
    }

    private int repeatEnd(byte[] codeSizes, int current, int repeatCount) {
        byte previous = codeSizes[current - 1];
        for (int i = 0; i < repeatCount; i++) {
            codeSizes[current + i] = previous;
        }
        return current + repeatCount;
    }

    private int repeatCount(int code) {
        if (code == HuffmanDecodingTable.SMALL_REPEAT_CODE) {
            return getBits(HuffmanDecodingTable.SMALL_REPEAT_EXTRA_BITS)
                    + HuffmanDecodingTable.SMALL_REPEAT_SIZE_MIN;
        }
        return getBits(HuffmanDecodingTable.BIG_REPEAT_EXTRA_BITS)
                + HuffmanDecodingTable.BIG_REPEAT_SIZE_MIN;
    }

    private void fill(int bitCount) {
        while (bitBufferSize < bitCount) {
            int value = position < end ? Byte.toUnsignedInt(data[position++]) : 0;
            bitBuffer |= value << bitBufferSize;
            bitBufferSize += 8;
        }
    }

    private static int floorLog2(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }
}
