package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

public class TestXuastcAstcPartitioner {
    @Test
    public void computesFiveByFourTwoPartitionMapForNativeSeed() {
        int[] partitions = XuastcAstcPartitioner.computePartitionMap(5, 4, 16, 2);

        assertArrayEquals(new int[] {
                1, 0, 0, 0, 0,
                1, 0, 0, 0, 0,
                1, 0, 0, 0, 0,
                1, 0, 0, 0, 0
        }, partitions);
    }
}
