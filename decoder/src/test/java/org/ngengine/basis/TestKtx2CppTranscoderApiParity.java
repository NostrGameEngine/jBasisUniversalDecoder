package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestKtx2CppTranscoderApiParity {

    @Test
    void fromCodeMapsKnownEnums() {
        assertEquals(Ktx2TranscoderTextureFormat.cTFETC1_RGB,
                Ktx2TranscoderTextureFormat.fromCode(0));
        assertEquals(Ktx2TranscoderTextureFormat.cTFBC7_RGBA,
                Ktx2TranscoderTextureFormat.fromCode(6));
        assertEquals(Ktx2TranscoderTextureFormat.cTFRGB_9E5,
                Ktx2TranscoderTextureFormat.fromCode(26));
    }

    @Test
    void blockFormatAndTextureTypeNameHelpers() {
        assertEquals("cETC1",
                Ktx2CppTranscoderApi.basis_get_block_format_name(Ktx2TextureFormat.cETC1));
        assertEquals(Ktx2BasisTextureType.cBASISTexTypeCubemapArray,
                Ktx2BasisTextureType.fromCode(2));
        assertEquals("cBASISTexTypeCubemapArray",
                Ktx2CppTranscoderApi.basis_get_texture_type_name(
                        Ktx2BasisTextureType.cBASISTexTypeCubemapArray));
        assertTrue(Ktx2CppTranscoderApi.basisu_transcoder_supports_ktx2());
        assertTrue(Ktx2CppTranscoderApi.basisu_transcoder_supports_ktx2_zstd());
    }

    @Test
    void headerAndSliceStructParsingMatchesRawLayout() {
        byte[] bytes = new byte[77];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0x42);
        buffer.put((byte) 0x73);
        buffer.putShort((short) 0x0010);
        buffer.putShort((short) 76);
        buffer.putShort((short) 0xABCD);
        buffer.putInt(1234);
        buffer.putShort((short) 0x2222);
        buffer.put(new byte[] {0x05, 0x00, 0x00});
        buffer.put(new byte[] {0x07, 0x00, 0x00});
        buffer.put((byte) Ktx2BasisTextureFormat.cUASTC_LDR_4x4.getCode());
        buffer.putShort((short) 0);
        buffer.put((byte) Ktx2BasisTextureType.cBASISTexType2D.getCode());
        buffer.put(new byte[] {(byte) 0x03, (byte) 0x00, (byte) 0x00});
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putShort((short) 2);
        buffer.putInt(99);
        buffer.put(new byte[] {0x01, (byte) 0x00, (byte) 0x00});
        buffer.putShort((short) 3);
        buffer.putInt(111);
        buffer.put(new byte[] {0x02, 0x00, 0x00});
        buffer.putInt(33);
        buffer.putInt(44);
        buffer.putInt(22);
        buffer.putInt(55);
        buffer.putInt(66);

        Ktx2BasisFileHeader header = Ktx2BasisFileHeader.parse(ByteBuffer.wrap(bytes), 0);
        assertEquals(0x7342, header.getSignature());
        assertEquals(0x10, header.getVersion());
        assertEquals(76, header.getHeaderSize());
        assertEquals(0xABCD, header.getHeaderCrc16());
        assertEquals(1234, header.getDataSize());
        assertEquals(0x2222, header.getDataCrc16());
        assertEquals(5, header.getTotalSlices());
        assertEquals(7, header.getTotalImages());
        assertEquals(Ktx2BasisTextureFormat.cUASTC_LDR_4x4.getCode(), header.getTextureFormat());
        assertEquals(0, header.getFlags());
        assertEquals(Ktx2BasisTextureType.cBASISTexType2D.getCode(), header.getTextureType());
        assertEquals(3, header.getUsPerFrame());
        assertEquals(99, header.getEndpointCodebookFileOffset());
        assertEquals(2, header.getTotalEndpoints());
        assertEquals(111, header.getSelectorCodebookFileOffset());
        assertEquals(3, header.getTotalSelectors());
        assertEquals(33, header.getTablesFileOffset());
        assertEquals(44, header.getTablesFileSize());
        assertEquals(22, header.getSliceDescFileOffset());
        assertEquals(55, header.getExtendedDataOffset());
        assertEquals(66, header.getExtendedDataSize());
    }

    @Test
    void constantsAndNativeApiSurfaceParity() {
        assertEquals(0, Ktx2Constants.KTX2_VK_FORMAT_UNDEFINED);
        assertEquals(Ktx2Constants.KTX2_FORMAT_ASTC_4x4_SFLOAT_BLOCK, 1000066000);
        assertEquals(Ktx2Constants.KTX2_FORMAT_ASTC_5x4_SFLOAT_BLOCK, 1000066001);
        assertEquals(Ktx2Constants.KTX2_FORMAT_ASTC_5x5_SFLOAT_BLOCK, 1000066002);
        assertEquals(Ktx2Constants.KTX2_FORMAT_ASTC_6x5_SFLOAT_BLOCK, 1000066003);
        assertEquals(Ktx2Constants.KTX2_FORMAT_ASTC_6x6_SFLOAT_BLOCK, 1000066004);
        assertEquals(Ktx2Constants.KTX2_FORMAT_ASTC_8x5_SFLOAT_BLOCK, 1000066005);
        assertEquals(Ktx2Constants.KTX2_FORMAT_ASTC_8x6_SFLOAT_BLOCK, 1000066006);

        assertEquals(210, Ktx2CppTranscoderApi.basis_get_version());

        final boolean previousDebugState = Ktx2CppTranscoderApi.basis_get_debug_printf_enabled();
        Ktx2CppTranscoderApi.basis_enable_debug_printf(1);
        assertTrue(Ktx2CppTranscoderApi.basis_get_debug_printf_enabled());
        Ktx2CppTranscoderApi.basis_enable_debug_printf(0);
        assertFalse(Ktx2CppTranscoderApi.basis_get_debug_printf_enabled());
        Ktx2CppTranscoderApi.basis_enable_debug_printf(previousDebugState ? 1 : 0);

        long zeroAlloc = Ktx2CppTranscoderApi.basis_alloc(0);
        long oneAlloc = Ktx2CppTranscoderApi.basis_alloc(1);
        assertNotEquals(0L, zeroAlloc);
        assertNotEquals(0L, oneAlloc);
        assertNotEquals(zeroAlloc, oneAlloc);

        Ktx2CppTranscoderApi.basis_free(zeroAlloc);
        Ktx2CppTranscoderApi.basis_free(oneAlloc);

        assertThrows(IllegalArgumentException.class,
                () -> Ktx2CppTranscoderApi.basis_alloc(-1));
        assertThrows(IllegalArgumentException.class,
                () -> Ktx2CppTranscoderApi.basis_alloc((long) Integer.MAX_VALUE + 1));
    }

    @Test
    void flagsAndLayoutHelpersRoundTrip() {
        assertEquals(23, Ktx2BasisSliceDesc.encodedLengthBytes());
        assertEquals(EnumSet.of(
                        Ktx2BasisHeaderFlag.cBASISHeaderFlagETC1S,
                        Ktx2BasisHeaderFlag.cBASISHeaderFlagSRGB),
                Ktx2BasisHeaderFlag.fromMask(17));
        assertEquals(17,
                Ktx2BasisHeaderFlag.toMask(EnumSet.of(
                        Ktx2BasisHeaderFlag.cBASISHeaderFlagETC1S,
                        Ktx2BasisHeaderFlag.cBASISHeaderFlagSRGB)));

        assertEquals(EnumSet.of(Ktx2BasisSliceDescFlag.cSliceDescFlagsHasAlpha),
                Ktx2BasisSliceDescFlag.fromMask(1));
        assertEquals(1, Ktx2BasisSliceDescFlag.toMask(Ktx2BasisSliceDescFlag.cSliceDescFlagsHasAlpha));

        assertEquals(EnumSet.of(Ktx2BasisTextureType.cBASISTexType2DArray),
                Ktx2BasisTextureType.fromBits(2));

        assertEquals(1, Ktx2DebugFlag.toMask(Ktx2DebugFlag.cDebugFlagVisCRs));
        assertEquals(EnumSet.of(Ktx2DebugFlag.cDebugFlagVisCRs),
                Ktx2DebugFlag.fromMask(1));
        assertEquals(EnumSet.of(Ktx2DebugFlag.cDebugFlagVisCRs, Ktx2DebugFlag.cDebugFlagVisBC1Endpoints),
                Ktx2DebugFlag.fromMask(5));
        assertEquals(5, Ktx2DebugFlag.toMask(EnumSet.of(
                Ktx2DebugFlag.cDebugFlagVisCRs,
                Ktx2DebugFlag.cDebugFlagVisBC1Endpoints)));

        assertEquals(2, Ktx2DecodeFlag.cDecodeFlagsPVRTCDecodeToNextPow2.getCode());
        assertEquals(EnumSet.of(
                Ktx2DecodeFlag.cDecodeFlagsPVRTCDecodeToNextPow2,
                Ktx2DecodeFlag.cDecodeFlagsTranscodeAlphaDataToOpaqueFormats,
                Ktx2DecodeFlag.cDecodeFlagsOutputHasAlphaIndices,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding
        ), Ktx2DecodeFlag.fromMask(2 | 4 | 16 | 1024));
        assertEquals(2 | 4 | 16 | 1024,
                Ktx2DecodeFlag.toMask(
                        Ktx2DecodeFlag.cDecodeFlagsPVRTCDecodeToNextPow2,
                        Ktx2DecodeFlag.cDecodeFlagsTranscodeAlphaDataToOpaqueFormats,
                        Ktx2DecodeFlag.cDecodeFlagsOutputHasAlphaIndices,
                        Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding
                ));

        assertEquals(3, Ktx2DfdChannelId.KTX2_DF_CHANNEL_ETC1S_RRR.getCode());
        assertEquals(6, Ktx2DfdChannelId.KTX2_DF_CHANNEL_UASTC_RG.getCode());
        assertEquals(Ktx2DfdChannelId.KTX2_DF_CHANNEL_ETC1S_RGB, Ktx2DfdChannelId.fromCode(0));
        assertEquals(Ktx2DfdChannelId.KTX2_DF_CHANNEL_ETC1S_AAA, Ktx2DfdChannelId.fromCode(15));

        assertEquals(11, Ktx2DfdColorPrimaries.KTX2_DF_PRIMARIES_ADOBERGB.getCode());
        assertEquals(Ktx2DfdColorPrimaries.KTX2_DF_PRIMARIES_BT709, Ktx2DfdColorPrimaries.fromCode(1));
    }

    @Test
    void parseBasisSliceDescFromBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(23).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] {0x05, 0x00, 0x00});
        buffer.put((byte) 3);
        buffer.put((byte) (Ktx2BasisSliceDescFlag.cSliceDescFlagsHasAlpha.getMask()
                | Ktx2BasisSliceDescFlag.cSliceDescFlagsFrameIsIFrame.getMask()));
        buffer.putShort((short) 256);
        buffer.putShort((short) 128);
        buffer.putShort((short) 16);
        buffer.putShort((short) 8);
        buffer.putInt(1024);
        buffer.putInt(88);
        buffer.putShort((short) 0xCAFE);

        Ktx2BasisSliceDesc slice = Ktx2BasisSliceDesc.parse(buffer, 0);
        assertEquals(5, slice.getImageIndex());
        assertEquals(3, slice.getLevelIndex());
        assertEquals(3, slice.getFlags());
        assertEquals(256, slice.getOriginalWidth());
        assertEquals(128, slice.getOriginalHeight());
        assertEquals(16, slice.getNumBlocksX());
        assertEquals(8, slice.getNumBlocksY());
        assertEquals(1024, slice.getFileOffset());
        assertEquals(88, slice.getFileSize());
        assertEquals(0xCAFE, slice.getCrc16());
        assertEquals(EnumSet.of(Ktx2BasisSliceDescFlag.cSliceDescFlagsHasAlpha,
                Ktx2BasisSliceDescFlag.cSliceDescFlagsFrameIsIFrame), slice.getFlagSet());
        assertTrue(slice.hasAlpha());
        assertTrue(slice.isIFrame());
    }

    @Test
    void parseBasisSliceDescRejectsInvalidInput() {
        ByteBuffer small = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(IllegalArgumentException.class, () -> Ktx2BasisSliceDesc.parse(small, 0));
        assertThrows(IllegalArgumentException.class, () -> Ktx2BasisSliceDesc.parse(small, 3));
        assertThrows(IllegalArgumentException.class, () -> Ktx2BasisSliceDesc.parse(small, -1));
        assertThrows(NullPointerException.class, () -> Ktx2BasisSliceDesc.parse(null, 0));
    }

    @Test
    void parseBasisFileHeaderHelpersAndValidation() {
        int headerSize = Ktx2BasisFileHeader.SIZE_BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0xABCD);
        buffer.putShort((short) 3);
        buffer.putShort((short) headerSize);
        buffer.putShort((short) 0x1111);
        buffer.putInt(1024);
        buffer.putShort((short) 0x2222);
        buffer.put((byte) 0x01); // totalSlices first byte (24-bit)
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x01); // totalImages first byte
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) Ktx2BasisTextureFormat.cETC1S.getCode());
        buffer.putShort((short) (Ktx2BasisHeaderFlag.cBASISHeaderFlagETC1S.getMask()
                | Ktx2BasisHeaderFlag.cBASISHeaderFlagSRGB.getMask()));
        buffer.put((byte) Ktx2BasisTextureType.cBASISTexType2D.getCode());
        buffer.put((byte) 0x01);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putShort((short) 0); // endpointCount (16-bit in file)
        buffer.putInt(0);          // endpointCodebookFileOffset
        buffer.put((byte) 0x00);   // endpointCodebookFileSize
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.putShort((short) 0); // selectorCount
        buffer.putInt(0);          // selectorCodebookFileOffset
        buffer.put((byte) 0x00);   // selectorCodebookFileSize
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.putInt(0);          // tablesFileOffset
        buffer.putInt(0);          // tablesFileSize
        buffer.putInt(0);          // sliceDescFileOffset
        buffer.putInt(0);          // extendedDataOffset
        buffer.putInt(0);          // extendedDataSize

        Ktx2BasisFileHeader header = Ktx2BasisFileHeader.parse(buffer, 0);
        assertEquals(Ktx2BasisTextureFormat.cETC1S.getCode(), header.getTextureFormat());
        assertEquals(1, header.getTotalSlices());
        assertEquals(1, header.getTotalImages());
        assertEquals(EnumSet.of(Ktx2BasisHeaderFlag.cBASISHeaderFlagETC1S,
                Ktx2BasisHeaderFlag.cBASISHeaderFlagSRGB), header.getHeaderFlags());
        assertEquals(Ktx2BasisTextureType.cBASISTexType2D, header.getTextureTypeEnum());

        assertThrows(IllegalArgumentException.class, () -> Ktx2BasisFileHeader.parse(buffer, headerSize));
        assertThrows(
                IllegalArgumentException.class,
                () -> Ktx2BasisFileHeader.parse(ByteBuffer.allocate(1), 0));
        assertThrows(NullPointerException.class, () -> Ktx2BasisFileHeader.parse(null, 0));
    }

    @Test
    void basisToTranscoderMapping() {
        assertEquals(Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x5_RGBA,
                Ktx2CppTranscoderApi.basis_get_transcoder_texture_format_from_basis_tex_format(
                        Ktx2BasisTextureFormat.cXUASTC_LDR_10x5));
        assertEquals(Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA,
                Ktx2CppTranscoderApi.basis_get_transcoder_texture_format_from_basis_tex_format(
                        Ktx2BasisTextureFormat.cUASTC_HDR_4x4));
        assertEquals(Ktx2TranscoderTextureFormat.cTFASTC_HDR_6x6_RGBA,
                Ktx2CppTranscoderApi.basis_get_transcoder_texture_format_from_basis_tex_format(
                        Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE));
    }


    @Test
    void computeBytesAndValidation() {
        int rgbaBlockBytes = Ktx2CppTranscoderApi.basis_get_uncompressed_bytes_per_pixel(
                Ktx2TranscoderTextureFormat.cTFRGBA32);
        assertEquals(4, rgbaBlockBytes);

        int size4x4 = Ktx2CppTranscoderApi.basis_compute_transcoded_image_size_in_bytes(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA, 16, 12);
        assertEquals(12,
                size4x4 / Ktx2CppTranscoderApi.basis_get_bytes_per_block_or_pixel(
                        Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA));

        int size5x4 = Ktx2CppTranscoderApi.basis_compute_transcoded_image_size_in_bytes(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x4_RGBA, 13, 1);
        assertEquals(48,
                size5x4);

        assertTrue(Ktx2CppTranscoderApi.basis_validate_output_buffer_size(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x4_RGBA,
                3,
                13,
                1,
                1,
                1));
        assertFalse(Ktx2CppTranscoderApi.basis_validate_output_buffer_size(
                Ktx2TranscoderTextureFormat.cTFETC2_RGBA,
                1,
                13,
                1,
                1,
                1));
    }

    @Test
    void unknownCodeRejected() {
        assertThrows(IllegalArgumentException.class, () -> Ktx2TranscoderTextureFormat.fromCode(999));
    }

    @Test
    void textureAndBasisFormatSupportQueries() {
        assertTrue(Ktx2CppTranscoderApi.basis_is_format_supported(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_6x6_RGBA,
                Ktx2BasisTextureFormat.cASTC_LDR_6x6));
        assertFalse(Ktx2CppTranscoderApi.basis_is_format_supported(
                Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA,
                Ktx2BasisTextureFormat.cASTC_LDR_4x4));

        assertTrue(Ktx2CppTranscoderApi.basis_is_format_supported(
                Ktx2TranscoderTextureFormat.cTFBC7_ALT,
                Ktx2BasisTextureFormat.cUASTC_LDR_4x4));
        assertFalse(Ktx2CppTranscoderApi.basis_is_format_supported(
                Ktx2TranscoderTextureFormat.cTFPVRTC2_4_RGBA,
                Ktx2BasisTextureFormat.cUASTC_LDR_4x4));
    }

    @Test
    void texFormatPropertiesAndNameBridges() {
        assertEquals(4, Ktx2CppTranscoderApi.basis_get_block_width(Ktx2TranscoderTextureFormat.cTFATC_RGB));
        assertEquals(4, Ktx2CppTranscoderApi.basis_get_block_height(Ktx2TranscoderTextureFormat.cTFATC_RGB));
        assertEquals(
                4,
                Ktx2CppTranscoderApi.basis_tex_format_get_block_width(Ktx2BasisTextureFormat.cETC1S));
        assertEquals(
                4,
                Ktx2CppTranscoderApi.basis_tex_format_get_block_height(Ktx2BasisTextureFormat.cETC1S));
        assertFalse(Ktx2CppTranscoderApi.basis_tex_format_is_hdr(Ktx2BasisTextureFormat.cETC1S));
        assertTrue(Ktx2CppTranscoderApi.basis_tex_format_is_ldr(Ktx2BasisTextureFormat.cXUASTC_LDR_4x4));
        assertTrue(
                Ktx2CppTranscoderApi.basis_tex_format_is_xuastc_ldr(Ktx2BasisTextureFormat.cXUASTC_LDR_5x4));
        assertTrue(
                Ktx2CppTranscoderApi.basis_tex_format_is_astc_ldr(Ktx2BasisTextureFormat.cASTC_LDR_5x4));

        assertEquals("ETC1S",
                Ktx2CppTranscoderApi.basis_get_tex_format_name(Ktx2BasisTextureFormat.cETC1S));
    }

    @Test
    void checksumHelpersMatchReferenceSemantics() {
        assertEquals(0, Ktx2CppTranscoderApi.hash_hsieh(new byte[]{}));
        assertEquals(521499119, Ktx2CppTranscoderApi.hash_hsieh(new byte[] {0, 1, 2, 3}));
        assertEquals((int) 2556998704L, Ktx2CppTranscoderApi.hash_hsieh(
                new byte[] {(byte) 0xFF, 0, 1, (byte) 0x80, 127}));
        assertEquals((int) 2658579249L, Ktx2CppTranscoderApi.hash_hsieh(new byte[]{1}));

        assertEquals(0, Ktx2CppTranscoderApi.crc16(new byte[]{}));
        assertEquals(3630, Ktx2CppTranscoderApi.crc16(new byte[]{1}));
        assertEquals(6028, Ktx2CppTranscoderApi.crc16(new byte[]{(byte) 0xFF, 0, 1, (byte) 0x80, 127}));
    }


    @Test
    void transcoderFacadeParityQueriesReturnDeterministicMetadata() throws Exception {
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");

        assertTrue(Ktx2CppTranscoderApi.validate_header_quick(payload, payload.length));
        assertTrue(Ktx2CppTranscoderApi.validate_header(payload, payload.length));
        assertTrue(Ktx2CppTranscoderApi.validate_file_checksums(payload, payload.length, true));

        int[] userdata = new int[2];
        assertTrue(Ktx2CppTranscoderApi.get_userdata(payload, payload.length, userdata));
        assertEquals(0, userdata[0]);
        assertEquals(0, userdata[1]);

        assertEquals(Ktx2BasisTextureType.cBASISTexType2D,
                Ktx2CppTranscoderApi.get_texture_type(payload, payload.length));
        assertEquals(Ktx2BasisTextureFormat.cUASTC_LDR_4x4,
                Ktx2CppTranscoderApi.get_basis_tex_format(payload, payload.length));
        assertEquals(1, Ktx2CppTranscoderApi.get_total_images(payload, payload.length));
        assertEquals(1, Ktx2CppTranscoderApi.get_total_image_levels(payload, payload.length, 0));
        assertEquals(0, Ktx2CppTranscoderApi.get_total_image_levels(payload, payload.length, 1));

        assertNotNull(Ktx2CppTranscoderApi.get_image_level_desc(payload, payload.length, 0, 0));
        Ktx2ImageLevelInfo levelInfo = Ktx2CppTranscoderApi.get_image_level_info(
                payload,
                payload.length,
                0,
                0);
        assertNotNull(levelInfo);
        assertEquals(0, levelInfo.getImageIndex());
        assertEquals(0, levelInfo.getLevelIndex());
        assertTrue(levelInfo.getRgbFileOffset() >= Ktx2Constants.KTX2_HEADER_SIZE);

        Ktx2ImageInfo imageInfo = Ktx2CppTranscoderApi.get_image_info(payload, payload.length, 0);
        assertNotNull(imageInfo);
        assertEquals(0, imageInfo.getImageIndex());
        assertEquals(1, imageInfo.getTotalLevels());

        Ktx2FileInfo fileInfo = Ktx2CppTranscoderApi.get_file_info(payload, payload.length);
        assertNotNull(fileInfo);
        assertEquals(1, fileInfo.getTotalImages());
        assertEquals(1, fileInfo.getImageMipmapLevels().length);
        assertEquals(1, fileInfo.getImageMipmapLevels()[0]);

        assertTrue(Ktx2CppTranscoderApi.start_transcoding(payload, payload.length));
        assertEquals(0, Ktx2CppTranscoderApi.find_slice(0, 0, false));
        assertEquals(-1, Ktx2CppTranscoderApi.find_slice(1, 0, false));
        assertEquals(0, Ktx2CppTranscoderApi.find_first_slice_index(0, false));
        assertEquals(-1, Ktx2CppTranscoderApi.find_first_slice_index(1, false));

        assertTrue(Ktx2CppTranscoderApi.stop_transcoding());
        assertEquals(-1, Ktx2CppTranscoderApi.find_slice(0, 0, false));
        int finalDebugState = Ktx2CppTranscoderApi.get_debug_flags();
        assertEquals(finalDebugState, Ktx2CppTranscoderApi.get_debug_flags());
    }



    @Test
    void getUserDataParsesBasisFileHeaderFieldsWhenAvailable() {
        byte[] payload = new byte[Ktx2BasisFileHeader.SIZE_BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 'B');
        buffer.put((byte) 's');
        buffer.putShort((short) Ktx2Constants.cBASISFirstVersion);
        buffer.putShort((short) Ktx2BasisFileHeader.SIZE_BYTES);
        buffer.putShort((short) 0); // header CRC
        buffer.putInt(0);           // data size
        buffer.putShort((short) 0); // data CRC

        put24(buffer, 1); // total slices
        put24(buffer, 1); // total images
        buffer.put((byte) Ktx2BasisTextureFormat.cUASTC_LDR_4x4.getCode());
        buffer.putShort((short) 0); // flags
        buffer.put((byte) Ktx2BasisTextureType.cBASISTexType2DArray.getCode());
        put24(buffer, 0); // microseconds per frame
        buffer.putInt(0); // reserved

        buffer.putInt(0x11223344); // userData0
        buffer.putInt(0x55667788); // userData1
        buffer.putShort((short) 1); // total endpoints
        buffer.putInt(0); // endpoint codebook offset
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0); // endpoint codebook size = 0
        buffer.putShort((short) 1); // total selectors
        buffer.putInt(0); // selector codebook offset
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0); // selector codebook size = 0
        buffer.putInt(0); // tables offset
        buffer.putInt(0); // tables size
        buffer.putInt(0); // slice desc offset
        buffer.putInt(0); // extended offset
        buffer.putInt(0); // extended size

        int[] userData = new int[2];
        assertTrue(Ktx2CppTranscoderApi.get_userdata(payload, payload.length, userData));
        assertEquals(0x11223344, userData[0]);
        assertEquals(0x55667788, userData[1]);
        assertFalse(Ktx2CppTranscoderApi.validate_header(payload, payload.length));
    }

    @Test
    void wasmHandleApiExposesLevelMetadataFromOpenHandle() throws Exception {
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");

        long handle = Ktx2CppTranscoderApi.bt_ktx2_open(payload);
        assertNotEquals(0, handle);
        assertTrue(Ktx2CppTranscoderApi.bt_ktx2_start_transcoding(handle));

        int fixtureWidth = Ktx2CppTranscoderApi.bt_ktx2_get_width(payload, payload.length);
        int fixtureHeight = Ktx2CppTranscoderApi.bt_ktx2_get_height(payload, payload.length);

        assertEquals(fixtureWidth, Ktx2CppTranscoderApi.bt_ktx2_get_level_orig_width(handle, 0, 0, 0));
        assertEquals(fixtureHeight, Ktx2CppTranscoderApi.bt_ktx2_get_level_orig_height(handle, 0, 0, 0));
        assertEquals(fixtureWidth, Ktx2CppTranscoderApi.bt_ktx2_get_level_actual_width(handle, 0, 0, 0));
        assertEquals(fixtureHeight, Ktx2CppTranscoderApi.bt_ktx2_get_level_actual_height(handle, 0, 0, 0));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_get_level_num_blocks_x(handle, 0, 0, 0));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_get_level_num_blocks_y(handle, 0, 0, 0));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_get_level_total_blocks(handle, 0, 0, 0));
        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_block_width(handle));
        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_block_height(handle));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_level_alpha_flag(handle, 0, 0, 0));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_level_iframe_flag(handle, 0, 0, 0));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_srgb(handle));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_video(handle));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_ldr_hdr_upconversion_nit_multiplier(handle));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_has_alpha(handle));

        Ktx2CppTranscoderApi.bt_ktx2_close(handle);
        assertFalse(Ktx2CppTranscoderApi.bt_ktx2_start_transcoding(handle));
    }

    @Test
    void validateHeaderQuickRejectsCorruptedLevelTable() throws IOException {
        byte[] validPayload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");

        // Truncate header table payload: remove part of the per-level index entries
        byte[] truncatedPayload = Arrays.copyOf(validPayload, validPayload.length - 4);
        assertFalse(Ktx2CppTranscoderApi.validate_header_quick(truncatedPayload, truncatedPayload.length));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(
                truncatedPayload,
                truncatedPayload.length,
                false));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(
                truncatedPayload,
                truncatedPayload.length,
                true));

        // Keep size valid but corrupt first level length to exceed buffer bounds.
        byte[] corruptedPayload = Arrays.copyOf(validPayload, validPayload.length);
        ByteBuffer corrupt = ByteBuffer.wrap(corruptedPayload).order(ByteOrder.LITTLE_ENDIAN);
        // KTX2 level table starts at offset 80, first entry: [byteOffset][byteLength][uncompressedLength]
        corrupt.position(Ktx2Constants.KTX2_HEADER_SIZE + 8);
        corrupt.putLong(Integer.MAX_VALUE);

        assertFalse(Ktx2CppTranscoderApi.validate_header_quick(corruptedPayload, corruptedPayload.length));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(
                corruptedPayload,
                corruptedPayload.length,
                false));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(
                corruptedPayload,
                corruptedPayload.length,
                true));

        byte[] overflowPayload = Arrays.copyOf(validPayload, validPayload.length);
        ByteBuffer overflow = ByteBuffer.wrap(overflowPayload).order(ByteOrder.LITTLE_ENDIAN);
        overflow.position(Ktx2Constants.KTX2_HEADER_SIZE);
        overflow.putLong(Long.MIN_VALUE);

        assertFalse(Ktx2CppTranscoderApi.validate_header_quick(overflowPayload, overflowPayload.length));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(
                overflowPayload,
                overflowPayload.length,
                false));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(
                overflowPayload,
                overflowPayload.length,
                true));
    }

    @Test
    void validateLevelTableRejectsOverlappingLevelRanges() {
        byte[] validPayload = buildSyntheticKtx2Payload(
                8,
                8,
                Ktx2Constants.KTX2_FORMAT_ASTC_4x4_UNORM_BLOCK,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                4,
                4,
                44,
                0,
                0,
                new byte[0],
                2);

        ByteBuffer patch = ByteBuffer.wrap(validPayload).order(ByteOrder.LITTLE_ENDIAN);
        int levelTableOffset = Ktx2Constants.KTX2_HEADER_SIZE;
        patch.position(levelTableOffset);

        // Default first level is placed after metadata; rewrite level ranges with
        // explicit overlap to guarantee strict rejection.
        long levelDataOffset = patch.getLong(levelTableOffset);
        patch.position(levelTableOffset);
        patch.putLong(levelDataOffset);
        patch.putLong(8L);
        patch.putLong(8L);

        // Second level intentionally overlaps bytes [levelDataOffset, levelDataOffset + 8)
        patch.position(levelTableOffset + 24);
        patch.putLong(levelDataOffset + 4);
        patch.putLong(4L);
        patch.putLong(4L);

        assertFalse(Ktx2CppTranscoderApi.validate_header_quick(validPayload, validPayload.length));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(validPayload, validPayload.length, false));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(validPayload, validPayload.length, true));

        // Adjacent zero-length entry must stay accepted (non-overlap when a slice is empty).
        ByteBuffer nonOverlapping = ByteBuffer.wrap(Arrays.copyOf(validPayload, validPayload.length)).order(
                ByteOrder.LITTLE_ENDIAN);
        nonOverlapping.position(levelTableOffset);
        nonOverlapping.putLong(levelDataOffset);
        nonOverlapping.putLong(0L);
        nonOverlapping.putLong(0L);
        nonOverlapping.position(levelTableOffset + 24);
        nonOverlapping.putLong(levelDataOffset);
        nonOverlapping.putLong(4L);
        nonOverlapping.putLong(4L);

        byte[] nonOverlappingPayload = nonOverlapping.array();
        assertTrue(Ktx2CppTranscoderApi.validate_header_quick(
                nonOverlappingPayload,
                nonOverlappingPayload.length));
        assertTrue(Ktx2CppTranscoderApi.validate_file_checksums(nonOverlappingPayload,
                nonOverlappingPayload.length,
                true));
    }

    @Test
    void transcoderStartIsIdempotentAndStateTransitionSafe() throws Exception {
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");

        assertTrue(Ktx2CppTranscoderApi.start_transcoding(payload, payload.length));
        assertEquals(0, Ktx2CppTranscoderApi.find_slice(0, 0, false));

        // Re-enter the ready state on same payload without stopping first.
        assertTrue(Ktx2CppTranscoderApi.start_transcoding(payload, payload.length));
        assertEquals(0, Ktx2CppTranscoderApi.find_slice(0, 0, false));
        assertTrue(Ktx2CppTranscoderApi.stop_transcoding());

        assertEquals(-1, Ktx2CppTranscoderApi.find_slice(0, 0, false));
    }

    @Test
    void transcoderFacadeRejectsInvalidPayloadsFast() {
        byte[] shortPayload = new byte[] {1, 2, 3};

        assertFalse(Ktx2CppTranscoderApi.validate_header(shortPayload, shortPayload.length));
        assertFalse(Ktx2CppTranscoderApi.validate_header_quick(shortPayload, shortPayload.length));
        assertFalse(Ktx2CppTranscoderApi.validate_file_checksums(shortPayload, shortPayload.length, true));
        assertEquals(0, Ktx2CppTranscoderApi.get_total_images(shortPayload, shortPayload.length));
        assertNull(Ktx2CppTranscoderApi.get_image_info(shortPayload, shortPayload.length, 0));
        assertNull(Ktx2CppTranscoderApi.get_image_level_info(shortPayload, shortPayload.length, 0, 0));
        assertNull(Ktx2CppTranscoderApi.get_file_info(shortPayload, shortPayload.length));
        assertFalse(Ktx2CppTranscoderApi.start_transcoding(shortPayload, shortPayload.length));

        assertThrows(IllegalArgumentException.class, () ->
                Ktx2CppTranscoderApi.get_userdata(shortPayload, shortPayload.length, new int[1]));
    }

    @Test
    void debugFlagsRoundTrip() {
        Ktx2CppTranscoderApi.set_debug_flags(Ktx2DebugFlag.toMask(
                Ktx2DebugFlag.cDebugFlagVisCRs,
                Ktx2DebugFlag.cDebugFlagVisBC1Sels));
        assertEquals(3, Ktx2CppTranscoderApi.get_debug_flags());
        Ktx2CppTranscoderApi.basis_enable_debug_printf(1);
        assertTrue(Ktx2CppTranscoderApi.basis_get_debug_printf_enabled());
        Ktx2CppTranscoderApi.basis_enable_debug_printf(0);

        Ktx2CppTranscoderApi.set_debug_flags(Ktx2DebugFlag.toMask(Ktx2DebugFlag.cDebugFlagVisBC1Endpoints));
        assertEquals(4, Ktx2CppTranscoderApi.get_debug_flags());

        Ktx2CppTranscoderApi.set_debug_flags(0);
        assertEquals(0, Ktx2CppTranscoderApi.get_debug_flags());

        assertThrows(IllegalArgumentException.class,
                () -> Ktx2CppTranscoderApi.set_debug_flags(0x8000));
    }

    @Test
    void basisTextureFormatDetectionUsesKtx2DfdMetadata() {
        byte[] astc4x4 = buildSyntheticKtx2Payload(
                4,
                4,
                Ktx2Constants.KTX2_FORMAT_ASTC_4x4_UNORM_BLOCK,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                4,
                4,
                44,
                3,
                0,
                new byte[0]);
        Ktx2BasisTextureFormat astcFormat =
                Ktx2CppTranscoderApi.get_basis_tex_format(astc4x4, astc4x4.length);
        assertNotNull(astcFormat);
        assertTrue(astcFormat.name().contains("ASTC"));
        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_width(astc4x4, astc4x4.length));
        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_height(astc4x4, astc4x4.length));

        byte[] uastc = buildSyntheticKtx2Payload(
                4,
                4,
                0,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_LDR_4X4,
                4,
                4,
                44,
                3,
                0,
                new byte[0]);
        assertEquals(Ktx2BasisTextureFormat.cUASTC_LDR_4x4,
                Ktx2CppTranscoderApi.get_basis_tex_format(uastc, uastc.length));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_is_uastc_ldr_4x4(uastc, uastc.length));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_get_basis_tex_format(uastc, uastc.length));

        byte[] etc1s = buildSyntheticKtx2Payload(
                4,
                4,
                0,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S,
                4,
                4,
                60,
                3,
                3,
                new byte[0]);
        Ktx2BasisTextureFormat etc1sFormat = Ktx2CppTranscoderApi.get_basis_tex_format(etc1s, etc1s.length);
        assertNotNull(etc1sFormat);
    }

    @Test
    void metadataQueriesAreCorrectForMultiImageLayout() {
        byte[] multiImageTexture = buildSyntheticKtx2Payload(
                4,
                4,
                0,
                2,
                6,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S,
                4,
                4,
                60,
                3,
                3,
                new byte[0],
                3);

        assertEquals(12, Ktx2CppTranscoderApi.get_total_images(multiImageTexture, multiImageTexture.length));
        assertEquals(
                3,
                Ktx2CppTranscoderApi.get_total_image_levels(multiImageTexture, multiImageTexture.length, 0));
        assertEquals(
                3,
                Ktx2CppTranscoderApi.get_total_image_levels(multiImageTexture, multiImageTexture.length, 11));
        assertEquals(
                0,
                Ktx2CppTranscoderApi.get_total_image_levels(multiImageTexture, multiImageTexture.length, 12));

        Ktx2FileInfo fileInfo = Ktx2CppTranscoderApi.get_file_info(
                multiImageTexture,
                multiImageTexture.length);
        assertNotNull(fileInfo);
        assertEquals(12, fileInfo.getTotalImages());
        assertEquals(12, fileInfo.getImageMipmapLevels().length);
        assertEquals(3, fileInfo.getImageMipmapLevels()[0]);
        assertEquals(3, fileInfo.getImageMipmapLevels()[11]);

        Ktx2ImageInfo firstImageInfo = Ktx2CppTranscoderApi.get_image_info(multiImageTexture,
                multiImageTexture.length,
                0);
        Ktx2ImageInfo lastImageInfo = Ktx2CppTranscoderApi.get_image_info(multiImageTexture,
                multiImageTexture.length,
                11);
        assertNotNull(firstImageInfo);
        assertNotNull(lastImageInfo);
        assertEquals(0, firstImageInfo.getFirstSliceIndex());
        assertEquals(33, lastImageInfo.getFirstSliceIndex());

        Ktx2ImageLevelInfo level2Info = Ktx2CppTranscoderApi.get_image_level_info(multiImageTexture,
                multiImageTexture.length,
                11,
                2);
        assertNotNull(level2Info);
        assertEquals(11, level2Info.getImageIndex());
        assertEquals(2, level2Info.getLevelIndex());
        assertEquals(11 * 3 + 2, level2Info.getFirstSliceIndex());
        assertTrue(level2Info.getRgbFileOffset() >= Ktx2Constants.KTX2_HEADER_SIZE);

        assertTrue(Ktx2CppTranscoderApi.start_transcoding(multiImageTexture, multiImageTexture.length));
        assertEquals(0, Ktx2CppTranscoderApi.find_slice(0, 0, false));
        assertEquals(11 * 3 + 2, Ktx2CppTranscoderApi.find_slice(11, 2, false));
        assertEquals(33, Ktx2CppTranscoderApi.find_first_slice_index(11, false));
        assertEquals(-1, Ktx2CppTranscoderApi.find_first_slice_index(12, false));
        assertTrue(Ktx2CppTranscoderApi.stop_transcoding());
    }

    @Test
    void textureTypeUsesFaceAndLayerMetadataWhenAvailable() {

        byte[] texture = buildSyntheticKtx2Payload(
                2,
                2,
                0,
                2,
                6,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S,
                4,
                4,
                60,
                3,
                3,
                new byte[0]);
        assertEquals(Ktx2BasisTextureType.cBASISTexTypeCubemapArray,
                Ktx2CppTranscoderApi.get_texture_type(texture, texture.length));
        assertEquals(Ktx2BasisTextureType.cBASISTexTypeCubemapArray,
                Ktx2CppTranscoderApi.get_file_info(texture, texture.length).getTextureType());
    }

    @Test
    void wasmHandleLifecycleRemainsLeakFreeAfterBulkOpenClose() throws Exception {
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        int beforeOpenHandles = getStaticCollectionSize("WASM_HANDLE_STATE");
        int beforeActiveStates = getStaticCollectionSize("WASM_ACTIVE_STATES");

        Set<Long> handles = new HashSet<Long>();
        for (int i = 0; i < 1000; i++) {
            long handle = Ktx2CppTranscoderApi.bt_ktx2_open(payload);
            assertNotEquals(0L, handle);
            handles.add(handle);
            assertTrue(Ktx2CppTranscoderApi.bt_ktx2_start_transcoding(handle));
        }

        assertEquals(beforeOpenHandles + handles.size(), getStaticCollectionSize("WASM_HANDLE_STATE"));
        assertEquals(beforeActiveStates + handles.size(), getStaticCollectionSize("WASM_ACTIVE_STATES"));

        for (long handle : handles) {
            Ktx2CppTranscoderApi.bt_ktx2_destroy_transcode_state(handle);
            Ktx2CppTranscoderApi.bt_ktx2_close(handle);
        }

        assertEquals(beforeOpenHandles, getStaticCollectionSize("WASM_HANDLE_STATE"));
        assertEquals(beforeActiveStates, getStaticCollectionSize("WASM_ACTIVE_STATES"));
    }

    @Test
    void transcodeStateAllocationDoesNotLeak() throws Exception {
        assertTrue(getStaticCollectionSize("WASM_ACTIVE_STATES") >= 0);
        assertTrue(getStaticCollectionSize("WASM_TRANSCODE_STATES") >= 0);

        for (int i = 0; i < 200; i++) {
            long state = Ktx2CppTranscoderApi.bt_ktx2_create_transcode_state();
            assertNotEquals(0L, state);
            assertTrue(getStaticCollectionSize("WASM_TRANSCODE_STATES") >= 1);
            Ktx2CppTranscoderApi.bt_ktx2_destroy_transcode_state(state);
            assertEquals(0, getStaticCollectionSize("WASM_TRANSCODE_STATES"));
        }

        assertEquals(0, getStaticCollectionSize("WASM_ACTIVE_STATES"));
        Ktx2CppTranscoderApi.bt_ktx2_destroy_transcode_state(999_999_999L);

        assertEquals(0, getStaticCollectionSize("WASM_ACTIVE_STATES"));
        assertEquals(0, getStaticCollectionSize("WASM_TRANSCODE_STATES"));
    }

    @Test
    void apiNameAndPredicateParityCoverage() {
        assertEquals(
                Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA.toNativeName(),
                Ktx2CppTranscoderApi.basis_get_format_name(
                        Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA));
        assertEquals(
                Ktx2TextureFormat.cASTC_HDR_4x4,
                Ktx2CppTranscoderApi.basis_get_basis_texture_format(
                        Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA));
        assertEquals(Ktx2BasisTextureFormat.cETC1S.toNativeName(),
                Ktx2CppTranscoderApi.basis_get_tex_format_name(Ktx2BasisTextureFormat.cETC1S));
        assertEquals(Ktx2TextureFormat.cATC_RGB.toNativeName(),
                Ktx2CppTranscoderApi.basis_get_block_format_name(Ktx2TextureFormat.cATC_RGB));

        assertEquals(4, Ktx2CppTranscoderApi.basis_get_block_width(Ktx2TranscoderTextureFormat.cTFBC7_RGBA));
        assertEquals(4, Ktx2CppTranscoderApi.basis_get_block_height(Ktx2TranscoderTextureFormat.cTFBC7_RGBA));
        assertEquals(
                Ktx2TranscoderTextureFormat.cTFBC7_RGBA.getBytesPerBlockOrPixel(),
                Ktx2CppTranscoderApi.basis_get_bytes_per_block_or_pixel(
                        Ktx2TranscoderTextureFormat.cTFBC7_RGBA));
        assertEquals(Ktx2TranscoderTextureFormat.cTFBC7_RGBA.getUncompressedBytesPerPixel(),
                Ktx2CppTranscoderApi.basis_get_uncompressed_bytes_per_pixel(
                        Ktx2TranscoderTextureFormat.cTFBC7_RGBA));

        assertTrue(Ktx2CppTranscoderApi.basis_transcoder_format_is_hdr(
                Ktx2TranscoderTextureFormat.cTFASTC_HDR_6x6_RGBA));
        assertFalse(Ktx2CppTranscoderApi.basis_transcoder_format_is_hdr(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA));
        assertTrue(Ktx2CppTranscoderApi.basis_transcoder_format_is_ldr(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_12x12_RGBA));
        assertFalse(Ktx2CppTranscoderApi.basis_transcoder_format_is_ldr(
                Ktx2TranscoderTextureFormat.cTFASTC_HDR_6x6_RGBA));

        assertTrue(Ktx2CppTranscoderApi.basis_is_transcoder_texture_format_astc(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x4_RGBA));
        assertFalse(Ktx2CppTranscoderApi.basis_is_transcoder_texture_format_astc(
                Ktx2TranscoderTextureFormat.cTFATC_RGB));

        assertTrue(Ktx2CppTranscoderApi.basis_tex_format_is_astc_ldr(Ktx2BasisTextureFormat.cASTC_LDR_8x5));
        assertFalse(Ktx2CppTranscoderApi.basis_tex_format_is_astc_ldr(Ktx2BasisTextureFormat.cETC1S));
        assertTrue(Ktx2CppTranscoderApi.basis_tex_format_is_hdr(Ktx2BasisTextureFormat.cASTC_HDR_6x6));
        assertFalse(Ktx2CppTranscoderApi.basis_tex_format_is_ldr(Ktx2BasisTextureFormat.cUASTC_HDR_4x4));

        assertEquals(Ktx2TextureFormat.cASTC_LDR_10x6,
                Ktx2CppTranscoderApi.basis_get_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(
                        Ktx2BasisTextureFormat.cASTC_LDR_10x6));
    }

    @Test
    void basisApiNullabilityMatchesDocumentedContracts() {
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_format_name(null));
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_tex_format_name(null));
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_block_format_name(null));
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_basisu_texture_format(null));
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_transcoder_format_is_uncompressed(null));
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_block_width(null));
        assertThrows(
                BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(
                        null));
    }

    @Test
    void btHandleQuerySurfaceIsFullyAddressable() throws Exception {
        byte[] payload = buildSyntheticKtx2Payload(
                4,
                4,
                Ktx2Constants.KTX2_FORMAT_ASTC_4x4_UNORM_BLOCK,
                2,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                4,
                4,
                44,
                3,
                0,
                new byte[0],
                1);

        long handle = Ktx2CppTranscoderApi.bt_ktx2_open(payload, payload.length);
        assertNotEquals(0L, handle);

        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_width(payload, payload.length));
        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_height(payload, payload.length));
        assertEquals(2, Ktx2CppTranscoderApi.bt_ktx2_get_layers(payload, payload.length));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_get_faces(payload, payload.length));

        assertNotEquals(-1, Ktx2CppTranscoderApi.bt_ktx2_get_basis_tex_format(payload, payload.length));

        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_block_width(handle));
        assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_block_height(handle));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_video(handle));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_srgb(handle));
        assertEquals(0f, Ktx2CppTranscoderApi.bt_ktx2_get_ldr_hdr_upconversion_nit_multiplier(handle));

        assertTrue(Ktx2CppTranscoderApi.bt_ktx2_start_transcoding(handle));
        assertEquals(Ktx2CppTranscoderApi.bt_ktx2_get_block_height(handle),
                Ktx2CppTranscoderApi.bt_ktx2_get_block_width(handle));
        Ktx2CppTranscoderApi.bt_ktx2_close(handle);
        assertFalse(Ktx2CppTranscoderApi.bt_ktx2_start_transcoding(handle));
    }

    @Test
    void openByNativeAllocationHandleCanReuseStoredAllocationData() throws Exception {
        byte[] payload = buildSyntheticKtx2Payload(
                8,
                8,
                Ktx2Constants.KTX2_FORMAT_ASTC_10x10_UNORM_BLOCK,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                10,
                10,
                44,
                1,
                0,
                new byte[0]);

        java.lang.reflect.Field allocField = Ktx2CppTranscoderApi.class.getDeclaredField("ALLOCATIONS");
        allocField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Long, byte[]> allocations = (java.util.Map<Long, byte[]>) allocField.get(null);
        long ptr = Ktx2CppTranscoderApi.basis_alloc(payload.length);
        assertNotEquals(0L, ptr);
        allocations.put(ptr, payload);
        try {
            long handle = Ktx2CppTranscoderApi.bt_ktx2_open(ptr, payload.length);
            assertNotEquals(0L, handle);

            assertTrue(Ktx2CppTranscoderApi.bt_ktx2_start_transcoding(handle));
            assertTrue(Ktx2CppTranscoderApi.bt_ktx2_start_transcoding(handle));
            assertEquals(10, Ktx2CppTranscoderApi.bt_ktx2_get_block_width(handle));
            assertEquals(10, Ktx2CppTranscoderApi.bt_ktx2_get_block_height(handle));
            Ktx2CppTranscoderApi.stop_transcoding();
            Ktx2CppTranscoderApi.bt_ktx2_close(handle);
        } finally {
            Ktx2CppTranscoderApi.basis_free(ptr);
            allocations.remove(ptr);
            allocField.setAccessible(false);
        }
    }


    private static void put24(ByteBuffer buffer, int value) {
        buffer.put((byte) (value & 0xFF));
        buffer.put((byte) ((value >>> 8) & 0xFF));
        buffer.put((byte) ((value >>> 16) & 0xFF));
    }

    private static byte[] loadFixture(String path) throws IOException {
        try (java.io.InputStream stream = TestKtx2Header.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing test fixture: " + path);
            }
            return stream.readAllBytes();
        }
    }

    private static int getStaticCollectionSize(String fieldName) throws Exception {
        java.lang.reflect.Field field = Ktx2CppTranscoderApi.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        if (value instanceof java.util.Map) {
            return ((java.util.Map) value).size();
        }
        if (value instanceof java.util.Collection) {
            return ((java.util.Collection) value).size();
        }
        throw new IllegalStateException("Unsupported field type for " + fieldName + ": " + value);
    }

    private static byte[] buildSyntheticKtx2Payload(int width,
                                                  int height,
                                                  int vkFormat,
                                                  int layerCount,
                                                  int faceCount,
                                                  int dfdModel,
                                                  int dfdBlockWidth,
                                                  int dfdBlockHeight,
                                                  int dfdLength,
                                                  int sample0Channel,
                                                  int sample1Channel,
                                                  byte[] kvd) {
        return buildSyntheticKtx2Payload(
                width,
                height,
                vkFormat,
                layerCount,
                faceCount,
                dfdModel,
                dfdBlockWidth,
                dfdBlockHeight,
                dfdLength,
                sample0Channel,
                sample1Channel,
                kvd,
                1,
                Ktx2Constants.KTX2_SS_NONE);
    }

    private static byte[] buildSyntheticKtx2Payload(int width,
                                                  int height,
                                                  int vkFormat,
                                                  int layerCount,
                                                  int faceCount,
                                                  int dfdModel,
                                                  int dfdBlockWidth,
                                                  int dfdBlockHeight,
                                                  int dfdLength,
                                                  int sample0Channel,
                                                  int sample1Channel,
                                                  byte[] kvd,
                                                  int levelCount) {
        return buildSyntheticKtx2Payload(
                width,
                height,
                vkFormat,
                layerCount,
                faceCount,
                dfdModel,
                dfdBlockWidth,
                dfdBlockHeight,
                dfdLength,
                sample0Channel,
                sample1Channel,
                kvd,
                levelCount,
                Ktx2Constants.KTX2_SS_NONE);
    }

    private static byte[] buildSyntheticKtx2Payload(int width,
                                                  int height,
                                                  int vkFormat,
                                                  int layerCount,
                                                  int faceCount,
                                                  int dfdModel,
                                                  int dfdBlockWidth,
                                                  int dfdBlockHeight,
                                                  int dfdLength,
                                                  int sample0Channel,
                                                  int sample1Channel,
                                                  byte[] kvd,
                                                  int levelCount,
                                                  int supercompressionScheme) {
        if (dfdLength != 44 && dfdLength != 60) {
            throw new IllegalArgumentException("Unsupported synthetic DFD length: " + dfdLength);
        }
        if (levelCount <= 0) {
            throw new IllegalArgumentException("Unsupported synthetic level count: " + levelCount);
        }

        int levelTableSize = levelCount * 24;
        int dfdOffset = Ktx2Constants.KTX2_HEADER_SIZE + levelTableSize;
        int kvdOffset = dfdOffset + dfdLength;
        int levelDataOffset = kvdOffset + (kvd == null ? 0 : kvd.length);
        int levelDataSize = 4;
        int totalSize = levelDataOffset + (levelDataSize * levelCount);

        ByteBuffer buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        // KTX2 identifier.
        buffer.put((byte) 0xab);
        buffer.put((byte) 0x4b);
        buffer.put((byte) 0x54);
        buffer.put((byte) 0x58);
        buffer.put((byte) 0x20);
        buffer.put((byte) 0x32);
        buffer.put((byte) 0x30);
        buffer.put((byte) 0xbb);
        buffer.put((byte) 0x0d);
        buffer.put((byte) 0x0a);
        buffer.put((byte) 0x1a);
        buffer.put((byte) 0x0a);
        // Level descriptor immediately follows header at byte 80.
        buffer.putInt(vkFormat);
        buffer.putInt(1); // typeSize
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.putInt(0); // pixelDepth
        buffer.putInt(layerCount);
        buffer.putInt(faceCount);
        buffer.putInt(levelCount);
        buffer.putInt(supercompressionScheme); // supercompressionScheme
        buffer.putInt(dfdOffset);
        buffer.putInt(dfdLength);
        buffer.putInt(kvdOffset);
        buffer.putInt(kvd == null ? 0 : kvd.length);
        buffer.putLong(0); // sgd offset/length

        while (buffer.position() < Ktx2Constants.KTX2_HEADER_SIZE) {
            buffer.put((byte) 0);
        }

        // Level table starts directly after header.
        int levelOffset = levelDataOffset;
        for (int level = 0; level < levelCount; level++) {
            int thisOffset = levelOffset + (level * levelDataSize);
            buffer.putLong((long) thisOffset);
            buffer.putLong((long) levelDataSize);
            buffer.putLong((long) levelDataSize);
        }

        while (buffer.position() < dfdOffset) {
            buffer.put((byte) 0);
        }

        buffer.putInt(dfdLength);
        buffer.putInt(0); // vendorID/descriptorType
        buffer.putInt(0); // version/??
        int dfdBits = dfdModel | (0 << 8) | (Ktx2Constants.KTX2_KHR_DF_TRANSFER_LINEAR << 16);
        buffer.position(dfdOffset + 12);
        buffer.putInt(dfdBits);
        int texelDims = ((dfdBlockHeight - 1) << 8) | (dfdBlockWidth - 1);
        buffer.position(dfdOffset + 16);
        buffer.putInt(texelDims);
        buffer.position(dfdOffset + 28);
        buffer.putInt(sample0Channel << 24);
        if (dfdLength == 60) {
            buffer.position(dfdOffset + 44);
            buffer.putInt(sample1Channel << 24);
        }

        if (kvd != null && kvd.length > 0) {
            while (buffer.position() < kvdOffset) {
                buffer.put((byte) 0);
            }
            buffer.put(kvd);
        } else {
            while (buffer.position() < kvdOffset) {
                buffer.put((byte) 0);
            }
        }

        while (buffer.position() < levelDataOffset) {
            buffer.put((byte) 0);
        }
        // ensure the level payload exists
        for (int i = 0; i < levelCount; i++) {
            buffer.putInt(0);
        }
        while (buffer.position() < totalSize) {
            buffer.put((byte) 0);
        }

        return buffer.array();
    }

    @Test
    void validateUnknownsFailWithMeaningfulErrors() {
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_block_width((Ktx2TranscoderTextureFormat) null));
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_tex_format_get_block_height((Ktx2BasisTextureFormat) null));
        assertThrows(BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_is_format_supported(null, Ktx2BasisTextureFormat.cETC1S));
    }

    @Test
    void apiBootstrapAndSurfaceParitySmoke() {
        // Idempotent init path + deterministic facade constants.
        Ktx2CppTranscoderApi.basis_init();
        Ktx2CppTranscoderApi.basisu_transcoder_init();
        assertEquals(210, Ktx2CppTranscoderApi.basis_get_version());

        Ktx2CppTranscoderApi.basis_enable_debug_printf(0);
        assertEquals(false, Ktx2CppTranscoderApi.basis_get_debug_printf_enabled());
        Ktx2CppTranscoderApi.basis_enable_debug_printf(1);
        assertEquals(true, Ktx2CppTranscoderApi.basis_get_debug_printf_enabled());

        assertTrue(Ktx2CppTranscoderApi.basisu_transcoder_supports_ktx2());
        assertTrue(Ktx2CppTranscoderApi.basisu_transcoder_supports_ktx2_zstd());

        assertFalse(Ktx2CppTranscoderApi.basis_transcoder_format_has_alpha(
                Ktx2TranscoderTextureFormat.cTFETC1_RGB));
        assertTrue(Ktx2CppTranscoderApi.basis_transcoder_format_has_alpha(
                Ktx2TranscoderTextureFormat.cTFBC3_RGBA));

        assertEquals(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA,
                Ktx2CppTranscoderApi
                        .basis_get_transcoder_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(
                        Ktx2BasisTextureFormat.cASTC_LDR_4x4));
        assertEquals(
                Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x4_RGBA,
                Ktx2CppTranscoderApi
                        .basis_get_transcoder_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(
                        Ktx2BasisTextureFormat.cXUASTC_LDR_5x4));
    }

    @Test
    void btKtx2ImageQueriesCoverFormatPredicatesAndDimensions() {
        byte[] astcPayload = buildSyntheticKtx2Payload(
                128,
                64,
                Ktx2Constants.KTX2_FORMAT_ASTC_4x4_UNORM_BLOCK,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                4,
                4,
                44,
                0,
                0,
                null,
                2);

        assertEquals(Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                Ktx2CppTranscoderApi.bt_ktx2_get_dfd_color_model(astcPayload, astcPayload.length));
        assertEquals(2, Ktx2CppTranscoderApi.bt_ktx2_get_levels(astcPayload, astcPayload.length));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_get_faces(astcPayload, astcPayload.length));

        long astcHandle = Ktx2CppTranscoderApi.bt_ktx2_open(astcPayload);
        try {
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_etc1s(astcPayload, astcPayload.length));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_uastc_ldr_4x4(astcPayload, astcPayload.length));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_hdr(astcPayload, astcPayload.length));
            assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_is_astc_ldr(astcPayload, astcPayload.length));
            assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_is_ldr(astcPayload, astcPayload.length));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_xuastc_ldr(astcPayload, astcPayload.length));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_hdr_4x4(astcPayload, astcPayload.length));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_hdr_6x6(astcPayload, astcPayload.length));

            assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_block_width(astcHandle));
            assertEquals(4, Ktx2CppTranscoderApi.bt_ktx2_get_block_height(astcHandle));

            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_level_alpha_flag(astcHandle, 0, 0, 0));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_level_iframe_flag(astcHandle, 0, 0, 0));

            assertEquals(128, Ktx2CppTranscoderApi.bt_ktx2_get_level_orig_width(astcHandle, 0, 0, 0));
            assertEquals(64, Ktx2CppTranscoderApi.bt_ktx2_get_level_orig_height(astcHandle, 0, 0, 0));
            assertEquals(128, Ktx2CppTranscoderApi.bt_ktx2_get_level_actual_width(astcHandle, 0, 0, 0));
            assertEquals(64, Ktx2CppTranscoderApi.bt_ktx2_get_level_actual_height(astcHandle, 0, 0, 0));

            int expectedBlocksX = (128 + 4 - 1) / 4;
            int expectedBlocksY = (64 + 4 - 1) / 4;
            assertEquals(
                    expectedBlocksX,
                    Ktx2CppTranscoderApi.bt_ktx2_get_level_num_blocks_x(astcHandle, 0, 0, 0));
            assertEquals(
                    expectedBlocksY,
                    Ktx2CppTranscoderApi.bt_ktx2_get_level_num_blocks_y(astcHandle, 0, 0, 0));
            assertEquals(
                    expectedBlocksX * expectedBlocksY,
                    Ktx2CppTranscoderApi.bt_ktx2_get_level_total_blocks(astcHandle, 0, 0, 0));
        } finally {
            Ktx2CppTranscoderApi.bt_ktx2_close(astcHandle);
        }
    }

    @Test
    void btKtx2DfdQueriesCoverGetterSurface() {
        byte[] payload60 = buildSyntheticKtx2Payload(
                32,
                32,
                Ktx2Constants.KTX2_FORMAT_ASTC_4x4_SRGB_BLOCK,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_HDR_4X4,
                4,
                4,
                60,
                3,
                15,
                null);

        assertEquals(Ktx2BasisTextureFormat.cUASTC_HDR_4x4,
                Ktx2CppTranscoderApi.get_basis_tex_format(payload60, payload60.length));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_uastc_ldr_4x4(payload60, payload60.length));
        assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_is_hdr(payload60, payload60.length));

        assertEquals(Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_HDR_4X4,
                Ktx2CppTranscoderApi.bt_ktx2_get_dfd_color_model(payload60, payload60.length));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_dfd_flags(payload60, payload60.length));
        assertEquals(2, Ktx2CppTranscoderApi.bt_ktx2_get_dfd_total_samples(payload60, payload60.length));
        assertEquals(3, Ktx2CppTranscoderApi.bt_ktx2_get_dfd_channel_id0(payload60, payload60.length));
        assertEquals(15, Ktx2CppTranscoderApi.bt_ktx2_get_dfd_channel_id1(payload60, payload60.length));
        assertEquals(Ktx2Constants.KTX2_KHR_DF_TRANSFER_LINEAR,
                Ktx2CppTranscoderApi.bt_ktx2_get_dfd_transfer_func(payload60, payload60.length));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_dfd_color_primaries(payload60, payload60.length));

        long payloadHandle = Ktx2CppTranscoderApi.bt_ktx2_open(payload60);
        try {
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_video(payloadHandle));
            assertEquals(
                    1.0f,
                    Ktx2CppTranscoderApi.bt_ktx2_get_ldr_hdr_upconversion_nit_multiplier(payloadHandle));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_srgb(payloadHandle));
            assertEquals(1, Ktx2CppTranscoderApi.bt_ktx2_get_level_alpha_flag(payloadHandle, 0, 0, 0));
            assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_level_iframe_flag(payloadHandle, 0, 0, 0));
            assertEquals(8,
                    Ktx2CppTranscoderApi.bt_ktx2_get_level_num_blocks_x(payloadHandle, 0, 0, 0));
            assertEquals(8,
                    Ktx2CppTranscoderApi.bt_ktx2_get_level_num_blocks_y(payloadHandle, 0, 0, 0));
        } finally {
            Ktx2CppTranscoderApi.bt_ktx2_close(payloadHandle);
        }
    }

    @Test
    void xuastcModelAndAstcFallbackSliceMappingIsCovered() {
        byte[] xuastcPayload = buildSyntheticKtx2Payload(
                16,
                16,
                Ktx2Constants.KTX2_FORMAT_ASTC_4x4_UNORM_BLOCK,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_XUASTC_LDR_INTERMEDIATE,
                4,
                4,
                44,
                1,
                0,
                null);
        assertEquals(Ktx2BasisTextureFormat.cASTC_LDR_4x4,
                Ktx2CppTranscoderApi.get_basis_tex_format(xuastcPayload, xuastcPayload.length));

        byte[] fallbackPayload = buildSyntheticKtx2Payload(
                16,
                16,
                0,
                1,
                1,
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                4,
                4,
                60,
                3,
                15,
                null,
                1,
                Ktx2Constants.KTX2_SS_BASISLZ);

        assertTrue(Ktx2CppTranscoderApi.start_transcoding(fallbackPayload, fallbackPayload.length));
        try {
            assertEquals(0, Ktx2CppTranscoderApi.find_slice(0, 0, false));
            assertEquals(0, Ktx2CppTranscoderApi.find_slice(0, 0, true));
        } finally {
            Ktx2CppTranscoderApi.stop_transcoding();
        }
    }
}
