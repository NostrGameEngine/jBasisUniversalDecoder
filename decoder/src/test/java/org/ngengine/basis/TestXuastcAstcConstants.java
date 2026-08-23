package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TestXuastcAstcConstants {
    @Test
    public void mapsCemAndIseLevelTables() {
        assertEquals(XuastcAstcConstants.CEM_LDR_RGB_DIRECT,
                XuastcAstcConstants.uniqueLdrIndexToAstcCem(3));
        assertEquals(3, XuastcAstcConstants.cemToLdrCemIndex(
                XuastcAstcConstants.CEM_LDR_RGB_DIRECT));
        assertEquals(7, XuastcAstcConstants.cemToLdrCemIndex(
                XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET));
        assertEquals(6, XuastcAstcConstants.numCemEndpointValues(
                XuastcAstcConstants.CEM_LDR_RGB_DIRECT));
        assertEquals(10, XuastcAstcConstants.getIseLevels(6));
        assertEquals(559, XuastcAstcConstants.getTotalUniquePatterns(1, 2));
        assertEquals(405, XuastcAstcConstants.getTotalUniquePatterns(1, 3));
        assertTrue(XuastcAstcConstants.getScaleQuantStep(1) > 1.0f);
    }

    @Test
    public void rejectsInvalidTableLookups() {
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.uniqueLdrIndexToAstcCem(99));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.cemToLdrCemIndex(99));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.numCemEndpointValues(99));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.getIseLevels(99));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.getTotalUniquePatterns(99, 2));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.getScaleQuantStep(99));
    }

    @Test
    public void dequantizesEndpointRanges() {
        assertEquals(255, XuastcAstcConstants.dequantBiseEndpoint(7, 5));
        assertEquals(255, XuastcAstcConstants.dequantBiseEndpoint(15, 8));
        assertEquals(255, XuastcAstcConstants.dequantBiseEndpoint(31, 11));
        assertEquals(255, XuastcAstcConstants.dequantBiseEndpoint(63, 14));
        assertEquals(255, XuastcAstcConstants.dequantBiseEndpoint(127, 17));
        assertEquals(200, XuastcAstcConstants.dequantBiseEndpoint(200, 20));

        for (int range = 4; range <= 19; range++) {
            int levels = XuastcAstcConstants.getIseLevels(range);
            int value = XuastcAstcConstants.dequantBiseEndpoint(levels - 1, range);
            assertTrue(value >= 0 && value <= 255);
        }
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.dequantBiseEndpoint(999, 12));
    }

    @Test
    public void decodesLdrEndpointModes() {
        assertArrayEquals(new int[] {10, 10, 10, 255, 20, 20, 20, 255},
                XuastcAstcConstants.decodeLdrEndpoints(
                        XuastcAstcConstants.CEM_LDR_LUM_DIRECT,
                        new int[] {10, 20},
                        0,
                        20));
        assertArrayEquals(new int[] {10, 10, 10, 30, 20, 20, 20, 40},
                XuastcAstcConstants.decodeLdrEndpoints(
                        XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT,
                        new int[] {10, 20, 30, 40},
                        0,
                        20));
        assertArrayEquals(new int[] {64, 32, 16, 255, 128, 64, 32, 255},
                XuastcAstcConstants.decodeLdrEndpoints(
                        XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE,
                        new int[] {128, 64, 32, 128},
                        0,
                        20));
        assertArrayEquals(new int[] {50, 60, 70, 255, 55, 70, 85, 255},
                XuastcAstcConstants.decodeLdrEndpoints(
                        XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET,
                        new int[] {100, 10, 120, 20, 140, 30},
                        0,
                        20));
        assertArrayEquals(new int[] {16, 32, 48, 64, 80, 96, 112, 128},
                XuastcAstcConstants.decodeLdrEndpoints(
                        XuastcAstcConstants.CEM_LDR_RGBA_DIRECT,
                        new int[] {16, 80, 32, 96, 48, 112, 64, 128},
                        0,
                        20));
    }

    @Test
    public void dequantizesWeightRangesAndNearestIse() {
        assertEquals(64, XuastcAstcConstants.dequantBiseWeight(1, 0));
        assertEquals(32, XuastcAstcConstants.dequantBiseWeight(1, 1));
        assertEquals(64, XuastcAstcConstants.dequantBiseWeight(3, 2));
        assertEquals(48, XuastcAstcConstants.dequantBiseWeight(3, 3));
        assertEquals(64, XuastcAstcConstants.dequantBiseWeight(7, 5));
        assertEquals(64, XuastcAstcConstants.dequantBiseWeight(15, 8));
        assertEquals(64, XuastcAstcConstants.dequantBiseWeight(31, 11));

        for (int range = 4; range <= 10; range++) {
            int levels = XuastcAstcConstants.getIseLevels(range);
            int value = XuastcAstcConstants.dequantBiseWeight(levels - 1, range);
            assertTrue(value >= 0 && value <= 64);
        }
        assertEquals(1, XuastcAstcConstants.nearestWeightIse(30, 1));
        assertEquals(2, XuastcAstcConstants.nearestWeightIse(64, 1));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.dequantBiseWeight(999, 1));
    }

    @Test
    public void mapsWeightIseRanksUsingDequantizedOrder() {
        for (int range = XuastcAstcConstants.FIRST_VALID_WEIGHT_ISE_RANGE;
                range <= XuastcAstcConstants.LAST_VALID_WEIGHT_ISE_RANGE;
                range++) {
            int previous = -1;
            int levels = XuastcAstcConstants.getIseLevels(range);
            for (int rank = 0; rank < levels; rank++) {
                int ise = XuastcAstcConstants.weightRankToIse(rank, range);
                assertEquals(rank, XuastcAstcConstants.weightIseToRank(ise, range));
                int dequantized = XuastcAstcConstants.dequantBiseWeight(ise, range);
                assertTrue(dequantized >= previous);
                previous = dequantized;
            }
        }
    }

    @Test
    public void mapsEndpointIseRanksUsingDequantizedOrder() {
        for (int range = XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE;
                range <= XuastcAstcConstants.LAST_VALID_ENDPOINT_ISE_RANGE;
                range++) {
            int previous = -1;
            int levels = XuastcAstcConstants.getIseLevels(range);
            for (int rank = 0; rank < levels; rank++) {
                int ise = XuastcAstcConstants.endpointRankToIse(rank, range);
                assertEquals(rank, XuastcAstcConstants.endpointIseToRank(ise, range));
                int dequantized = XuastcAstcConstants.dequantBiseEndpoint(ise, range);
                assertTrue(dequantized >= previous);
                previous = dequantized;
            }
        }
    }

    @Test
    public void rejectsInvalidIseRankLookups() {
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.weightRankToIse(0, 99));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.weightRankToIse(99, 1));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.weightIseToRank(99, 1));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.endpointRankToIse(0, 99));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.endpointRankToIse(99, 4));
        assertThrows(BasisDecodeException.class,
                () -> XuastcAstcConstants.endpointIseToRank(99, 4));
    }

    @Test
    public void classifiesGridAnisotropy() {
        assertEquals(0, XuastcAstcConstants.gridAniso(5, 4, 5, 4));
        assertEquals(1, XuastcAstcConstants.gridAniso(5, 2, 5, 4));
        assertEquals(2, XuastcAstcConstants.gridAniso(2, 4, 5, 4));
    }
}
