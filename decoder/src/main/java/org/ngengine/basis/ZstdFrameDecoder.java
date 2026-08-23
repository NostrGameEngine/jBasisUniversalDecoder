package org.ngengine.basis;

import java.util.ArrayList;
import java.util.List;

/**
 * Small Zstandard frame decoder for Basis Universal side streams.
 *
 * <p>This is a Java 11/Android/TeaVM-safe byte-array decoder. It is adapted
 * from the Zstandard format specification and the MIT-licensed fzstd decoder
 * by Arjun Barrett, with native/streaming features intentionally omitted.</p>
 */
final class ZstdFrameDecoder {
    private static final int ZSTD_MAGIC = 0xFD2FB528;
    private static final int SKIPPABLE_MAGIC_PREFIX = 0x184D2A50;
    private static final int MAX_WINDOW_SIZE = 2_145_386_496;
    private static final int MAX_BLOCK_SIZE = 131_072;

    private static final FseTable DEFAULT_LITERAL_LENGTH_TABLE = readFse(
            new byte[] {
                    81, 16, 99, -116, 49, -58, 24, 99, 12, 33, -60, 24, 99, 102, 102, -122, 70, -110, 4
            },
            0,
            6).table;
    private static final FseTable DEFAULT_MATCH_LENGTH_TABLE = readFse(
            new byte[] {
                    33, 20, -60, 24, 99, -116, 33, -124, 16, 66, 8, 33, -124, 16,
                    66, 8, 33, 68, 68, 68, 68, 68, 68, 68, 68, 36, 9
            },
            0,
            6).table;
    private static final FseTable DEFAULT_OFFSET_TABLE = readFse(
            new byte[] {
                    32, -124, 16, 66, 102, 70, 68, 68, 68, 68, 36, 73, 2
            },
            0,
            5).table;

    private static final int[] LITERAL_LENGTH_BITS = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 2, 2, 3, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
    };
    private static final int[] MATCH_LENGTH_BITS = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
    };
    private static final int[] LITERAL_LENGTH_BASE = toBaseline(LITERAL_LENGTH_BITS, 0);
    private static final int[] MATCH_LENGTH_BASE = toBaseline(MATCH_LENGTH_BITS, 3);

    private ZstdFrameDecoder() {
    }

    static byte[] decode(byte[] input, int offset, int length) {
        return decompress(input, offset, length);
    }

    static byte[] decompress(byte[] input, int offset, int length) {
        if (input == null) {
            throw new BasisDecodeException("Zstd input must not be null");
        }
        if (offset < 0 || length < 0 || offset > input.length || length > input.length - offset) {
            throw new BasisDecodeException("Zstd input range extends beyond buffer");
        }

        int position = offset;
        int end = offset + length;
        List<byte[]> frames = new ArrayList<byte[]>();
        int totalLength = 0;
        while (position < end) {
            FrameHeader header = readFrameHeader(input, position, end);
            if (header.skippableLength >= 0) {
                position += header.skippableLength;
                continue;
            }
            State state = new State(header.contentSize, header.windowSize, header.checksum);
            state.inputPosition = header.headerBytes;
            while (!state.lastBlock) {
                readBlock(input, position + state.inputPosition, end, state);
            }
            if (state.checksum) {
                state.inputPosition += Integer.BYTES;
            }
            if (position + state.inputPosition > end) {
                throw new BasisDecodeException("Zstd frame exceeds input range");
            }
            byte[] frameOutput = state.frameOutput();
            frames.add(frameOutput);
            totalLength += frameOutput.length;
            position += state.inputPosition;
        }

        if (frames.size() == 1) {
            return frames.get(0);
        }
        byte[] output = new byte[totalLength];
        int out = 0;
        for (byte[] frame : frames) {
            System.arraycopy(frame, 0, output, out, frame.length);
            out += frame.length;
        }
        return output;
    }

    private static FrameHeader readFrameHeader(byte[] data, int offset, int end) {
        requireRange(offset, 4, end, "Zstd frame magic");
        int magic = readIntLittleEndian(data, offset);
        if ((magic & 0xFFFFFFF0) == SKIPPABLE_MAGIC_PREFIX) {
            requireRange(offset + 4, 4, end, "Zstd skippable frame length");
            long size = Integer.toUnsignedLong(readIntLittleEndian(data, offset + 4));
            long total = 8L + size;
            if (total > Integer.MAX_VALUE || offset + total > end) {
                throw new BasisDecodeException("Zstd skippable frame exceeds input range");
            }
            return FrameHeader.skippable((int) total);
        }
        if (magic != ZSTD_MAGIC) {
            throw new BasisDecodeException("Invalid Zstd frame magic");
        }
        requireRange(offset + 4, 1, end, "Zstd frame descriptor");
        int flags = Byte.toUnsignedInt(data[offset + 4]);
        if ((flags & 0x08) != 0) {
            throw new BasisDecodeException("Zstd reserved frame descriptor bit is set");
        }
        boolean singleSegment = ((flags >>> 5) & 1) != 0;
        final boolean checksum = ((flags >>> 2) & 1) != 0;
        int dictionaryFlag = flags & 3;
        final int contentSizeFlag = flags >>> 6;

        int position = offset + 5;
        int windowSize = 0;
        if (!singleSegment) {
            requireRange(position, 1, end, "Zstd window descriptor");
            int descriptor = Byte.toUnsignedInt(data[position++]);
            int base = 1 << (10 + (descriptor >>> 3));
            windowSize = base + (base >>> 3) * (descriptor & 7);
        }

        int dictionaryBytes = dictionaryFlag == 3 ? 4 : dictionaryFlag;
        requireRange(position, dictionaryBytes, end, "Zstd dictionary id");
        position += dictionaryBytes;

        int contentSizeBytes = contentSizeFlag == 0
                ? (singleSegment ? 1 : 0)
                : (1 << contentSizeFlag);
        requireRange(position, contentSizeBytes, end, "Zstd frame content size");
        long contentSize = contentSizeBytes == 0 && !singleSegment
                ? -1
                : readUnsigned(data, position, contentSizeBytes);
        if (contentSizeFlag == 1) {
            contentSize += 256;
        }
        position += contentSizeBytes;
        if (singleSegment) {
            windowSize = checkedToInt(contentSize, "Zstd single-segment size exceeds Java array range");
        }
        if (windowSize > MAX_WINDOW_SIZE) {
            throw new BasisDecodeException("Zstd window size is too large");
        }
        return new FrameHeader(
                position - offset,
                contentSize < 0
                        ? -1
                        : checkedToInt(contentSize, "Zstd content size exceeds Java array range"),
                windowSize,
                checksum);
    }

    private static void readBlock(byte[] data, int blockOffset, int end, State state) {
        requireRange(blockOffset, 3, end, "Zstd block header");
        int blockHeader = read24(data, blockOffset);
        state.lastBlock = (blockHeader & 1) != 0;
        int blockType = (blockHeader >>> 1) & 3;
        int blockSize = blockHeader >>> 3;
        int contentOffset = blockOffset + 3;
        if (blockType == 0) {
            requireRange(contentOffset, blockSize, end, "Zstd raw block");
            state.copyToOutput(data, contentOffset, blockSize);
            state.inputPosition += 3 + blockSize;
            state.historyPosition = state.outputPosition;
            return;
        }
        if (blockType == 1) {
            requireRange(contentOffset, 1, end, "Zstd RLE block");
            state.fillOutput(Byte.toUnsignedInt(data[contentOffset]), blockSize);
            state.inputPosition += 4;
            state.historyPosition = state.outputPosition;
            return;
        }
        if (blockType != 2) {
            throw new BasisDecodeException("Invalid Zstd block type");
        }
        requireRange(contentOffset, blockSize, end, "Zstd compressed block");
        decodeCompressedBlock(data, contentOffset, contentOffset + blockSize, state);
        state.inputPosition += 3 + blockSize;
        state.historyPosition = state.outputPosition;
    }

    private static void decodeCompressedBlock(byte[] data, int offset, int end, State state) {
        int position = offset;
        int literalHeader = Byte.toUnsignedInt(data[position]);
        int literalBlockType = literalHeader & 3;
        int sizeFormat = (literalHeader >>> 2) & 3;
        int literalSize = literalHeader >>> 4;
        int literalCompressedSize = 0;
        int fourStreams = 0;
        if (literalBlockType < 2) {
            if ((sizeFormat & 1) != 0) {
                literalSize |= Byte.toUnsignedInt(data[++position]) << 4;
                if ((sizeFormat & 2) != 0) {
                    literalSize |= Byte.toUnsignedInt(data[++position]) << 12;
                }
            } else {
                literalSize = literalHeader >>> 3;
            }
        } else {
            fourStreams = sizeFormat;
            if (sizeFormat < 2) {
                int next = Byte.toUnsignedInt(data[++position]);
                literalSize |= (next & 63) << 4;
                literalCompressedSize = (next >>> 6) | (Byte.toUnsignedInt(data[++position]) << 2);
            } else if (sizeFormat == 2) {
                literalSize |= Byte.toUnsignedInt(data[++position]) << 4;
                int next = Byte.toUnsignedInt(data[++position]);
                literalSize |= (next & 3) << 12;
                literalCompressedSize = (next >>> 2) | (Byte.toUnsignedInt(data[++position]) << 6);
            } else {
                literalSize |= Byte.toUnsignedInt(data[++position]) << 4;
                int next = Byte.toUnsignedInt(data[++position]);
                literalSize |= (next & 63) << 12;
                literalCompressedSize = (next >>> 6)
                        | (Byte.toUnsignedInt(data[++position]) << 2)
                        | (Byte.toUnsignedInt(data[++position]) << 10);
            }
        }
        position++;

        int blockLimit = state.currentBlockLimit();
        int literalStart = blockLimit - literalSize;
        if (literalStart < 0) {
            throw new BasisDecodeException("Zstd literals exceed block output limit");
        }
        if (literalBlockType == 0) {
            requireRange(position, literalSize, end, "Zstd raw literals");
            state.copyLiteral(data, position, literalStart, literalSize);
            position += literalSize;
        } else if (literalBlockType == 1) {
            requireRange(position, 1, end, "Zstd RLE literals");
            state.fillLiteral(Byte.toUnsignedInt(data[position++]), literalStart, literalSize);
        } else {
            HuffmanTable table = state.huffmanTable;
            if (literalBlockType == 2) {
                HuffmanReadResult result = readHuffman(data, position);
                literalCompressedSize += position - result.position;
                position = result.position;
                table = result.table;
                state.huffmanTable = table;
            } else if (table == null) {
                throw new BasisDecodeException("Zstd repeat Huffman table without previous table");
            }
            if (fourStreams == 0) {
                decodeHuffman(data, position, position + literalCompressedSize, state.output,
                        state.outputPosition + literalStart, literalSize, table);
            } else {
                decodeHuffman4(data, position, position + literalCompressedSize, state.output,
                        state.outputPosition + literalStart, literalSize, table);
            }
            position += literalCompressedSize;
        }

        requireRange(position, 1, end, "Zstd sequence count");
        int sequenceCount = Byte.toUnsignedInt(data[position++]);
        if (sequenceCount == 255) {
            sequenceCount = read16(data, position) + 0x7F00;
            position += 2;
        } else if (sequenceCount > 127) {
            sequenceCount = ((sequenceCount - 128) << 8) | Byte.toUnsignedInt(data[position++]);
        }

        int produced;
        if (sequenceCount == 0) {
            produced = literalSize;
            if (literalStart != 0) {
                state.copyWithinBlock(literalStart, 0, literalSize);
            }
        } else {
            produced = decodeSequences(data, position, end, state, sequenceCount, literalStart, blockLimit);
        }
        state.outputPosition += produced;
    }

    private static int decodeSequences(
            byte[] data,
            int position,
            int end,
            State state,
            int sequenceCount,
            int literalStart,
            int blockLimit) {
        requireRange(position, 1, end, "Zstd sequence compression modes");
        int modes = Byte.toUnsignedInt(data[position++]);
        if ((modes & 3) != 0) {
            throw new BasisDecodeException("Zstd predefined sequence mode is invalid for literal lengths");
        }
        FseTable[] tables = {
                DEFAULT_MATCH_LENGTH_TABLE,
                DEFAULT_OFFSET_TABLE,
                DEFAULT_LITERAL_LENGTH_TABLE
        };
        for (int i = 2; i >= 0; i--) {
            int mode = (modes >>> ((i << 1) + 2)) & 3;
            if (mode == 1) {
                tables[i] = FseTable.rle(Byte.toUnsignedInt(data[position++]));
            } else if (mode == 2) {
                FseReadResult result = readFse(data, position, 9 - (i & 1));
                position = result.position;
                tables[i] = result.table;
            } else if (mode == 3) {
                if (state.fseTables == null) {
                    throw new BasisDecodeException("Zstd repeat FSE table without previous table");
                }
                tables[i] = state.fseTables[i];
            }
        }
        state.fseTables = tables;
        FseTable matchLengthTable = tables[0];
        FseTable offsetTable = tables[1];
        FseTable literalLengthTable = tables[2];

        int lastByte = Byte.toUnsignedInt(data[end - 1]);
        if (lastByte == 0) {
            throw new BasisDecodeException("Invalid Zstd sequence bitstream terminator");
        }
        int bitPosition = (end << 3) - 8 + mostSignificantBit(lastByte) - literalLengthTable.accuracyLog;
        int literalState = readBitsAt(data, bitPosition, literalLengthTable.accuracyLog);
        bitPosition -= offsetTable.accuracyLog;
        int offsetState = readBitsAt(data, bitPosition, offsetTable.accuracyLog);
        bitPosition -= matchLengthTable.accuracyLog;
        int matchState = readBitsAt(data, bitPosition, matchLengthTable.accuracyLog);

        int outputBlockPosition = 0;
        int literalPosition = literalStart;
        for (int remaining = sequenceCount; remaining > 0; remaining--) {
            int literalLengthCode = literalLengthTable.symbols[literalState];
            final int literalBits = literalLengthTable.numBits[literalState];
            int matchLengthCode = matchLengthTable.symbols[matchState];
            final int matchBits = matchLengthTable.numBits[matchState];
            int offsetCode = offsetTable.symbols[offsetState];
            final int offsetBits = offsetTable.numBits[offsetState];

            bitPosition -= offsetCode;
            final int decodedOffset = (1 << offsetCode) + readBitsAt(data, bitPosition, offsetCode);
            bitPosition -= MATCH_LENGTH_BITS[matchLengthCode];
            int matchLength = MATCH_LENGTH_BASE[matchLengthCode]
                    + readBitsAt(data, bitPosition, MATCH_LENGTH_BITS[matchLengthCode]);
            bitPosition -= LITERAL_LENGTH_BITS[literalLengthCode];
            int literalLength = LITERAL_LENGTH_BASE[literalLengthCode]
                    + readBitsAt(data, bitPosition, LITERAL_LENGTH_BITS[literalLengthCode]);
            bitPosition -= literalBits;
            literalState = literalLengthTable.nextStates[literalState]
                    + readBitsAt(data, bitPosition, literalBits);
            bitPosition -= matchBits;
            matchState = matchLengthTable.nextStates[matchState] + readBitsAt(data, bitPosition, matchBits);
            bitPosition -= offsetBits;
            offsetState = offsetTable.nextStates[offsetState] + readBitsAt(data, bitPosition, offsetBits);

            int offset = decodedOffset;
            if (offset > 3) {
                state.repeatedOffsets[2] = state.repeatedOffsets[1];
                state.repeatedOffsets[1] = state.repeatedOffsets[0];
                state.repeatedOffsets[0] = offset - 3;
                offset = state.repeatedOffsets[0];
            } else {
                int repeatedIndex = offset - (literalLength != 0 ? 1 : 0);
                if (repeatedIndex != 0) {
                    offset = repeatedIndex == 3
                            ? state.repeatedOffsets[0] - 1
                            : state.repeatedOffsets[repeatedIndex];
                    if (repeatedIndex > 1) {
                        state.repeatedOffsets[2] = state.repeatedOffsets[1];
                    }
                    state.repeatedOffsets[1] = state.repeatedOffsets[0];
                    state.repeatedOffsets[0] = offset;
                } else {
                    offset = state.repeatedOffsets[0];
                }
            }

            state.copyWithinBlock(literalPosition, outputBlockPosition, literalLength);
            outputBlockPosition += literalLength;
            literalPosition += literalLength;

            int source = outputBlockPosition - offset;
            if (source < 0) {
                int historyLength = Math.min(-source, matchLength);
                int historySource = state.historyPosition + source;
                if (historySource < 0 || historyLength > state.output.length - historySource) {
                    throw new BasisDecodeException(
                            "Zstd match distance is too far back: offset=" + offset
                                    + ", outputBlockPosition=" + outputBlockPosition
                                    + ", historyPosition=" + state.historyPosition
                                    + ", frameOutputPosition=" + state.outputPosition
                                    + ", literalLength=" + literalLength
                                    + ", matchLength=" + matchLength);
                }
                state.copyFromHistory(historySource, outputBlockPosition, historyLength);
                outputBlockPosition += historyLength;
                matchLength -= historyLength;
                source = 0;
            }
            int matchCopyLength = Math.min(matchLength, blockLimit - outputBlockPosition);
            if (matchCopyLength > 0) {
                state.copyWithinBlock(source, outputBlockPosition, matchCopyLength);
            }
            outputBlockPosition += matchLength;
        }
        if (outputBlockPosition >= blockLimit) {
            return blockLimit;
        }
        if (outputBlockPosition != literalPosition) {
            while (literalPosition < blockLimit && outputBlockPosition < blockLimit) {
                state.output[state.outputPosition + outputBlockPosition++] =
                        state.output[state.outputPosition + literalPosition++];
            }
            if (outputBlockPosition > blockLimit) {
                throw new BasisDecodeException("Zstd sequence output exceeds block limit");
            }
        } else {
            outputBlockPosition = blockLimit;
        }
        return outputBlockPosition;
    }

    private static FseReadResult readFse(byte[] data, int offset, int maxAccuracyLog) {
        int tablePosition = (offset << 3) + 4;
        int accuracyLog = (Byte.toUnsignedInt(data[offset]) & 15) + 5;
        if (accuracyLog > maxAccuracyLog) {
            throw new BasisDecodeException("Zstd FSE accuracy log is too high");
        }
        int tableSize = 1 << accuracyLog;
        int remainingProbability = tableSize;
        int symbol = -1;
        int repeat = -1;
        int highThreshold = tableSize;
        short[] frequencies = new short[256];
        int[] distributionState = new int[256];
        int[] nextStates = new int[tableSize];
        int[] symbols = new int[tableSize];
        int[] numBits = new int[tableSize];
        while (symbol < 255 && remainingProbability > 0) {
            int bits = mostSignificantBit(remainingProbability + 1);
            int bytePosition = tablePosition >>> 3;
            int mask = (1 << (bits + 1)) - 1;
            int value = (read24Lenient(data, bytePosition) >>> (tablePosition & 7)) & mask;
            int shortMask = (1 << bits) - 1;
            int maxSmallValue = mask - remainingProbability - 1;
            int smallValue = value & shortMask;
            if (smallValue < maxSmallValue) {
                tablePosition += bits;
                value = smallValue;
            } else {
                tablePosition += bits + 1;
                if (value > shortMask) {
                    value -= maxSmallValue;
                }
            }
            frequencies[++symbol] = (short) (--value);
            distributionState[symbol] = value & 0xFFFF;
            if (value == -1) {
                remainingProbability += value;
                symbols[--highThreshold] = symbol;
            } else {
                remainingProbability -= value;
            }
            if (value == 0) {
                do {
                    int repeatByte = tablePosition >>> 3;
                    repeat = (read16Lenient(data, repeatByte) >>> (tablePosition & 7)) & 3;
                    tablePosition += 2;
                    symbol += repeat;
                } while (repeat == 3);
            }
        }
        if (symbol > 255 || remainingProbability != 0) {
            throw new BasisDecodeException("Invalid Zstd FSE table");
        }
        int symbolPosition = 0;
        int step = (tableSize >>> 1) + (tableSize >>> 3) + 3;
        int mask = tableSize - 1;
        for (int s = 0; s <= symbol; s++) {
            int frequency = frequencies[s];
            if (frequency < 1) {
                distributionState[s] = -frequency;
                continue;
            }
            distributionState[s] = frequency;
            for (int i = 0; i < frequency; i++) {
                symbols[symbolPosition] = s;
                do {
                    symbolPosition = (symbolPosition + step) & mask;
                } while (symbolPosition >= highThreshold);
            }
        }
        if (symbolPosition != 0) {
            throw new BasisDecodeException("Invalid Zstd FSE symbol spread");
        }
        for (int i = 0; i < tableSize; i++) {
            int nextState = distributionState[symbols[i]]++;
            int bits = accuracyLog - mostSignificantBit(nextState);
            numBits[i] = bits;
            nextStates[i] = (nextState << bits) - tableSize;
        }
        return new FseReadResult(
                (tablePosition + 7) >>> 3,
                new FseTable(accuracyLog, symbols, numBits, nextStates));
    }

    private static HuffmanReadResult readHuffman(byte[] data, int offset) {
        int position = offset;
        int weightCount;
        int[] weights = new int[256];
        int header = Byte.toUnsignedInt(data[position]);
        if (header < 128) {
            FseReadResult fse = readFse(data, position + 1, 6);
            position += header;
            int endPosition = fse.position << 3;
            int lastByte = Byte.toUnsignedInt(data[position]);
            if (lastByte == 0) {
                throw new BasisDecodeException("Invalid Zstd Huffman weight stream");
            }
            int state1 = 0;
            int state2 = 0;
            int bits1 = fse.table.accuracyLog;
            int bits2 = bits1;
            int bitPosition = ((++position) << 3) - 8 + mostSignificantBit(lastByte);
            weightCount = -1;
            while (true) {
                bitPosition -= bits1;
                if (bitPosition < endPosition) {
                    break;
                }
                state1 += readBitsAt(data, bitPosition, bits1);
                weights[++weightCount] = fse.table.symbols[state1];
                bitPosition -= bits2;
                if (bitPosition < endPosition) {
                    break;
                }
                state2 += readBitsAt(data, bitPosition, bits2);
                weights[++weightCount] = fse.table.symbols[state2];
                bits1 = fse.table.numBits[state1];
                state1 = fse.table.nextStates[state1];
                bits2 = fse.table.numBits[state2];
                state2 = fse.table.nextStates[state2];
            }
            if (++weightCount > 255) {
                throw new BasisDecodeException("Zstd Huffman table has too many weights");
            }
        } else {
            weightCount = header - 127;
            for (int i = 0; i < weightCount; i += 2) {
                int value = Byte.toUnsignedInt(data[++position]);
                weights[i] = value >>> 4;
                weights[i + 1] = value & 15;
            }
            position++;
        }

        int weightSum = 0;
        for (int i = 0; i < weightCount; i++) {
            int weight = weights[i];
            if (weight > 11) {
                throw new BasisDecodeException("Zstd Huffman weight is too large");
            }
            if (weight != 0) {
                weightSum += 1 << (weight - 1);
            }
        }
        int maxBits = mostSignificantBit(weightSum) + 1;
        int tableSize = 1 << maxBits;
        int remaining = tableSize - weightSum;
        if ((remaining & (remaining - 1)) != 0) {
            throw new BasisDecodeException("Invalid Zstd Huffman weight sum");
        }
        weights[weightCount++] = mostSignificantBit(remaining) + 1;

        int[] rankCount = new int[12];
        for (int i = 0; i < weightCount; i++) {
            int weight = weights[i];
            weights[i] = weight == 0 ? 0 : maxBits + 1 - weight;
            rankCount[weights[i]]++;
        }

        int[] rankIndex = new int[12];
        int[] symbols = new int[tableSize];
        int[] numBits = new int[tableSize];
        rankIndex[maxBits] = 0;
        for (int i = maxBits; i > 0; i--) {
            int previous = rankIndex[i];
            rankIndex[i - 1] = previous + rankCount[i] * (1 << (maxBits - i));
            fill(numBits, previous, rankIndex[i - 1], i);
        }
        if (rankIndex[0] != tableSize) {
            throw new BasisDecodeException("Invalid Zstd Huffman rank table");
        }
        for (int i = 0; i < weightCount; i++) {
            int bits = weights[i];
            if (bits != 0) {
                int code = rankIndex[bits];
                fill(symbols, code, code + (1 << (maxBits - bits)), i);
                rankIndex[bits] = code + (1 << (maxBits - bits));
            }
        }
        return new HuffmanReadResult(position, new HuffmanTable(maxBits, symbols, numBits));
    }

    private static void decodeHuffman(
            byte[] data,
            int offset,
            int end,
            byte[] output,
            int outputOffset,
            int outputLength,
            HuffmanTable table) {
        int lastByte = Byte.toUnsignedInt(data[end - 1]);
        if (lastByte == 0) {
            throw new BasisDecodeException("Invalid Zstd Huffman stream terminator");
        }
        int state = 0;
        int bitsToRead = table.initialBits;
        int bitPosition = (end << 3) - 8 + mostSignificantBit(lastByte) - bitsToRead;
        int expectedEnd = (offset << 3) - table.initialBits;
        int outputPosition = 0;
        int mask = (1 << table.initialBits) - 1;
        while (bitPosition > expectedEnd && outputPosition < outputLength) {
            state = ((state << bitsToRead) | readBitsAt(data, bitPosition, bitsToRead)) & mask;
            output[outputOffset + outputPosition++] = (byte) table.symbols[state];
            bitPosition -= table.numBits[state];
            bitsToRead = table.numBits[state];
        }
        if (bitPosition != expectedEnd || outputPosition != outputLength) {
            throw new BasisDecodeException("Invalid Zstd Huffman stream length");
        }
    }

    private static void decodeHuffman4(
            byte[] data,
            int offset,
            int end,
            byte[] output,
            int outputOffset,
            int outputLength,
            HuffmanTable table) {
        int position = offset + 6;
        int size1 = (outputLength + 3) >>> 2;
        int size2 = size1 << 1;
        final int size3 = size1 + size2;
        int stream1 = read16(data, offset);
        int stream2 = read16(data, offset + 2);
        final int stream3 = read16(data, offset + 4);
        decodeHuffman(data, position, position + stream1, output, outputOffset, size1, table);
        position += stream1;
        decodeHuffman(data, position, position + stream2, output, outputOffset + size1, size1, table);
        position += stream2;
        decodeHuffman(data, position, position + stream3, output, outputOffset + size2, size1, table);
        position += stream3;
        decodeHuffman(data, position, end, output, outputOffset + size3, outputLength - size3, table);
    }

    private static int[] toBaseline(int[] bits, int start) {
        int[] baseline = new int[bits.length];
        int value = start;
        for (int i = 0; i < bits.length; i++) {
            baseline[i] = value;
            value += 1 << bits[i];
        }
        return baseline;
    }

    private static int mostSignificantBit(int value) {
        if (value <= 0) {
            return -1;
        }
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    private static int readBitsAt(byte[] data, int bitPosition, int bitCount) {
        if (bitCount == 0) {
            return 0;
        }
        int bytePosition = bitPosition >>> 3;
        return (readIntLenient(data, bytePosition) >>> (bitPosition & 7)) & ((1 << bitCount) - 1);
    }

    private static int readUnsigned(byte[] data, int offset, int bytes) {
        int value = 0;
        for (int i = 0; i < bytes; i++) {
            value |= Byte.toUnsignedInt(data[offset + i]) << (i << 3);
        }
        return value;
    }

    private static int read16(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]) | (Byte.toUnsignedInt(data[offset + 1]) << 8);
    }

    private static int read24(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16);
    }

    private static int readIntLittleEndian(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                | (Byte.toUnsignedInt(data[offset + 3]) << 24);
    }

    private static int read16Lenient(byte[] data, int offset) {
        int value = 0;
        if (offset < data.length) {
            value |= Byte.toUnsignedInt(data[offset]);
        }
        if (offset + 1 < data.length) {
            value |= Byte.toUnsignedInt(data[offset + 1]) << 8;
        }
        return value;
    }

    private static int read24Lenient(byte[] data, int offset) {
        return read16Lenient(data, offset)
                | (offset + 2 < data.length ? Byte.toUnsignedInt(data[offset + 2]) << 16 : 0);
    }

    private static int readIntLenient(byte[] data, int offset) {
        return read24Lenient(data, offset)
                | (offset + 3 < data.length ? Byte.toUnsignedInt(data[offset + 3]) << 24 : 0);
    }

    private static void fill(int[] data, int start, int end, int value) {
        for (int i = start; i < end; i++) {
            data[i] = value;
        }
    }

    private static void requireRange(int offset, int length, int end, String label) {
        if (offset < 0 || length < 0 || offset > end || length > end - offset) {
            throw new BasisDecodeException(label + " exceeds input range");
        }
    }

    private static int checkedToInt(long value, String message) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new BasisDecodeException(message);
        }
        return (int) value;
    }

    private static final class State {
        private byte[] output;
        private final boolean dynamicOutput;
        private final int maxBlockSize;
        private final boolean checksum;
        private final int[] repeatedOffsets = {1, 4, 8};
        private int inputPosition;
        private int outputPosition;
        private int historyPosition;
        private boolean lastBlock;
        private HuffmanTable huffmanTable;
        private FseTable[] fseTables;

        State(int contentSize, int windowSize, boolean checksum) {
            this.dynamicOutput = contentSize < 0;
            this.maxBlockSize = Math.min(MAX_BLOCK_SIZE, windowSize);
            this.output = new byte[initialOutputCapacity(contentSize, maxBlockSize)];
            this.historyPosition = dynamicOutput ? 0 : Math.min(windowSize, contentSize);
            this.checksum = checksum;
        }

        int currentBlockLimit() {
            int blockLimit = dynamicOutput
                    ? maxBlockSize
                    : Math.min(maxBlockSize, output.length - outputPosition);
            ensureCapacity(outputPosition + blockLimit);
            return blockLimit;
        }

        void copyToOutput(byte[] source, int offset, int length) {
            ensureCapacity(outputPosition + length);
            if (!dynamicOutput && length > output.length - outputPosition) {
                throw new BasisDecodeException("Zstd block exceeds declared content size");
            }
            System.arraycopy(source, offset, output, outputPosition, length);
            outputPosition += length;
        }

        void fillOutput(int value, int length) {
            ensureCapacity(outputPosition + length);
            if (!dynamicOutput && length > output.length - outputPosition) {
                throw new BasisDecodeException("Zstd RLE block exceeds declared content size");
            }
            for (int i = 0; i < length; i++) {
                output[outputPosition++] = (byte) value;
            }
        }

        void copyLiteral(byte[] source, int sourceOffset, int blockOffset, int length) {
            ensureCapacity(outputPosition + blockOffset + length);
            System.arraycopy(source, sourceOffset, output, outputPosition + blockOffset, length);
        }

        void fillLiteral(int value, int blockOffset, int length) {
            ensureCapacity(outputPosition + blockOffset + length);
            for (int i = 0; i < length; i++) {
                output[outputPosition + blockOffset + i] = (byte) value;
            }
        }

        void copyWithinBlock(int sourceBlockOffset, int targetBlockOffset, int length) {
            ensureCapacity(outputPosition + targetBlockOffset + length);
            if (sourceBlockOffset < 0 || targetBlockOffset < 0
                    || sourceBlockOffset > output.length - outputPosition
                    || targetBlockOffset > output.length - outputPosition
                    || length < 0
                    || length > output.length - outputPosition - targetBlockOffset) {
                throw new BasisDecodeException("Zstd block copy exceeds output range: sourceBlockOffset="
                        + sourceBlockOffset + ", targetBlockOffset=" + targetBlockOffset
                        + ", length=" + length + ", outputPosition=" + outputPosition
                        + ", outputLength=" + output.length);
            }
            for (int i = 0; i < length; i++) {
                output[outputPosition + targetBlockOffset + i] =
                        output[outputPosition + sourceBlockOffset + i];
            }
        }

        void copyFromHistory(int sourceOffset, int targetBlockOffset, int length) {
            ensureCapacity(outputPosition + targetBlockOffset + length);
            for (int i = 0; i < length; i++) {
                output[outputPosition + targetBlockOffset + i] = output[sourceOffset + i];
            }
        }

        byte[] frameOutput() {
            if (!dynamicOutput) {
                if (outputPosition != output.length) {
                    throw new BasisDecodeException("Zstd frame did not produce declared content size");
                }
                return output;
            }
            byte[] trimmed = new byte[outputPosition];
            System.arraycopy(output, 0, trimmed, 0, outputPosition);
            return trimmed;
        }

        private void ensureCapacity(int required) {
            if (required < 0) {
                throw new BasisDecodeException("Zstd output size exceeds Java array range");
            }
            if (required <= output.length) {
                return;
            }
            if (!dynamicOutput) {
                throw new BasisDecodeException("Zstd block exceeds declared content size");
            }
            int grown = output.length;
            while (grown < required) {
                if (grown >= Integer.MAX_VALUE / 2) {
                    grown = Integer.MAX_VALUE;
                } else {
                    grown = Math.max(required, grown << 1);
                }
                if (grown == Integer.MAX_VALUE && grown < required) {
                    throw new BasisDecodeException("Zstd output size exceeds Java array range");
                }
            }
            byte[] next = new byte[grown];
            System.arraycopy(output, 0, next, 0, outputPosition);
            output = next;
        }

        private static int initialOutputCapacity(int contentSize, int maxBlockSize) {
            if (contentSize >= 0) {
                return contentSize;
            }
            return Math.max(1, maxBlockSize);
        }

    }

    private static final class FrameHeader {
        private final int headerBytes;
        private final int contentSize;
        private final int windowSize;
        private final boolean checksum;
        private final int skippableLength;

        FrameHeader(int headerBytes, int contentSize, int windowSize, boolean checksum) {
            this.headerBytes = headerBytes;
            this.contentSize = contentSize;
            this.windowSize = windowSize;
            this.checksum = checksum;
            this.skippableLength = -1;
        }

        static FrameHeader skippable(int length) {
            return new FrameHeader(length);
        }

        private FrameHeader(int skippableLength) {
            this.headerBytes = 0;
            this.contentSize = 0;
            this.windowSize = 0;
            this.checksum = false;
            this.skippableLength = skippableLength;
        }
    }

    private static final class HuffmanTable {
        private final int initialBits;
        private final int[] symbols;
        private final int[] numBits;

        HuffmanTable(int initialBits, int[] symbols, int[] numBits) {
            this.initialBits = initialBits;
            this.symbols = symbols;
            this.numBits = numBits;
        }
    }

    private static final class HuffmanReadResult {
        private final int position;
        private final HuffmanTable table;

        HuffmanReadResult(int position, HuffmanTable table) {
            this.position = position;
            this.table = table;
        }
    }

    private static final class FseTable {
        private final int accuracyLog;
        private final int[] symbols;
        private final int[] numBits;
        private final int[] nextStates;

        FseTable(int accuracyLog, int[] symbols, int[] numBits, int[] nextStates) {
            this.accuracyLog = accuracyLog;
            this.symbols = symbols;
            this.numBits = numBits;
            this.nextStates = nextStates;
        }

        static FseTable rle(int symbol) {
            return new FseTable(0, new int[] {symbol}, new int[] {0}, new int[] {0});
        }
    }

    private static final class FseReadResult {
        private final int position;
        private final FseTable table;

        FseReadResult(int position, FseTable table) {
            this.position = position;
            this.table = table;
        }
    }
}
