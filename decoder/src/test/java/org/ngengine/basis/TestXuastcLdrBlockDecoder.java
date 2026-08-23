package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestXuastcLdrBlockDecoder {

    @Test
    public void upsampleWeightsUsesXuastcLdrBilinearRules() {
        int[] weights = {
                0, 16,
                48, 64
        };

        int[] upsampled = XuastcLdrBlockDecoder.upsampleWeights(4, 4, 2, 2, weights);

        assertArrayEquals(new int[] {
                0, 5, 11, 16,
                15, 20, 26, 31,
                33, 38, 44, 49,
                48, 53, 59, 64
        }, upsampled);
    }

    @Test
    public void upsampleWeightsReturnsDefensiveIdentityCopy() {
        int[] weights = {
                0, 16,
                48, 64
        };

        int[] sameSize = XuastcLdrBlockDecoder.upsampleWeights(2, 2, 2, 2, weights);
        sameSize[0] = 99;

        assertArrayEquals(new int[] {
                0, 16,
                48, 64
        }, weights);
    }

    @Test
    public void upsampleWeightsRejectsInvalidDimensions() {
        assertThrows(BasisDecodeException.class,
                () -> XuastcLdrBlockDecoder.upsampleWeights(4, 4, 2, 2, new int[3]));
    }
}
