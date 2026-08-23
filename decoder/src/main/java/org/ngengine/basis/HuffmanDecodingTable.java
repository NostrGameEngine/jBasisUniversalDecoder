package org.ngengine.basis;

import java.util.Arrays;

/**
 * Canonical Huffman decode table used by BasisLZ/ETC1S bitstreams.
 */
final class HuffmanDecodingTable {
    static final int MAX_SUPPORTED_INTERNAL_CODE_SIZE = 31;
    static final int FAST_LOOKUP_BITS = 10;
    static final int MAX_SYMS_LOG2 = 14;
    static final int MAX_SYMS = 1 << MAX_SYMS_LOG2;
    static final int TOTAL_CODELENGTH_CODES = 21;
    static final int SMALL_ZERO_RUN_CODE = 17;
    static final int BIG_ZERO_RUN_CODE = 18;
    static final int SMALL_REPEAT_CODE = 19;
    static final int BIG_REPEAT_CODE = 20;
    static final int SMALL_ZERO_RUN_EXTRA_BITS = 3;
    static final int BIG_ZERO_RUN_EXTRA_BITS = 7;
    static final int SMALL_REPEAT_EXTRA_BITS = 2;
    static final int BIG_REPEAT_EXTRA_BITS = 7;
    static final int SMALL_ZERO_RUN_SIZE_MIN = 3;
    static final int BIG_ZERO_RUN_SIZE_MIN = 11;
    static final int SMALL_REPEAT_SIZE_MIN = 3;
    static final int BIG_REPEAT_SIZE_MIN = 7;
    static final int[] SORTED_CODELENGTH_CODES = {
        SMALL_ZERO_RUN_CODE,
        BIG_ZERO_RUN_CODE,
        SMALL_REPEAT_CODE,
        BIG_REPEAT_CODE,
        0,
        8,
        7,
        9,
        6,
        10,
        5,
        11,
        4,
        12,
        3,
        13,
        2,
        14,
        1,
        15,
        16
    };

    private byte[] codeSizes = new byte[0];
    private int[] lookup = new int[0];
    private short[] tree = new short[0];

    boolean init(int totalSymbols, byte[] sizes) {
        if (totalSymbols == 0) {
            clear();
            return true;
        }
        if (sizes == null || sizes.length < totalSymbols) {
            return false;
        }

        byte[] newCodeSizes = Arrays.copyOf(sizes, totalSymbols);
        int fastLookupSize = 1 << FAST_LOOKUP_BITS;
        int[] newLookup = new int[fastLookupSize];
        short[] newTree = new short[Math.max(1, totalSymbols * 2)];

        int[] symbolsUsingCodeSize = new int[MAX_SUPPORTED_INTERNAL_CODE_SIZE + 1];
        for (int i = 0; i < totalSymbols; i++) {
            int codeSize = Byte.toUnsignedInt(newCodeSizes[i]);
            if (codeSize > MAX_SUPPORTED_INTERNAL_CODE_SIZE) {
                return false;
            }
            symbolsUsingCodeSize[codeSize]++;
        }

        int[] nextCode = new int[MAX_SUPPORTED_INTERNAL_CODE_SIZE + 1];
        int usedSymbols = 0;
        int total = 0;
        for (int i = 1; i < MAX_SUPPORTED_INTERNAL_CODE_SIZE; i++) {
            usedSymbols += symbolsUsingCodeSize[i];
            total = (total + symbolsUsingCodeSize[i]) << 1;
            nextCode[i + 1] = total;
        }

        if (((1 << MAX_SUPPORTED_INTERNAL_CODE_SIZE) != total) && usedSymbols != 1) {
            return false;
        }

        int treeNext = -1;
        for (int symbolIndex = 0; symbolIndex < totalSymbols; symbolIndex++) {
            int codeSize = Byte.toUnsignedInt(newCodeSizes[symbolIndex]);
            if (codeSize == 0) {
                continue;
            }

            int currentCode = nextCode[codeSize]++;
            int reversedCode = reverseCode(currentCode, codeSize);
            if (codeSize <= FAST_LOOKUP_BITS) {
                int lookupValue = (codeSize << 16) | symbolIndex;
                while (reversedCode < fastLookupSize) {
                    if (newLookup[reversedCode] != 0) {
                        return false;
                    }
                    newLookup[reversedCode] = lookupValue;
                    reversedCode += 1 << codeSize;
                }
            } else {
                TreeBuildResult result = insertLongCode(
                        newLookup,
                        newTree,
                        treeNext,
                        reversedCode,
                        codeSize,
                        symbolIndex);
                if (!result.success) {
                    return false;
                }
                treeNext = result.nextTreeIndex;
                newTree = result.tree;
            }
        }

        codeSizes = newCodeSizes;
        lookup = newLookup;
        tree = newTree;
        return true;
    }

    void clear() {
        codeSizes = new byte[0];
        lookup = new int[0];
        tree = new short[0];
    }

    boolean isValid() {
        return codeSizes.length > 0;
    }

    int getSymbolCount() {
        return codeSizes.length;
    }

    int[] getLookup() {
        return lookup;
    }

    short[] getTree() {
        return tree;
    }

    private static int reverseCode(int currentCode, int codeSize) {
        int reversedCode = 0;
        for (int bit = codeSize; bit > 0; bit--, currentCode >>>= 1) {
            reversedCode = (reversedCode << 1) | (currentCode & 1);
        }
        return reversedCode;
    }

    private static TreeBuildResult insertLongCode(
            int[] lookupTable,
            short[] currentTree,
            int treeNext,
            int reversedCode,
            int codeSize,
            int symbolIndex) {
        int lookupIndex = reversedCode & ((1 << FAST_LOOKUP_BITS) - 1);
        int treeCursor = lookupTable[lookupIndex];
        if (treeCursor == 0) {
            lookupTable[lookupIndex] = treeNext;
            treeCursor = treeNext;
            treeNext -= 2;
        }
        if (treeCursor >= 0) {
            return TreeBuildResult.failure(currentTree, treeNext);
        }

        int rev = reversedCode >>> (FAST_LOOKUP_BITS - 1);
        short[] tree = currentTree;
        for (int j = codeSize; j > FAST_LOOKUP_BITS + 1; j--) {
            treeCursor -= (rev >>>= 1) & 1;
            int idx = -treeCursor - 1;
            tree = ensureTreeSize(tree, idx + 1);
            if (tree[idx] == 0) {
                tree[idx] = (short) treeNext;
                treeCursor = treeNext;
                treeNext -= 2;
            } else {
                treeCursor = tree[idx];
                if (treeCursor >= 0) {
                    return TreeBuildResult.failure(tree, treeNext);
                }
            }
        }

        treeCursor -= (rev >>>= 1) & 1;
        int idx = -treeCursor - 1;
        tree = ensureTreeSize(tree, idx + 1);
        if (tree[idx] != 0) {
            return TreeBuildResult.failure(tree, treeNext);
        }
        tree[idx] = (short) symbolIndex;
        return TreeBuildResult.success(tree, treeNext);
    }

    private static short[] ensureTreeSize(short[] values, int length) {
        if (values.length >= length) {
            return values;
        }
        return Arrays.copyOf(values, length);
    }

    private static final class TreeBuildResult {
        private final boolean success;
        private final short[] tree;
        private final int nextTreeIndex;

        private TreeBuildResult(boolean success, short[] tree, int nextTreeIndex) {
            this.success = success;
            this.tree = tree;
            this.nextTreeIndex = nextTreeIndex;
        }

        private static TreeBuildResult success(short[] tree, int nextTreeIndex) {
            return new TreeBuildResult(true, tree, nextTreeIndex);
        }

        private static TreeBuildResult failure(short[] tree, int nextTreeIndex) {
            return new TreeBuildResult(false, tree, nextTreeIndex);
        }
    }
}
