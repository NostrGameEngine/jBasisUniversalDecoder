package org.ngengine.basis;

/**
 * Arithmetic decoder primitive used by XUASTC LDR streams.
 */
final class XuastcArithmeticDecoder {
    private static final long UINT_MASK = 0xFFFF_FFFFL;
    private static final int MIN_LENGTH = 1 << 24;
    private static final int MAX_GET_BITS = 20;
    private static final int MIN_EXPECTED_DATA_BYTES = 5;

    private final byte[] data;
    private final int end;
    private int position;
    private int value;
    private int length;

    XuastcArithmeticDecoder(byte[] data, int offset, int byteLength) {
        if (data == null) {
            throw new BasisDecodeException("Arithmetic stream data must not be null");
        }
        if (offset < 0 || byteLength < MIN_EXPECTED_DATA_BYTES
                || offset > data.length || byteLength > data.length - offset) {
            throw new BasisDecodeException("Arithmetic stream range extends beyond input buffer");
        }
        this.data = data;
        this.position = offset + Integer.BYTES;
        this.end = offset + byteLength;
        this.value = (Byte.toUnsignedInt(data[offset]) << 24)
                | (Byte.toUnsignedInt(data[offset + 1]) << 16)
                | (Byte.toUnsignedInt(data[offset + 2]) << 8)
                | Byte.toUnsignedInt(data[offset + 3]);
        this.length = -1;
    }

    int getBit() {
        length >>>= 1;
        int bit = Integer.compareUnsigned(value, length) >= 0 ? 1 : 0;
        if (bit != 0) {
            value -= length;
        }
        if (Integer.compareUnsigned(length, MIN_LENGTH) < 0) {
            renormalize();
        }
        return bit;
    }

    int getBits(int bitCount) {
        if (bitCount < 1 || bitCount > MAX_GET_BITS) {
            throw new BasisDecodeException("Arithmetic bit count must be in range 1..20");
        }

        length >>>= bitCount;
        if (length == 0) {
            throw new BasisDecodeException("Arithmetic stream length underflow");
        }

        int result = (int) Long.divideUnsigned(value & UINT_MASK, length & UINT_MASK);
        value -= length * result;
        if (Integer.compareUnsigned(length, MIN_LENGTH) < 0) {
            renormalize();
        }
        return result;
    }

    int decodeBit(XuastcArithmeticBitModel model) {
        int split = model.bit0Probability * (length >>> XuastcArithmeticBitModel.getLengthShift());
        int bit = Integer.compareUnsigned(value, split) >= 0 ? 1 : 0;
        if (bit == 0) {
            length = split;
            model.bit0Count++;
        } else {
            value -= split;
            length -= split;
        }
        model.bitCount++;

        if (Integer.compareUnsigned(length, MIN_LENGTH) < 0) {
            renormalize();
        }
        if (--model.bitsUntilUpdate <= 0) {
            model.update();
        }
        return bit;
    }

    int decodeSymbol(XuastcArithmeticDataModel model) {
        int lowRange = 0;
        int highRange = length;
        length >>>= XuastcArithmeticDataModel.getLengthShift();

        int lowIndex = 0;
        int highIndex = model.numberOfSymbols;
        int midIndex = highIndex >>> 1;
        do {
            int split = length * model.cumulativeSymbolFrequencies[midIndex];
            if (Integer.compareUnsigned(split, value) > 0) {
                highIndex = midIndex;
                highRange = split;
            } else {
                lowIndex = midIndex;
                lowRange = split;
            }
            midIndex = (lowIndex + highIndex) >>> 1;
        } while (midIndex != lowIndex);

        value -= lowRange;
        length = highRange - lowRange;
        if (Integer.compareUnsigned(length, MIN_LENGTH) < 0) {
            renormalize();
        }

        model.symbolFrequencies[lowIndex]++;
        model.totalSymbolFrequency++;
        if (--model.symbolsUntilUpdate <= 0) {
            model.update();
        }
        return lowIndex;
    }

    int decodeGamma(XuastcArithmeticGammaContext context) {
        int prefixLength = 0;
        while (decodeBit(context.prefix(prefixLength)) != 0) {
            prefixLength++;
            if (prefixLength > 16) {
                throw new BasisDecodeException("XUASTC arithmetic gamma prefix overflow");
            }
        }

        int value = 1 << prefixLength;
        for (int i = prefixLength - 1; i >= 0; i--) {
            int bit = decodeBit(context.tail(i));
            value |= bit << i;
        }
        return value;
    }

    int decodeTruncatedBinary(int n) {
        if (n < 2) {
            throw new BasisDecodeException("Truncated binary range must be >= 2");
        }
        int k = 31 - Integer.numberOfLeadingZeros(n);
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

        int quotient = 0;
        while (getBit() != 0) {
            quotient++;
            if (quotient > 64) {
                throw new BasisDecodeException("Rice quotient overflow");
            }
        }
        return (quotient << m) + getBits(m);
    }

    private void renormalize() {
        do {
            int nextByte = position < end ? Byte.toUnsignedInt(data[position++]) : 0;
            value = (value << Byte.SIZE) | nextByte;
            length <<= Byte.SIZE;
        } while (Integer.compareUnsigned(length, MIN_LENGTH) < 0);
    }
}
