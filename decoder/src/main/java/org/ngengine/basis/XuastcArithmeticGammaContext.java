package org.ngengine.basis;

/**
 * Contexts for XUASTC arithmetic gamma-coded integers.
 */
final class XuastcArithmeticGammaContext {
    static final int MAX_TAIL_CONTEXTS = 4;
    static final int MAX_PREFIX_CONTEXTS = 3;

    private final XuastcArithmeticBitModel[] prefixContexts =
            new XuastcArithmeticBitModel[MAX_PREFIX_CONTEXTS];
    private final XuastcArithmeticBitModel[] tailContexts =
            new XuastcArithmeticBitModel[MAX_TAIL_CONTEXTS];

    XuastcArithmeticGammaContext() {
        for (int i = 0; i < prefixContexts.length; i++) {
            prefixContexts[i] = new XuastcArithmeticBitModel();
        }
        for (int i = 0; i < tailContexts.length; i++) {
            tailContexts[i] = new XuastcArithmeticBitModel();
        }
    }

    XuastcArithmeticBitModel prefix(int index) {
        return prefixContexts[Math.min(index, MAX_PREFIX_CONTEXTS - 1)];
    }

    XuastcArithmeticBitModel tail(int index) {
        return tailContexts[Math.min(index, MAX_TAIL_CONTEXTS - 1)];
    }
}
