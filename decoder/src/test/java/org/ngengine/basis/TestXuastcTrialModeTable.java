package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TestXuastcTrialModeTable {
    @Test
    public void buildsFiveByFourTrialModesFromNativeConfigTable() {
        XuastcTrialModeTable table = XuastcTrialModeTable.forBlockSize(5, 4);

        assertEquals(5, table.getBlockWidth());
        assertEquals(4, table.getBlockHeight());
        assertEquals(2944, table.size());
        assertEquals(160, table.getUsedGroupCount());

        XuastcTrialMode first = table.get(0);
        assertEquals(2, first.getGridWidth());
        assertEquals(2, first.getGridHeight());
        assertEquals(XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT, first.getColorEndpointMode());
        assertEquals(3, first.getColorComponentSelector());
        assertEquals(20, first.getEndpointIseRange());
        assertEquals(5, first.getWeightIseRange());
        assertEquals(1, first.getNumberOfPartitions());

        XuastcTrialMode last = table.get(table.size() - 1);
        assertEquals(5, last.getGridWidth());
        assertEquals(4, last.getGridHeight());
        assertEquals(XuastcAstcConstants.CEM_LDR_RGBA_DIRECT, last.getColorEndpointMode());
        assertEquals(-1, last.getColorComponentSelector());
        assertEquals(4, last.getEndpointIseRange());
        assertEquals(4, last.getWeightIseRange());
        assertEquals(2, last.getNumberOfPartitions());
    }

    @Test
    public void groupsTrialModesByDecoderTriageKeys() {
        XuastcTrialModeTable table = XuastcTrialModeTable.forBlockSize(5, 4);

        int[] candidates = table.getCandidateIndices(
                XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT,
                0,
                4,
                0,
                2);

        assertEquals(48, candidates.length);
        assertEquals(0, candidates[0]);
        assertEquals(1, candidates[1]);
        assertEquals(2, candidates[2]);
        assertEquals(121, candidates[7]);
        assertSame(table, XuastcTrialModeTable.forBlockSize(5, 4));
        assertTrue(table.getCandidateIndices(0, 2, 0, 1, 2).length >= 0);
    }
}
