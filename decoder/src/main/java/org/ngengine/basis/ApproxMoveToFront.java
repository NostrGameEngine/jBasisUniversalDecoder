package org.ngengine.basis;

/**
 * Small approximate move-to-front history used by ETC1S selector decoding.
 */
final class ApproxMoveToFront {
    private final int[] values;
    private int rover;

    ApproxMoveToFront(int size) {
        if (size <= 0) {
            throw new BasisDecodeException("Move-to-front history size must be positive");
        }
        values = new int[size];
        rover = size / 2;
    }

    int size() {
        return values.length;
    }

    int get(int index) {
        return values[index];
    }

    void add(int value) {
        values[rover++] = value;
        if (rover == values.length) {
            rover = values.length / 2;
        }
    }

    void use(int index) {
        if (index != 0) {
            int parent = index / 2;
            int previous = values[parent];
            values[parent] = values[index];
            values[index] = previous;
        }
    }
}
