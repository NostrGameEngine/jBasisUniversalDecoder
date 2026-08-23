package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TestBc7PartitionTables {

    @Test
    public void testPartition2MapsMatchReferenceRows() {
        assertArrayEquals(
                new int[] {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1},
                Bc7PartitionTables.partition2Map(0));
        assertArrayEquals(
                new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1},
                Bc7PartitionTables.partition2Map(15));
        assertArrayEquals(
                new int[] {0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0},
                Bc7PartitionTables.partition2Map(62));
        assertArrayEquals(
                new int[] {0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 1, 1, 1},
                Bc7PartitionTables.partition2Map(63));
    }

    @Test
    public void testPartition3MapsMatchReferenceRows() {
        assertArrayEquals(
                new int[] {0, 0, 1, 1, 0, 0, 1, 1, 0, 2, 2, 1, 2, 2, 2, 2},
                Bc7PartitionTables.partition3Map(0));
        assertArrayEquals(
                new int[] {0, 0, 1, 1, 2, 0, 0, 1, 2, 2, 0, 0, 2, 2, 2, 0},
                Bc7PartitionTables.partition3Map(15));
        assertArrayEquals(
                new int[] {0, 1, 1, 1, 2, 0, 1, 1, 2, 2, 0, 1, 2, 2, 2, 0},
                Bc7PartitionTables.partition3Map(63));
    }

    @Test
    public void testPartitionMasksMatchReferenceConstruction() {
        assertEquals(0xCCCC, Bc7PartitionTables.partition2Mask(0));
        assertEquals(0xF000, Bc7PartitionTables.partition2Mask(15));
        assertEquals(0x7310008C, Bc7PartitionTables.partition3Masks(15));
        assertEquals(0x731008CE, Bc7PartitionTables.partition3Masks(63));
    }
}
