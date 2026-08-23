package org.ngengine.basis;

/**
 * Arithmetic DCT symbols for one XUASTC LDR weight plane.
 */
final class XuastcLdrDctWeightPlane {
    private final int dcSymbol;
    private final int dcLevels;
    private final int[] zeroRuns;
    private final int[] coefficients;

    XuastcLdrDctWeightPlane(int dcSymbol, int dcLevels, int[] zeroRuns, int[] coefficients) {
        this.dcSymbol = dcSymbol;
        this.dcLevels = dcLevels;
        this.zeroRuns = zeroRuns.clone();
        this.coefficients = coefficients.clone();
    }

    int getDcSymbol() {
        return dcSymbol;
    }

    int getDcLevels() {
        return dcLevels;
    }

    int[] getZeroRuns() {
        return zeroRuns.clone();
    }

    int[] getCoefficients() {
        return coefficients.clone();
    }
}
