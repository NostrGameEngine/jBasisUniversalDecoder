package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class TestKtx2NativeParityStubs {

    @Test
    public void testStructureParsersRoundTrip() {
        ByteBuffer buffer = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(Ktx2Constants.KTX2_FILE_IDENTIFIER);
        buffer.putInt(12345); // vkFormat
        buffer.putInt(1); // typeSize
        buffer.putInt(16); // width
        buffer.putInt(8); // height
        buffer.putInt(0); // pixelDepth
        buffer.putInt(0); // layerCount
        buffer.putInt(1); // faceCount
        buffer.putInt(1); // levelCount
        buffer.putInt(0); // supercompression
        buffer.putInt(0); // dfdByteOffset
        buffer.putInt(0); // dfdByteLength
        buffer.putInt(0); // kvdByteOffset
        buffer.putInt(0); // kvdByteLength
        buffer.putLong(0L); // sgdByteOffset
        buffer.putLong(0L); // sgdByteLength

        byte[] payload = ByteBuffer.allocate(80 + 24)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(buffer.array())
                .putLong(80L)
                .putLong(16L)
                .putLong(32L)
                .array();

        Ktx2NativeParityStubs.Ktx2Header parsedHeader =
                Ktx2NativeParityStubs.Ktx2Header.parse(payload, 0);
        assertEquals(16, parsedHeader.pixelWidth);
        assertEquals(8, parsedHeader.pixelHeight);
        assertEquals(1, parsedHeader.levelCount);

        Ktx2NativeParityStubs.Ktx2LevelIndex levelIndex =
                Ktx2NativeParityStubs.Ktx2LevelIndex.parse(payload, Ktx2Constants.KTX2_HEADER_SIZE);
        assertEquals(80L, levelIndex.byteOffset);
        assertEquals(16L, levelIndex.byteLength);

        Ktx2NativeParityStubs.Ktx2Etc1sGlobalDataHeader globalHeader =
                Ktx2NativeParityStubs.Ktx2Etc1sGlobalDataHeader.parse(
                        new byte[] {2, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                        0);
        assertEquals(2, globalHeader.endpointCount);
        assertEquals(2, globalHeader.selectorCount);

        Ktx2NativeParityStubs.Ktx2Etc1sImageDesc imageDesc =
                Ktx2NativeParityStubs.Ktx2Etc1sImageDesc.parse(
                        new byte[] {1, 0, 0, 0, 4, 0, 0, 0, 8, 0, 0, 0, 0, 1, 0, 0, 0, 16, 0, 0, 0},
                        0);
        assertEquals(1L, imageDesc.imageFlags);
        assertEquals(4L, imageDesc.rgbSliceByteOffset);

        Ktx2NativeParityStubs.Ktx2SliceOffsetLenDescStd stdDesc =
                Ktx2NativeParityStubs.Ktx2SliceOffsetLenDescStd.parse(
                        new byte[] {4, 0, 0, 0, 16, 0, 0, 0, 2, 0, 0, 0},
                        0);
        assertEquals(4L, stdDesc.sliceByteOffset);
        assertEquals(16L, stdDesc.sliceByteLength);

        Ktx2NativeParityStubs.Ktx2AnimData animData =
                Ktx2NativeParityStubs.Ktx2AnimData.parse(
                        new byte[] {7, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0},
                        0);
        assertEquals(7L, animData.duration);
        assertEquals(256L, animData.timescale);
    }

    @Test
    public void testBasisuTranscoderFacadeWrapsCoreHelpers() {
        ByteBuffer buffer = ByteBuffer.allocate(80 + 24).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(Ktx2Constants.KTX2_FILE_IDENTIFIER);
        buffer.putInt(0); // vkFormat
        buffer.putInt(1); // typeSize
        buffer.putInt(4); // width
        buffer.putInt(4); // height
        buffer.putInt(0); // pixelDepth
        buffer.putInt(1); // layerCount
        buffer.putInt(1); // faceCount
        buffer.putInt(1); // levelCount
        buffer.putInt(0); // supercompression
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putLong(0L);
        buffer.putLong(0L);
        // one level table entry (24-byte record)
        buffer.putLong(80L);
        buffer.putLong(16L);
        buffer.putLong(16L);

        byte[] payload = buffer.array();

        Ktx2NativeParityStubs.BasisuTranscoder transcoder = new Ktx2NativeParityStubs.BasisuTranscoder();
        assertTrue(transcoder.init(payload, payload.length));
        assertEquals(4, transcoder.getWidth());
        assertEquals(4, transcoder.getHeight());
        assertEquals(1, transcoder.getLevels());

        assertTrue(transcoder.validateHeader());
        assertTrue(transcoder.validateFileChecksums(false));
        assertEquals(Ktx2BasisTextureType.cBASISTexType2D, transcoder.getTextureType());
        assertEquals(Ktx2BasisTextureFormat.cUASTC_LDR_4x4, transcoder.getBasisTexFormat());
    }


    @Test
    public void testBasisuLevelIndexParseHonorsActualLevelCount() {
        ByteBuffer buffer = ByteBuffer.allocate(80 + 24).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(Ktx2Constants.KTX2_FILE_IDENTIFIER);
        buffer.putInt(0);
        buffer.putInt(1);
        buffer.putInt(4);
        buffer.putInt(4);
        buffer.putInt(0);
        buffer.putInt(1);
        buffer.putInt(1);
        buffer.putInt(1);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putLong(0L);
        buffer.putLong(0L);
        buffer.putLong(80L);
        buffer.putLong(16L);
        buffer.putLong(16L);

        byte[] payload = buffer.array();
        Ktx2NativeParityStubs.Ktx2Header parsedHeader = Ktx2NativeParityStubs.Ktx2Header.parse(payload, 0);
        assertEquals(1, parsedHeader.levelCount);

        Ktx2NativeParityStubs.BasisuTranscoder transcoder = new Ktx2NativeParityStubs.BasisuTranscoder();
        assertTrue(transcoder.init(payload, payload.length));
        assertEquals(1, transcoder.getLevelIndex().length);
        assertEquals(80L, transcoder.getLevelIndex()[0].byteOffset);

        byte[] truncated = java.util.Arrays.copyOf(payload, 100);
        Ktx2NativeParityStubs.BasisuTranscoder truncatedTranscoder =
                new Ktx2NativeParityStubs.BasisuTranscoder();
        assertTrue(truncatedTranscoder.init(truncated, truncated.length));
        assertEquals(0, truncatedTranscoder.getLevelIndex().length);
    }

    @Test
    public void testByteLevelHelpers() {
        byte[] src = new byte[] {10, 20, 30, 40};
        byte[] dst = new byte[] {0, 0, 0, 0};
        int copied = Ktx2NativeParityStubs.memcpy(dst, 0, src, 1, 2);
        assertEquals(2, copied);
        assertEquals(20, dst[0]);
        assertEquals(30, dst[1]);
        assertEquals(0, dst[2]);
    }

    @Test
    public void testKeyValueParserAndDecoders() {
        ByteBuffer payload = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        byte[] entry = "foo\0bar\0".getBytes(StandardCharsets.UTF_8);
        payload.putInt(entry.length);
        payload.put(entry);
        int align = (4 - (entry.length & 3)) & 3;
        payload.put(new byte[align]);

        int used = payload.position();
        assertTrue(Ktx2NativeParityStubs.readKeyValues(payload.array(), used));
        assertFalse(Ktx2NativeParityStubs.readKeyValues(new byte[] {0, 0, 0}, 3));

        ByteBuffer zeroLen = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        zeroLen.putInt(0);
        zeroLen.put(new byte[] {1, 0, 0, 0});
        assertFalse(Ktx2NativeParityStubs.readKeyValues(zeroLen.array(), zeroLen.position()));

        byte[] compressed = new byte[] {1, 2, 3, 4, 5};
        byte[] decoded = new byte[3];
        assertEquals(
                3,
                Ktx2NativeParityStubs.decompressEtc1sGlobalData(
                        compressed,
                        3,
                        decoded,
                        decoded.length));
        assertEquals(-1, Ktx2NativeParityStubs.decompressEtc1sGlobalData(compressed, 3, decoded, 2));

        byte[] raw = new byte[128];
        assertTrue(Ktx2NativeParityStubs.decodeTables(raw, raw.length));
        assertFalse(Ktx2NativeParityStubs.decodeTables(null, 0));

        ByteBuffer dummyHeader = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN);
        dummyHeader.put(Ktx2Constants.KTX2_FILE_IDENTIFIER);
        dummyHeader.putInt(0); // vkFormat
        dummyHeader.putInt(1); // typeSize
        dummyHeader.putInt(4); // width
        dummyHeader.putInt(4); // height
        dummyHeader.putInt(1); // pixelDepth
        dummyHeader.putInt(2); // layerCount
        dummyHeader.putInt(1); // faceCount
        dummyHeader.putInt(1); // levelCount
        dummyHeader.putInt(0); // supercompression
        dummyHeader.putInt(0);
        dummyHeader.putInt(0);
        dummyHeader.putInt(0);
        dummyHeader.putInt(0);
        dummyHeader.putLong(0L);
        dummyHeader.putLong(24L);

        byte[] stdGlobalData = new byte[] {
                // 2 image records (12 bytes each = 24 total)
                0, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0,
                12, 0, 0, 0, 8, 0, 0, 0, 2, 0, 0, 0
        };
        assertEquals(24, Ktx2NativeParityStubs.readSliceOffsetLenGlobalData(
                Ktx2NativeParityStubs.Ktx2Header.parse(dummyHeader.array(), 0),
                stdGlobalData,
                0));

        byte[] invalidGlobalData = new byte[] {
                // first entry length is zero -> invalid
                0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0,
                12, 0, 0, 0, 8, 0, 0, 0, 2, 0, 0, 0
        };
        assertEquals(-1, Ktx2NativeParityStubs.readSliceOffsetLenGlobalData(
                Ktx2NativeParityStubs.Ktx2Header.parse(dummyHeader.array(), 0),
                invalidGlobalData,
                0));

        byte[] tooShortHeaderBytes = dummyHeader.array().clone();
        java.nio.ByteBuffer.wrap(tooShortHeaderBytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putLong(72, 8L);
        byte[] tooShortPayload = new byte[] {
                // one orig descriptor but header says imageCount = 2, so this is invalid
                1, 0, 0, 0, 2, 0, 0, 0
        };
        assertEquals(-1, Ktx2NativeParityStubs.readSliceOffsetLenGlobalData(
                Ktx2NativeParityStubs.Ktx2Header.parse(tooShortHeaderBytes, 0),
                tooShortPayload,
                0));

        byte[] headerWithOffsetMismatch = dummyHeader.array().clone();
        java.nio.ByteBuffer.wrap(headerWithOffsetMismatch)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putLong(64, 1L);

        assertEquals(-1, Ktx2NativeParityStubs.readSliceOffsetLenGlobalData(
                Ktx2NativeParityStubs.Ktx2Header.parse(headerWithOffsetMismatch, 0),
                stdGlobalData,
                0));

        Ktx2NativeParityStubs.Ktx2SliceOffsetLenDescOrig orig =
                Ktx2NativeParityStubs.Ktx2SliceOffsetLenDescOrig.parse(
                        new byte[] {1, 0, 0, 0, 2, 0, 0, 0},
                        0);
        assertEquals(1L, orig.sliceByteOffset);
        assertEquals(2L, orig.sliceByteLength);
    }


    @Test
    public void testLowlevelTranscodersCoverAllBranches() {
        Ktx2NativeParityStubs.BasisuLowlevelEtc1sTranscoder etc1s =
                new Ktx2NativeParityStubs.BasisuLowlevelEtc1sTranscoder();
        assertFalse(etc1s.decodePalettes(0, new byte[4], 0, 0, new byte[4], 0));
        assertFalse(etc1s.decodePalettes(4, new byte[4], -1, 4, new byte[4], 4));
        assertTrue(etc1s.decodePalettes(4, new byte[4], 4, 4, new byte[4], 4));
        assertTrue(etc1s.decodeTables(new byte[16], 16));
        assertTrue(etc1s.hasTableData());
        assertFalse(etc1s.decodeTables(new byte[8], 16));
        assertFalse(etc1s.decodeTables(new byte[4], -1));
        assertFalse(etc1s.hasTableData());

        assertTrue(etc1s.hasPaletteData());
        assertEquals(4, etc1s.getLastEndpointCount());
        assertEquals(4, etc1s.getLastSelectorCount());
        assertEquals(4, etc1s.getStoredEndpointBytes());
        assertEquals(4, etc1s.getStoredSelectorBytes());

        etc1s.clear();
        assertFalse(etc1s.hasTableData());
        assertFalse(etc1s.hasPaletteData());
        assertEquals(0, etc1s.getLastEndpointCount());
        assertEquals(0, etc1s.getLastSelectorCount());

        Ktx2NativeParityStubs.BasisuLowlevelUastcLdr4x4Transcoder uastcLdr4x4 =
                new Ktx2NativeParityStubs.BasisuLowlevelUastcLdr4x4Transcoder();
        assertFalse(uastcLdr4x4.transcodeSlice(new byte[4], 0, new byte[0], 0, 0));
        assertFalse(uastcLdr4x4.transcodeSlice(new byte[4], 4, new byte[3], 4, 0));
        assertFalse(uastcLdr4x4.transcodeSlice(new byte[4], 4, new byte[4], 4, 0x400000));
        assertTrue(uastcLdr4x4.transcodeSlice(new byte[4], 4, new byte[4], 4, 0));
        uastcLdr4x4.markPlaneDecoded(2);
        assertEquals(2, uastcLdr4x4.getDecodedPlaneCount());
        uastcLdr4x4.clear();
        assertEquals(0, uastcLdr4x4.getDecodedPlaneCount());

        Ktx2NativeParityStubs.BasisuLowlevelXuastcLdrTranscoder xuastcLdr =
                new Ktx2NativeParityStubs.BasisuLowlevelXuastcLdrTranscoder();
        assertFalse(xuastcLdr.transcodeSlice(new byte[4], 4, new byte[4], 4, 0x00100000));
        assertTrue(xuastcLdr.transcodeSlice(new byte[4], 4, new byte[4], 4, 0));
        xuastcLdr.markPlaneDecoded(0);
        assertEquals(2, xuastcLdr.getDecodedPlaneCount());
        xuastcLdr.clear();
        assertEquals(0, xuastcLdr.getDecodedPlaneCount());

        Ktx2NativeParityStubs.BasisuLowlevelUastcHdr4x4Transcoder uastcHdr4x4 =
                new Ktx2NativeParityStubs.BasisuLowlevelUastcHdr4x4Transcoder();
        assertFalse(uastcHdr4x4.transcodeSlice(new byte[4], 1, new byte[1], 1, -1));
        assertTrue(uastcHdr4x4.transcodeSlice(new byte[4], 4, new byte[4], 4, 0));

        Ktx2NativeParityStubs.BasisuLowlevelAstcHdr6x6Transcoder astcHdr6x6 =
                new Ktx2NativeParityStubs.BasisuLowlevelAstcHdr6x6Transcoder();
        assertFalse(astcHdr6x6.transcodeSlice(new byte[4], 4, new byte[4], 4, 0x200000));
        assertTrue(astcHdr6x6.transcodeSlice(new byte[4], 4, new byte[4], 4, 0));

        Ktx2NativeParityStubs.BasisuLowlevelUastcHdr6x6IntermediateTranscoder uastcHdr6x6Intermediate =
                new Ktx2NativeParityStubs.BasisuLowlevelUastcHdr6x6IntermediateTranscoder();
        assertFalse(uastcHdr6x6Intermediate.transcodeSlice(new byte[4], 4, new byte[4], 4, 0x200000));
        assertTrue(uastcHdr6x6Intermediate.transcodeSlice(new byte[4], 4, new byte[4], 4, 0));

        Ktx2NativeParityStubs.BasisuTranscoderState state = new Ktx2NativeParityStubs.BasisuTranscoderState();
        assertEquals(0, state.getPendingFrameCount());
        state.addPendingFrame(7);
        state.addPendingFrame(8);
        assertEquals(2, state.getPendingFrameCount());
        assertEquals(7, state.getPendingFrame(0));
        state.clearPendingFrames();
        assertEquals(0, state.getPendingFrameCount());
        state.destroy();

        Ktx2NativeParityStubs.BlockPreds blockPreds = new Ktx2NativeParityStubs.BlockPreds();
        blockPreds.addPredicted((byte) 1);
        blockPreds.addPredicted((byte) 2);
        assertEquals(2, blockPreds.blockPredictions.size());
    }

    @Test
    public void testBasisuTranscoderFacadeNullAndInvalidInputs() {
        Ktx2NativeParityStubs.BasisuTranscoder transcoder = new Ktx2NativeParityStubs.BasisuTranscoder();
        assertThrows(NullPointerException.class, () -> transcoder.init(null, 0));

        final byte[] malformed = new byte[64];
        assertFalse(transcoder.startTranscoding());
        assertFalse(transcoder.stopTranscoding());
        assertEquals(-1, transcoder.findSlice(0, 0, false));
        assertEquals(-1, transcoder.findFirstSliceIndex(0, false));

        assertThrows(IllegalArgumentException.class, () -> transcoder.init(malformed, malformed.length));
        assertEquals(0, transcoder.getWidth());
        assertEquals(0, transcoder.getHeight());
        assertEquals(0, transcoder.getLevels());
        assertEquals(0, transcoder.getLayers());
        assertEquals(0, transcoder.getFaces());
        assertNull(transcoder.getHeader());
        assertEquals(0, transcoder.getTotalImages());
        assertFalse(transcoder.validateHeader());
        assertFalse(transcoder.validateFileChecksums(false));
        assertEquals(0, transcoder.getUserData()[0]);

        assertNotNull(transcoder.getData());
        assertEquals(64, transcoder.getDataSize());
        transcoder.clear();
        assertNull(transcoder.getData());
        assertEquals(0, transcoder.getDataSize());
    }
}
