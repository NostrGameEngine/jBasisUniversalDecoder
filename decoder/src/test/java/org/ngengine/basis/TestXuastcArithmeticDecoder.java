package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestXuastcArithmeticDecoder {

    @Test
    public void decodesRawBitsFromArithmeticStream() {
        XuastcArithmeticDecoder decoder = new XuastcArithmeticDecoder(zeroStream(), 0, zeroStream().length);

        assertEquals(0, decoder.getBits(5));
        assertEquals(0, decoder.getBit());
    }

    @Test
    public void decodesAdaptiveBitAndSymbolModels() {
        XuastcArithmeticDecoder decoder = new XuastcArithmeticDecoder(zeroStream(), 0, zeroStream().length);
        XuastcArithmeticBitModel bitModel = new XuastcArithmeticBitModel();
        XuastcArithmeticDataModel symbolModel = new XuastcArithmeticDataModel(4, true);

        assertEquals(0, decoder.decodeBit(bitModel));
        assertEquals(0, decoder.decodeSymbol(symbolModel));
        assertEquals(2, bitModel.bit0Count);
        assertEquals(2, symbolModel.symbolFrequencies[0]);
    }

    @Test
    public void decodesGammaAndRiceValues() {
        XuastcArithmeticDecoder decoder = new XuastcArithmeticDecoder(zeroStream(), 0, zeroStream().length);

        assertEquals(1, decoder.decodeGamma(new XuastcArithmeticGammaContext()));
        assertEquals(0, decoder.decodeRice(2));
    }

    @Test
    public void rejectsTooSmallArithmeticStream() {
        byte[] payload = new byte[4];

        assertThrows(BasisDecodeException.class,
                () -> new XuastcArithmeticDecoder(payload, 0, payload.length));
    }

    private static byte[] zeroStream() {
        return new byte[] {0, 0, 0, 0, 0, 0, 0, 0};
    }
}
