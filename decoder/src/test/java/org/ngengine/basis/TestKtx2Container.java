package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class TestKtx2Container {

    @Test
    public void parsesKtx2LevelIndexAndDfd() {
        byte[] payload = syntheticKtx2WithDfd(Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S, 4, 4);

        Ktx2Container container = Ktx2Container.parse(payload);

        assertEquals(1, container.getLevels().length);
        assertEquals(148, container.getLevel(0).getByteOffset());
        assertEquals(8, container.getLevel(0).getByteLength());
        assertEquals(Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S,
                container.getDataFormatDescriptor().getColorModel());
        assertEquals(4, container.getDataFormatDescriptor().getBlockWidth());
        assertEquals(4, container.getDataFormatDescriptor().getBlockHeight());
        assertEquals(Ktx2BasisTextureFormat.cETC1S, container.getBasisTextureFormat());
    }

    @Test
    public void rejectsInvalidDfdTransferFunction() {
        byte[] payload = syntheticKtx2WithDfd(Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S, 4, 4);
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(104 + 12, Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S | (99 << 16));

        assertThrows(BasisDecodeException.class, () -> Ktx2Container.parse(payload));
    }

    @Test
    public void rejectsLevelRangeOutsidePayload() {
        byte[] payload = syntheticKtx2WithDfd(Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S, 4, 4);
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(Ktx2Constants.KTX2_HEADER_SIZE + Long.BYTES, 1_000_000L);

        assertThrows(BasisDecodeException.class, () -> Ktx2Container.parse(payload));
    }

    @Test
    public void mapsXuastcBlockSizeToBasisTextureFormat() {
        byte[] payload = syntheticKtx2WithDfd(
                Ktx2Constants.KTX2_KDF_DF_MODEL_XUASTC_LDR_INTERMEDIATE,
                10,
                6);

        Ktx2Container container = Ktx2Container.parse(payload);

        assertEquals(Ktx2BasisTextureFormat.cXUASTC_LDR_10x6, container.getBasisTextureFormat());
    }

    @Test
    public void mapsAstcLdrVkFormatAndBlockSizeToBasisTextureFormat() {
        byte[] payload = syntheticKtx2WithDfd(
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                Ktx2Constants.KTX2_FORMAT_ASTC_8x6_UNORM_BLOCK,
                8,
                6);

        Ktx2Container container = Ktx2Container.parse(payload);

        assertEquals(Ktx2BasisTextureFormat.cASTC_LDR_8x6, container.getBasisTextureFormat());
    }

    @Test
    public void mapsAstcHdrSixBySixSfloatToBasisTextureFormat() {
        byte[] payload = syntheticKtx2WithDfd(
                Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC,
                Ktx2Constants.KTX2_FORMAT_ASTC_6x6_SFLOAT_BLOCK,
                6,
                6);

        Ktx2Container container = Ktx2Container.parse(payload);

        assertEquals(Ktx2BasisTextureFormat.cASTC_HDR_6x6, container.getBasisTextureFormat());
    }

    @Test
    public void parsesKtx2KeyValueData() {
        byte[] payload = syntheticKtx2WithDfd(
                Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S,
                Ktx2Constants.KTX2_VK_FORMAT_UNDEFINED,
                4,
                4,
                keyValueData("KTXwriter", "unit-test"));

        Ktx2Container container = Ktx2Container.parse(payload);

        Ktx2KeyValue entry = container.getKeyValueData().find("KTXwriter");
        assertEquals("unit-test", new String(entry.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    public void parsesXuastcSliceSupercompressionGlobalData() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");

        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange[] slices = container.getSupercompressionGlobalData().getSliceRanges();

        assertEquals(Ktx2BasisTextureFormat.cXUASTC_LDR_5x4, container.getBasisTextureFormat());
        assertEquals(1, slices.length);
        assertEquals(0, slices[0].getByteOffset());
        assertEquals(container.getLevel(0).getByteLength(), slices[0].getByteLength());
    }

    @Test
    public void parsesEtc1sSupercompressionGlobalData() {
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2Etc1sGlobalData globalData = container.getSupercompressionGlobalData().getEtc1sGlobalData();

        assertEquals(Ktx2BasisTextureFormat.cETC1S, container.getBasisTextureFormat());
        assertEquals(3867, globalData.getEndpointCount());
        assertEquals(16086, globalData.getSelectorCount());
        assertEquals(6531, globalData.getEndpointsByteLength());
        assertEquals(30057, globalData.getSelectorsByteLength());
        assertEquals(2971, globalData.getTablesByteLength());

        Ktx2Etc1sImageDesc[] imageDescriptors = globalData.getImageDescriptors();
        assertEquals(1, imageDescriptors.length);
        assertEquals(0, imageDescriptors[0].getRgbSliceByteOffset());
        assertEquals(container.getLevel(0).getByteLength(), imageDescriptors[0].getRgbSliceByteLength());
    }

    @Test
    public void decodesEtc1sCodebookHuffmanTablesFromNativeGoldFixture() {
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2Etc1sGlobalData globalData = container.getSupercompressionGlobalData().getEtc1sGlobalData();

        Etc1sCodebookTables tables = Etc1sCodebookTables.decode(
                payload,
                (int) globalData.getTablesByteOffset(),
                (int) globalData.getTablesByteLength());

        assertTrue(tables.getEndpointPredictionSymbolCount() > 0);
        assertTrue(tables.getEndpointDeltaSymbolCount() > 0);
        assertTrue(tables.getSelectorSymbolCount() > 0);
        assertTrue(tables.getSelectorHistoryRunLengthSymbolCount() > 0);
        assertTrue(tables.getSelectorHistoryBufferSize() > 0);
    }

    @Test
    public void decodesEtc1sEndpointAndSelectorPalettesFromNativeGoldFixture() {
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2Etc1sGlobalData globalData = container.getSupercompressionGlobalData().getEtc1sGlobalData();

        Etc1sPalettes palettes = Etc1sPalettes.decode(
                globalData.getEndpointCount(),
                payload,
                (int) globalData.getEndpointsByteOffset(),
                (int) globalData.getEndpointsByteLength(),
                globalData.getSelectorCount(),
                payload,
                (int) globalData.getSelectorsByteOffset(),
                (int) globalData.getSelectorsByteLength());

        assertEquals(globalData.getEndpointCount(), palettes.getEndpointCount());
        assertEquals(globalData.getSelectorCount(), palettes.getSelectorCount());
        assertTrue(palettes.getEndpoint(0).getRed5() >= 0);
        assertTrue(palettes.getSelector(0).getUniqueSelectorCount() > 0);
    }

    @Test
    public void decodesEtc1sRgbaFromNativeGoldFixture() {
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");
        byte[] expected = loadFixture("fixtures/native_gold/kodim23.dat");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2Etc1sGlobalData globalData = container.getSupercompressionGlobalData().getEtc1sGlobalData();
        Ktx2Etc1sImageDesc imageDesc = globalData.getImageDescriptors()[0];
        Etc1sCodebookTables tables = Etc1sCodebookTables.decode(
                payload,
                (int) globalData.getTablesByteOffset(),
                (int) globalData.getTablesByteLength());
        Etc1sPalettes palettes = Etc1sPalettes.decode(
                globalData.getEndpointCount(),
                payload,
                (int) globalData.getEndpointsByteOffset(),
                (int) globalData.getEndpointsByteLength(),
                globalData.getSelectorCount(),
                payload,
                (int) globalData.getSelectorsByteOffset(),
                (int) globalData.getSelectorsByteLength());

        byte[] actual = Etc1sRgbaDecoder.decode(
                payload,
                (int) (container.getLevel(0).getByteOffset() + imageDesc.getRgbSliceByteOffset()),
                (int) imageDesc.getRgbSliceByteLength(),
                container.getHeader().getPixelWidth(),
                container.getHeader().getPixelHeight(),
                tables,
                palettes);

        assertArrayEquals(expected, actual);
    }

    @Test
    public void parsesXuastcFullZstdPayloadHeader() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        XuastcLdrPayload header = XuastcLdrPayload.parse(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());

        assertEquals(XuastcLdrSyntax.FULL_ZSTD, header.getSyntax());
        assertEquals(2, header.getFlags());
        assertTrue(header.getTotalSectionBytes() > 0);
    }

    @Test
    public void parsesXuastcFullArithPayloadHeader() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        XuastcLdrPayload header = XuastcLdrPayload.parse(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());

        assertEquals(XuastcLdrSyntax.FULL_ARITH, header.getSyntax());
        assertEquals(0, header.getFlags());
        assertEquals(slice.getByteLength() - 1, header.getTotalSectionBytes());
    }

    @Test
    public void parsesXuastcFullZstdImageHeader() {
        XuastcHeaderFixture fixture = parseXuastcHeader("fixtures/ktx2/base_xuastc_zstd.ktx2");

        assertEquals(XuastcLdrSyntax.FULL_ZSTD, fixture.imageHeader.getSyntax());
        assertEquals(fixture.container.getHeader().getPixelWidth(), fixture.imageHeader.getWidth());
        assertEquals(fixture.container.getHeader().getPixelHeight(), fixture.imageHeader.getHeight());
        assertEquals(fixture.container.getDataFormatDescriptor().getBlockWidth(),
                fixture.imageHeader.getBlockWidth());
        assertEquals(fixture.container.getDataFormatDescriptor().getBlockHeight(),
                fixture.imageHeader.getBlockHeight());
    }

    @Test
    public void parsesXuastcFullArithImageHeader() {
        XuastcHeaderFixture fixture = parseXuastcHeader("fixtures/ktx2/base_xuastc_arith.ktx2");

        assertEquals(XuastcLdrSyntax.FULL_ARITH, fixture.imageHeader.getSyntax());
        assertEquals(fixture.container.getHeader().getPixelWidth(), fixture.imageHeader.getWidth());
        assertEquals(fixture.container.getHeader().getPixelHeight(), fixture.imageHeader.getHeight());
        assertEquals(fixture.container.getDataFormatDescriptor().getBlockWidth(),
                fixture.imageHeader.getBlockWidth());
        assertEquals(fixture.container.getDataFormatDescriptor().getBlockHeight(),
                fixture.imageHeader.getBlockHeight());
    }

    @Test
    public void summarizesXuastcFullArithModeStreamUntilFirstComplexMode() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        XuastcLdrModeStreamSummary summary = XuastcLdrModeStreamReader.summarize(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());

        assertEquals(13_184, summary.getTotalBlocks());
        assertEquals(0, summary.getDecodedBlocks());
        assertEquals(0, summary.getSolidBlocks());
        assertEquals(0, summary.getRunCopiedBlocks());
        assertEquals(1, summary.getExplicitModeCount(XuastcLdrMode.RAW));
        assertEquals(XuastcLdrMode.RAW, summary.getFirstUnsupportedMode());
        assertEquals(0, summary.getFirstUnsupportedBlockX());
        assertEquals(0, summary.getFirstUnsupportedBlockY());
    }

    @Test
    public void decodesFirstXuastcFullArithRawBlockConfig() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        XuastcLdrRawBlockConfig config = XuastcLdrRawBlockConfigReader.readFirst(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());

        assertEquals(0, config.getBlockX());
        assertEquals(0, config.getBlockY());
        assertEquals(3, config.getConfigReuseIndex());
        assertEquals(XuastcAstcConstants.CEM_LDR_RGB_DIRECT, config.getColorEndpointModeIndex());
        assertEquals(1, config.getSubsetIndex());
        assertEquals(0, config.getColorComponentSelectorIndex());
        assertEquals(1, config.getGridSizeIndex());
        assertEquals(0, config.getGridAnisoIndex());
        assertEquals(0, config.getSubmodeIndex());
        assertEquals(2877, config.getTrialModeIndex());
        assertEquals(XuastcAstcConstants.CEM_LDR_RGB_DIRECT, config.getActualColorEndpointMode());
        assertEquals(13, config.getUniquePatternIndex());
        assertEquals(16, config.getPartitionSeed());
        assertFalse(config.isBaseOffsetMode());
        assertFalse(config.isPartitionHashUsed());
        assertFalse(config.usesDpcmEndpoints());
        assertTrue(config.blockUsesDct());
        assertArrayEquals(new int[] {16, 32, 34, 34, 22, 22, 10, 18, 34, 34, 22, 14},
                config.getEndpoints());
        assertEquals(1, config.getDctWeightPlanes().length);
        XuastcLdrDctWeightPlane dctPlane = config.getDctWeightPlanes()[0];
        assertEquals(5, dctPlane.getDcSymbol());
        assertEquals(9, dctPlane.getDcLevels());
        assertArrayEquals(new int[] {0}, dctPlane.getZeroRuns());
        assertArrayEquals(new int[] {-2}, dctPlane.getCoefficients());
        XuastcLdrWeightGrid weightGrid = config.getWeightGrid();
        assertEquals(5, weightGrid.getGridWidth());
        assertEquals(4, weightGrid.getGridHeight());
        assertEquals(1, weightGrid.getPlaneCount());
        assertArrayEquals(new int[] {
                1, 1, 1, 2, 2,
                1, 1, 1, 2, 2,
                1, 1, 1, 2, 2,
                1, 1, 1, 2, 2
        }, weightGrid.getWeights());

        XuastcTrialMode trialMode = config.getTrialMode();
        assertEquals(5, trialMode.getGridWidth());
        assertEquals(4, trialMode.getGridHeight());
        assertEquals(12, trialMode.getEndpointIseRange());
        assertEquals(1, trialMode.getWeightIseRange());
        assertEquals(2, trialMode.getNumberOfPartitions());
    }

    @Test
    public void decodesFirstXuastcFullArithRawBlockPixelsAgainstNativeGold() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");
        byte[] nativeGold = loadFixture("fixtures/native_gold/base_xuastc_arith.dat");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];
        int sliceOffset = (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset());
        int sliceLength = (int) slice.getByteLength();
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(payload, sliceOffset, sliceLength);
        XuastcLdrRawBlockConfig config = XuastcLdrRawBlockConfigReader.readFirst(
                payload,
                sliceOffset,
                sliceLength);

        byte[] block = XuastcLdrBlockDecoder.decodeRgbaBlock(header, config);

        assertArrayEquals(topLeftRgbaBlock(nativeGold, header.getWidth(),
                header.getBlockWidth(), header.getBlockHeight()), block);
    }

    @Test
    public void decodesXuastcFullArithRgbaFromNativeGoldFixture() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");
        byte[] expected = loadFixture("fixtures/native_gold/base_xuastc_arith.dat");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        byte[] actual = XuastcLdrImageDecoder.decodeRgba(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());

        assertArrayEquals(expected, actual);
    }

    @Test
    public void decodesXuastcFullArithBlockConfigPrefix() {
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];

        XuastcLdrRawBlockConfig[] configs = XuastcLdrRawBlockConfigReader.readBlocks(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength(),
                13184);

        assertEquals(13059, configs.length);
        assertEquals(0, configs[0].getBlockX());
        assertEquals(0, configs[0].getBlockY());
        assertEquals(3, configs[0].getConfigReuseIndex());
        assertTrue(configs[1].usesDpcmEndpoints());
        assertEquals(XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE, configs[8].getActualColorEndpointMode());
        assertEquals(102, configs[13058].getBlockX());
        assertEquals(127, configs[13058].getBlockY());
    }

    @Test
    public void decodesXuastcRawWeightDeltasPerPlane() {
        assertArrayEquals(new int[] {1, 2, 1},
                XuastcLdrRawBlockConfigReader.decodeRawWeightSymbols(new int[] {0, 1, 2}, 1, 1));
        assertArrayEquals(new int[] {1, 2, 2, 1},
                XuastcLdrRawBlockConfigReader.decodeRawWeightSymbols(new int[] {0, 1, 1, 2}, 1, 2));
        assertThrows(BasisDecodeException.class,
                () -> XuastcLdrRawBlockConfigReader.decodeRawWeightSymbols(new int[] {3}, 1, 1));
    }

    private static byte[] syntheticKtx2WithDfd(int colorModel, int blockWidth, int blockHeight) {
        return syntheticKtx2WithDfd(
                colorModel,
                Ktx2Constants.KTX2_VK_FORMAT_UNDEFINED,
                blockWidth,
                blockHeight,
                new byte[0]);
    }

    private static byte[] syntheticKtx2WithDfd(
            int colorModel,
            int vkFormat,
            int blockWidth,
            int blockHeight) {
        return syntheticKtx2WithDfd(colorModel, vkFormat, blockWidth, blockHeight, new byte[0]);
    }

    private static byte[] syntheticKtx2WithDfd(
            int colorModel,
            int vkFormat,
            int blockWidth,
            int blockHeight,
            byte[] keyValueData) {
        int levelTableOffset = Ktx2Constants.KTX2_HEADER_SIZE;
        int dfdOffset = levelTableOffset + 24;
        int dfdLength = 44;
        int kvdOffset = keyValueData.length == 0 ? 0 : dfdOffset + dfdLength;
        int levelDataOffset = dfdOffset + dfdLength + keyValueData.length;
        byte[] payload = new byte[levelDataOffset + 8];
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(Ktx2Constants.KTX2_FILE_IDENTIFIER);
        buffer.putInt(vkFormat);
        buffer.putInt(1);
        buffer.putInt(4);
        buffer.putInt(4);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(1);
        buffer.putInt(1);
        buffer.putInt(Ktx2Constants.KTX2_SS_BASISLZ);
        buffer.putInt(dfdOffset);
        buffer.putInt(dfdLength);
        buffer.putInt(kvdOffset);
        buffer.putInt(keyValueData.length);
        buffer.putLong(0);
        buffer.putLong(0);

        buffer.position(levelTableOffset);
        buffer.putLong(levelDataOffset);
        buffer.putLong(8);
        buffer.putLong(8);

        buffer.position(dfdOffset);
        buffer.putInt(dfdLength);
        buffer.position(dfdOffset + 12);
        buffer.putInt(colorModel | (Ktx2Constants.KTX2_KHR_DF_TRANSFER_LINEAR << 16));
        buffer.position(dfdOffset + 16);
        buffer.putInt((blockWidth - 1) | ((blockHeight - 1) << 8));
        buffer.position(dfdOffset + 28);
        buffer.putInt(0);

        if (keyValueData.length != 0) {
            buffer.position(kvdOffset);
            buffer.put(keyValueData);
        }

        buffer.position(levelDataOffset);
        buffer.putLong(0x1122334455667788L);
        return payload;
    }

    private static byte[] keyValueData(String key, String value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        int entryLength = keyBytes.length + 1 + valueBytes.length;
        int padding = (4 - (entryLength & 3)) & 3;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + entryLength + padding)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(entryLength);
        buffer.put(keyBytes);
        buffer.put((byte) 0);
        buffer.put(valueBytes);
        return buffer.array();
    }

    private static XuastcHeaderFixture parseXuastcHeader(String fixtureName) {
        byte[] payload = loadFixture(fixtureName);
        Ktx2Container container = Ktx2Container.parse(payload);
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(
                payload,
                (int) (container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                (int) slice.getByteLength());
        return new XuastcHeaderFixture(container, header);
    }

    private static byte[] loadFixture(String name) {
        try (InputStream stream = TestKtx2Container.class.getClassLoader().getResourceAsStream(name)) {
            Objects.requireNonNull(stream, "Fixture not found: " + name);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[4096];
            int read;
            while ((read = stream.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Could not read fixture: " + name, exception);
        }
    }

    private static byte[] topLeftRgbaBlock(byte[] image, int imageWidth, int blockWidth, int blockHeight) {
        byte[] block = new byte[blockWidth * blockHeight * 4];
        for (int y = 0; y < blockHeight; y++) {
            System.arraycopy(image, y * imageWidth * 4, block, y * blockWidth * 4, blockWidth * 4);
        }
        return block;
    }

    private static final class XuastcHeaderFixture {
        private final Ktx2Container container;
        private final XuastcLdrImageHeader imageHeader;

        XuastcHeaderFixture(Ktx2Container container, XuastcLdrImageHeader imageHeader) {
            this.container = container;
            this.imageHeader = imageHeader;
        }
    }
}
