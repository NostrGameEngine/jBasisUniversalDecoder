package org.ngengine.basis;

import org.junit.jupiter.api.Test;

public class TestNativeGoldParityGap {

    @Test
    public void strictNativeGoldVerifierPassesWithoutRemainingXuastcZstdGap() {
        ParityVerifier.main(new String[0]);
    }
}
