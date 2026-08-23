package org.ngengine.basis;

import java.util.ArrayList;

/**
 * Incremental reader for XUASTC LDR block configurations.
 */
final class XuastcLdrRawBlockConfigReader {
    private static final int MODE_COUNT = 6;
    private static final int CONFIG_REUSE_MODEL_COUNT = 4;
    private static final int CONFIG_REUSE_NEIGHBORS = 3;
    private static final int DCT_RUN_LENGTH_EOB = 64;
    private static final int DCT_MEAN_LEVELS_0 = 9;
    private static final int DCT_MEAN_LEVELS_1 = 33;
    private static final int PART_HASH_SIZE = 64;
    private static final int TM_HASH_SIZE = 128;
    private static final int TM_HASH_BITS = 7;
    private static final int FULL_ZSTD_MODE_RUN = 0x01;
    private static final int FULL_ZSTD_MODE_SOLID = 0x03;
    private static final int FULL_ZSTD_MODE_REUSE_CFG_ENDPOINTS_LEFT = 0x07;
    private static final int FULL_ZSTD_MODE_IS_BASE_OFFSET_FLAG = 1 << 3;
    private static final int FULL_ZSTD_MODE_PART_HASH_HIT_FLAG = 1 << 4;
    private static final int FULL_ZSTD_MODE_DPCM_ENDPOINTS_FLAG = 1 << 5;
    private static final int FULL_ZSTD_MODE_TM_HASH_HIT_FLAG = 1 << 6;
    private static final int FULL_ZSTD_MODE_USE_DCT_FLAG = 1 << 7;
    private static final int[][] ENDPOINT_REUSE_DELTAS = {
            {-1, 0}, {-2, 0}, {-3, 0}, {-4, 0},
            {3, -1}, {2, -1}, {1, -1}, {0, -1}, {-1, -1}, {-2, -1}, {-3, -1}, {-4, -1},
            {3, -2}, {2, -2}, {1, -2}, {0, -2}, {-1, -2}, {-2, -2}, {-3, -2}, {-4, -2},
            {3, -3}, {2, -3}, {1, -3}, {0, -3}, {-1, -3}, {-2, -3}, {-3, -3}, {-4, -3},
            {3, -4}, {2, -4}, {1, -4}, {0, -4}
    };

    private XuastcLdrRawBlockConfigReader() {
    }

    static XuastcLdrRawBlockConfig readFirst(byte[] data, int offset, int length) {
        XuastcLdrRawBlockConfig[] blocks = readBlocks(data, offset, length, 1);
        if (blocks.length == 0) {
            throw new BasisDecodeException("XUASTC stream did not contain a decodable complex block");
        }
        return blocks[0];
    }

    static XuastcLdrRawBlockConfig[] readBlocks(byte[] data, int offset, int length, int maxBlocks) {
        return readStream(data, offset, length, maxBlocks, true).getConfigs();
    }

    static XuastcLdrDecodedBlock[] readDecodedBlocks(byte[] data, int offset, int length) {
        return readStream(data, offset, length, Integer.MAX_VALUE, false).getDecodedBlocks();
    }

    private static StreamResult readStream(
            byte[] data,
            int offset,
            int length,
            int maxBlocks,
            boolean stopAtConfigLimit) {
        if (maxBlocks < 1) {
            throw new BasisDecodeException("XUASTC block count must be positive");
        }
        XuastcLdrPayload payload = XuastcLdrPayload.parse(data, offset, length);
        if (payload.getSyntax() == XuastcLdrSyntax.FULL_ZSTD) {
            return readFullZstdStream(data, offset, length, maxBlocks, stopAtConfigLimit);
        }
        if (payload.getSyntax() != XuastcLdrSyntax.FULL_ARITH) {
            throw new BasisDecodeException("XUASTC RAW config reader currently requires FULL_ARITH");
        }

        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        XuastcArithmeticDecoder decoder = new XuastcArithmeticDecoder(data, offset + 1, length - 1);
        consumeImageHeader(decoder);

        int blocksX = divideRoundUp(header.getWidth(), header.getBlockWidth());
        int blocksY = divideRoundUp(header.getHeight(), header.getBlockHeight());
        XuastcTrialModeTable trialModeTable =
                XuastcTrialModeTable.forBlockSize(header.getBlockWidth(), header.getBlockHeight());

        XuastcArithmeticDataModel modeModel = new XuastcArithmeticDataModel(MODE_COUNT);
        XuastcArithmeticDataModel[] solidColorModels = new XuastcArithmeticDataModel[] {
                new XuastcArithmeticDataModel(256, true),
                new XuastcArithmeticDataModel(256, true),
                new XuastcArithmeticDataModel(256, true),
                new XuastcArithmeticDataModel(256, true)
        };
        XuastcArithmeticDataModel[] configReuseModels = newConfigReuseModels();
        XuastcArithmeticDataModel[] cemIndexModels =
                newDataModels(8, XuastcAstcConstants.OTM_NUM_CEMS);
        XuastcArithmeticDataModel[] subsetIndexModels =
                newDataModels(XuastcAstcConstants.OTM_NUM_SUBSETS, XuastcAstcConstants.OTM_NUM_SUBSETS);
        XuastcArithmeticDataModel[] ccsIndexModels =
                newDataModels(XuastcAstcConstants.OTM_NUM_CCS, XuastcAstcConstants.OTM_NUM_CCS);
        XuastcArithmeticDataModel[] gridSizeModels =
                newDataModels(XuastcAstcConstants.OTM_NUM_GRID_SIZES, XuastcAstcConstants.OTM_NUM_GRID_SIZES);
        XuastcArithmeticDataModel[] gridAnisoModels =
                newDataModels(
                        XuastcAstcConstants.OTM_NUM_GRID_ANISOS,
                        XuastcAstcConstants.OTM_NUM_GRID_ANISOS);
        XuastcArithmeticDataModel[] submodeModels = new XuastcArithmeticDataModel[
                XuastcAstcConstants.OTM_NUM_CEMS
                        * XuastcAstcConstants.OTM_NUM_SUBSETS
                        * XuastcAstcConstants.OTM_NUM_CCS
                        * XuastcAstcConstants.OTM_NUM_GRID_SIZES
                        * XuastcAstcConstants.OTM_NUM_GRID_ANISOS];
        XuastcArithmeticDataModel[] rawEndpointModels = newRawEndpointModels();
        XuastcArithmeticDataModel dctRunLengthModel = new XuastcArithmeticDataModel(65);
        XuastcArithmeticDataModel dctCoefficientMagnitudeModel = new XuastcArithmeticDataModel(255);
        XuastcArithmeticDataModel[] weightMeanModels = {
                new XuastcArithmeticDataModel(DCT_MEAN_LEVELS_0),
                new XuastcArithmeticDataModel(DCT_MEAN_LEVELS_1)
        };
        XuastcArithmeticDataModel[] rawWeightModels = newRawWeightModels();
        XuastcArithmeticBitModel isBaseOffsetModel = new XuastcArithmeticBitModel();
        XuastcArithmeticBitModel[] usePartHashModels = newBitModels(4);
        XuastcArithmeticBitModel useDpcmEndpointsModel = new XuastcArithmeticBitModel();
        XuastcArithmeticBitModel[] useDctModels = newBitModels(4);
        XuastcArithmeticDataModel endpointReuseDeltaModel =
                new XuastcArithmeticDataModel(ENDPOINT_REUSE_DELTAS.length);
        XuastcArithmeticDataModel[] dpcmEndpointModels = newRawEndpointModels();
        XuastcArithmeticBitModel[] endpointsUseBcModels = newBitModels(4);
        XuastcArithmeticDataModel part2HashIndexModel = new XuastcArithmeticDataModel(PART_HASH_SIZE, true);
        XuastcArithmeticDataModel part3HashIndexModel = new XuastcArithmeticDataModel(PART_HASH_SIZE, true);
        XuastcArithmeticGammaContext runLengthContext = new XuastcArithmeticGammaContext();
        int[] part2Hash = newPartHash();
        int[] part3Hash = newPartHash();
        BlockState[][] blockStates = new BlockState[2][blocksX];
        XuastcLdrRawBlockConfig[][] blockConfigs = new XuastcLdrRawBlockConfig[8][blocksX];
        XuastcLdrDecodedBlock[][] decodedBlockGrid = new XuastcLdrDecodedBlock[8][blocksX];
        ArrayList<XuastcLdrRawBlockConfig> configs = new ArrayList<XuastcLdrRawBlockConfig>();
        ArrayList<XuastcLdrDecodedBlock> decodedBlocks = new ArrayList<XuastcLdrDecodedBlock>();

        int currentRunLength = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                BlockState leftState = bx > 0 ? blockStates[by & 1][bx - 1] : null;
                BlockState upperState = by > 0 ? blockStates[(by - 1) & 1][bx] : null;
                final BlockState diagState = bx > 0 && by > 0
                        ? blockStates[(by - 1) & 1][bx - 1]
                        : null;
                if (currentRunLength > 0) {
                    BlockState previousState = leftState != null ? leftState : upperState;
                    XuastcLdrRawBlockConfig previousConfig = previousBlockConfig(blockConfigs, bx, by);
                    XuastcLdrDecodedBlock copiedBlock = previousDecodedBlock(decodedBlockGrid, bx, by)
                            .copyTo(bx, by);
                    blockStates[by & 1][bx] = BlockState.copyForRun(previousState);
                    blockConfigs[by & 7][bx] = previousConfig;
                    decodedBlockGrid[by & 7][bx] = copiedBlock;
                    decodedBlocks.add(copiedBlock);
                    if (previousConfig != null) {
                        configs.add(previousConfig);
                    }
                    currentRunLength--;
                    if (stopAtConfigLimit && configs.size() >= maxBlocks) {
                        return new StreamResult(configs, decodedBlocks);
                    }
                    continue;
                }

                XuastcLdrMode mode = XuastcLdrMode.fromCode(decoder.decodeSymbol(modeModel));
                if (mode == XuastcLdrMode.SOLID) {
                    int[] solidColor = decodeSolidColor(
                            decoder,
                            solidColorModels,
                            header,
                            decodedBlockGrid,
                            bx,
                            by);
                    XuastcLdrDecodedBlock solidBlock = XuastcLdrDecodedBlock.solid(bx, by, solidColor);
                    blockStates[by & 1][bx] = BlockState.solid(header.usesDct());
                    blockConfigs[by & 7][bx] = null;
                    decodedBlockGrid[by & 7][bx] = solidBlock;
                    decodedBlocks.add(solidBlock);
                    continue;
                }
                if (mode == XuastcLdrMode.RUN) {
                    if (bx == 0 && by == 0) {
                        throw new BasisDecodeException("XUASTC run mode cannot appear at the first block");
                    }
                    currentRunLength = decoder.decodeGamma(runLengthContext);
                    if (currentRunLength <= 0 || currentRunLength > blocksX - bx) {
                        throw new BasisDecodeException("Invalid XUASTC run length");
                    }
                    BlockState previousState = leftState != null ? leftState : upperState;
                    XuastcLdrRawBlockConfig previousConfig = previousBlockConfig(blockConfigs, bx, by);
                    XuastcLdrDecodedBlock copiedBlock = previousDecodedBlock(decodedBlockGrid, bx, by)
                            .copyTo(bx, by);
                    blockStates[by & 1][bx] = BlockState.copyForRun(previousState);
                    blockConfigs[by & 7][bx] = previousConfig;
                    decodedBlockGrid[by & 7][bx] = copiedBlock;
                    decodedBlocks.add(copiedBlock);
                    if (previousConfig != null) {
                        configs.add(previousConfig);
                    }
                    currentRunLength--;
                    if (stopAtConfigLimit && configs.size() >= maxBlocks) {
                        return new StreamResult(configs, decodedBlocks);
                    }
                    continue;
                }
                XuastcLdrRawBlockConfig config;
                try {
                    config = decodeBlockConfig(
                            decoder,
                            trialModeTable,
                            configReuseModels,
                            cemIndexModels,
                            subsetIndexModels,
                            ccsIndexModels,
                            gridSizeModels,
                            gridAnisoModels,
                            submodeModels,
                            rawEndpointModels,
                            dctRunLengthModel,
                            dctCoefficientMagnitudeModel,
                            weightMeanModels,
                            isBaseOffsetModel,
                            usePartHashModels,
                            useDpcmEndpointsModel,
                            useDctModels,
                            rawWeightModels,
                            endpointReuseDeltaModel,
                            dpcmEndpointModels,
                            endpointsUseBcModels,
                            part2HashIndexModel,
                            part3HashIndexModel,
                            part2Hash,
                            part3Hash,
                            blockConfigs,
                            header,
                            mode,
                            leftState,
                            upperState,
                            diagState,
                            bx,
                            by);
                } catch (BasisDecodeException exception) {
                    throw new BasisDecodeException(
                            "XUASTC block " + bx + "," + by + " decode failed: " + exception.getMessage(),
                            exception);
                }
                blockStates[by & 1][bx] = BlockState.fromConfig(config);
                blockConfigs[by & 7][bx] = config;
                XuastcLdrDecodedBlock decodedBlock = XuastcLdrDecodedBlock.config(bx, by, config);
                decodedBlockGrid[by & 7][bx] = decodedBlock;
                decodedBlocks.add(decodedBlock);
                configs.add(config);
                if (stopAtConfigLimit && configs.size() >= maxBlocks) {
                    return new StreamResult(configs, decodedBlocks);
                }
            }
        }
        int finalMarker = decoder.getBits(8);
        if (finalMarker != 0xAF) {
            throw new BasisDecodeException("XUASTC final sync marker mismatch");
        }
        return new StreamResult(configs, decodedBlocks);
    }

    private static StreamResult readFullZstdStream(
            byte[] data,
            int offset,
            int length,
            int maxBlocks,
            boolean stopAtConfigLimit) {
        XuastcLdrImageHeader header = XuastcLdrImageHeader.parse(data, offset, length);
        FullZstdStreams streams = FullZstdStreams.parse(data, offset, length);
        consumeFullZstdImageHeader(streams.rawBits);

        int blocksX = divideRoundUp(header.getWidth(), header.getBlockWidth());
        int blocksY = divideRoundUp(header.getHeight(), header.getBlockHeight());
        XuastcTrialModeTable trialModeTable =
                XuastcTrialModeTable.forBlockSize(header.getBlockWidth(), header.getBlockHeight());
        int[] part2Hash = newPartHash();
        int[] part3Hash = newPartHash();
        int[] trialModeHash = new int[TM_HASH_SIZE];
        fillNegative(trialModeHash);
        BlockState[][] blockStates = new BlockState[2][blocksX];
        XuastcLdrRawBlockConfig[][] blockConfigs = new XuastcLdrRawBlockConfig[8][blocksX];
        XuastcLdrDecodedBlock[][] decodedBlockGrid = new XuastcLdrDecodedBlock[8][blocksX];
        ArrayList<XuastcLdrRawBlockConfig> configs = new ArrayList<XuastcLdrRawBlockConfig>();
        ArrayList<XuastcLdrDecodedBlock> decodedBlocks = new ArrayList<XuastcLdrDecodedBlock>();

        int currentRunLength = 0;
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                BlockState leftState = bx > 0 ? blockStates[by & 1][bx - 1] : null;
                BlockState upperState = by > 0 ? blockStates[(by - 1) & 1][bx] : null;
                final BlockState diagState = bx > 0 && by > 0 ? blockStates[(by - 1) & 1][bx - 1] : null;
                if (currentRunLength > 0) {
                    currentRunLength = copyFullZstdRunBlock(
                            blockStates,
                            blockConfigs,
                            decodedBlockGrid,
                            configs,
                            decodedBlocks,
                            bx,
                            by,
                            leftState,
                            upperState,
                            stopAtConfigLimit,
                            maxBlocks,
                            currentRunLength);
                    if (stopAtConfigLimit && configs.size() >= maxBlocks) {
                        return new StreamResult(configs, decodedBlocks);
                    }
                    continue;
                }

                int modeByte = streams.modeBytes.getBits8();
                if ((modeByte & 3) == FULL_ZSTD_MODE_RUN) {
                    if (bx == 0 && by == 0) {
                        throw new BasisDecodeException("XUASTC FULL_ZSTD run cannot start at first block");
                    }
                    currentRunLength = 1 + (modeByte >>> 2);
                    if (currentRunLength > blocksX - bx) {
                        throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD run length");
                    }
                    currentRunLength = copyFullZstdRunBlock(
                            blockStates,
                            blockConfigs,
                            decodedBlockGrid,
                            configs,
                            decodedBlocks,
                            bx,
                            by,
                            leftState,
                            upperState,
                            stopAtConfigLimit,
                            maxBlocks,
                            currentRunLength);
                    if (stopAtConfigLimit && configs.size() >= maxBlocks) {
                        return new StreamResult(configs, decodedBlocks);
                    }
                    continue;
                }

                if ((modeByte & 15) == FULL_ZSTD_MODE_SOLID) {
                    int[] solidColor = decodeFullZstdSolidColor(streams, header, decodedBlockGrid, bx, by);
                    XuastcLdrDecodedBlock solidBlock = XuastcLdrDecodedBlock.solid(bx, by, solidColor);
                    blockStates[by & 1][bx] = BlockState.solid(header.usesDct());
                    blockConfigs[by & 7][bx] = null;
                    decodedBlockGrid[by & 7][bx] = solidBlock;
                    decodedBlocks.add(solidBlock);
                    continue;
                }

                XuastcLdrRawBlockConfig config = decodeFullZstdConfig(
                        streams,
                        trialModeTable,
                        part2Hash,
                        part3Hash,
                        trialModeHash,
                        blockConfigs,
                        header,
                        modeByte,
                        leftState,
                        upperState,
                        diagState,
                        bx,
                        by);
                blockStates[by & 1][bx] = BlockState.fromConfig(config);
                blockConfigs[by & 7][bx] = config;
                XuastcLdrDecodedBlock decodedBlock = XuastcLdrDecodedBlock.config(bx, by, config);
                decodedBlockGrid[by & 7][bx] = decodedBlock;
                decodedBlocks.add(decodedBlock);
                configs.add(config);
                if (stopAtConfigLimit && configs.size() >= maxBlocks) {
                    return new StreamResult(configs, decodedBlocks);
                }
            }
        }
        if (currentRunLength != 0) {
            throw new BasisDecodeException("XUASTC FULL_ZSTD run length extends beyond row");
        }
        int finalMarker = streams.rawBits.getBits(8);
        if (finalMarker != 0xAF) {
            throw new BasisDecodeException("XUASTC FULL_ZSTD final sync marker mismatch");
        }
        return new StreamResult(configs, decodedBlocks);
    }

    private static int copyFullZstdRunBlock(
            BlockState[][] blockStates,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            XuastcLdrDecodedBlock[][] decodedBlockGrid,
            ArrayList<XuastcLdrRawBlockConfig> configs,
            ArrayList<XuastcLdrDecodedBlock> decodedBlocks,
            int blockX,
            int blockY,
            BlockState leftState,
            BlockState upperState,
            boolean stopAtConfigLimit,
            int maxBlocks,
            int currentRunLength) {
        BlockState previousState = leftState != null ? leftState : upperState;
        XuastcLdrRawBlockConfig previousConfig = previousBlockConfig(blockConfigs, blockX, blockY);
        XuastcLdrDecodedBlock copiedBlock = previousDecodedBlock(decodedBlockGrid, blockX, blockY)
                .copyTo(blockX, blockY);
        blockStates[blockY & 1][blockX] = BlockState.copyForRun(previousState);
        blockConfigs[blockY & 7][blockX] = previousConfig;
        decodedBlockGrid[blockY & 7][blockX] = copiedBlock;
        decodedBlocks.add(copiedBlock);
        if (previousConfig != null) {
            configs.add(previousConfig);
        }
        int remaining = currentRunLength - 1;
        if (stopAtConfigLimit && configs.size() >= maxBlocks) {
            return remaining;
        }
        return remaining;
    }

    private static int[] decodeFullZstdSolidColor(
            FullZstdStreams streams,
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[][] decodedBlocks,
            int blockX,
            int blockY) {
        int[] prediction = predictSolidColor(decodedBlocks, blockX, blockY);
        int red = (prediction[0] + streams.solidDpcmBytes.getBits8()) & 0xFF;
        int green = (prediction[1] + streams.solidDpcmBytes.getBits8()) & 0xFF;
        int blue = (prediction[2] + streams.solidDpcmBytes.getBits8()) & 0xFF;
        int alpha = header.hasAlpha()
                ? (prediction[3] + streams.solidDpcmBytes.getBits8()) & 0xFF
                : 255;
        return new int[] {red, green, blue, alpha};
    }

    private static XuastcLdrRawBlockConfig decodeFullZstdConfig(
            FullZstdStreams streams,
            XuastcTrialModeTable trialModeTable,
            int[] part2Hash,
            int[] part3Hash,
            int[] trialModeHash,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            XuastcLdrImageHeader header,
            int modeByte,
            BlockState leftState,
            BlockState upperState,
            BlockState diagState,
            int blockX,
            int blockY) {
        int configReuseIndex = -1;
        int trialModeIndex;
        XuastcTrialMode trialMode;
        int actualCem;
        boolean baseOffsetMode = false;
        int uniquePatternIndex = -1;
        int partitionSeed = 0;
        boolean partitionHashUsed = true;
        int[] endpoints = null;

        if ((modeByte & 1) == 0) {
            configReuseIndex = (modeByte >>> 1) & 3;
            if (configReuseIndex < CONFIG_REUSE_NEIGHBORS) {
                NeighborBlock neighbor = neighborForConfigReuse(
                        configReuseIndex,
                        leftState,
                        upperState,
                        diagState,
                        blockConfigs,
                        blockX,
                        blockY);
                trialModeIndex = neighbor.state.trialModeIndex;
                trialMode = trialModeTable.get(trialModeIndex);
                actualCem = neighbor.config.getActualColorEndpointMode();
                baseOffsetMode = neighbor.config.isBaseOffsetMode();
                uniquePatternIndex = neighbor.config.getUniquePatternIndex();
                partitionSeed = neighbor.config.getPartitionSeed();
                partitionHashUsed = neighbor.state.usedPartHash;
            } else {
                if ((modeByte & FULL_ZSTD_MODE_TM_HASH_HIT_FLAG) != 0) {
                    trialModeIndex = trialModeHash[streams.rawBits.getBits(TM_HASH_BITS)];
                    if (trialModeIndex < 0) {
                        throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD trial-mode hash entry");
                    }
                } else {
                    trialModeIndex = streams.rawBits.decodeTruncatedBinary(trialModeTable.size());
                    trialModeHash[tmHashIndex(trialModeIndex)] = trialModeIndex;
                }
                trialMode = trialModeTable.get(trialModeIndex);
                actualCem = trialMode.getColorEndpointMode();
                if (XuastcAstcConstants.isDirectCem(actualCem)) {
                    baseOffsetMode = (modeByte & FULL_ZSTD_MODE_IS_BASE_OFFSET_FLAG) != 0;
                    if (baseOffsetMode) {
                        actualCem = actualCem == XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                                ? XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET
                                : XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET;
                    }
                }
                if (trialMode.getNumberOfPartitions() > 1) {
                    int[] partHash = trialMode.getNumberOfPartitions() == 2 ? part2Hash : part3Hash;
                    partitionHashUsed = (modeByte & FULL_ZSTD_MODE_PART_HASH_HIT_FLAG) != 0;
                    if (partitionHashUsed) {
                        uniquePatternIndex = partHash[streams.rawBits.getBits(6)];
                        if (uniquePatternIndex < 0) {
                            throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD partition hash entry");
                        }
                    } else {
                        int blockSizeIndex = XuastcLdrImageHeader.findAstcBlockSizeIndex(
                                header.getBlockWidth(),
                                header.getBlockHeight());
                        int totalUniquePatterns = XuastcAstcConstants.getTotalUniquePatterns(
                                blockSizeIndex,
                                trialMode.getNumberOfPartitions());
                        uniquePatternIndex = streams.rawBits.decodeTruncatedBinary(totalUniquePatterns);
                        partHash[partHashIndex(uniquePatternIndex)] = uniquePatternIndex;
                    }
                    partitionSeed = XuastcAstcPartitionSeeds.seedForUniquePattern(
                            header.getBlockWidth(),
                            header.getBlockHeight(),
                            trialMode.getNumberOfPartitions(),
                            uniquePatternIndex);
                }
            }
        } else if ((modeByte & 15) >= FULL_ZSTD_MODE_REUSE_CFG_ENDPOINTS_LEFT) {
            int reuseIndex = ((modeByte >>> 2) & 3) - 1;
            XuastcLdrMode mode = XuastcLdrMode.fromCode(XuastcLdrMode.REUSE_CFG_ENDPOINTS_LEFT.getCode()
                    + reuseIndex);
            NeighborBlock neighbor = neighborForEndpointReuse(
                    mode,
                    leftState,
                    upperState,
                    diagState,
                    blockConfigs,
                    blockX,
                    blockY);
            configReuseIndex = -mode.getCode();
            trialModeIndex = neighbor.state.trialModeIndex;
            trialMode = trialModeTable.get(trialModeIndex);
            actualCem = neighbor.config.getActualColorEndpointMode();
            baseOffsetMode = neighbor.config.isBaseOffsetMode();
            uniquePatternIndex = neighbor.config.getUniquePatternIndex();
            partitionSeed = neighbor.config.getPartitionSeed();
            partitionHashUsed = neighbor.state.usedPartHash;
            endpoints = neighbor.config.getEndpoints();
        } else {
            throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD mode byte");
        }

        int totalEndpointValues = XuastcAstcConstants.numCemEndpointValues(actualCem);
        boolean dpcmEndpoints = (modeByte & FULL_ZSTD_MODE_DPCM_ENDPOINTS_FLAG) != 0;
        if (endpoints == null) {
            endpoints = new int[trialMode.getNumberOfPartitions() * totalEndpointValues];
            if (dpcmEndpoints) {
                decodeFullZstdDpcmEndpoints(
                        streams,
                        blockConfigs,
                        blockX,
                        blockY,
                        trialMode,
                        actualCem,
                        totalEndpointValues,
                        endpoints);
            } else {
                endpoints = decodeValues(streams.rawBits, endpoints.length, trialMode.getEndpointIseRange());
            }
        }

        boolean blockUsesDct = header.usesDct() && (modeByte & FULL_ZSTD_MODE_USE_DCT_FLAG) != 0;
        XuastcLdrDctWeightPlane[] dctWeightPlanes = blockUsesDct
                ? decodeFullZstdDctWeightPlanes(streams, trialMode)
                : new XuastcLdrDctWeightPlane[0];
        XuastcLdrWeightGrid weightGrid = blockUsesDct
                ? reconstructDctWeightGrid(header, trialMode, actualCem, endpoints, dctWeightPlanes)
                : decodeFullZstdRawWeightGrid(streams, trialMode);

        int cemIndex = trialMode.getColorEndpointMode();
        int subsetIndex = trialMode.getNumberOfPartitions() - 1;
        int ccsIndex = trialMode.getColorComponentSelector() + 1;
        int gridSizeIndex = trialMode.getGridWidth() >= header.getBlockWidth() - 1
                && trialMode.getGridHeight() >= header.getBlockHeight() - 1 ? 1 : 0;
        int gridAnisoIndex = XuastcAstcConstants.gridAniso(
                trialMode.getGridWidth(),
                trialMode.getGridHeight(),
                header.getBlockWidth(),
                header.getBlockHeight());
        return new XuastcLdrRawBlockConfig(
                blockX,
                blockY,
                configReuseIndex,
                cemIndex,
                subsetIndex,
                ccsIndex,
                gridSizeIndex,
                gridAnisoIndex,
                0,
                trialModeIndex,
                trialMode,
                actualCem,
                baseOffsetMode,
                uniquePatternIndex,
                partitionSeed,
                partitionHashUsed,
                dpcmEndpoints,
                endpoints,
                blockUsesDct,
                dctWeightPlanes,
                weightGrid);
    }

    private static void decodeFullZstdDpcmEndpoints(
            FullZstdStreams streams,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            int blockX,
            int blockY,
            XuastcTrialMode trialMode,
            int actualCem,
            int endpointValuesPerPartition,
            int[] endpoints) {
        int reuseDeltaIndex = streams.endpointDpcmReuseIndices.getBits8();
        if (reuseDeltaIndex < 0 || reuseDeltaIndex >= ENDPOINT_REUSE_DELTAS.length) {
            throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD endpoint reuse delta");
        }
        int reuseBlockX = blockX + ENDPOINT_REUSE_DELTAS[reuseDeltaIndex][0];
        int reuseBlockY = blockY + ENDPOINT_REUSE_DELTAS[reuseDeltaIndex][1];
        if (reuseBlockX < 0 || reuseBlockY < 0 || reuseBlockX >= blockConfigs[0].length) {
            throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD endpoint reuse block");
        }
        XuastcLdrRawBlockConfig predictedConfig = blockConfigs[reuseBlockY & 7][reuseBlockX];
        if (predictedConfig == null) {
            throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD endpoint DPCM predictor");
        }

        boolean[] endpointsUseBlueContraction = new boolean[trialMode.getNumberOfPartitions()];
        if (cemSupportsBlueContraction(actualCem)) {
            for (int partition = 0; partition < endpointsUseBlueContraction.length; partition++) {
                endpointsUseBlueContraction[partition] = streams.useBlueContractionBits.getBits1() != 0;
            }
        }
        int endpointIseRange = trialMode.getEndpointIseRange();
        int levels = XuastcAstcConstants.getIseLevels(endpointIseRange);
        int[] predictedEndpoints = new int[endpoints.length];
        for (int partition = 0; partition < trialMode.getNumberOfPartitions(); partition++) {
            requantizePredictedEndpoints(
                    predictedConfig,
                    actualCem,
                    endpointIseRange,
                    endpointValuesPerPartition,
                    predictedEndpoints,
                    partition * endpointValuesPerPartition,
                    endpointsUseBlueContraction[partition]);
        }

        SimpleBitReader deltaReader = streams.endpointDeltaReader(levels);
        int deltaBits = deltaBitCount(levels);
        for (int i = 0; i < endpoints.length; i++) {
            int delta = deltaReader.getBits(deltaBits);
            int predictedRank = XuastcAstcConstants.endpointIseToRank(
                    predictedEndpoints[i],
                    endpointIseRange);
            int rank = positiveMod(predictedRank + delta, levels);
            endpoints[i] = XuastcAstcConstants.endpointRankToIse(rank, endpointIseRange);
        }
    }

    private static XuastcLdrDctWeightPlane[] decodeFullZstdDctWeightPlanes(
            FullZstdStreams streams,
            XuastcTrialMode trialMode) {
        int totalPlanes = trialMode.getColorComponentSelector() >= 0 ? 2 : 1;
        int totalWeights = trialMode.getGridWidth() * trialMode.getGridHeight();
        int dcLevels = getDctDcLevels(trialMode.getWeightIseRange());
        XuastcLdrDctWeightPlane[] planes = new XuastcLdrDctWeightPlane[totalPlanes];
        for (int planeIndex = 0; planeIndex < totalPlanes; planeIndex++) {
            int dcSymbol = dcLevels == DCT_MEAN_LEVELS_1
                    ? streams.mean1Bytes.getBits8()
                    : streams.mean0Bits.getBits4();
            int zigzagOffset = 1;
            ArrayList<Integer> zeroRuns = new ArrayList<Integer>();
            ArrayList<Integer> coefficients = new ArrayList<Integer>();
            while (zigzagOffset < totalWeights) {
                int runLength = streams.runBytes.getBits8();
                if (runLength == DCT_RUN_LENGTH_EOB) {
                    break;
                }
                zigzagOffset += runLength;
                if (zigzagOffset >= totalWeights) {
                    throw new BasisDecodeException("XUASTC FULL_ZSTD DCT run length exceeds weight grid");
                }
                int coefficient = streams.coeffBytes.getBits8() + 1;
                if (streams.signBits.getBits1() != 0) {
                    coefficient = -coefficient;
                }
                zeroRuns.add(runLength);
                coefficients.add(coefficient);
                zigzagOffset++;
            }
            planes[planeIndex] = new XuastcLdrDctWeightPlane(
                    dcSymbol,
                    dcLevels,
                    toIntArray(zeroRuns),
                    toIntArray(coefficients));
        }
        return planes;
    }

    private static XuastcLdrWeightGrid decodeFullZstdRawWeightGrid(
            FullZstdStreams streams,
            XuastcTrialMode trialMode) {
        int planeCount = trialMode.getColorComponentSelector() >= 0 ? 2 : 1;
        int totalWeights = trialMode.getGridWidth() * trialMode.getGridHeight();
        int levels = XuastcAstcConstants.getIseLevels(trialMode.getWeightIseRange());
        int deltaBits = weightDeltaBitCount(levels);
        SimpleBitReader reader = streams.weightReader(levels);
        int[] deltas = new int[totalWeights * planeCount];
        for (int plane = 0; plane < planeCount; plane++) {
            for (int weight = 0; weight < totalWeights; weight++) {
                deltas[weight * planeCount + plane] = reader.getBits(deltaBits);
            }
        }
        return new XuastcLdrWeightGrid(
                trialMode.getGridWidth(),
                trialMode.getGridHeight(),
                planeCount,
                decodeRawWeightSymbols(deltas, trialMode.getWeightIseRange(), planeCount));
    }

    private static int[] decodeValues(BasisBitReader reader, int totalValues, int iseRange) {
        int bits = XuastcAstcConstants.getIseBitCount(iseRange);
        int trits = XuastcAstcConstants.getIseTritCount(iseRange);
        int quints = XuastcAstcConstants.getIseQuintCount(iseRange);
        int bundleSize = 0;
        int multiplier = 0;
        int totalTqValues = 0;
        if (trits != 0) {
            totalTqValues = (totalValues + 4) / 5;
            bundleSize = 5;
            multiplier = 3;
        } else if (quints != 0) {
            totalTqValues = (totalValues + 2) / 3;
            bundleSize = 3;
            multiplier = 5;
        }
        int[] tqValues = new int[totalTqValues];
        for (int i = 0; i < totalTqValues; i++) {
            int tqBits = trits != 0 ? 8 : 7;
            if (i == totalTqValues - 1) {
                int remaining = totalValues - (totalTqValues - 1) * bundleSize;
                if (trits != 0) {
                    tqBits = tqBitsForLastTritBundle(remaining);
                } else if (quints != 0) {
                    tqBits = remaining == 1 ? 3 : remaining == 2 ? 5 : 7;
                }
            }
            tqValues[i] = reader.getBits(tqBits);
        }
        int[] values = new int[totalValues];
        int accumulator = 0;
        int accumulatorRemaining = 0;
        int nextTqIndex = 0;
        for (int i = 0; i < totalValues; i++) {
            int value = reader.getBits(bits);
            if (totalTqValues != 0) {
                if (accumulatorRemaining == 0) {
                    accumulator = tqValues[nextTqIndex++];
                    accumulatorRemaining = bundleSize;
                }
                int tq = accumulator % multiplier;
                accumulator /= multiplier;
                accumulatorRemaining--;
                value |= tq << bits;
            }
            values[i] = value;
        }
        return values;
    }

    private static int tqBitsForLastTritBundle(int remaining) {
        switch (remaining) {
            case 1:
                return 2;
            case 2:
                return 4;
            case 3:
                return 5;
            case 4:
                return 7;
            default:
                return 8;
        }
    }

    private static void consumeFullZstdImageHeader(BasisBitReader reader) {
        reader.getBits(5);
        reader.getBits(4);
        reader.getBits(1);
        reader.getBits(16);
        reader.getBits(16);
        reader.getBits(1);
        boolean usesDct = reader.getBits(1) != 0;
        if (usesDct) {
            reader.getBits(8);
        }
    }

    private static int deltaBitCount(int levels) {
        if (levels <= 16) {
            return 4;
        }
        return 8;
    }

    private static int weightDeltaBitCount(int levels) {
        if (levels <= 4) {
            return 2;
        }
        if (levels <= 16) {
            return 4;
        }
        return 8;
    }

    private static int tmHashIndex(int trialModeIndex) {
        return (int) ((trialModeIndex * 2_654_435_769L) & (TM_HASH_SIZE - 1));
    }

    private static XuastcLdrRawBlockConfig decodeBlockConfig(
            XuastcArithmeticDecoder decoder,
            XuastcTrialModeTable trialModeTable,
            XuastcArithmeticDataModel[] configReuseModels,
            XuastcArithmeticDataModel[] cemIndexModels,
            XuastcArithmeticDataModel[] subsetIndexModels,
            XuastcArithmeticDataModel[] ccsIndexModels,
            XuastcArithmeticDataModel[] gridSizeModels,
            XuastcArithmeticDataModel[] gridAnisoModels,
            XuastcArithmeticDataModel[] submodeModels,
            XuastcArithmeticDataModel[] rawEndpointModels,
            XuastcArithmeticDataModel dctRunLengthModel,
            XuastcArithmeticDataModel dctCoefficientMagnitudeModel,
            XuastcArithmeticDataModel[] weightMeanModels,
            XuastcArithmeticBitModel isBaseOffsetModel,
            XuastcArithmeticBitModel[] usePartHashModels,
            XuastcArithmeticBitModel useDpcmEndpointsModel,
            XuastcArithmeticBitModel[] useDctModels,
            XuastcArithmeticDataModel[] rawWeightModels,
            XuastcArithmeticDataModel endpointReuseDeltaModel,
            XuastcArithmeticDataModel[] dpcmEndpointModels,
            XuastcArithmeticBitModel[] endpointsUseBcModels,
            XuastcArithmeticDataModel part2HashIndexModel,
            XuastcArithmeticDataModel part3HashIndexModel,
            int[] part2Hash,
            int[] part3Hash,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            XuastcLdrImageHeader header,
            XuastcLdrMode mode,
            BlockState leftState,
            BlockState upperState,
            BlockState diagState,
            int blockX,
            int blockY) {
        int configReuseIndex = -1;
        int cemIndex;
        int subsetIndex;
        int ccsIndex;
        int gridSizeIndex;
        int gridAnisoIndex;
        int submodeIndex = 0;
        int trialModeIndex;
        XuastcTrialMode trialMode;
        int actualCem;
        boolean baseOffsetMode = false;
        int uniquePatternIndex = -1;
        int partitionSeed = 0;
        boolean partitionHashUsed = true;
        int[] endpoints = null;

        if (mode != XuastcLdrMode.RAW) {
            NeighborBlock neighbor = neighborForEndpointReuse(
                    mode,
                    leftState,
                    upperState,
                    diagState,
                    blockConfigs,
                    blockX,
                    blockY);
            configReuseIndex = -mode.getCode();
            cemIndex = neighbor.state.baseCemIndex;
            subsetIndex = neighbor.state.subsetIndex;
            ccsIndex = neighbor.state.ccsIndex;
            gridSizeIndex = neighbor.state.gridSizeIndex;
            gridAnisoIndex = neighbor.state.gridAnisoIndex;
            trialModeIndex = neighbor.state.trialModeIndex;
            trialMode = trialModeTable.get(trialModeIndex);
            actualCem = neighbor.config.getActualColorEndpointMode();
            baseOffsetMode = neighbor.config.isBaseOffsetMode();
            uniquePatternIndex = neighbor.config.getUniquePatternIndex();
            partitionSeed = neighbor.config.getPartitionSeed();
            partitionHashUsed = neighbor.state.usedPartHash;
            endpoints = neighbor.config.getEndpoints();
        } else {
            int reuseModelIndex = neighborFlagModelIndex(leftState, upperState, FlagField.REUSED_FULL_CONFIG);
            configReuseIndex = decoder.decodeSymbol(configReuseModels[reuseModelIndex]);
            if (configReuseIndex < CONFIG_REUSE_NEIGHBORS) {
                NeighborBlock neighbor = neighborForConfigReuse(
                        configReuseIndex,
                        leftState,
                        upperState,
                        diagState,
                        blockConfigs,
                        blockX,
                        blockY);
                cemIndex = neighbor.state.baseCemIndex;
                subsetIndex = neighbor.state.subsetIndex;
                ccsIndex = neighbor.state.ccsIndex;
                gridSizeIndex = neighbor.state.gridSizeIndex;
                gridAnisoIndex = neighbor.state.gridAnisoIndex;
                trialModeIndex = neighbor.state.trialModeIndex;
                trialMode = trialModeTable.get(trialModeIndex);
                actualCem = neighbor.config.getActualColorEndpointMode();
                baseOffsetMode = neighbor.config.isBaseOffsetMode();
                uniquePatternIndex = neighbor.config.getUniquePatternIndex();
                partitionSeed = neighbor.config.getPartitionSeed();
                partitionHashUsed = neighbor.state.usedPartHash;
            } else {
                BlockState predictor = leftState != null ? leftState : upperState;
                int prevCemIndex = predictor != null
                        ? predictor.baseCemIndex
                        : XuastcAstcConstants.CEM_LDR_RGB_DIRECT;
                int prevSubsetIndex = predictor != null ? predictor.subsetIndex : 0;
                int prevCcsIndex = predictor != null ? predictor.ccsIndex : 0;
                final int prevGridSizeIndex = predictor != null ? predictor.gridSizeIndex : 0;
                final int prevGridAnisoIndex = predictor != null ? predictor.gridAnisoIndex : 0;

                cemIndex = decoder.decodeSymbol(cemIndexModels[
                        XuastcAstcConstants.cemToLdrCemIndex(prevCemIndex)]);
                subsetIndex = decoder.decodeSymbol(subsetIndexModels[prevSubsetIndex]);
                ccsIndex = decoder.decodeSymbol(ccsIndexModels[prevCcsIndex]);
                gridSizeIndex = decoder.decodeSymbol(gridSizeModels[prevGridSizeIndex]);
                gridAnisoIndex = decoder.decodeSymbol(gridAnisoModels[prevGridAnisoIndex]);
                int[] candidates = trialModeTable.getCandidateIndices(
                        cemIndex,
                        subsetIndex,
                        ccsIndex,
                        gridSizeIndex,
                        gridAnisoIndex);
                if (candidates.length == 0) {
                    throw new BasisDecodeException("XUASTC RAW config has no ASTC trial-mode candidates");
                }

                if (candidates.length > 1) {
                    int modelIndex = submodeModelIndex(
                            cemIndex,
                            subsetIndex,
                            ccsIndex,
                            gridSizeIndex,
                            gridAnisoIndex);
                    XuastcArithmeticDataModel submodeModel = submodeModels[modelIndex];
                    if (submodeModel == null) {
                        submodeModel = new XuastcArithmeticDataModel(candidates.length, true);
                        submodeModels[modelIndex] = submodeModel;
                    }
                    submodeIndex = decoder.decodeSymbol(submodeModel);
                }
                if (submodeIndex >= candidates.length) {
                    throw new BasisDecodeException("Invalid XUASTC RAW submode index");
                }

                trialModeIndex = candidates[submodeIndex];
                trialMode = trialModeTable.get(trialModeIndex);
                actualCem = trialMode.getColorEndpointMode();
                if (XuastcAstcConstants.isDirectCem(actualCem)) {
                    baseOffsetMode = decoder.decodeBit(isBaseOffsetModel) != 0;
                    if (baseOffsetMode) {
                        actualCem = actualCem == XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                                ? XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET
                                : XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET;
                    }
                }
                if (trialMode.getNumberOfPartitions() > 1) {
                    int usePartModelIndex = neighborFlagModelIndex(
                            leftState,
                            upperState,
                            FlagField.USED_PART_HASH);
                    partitionHashUsed = decoder.decodeBit(usePartHashModels[usePartModelIndex]) != 0;
                    int[] partHash = trialMode.getNumberOfPartitions() == 2 ? part2Hash : part3Hash;
                    if (partitionHashUsed) {
                        XuastcArithmeticDataModel partHashModel = trialMode.getNumberOfPartitions() == 2
                                ? part2HashIndexModel
                                : part3HashIndexModel;
                        uniquePatternIndex = partHash[decoder.decodeSymbol(partHashModel)];
                        if (uniquePatternIndex < 0) {
                            throw new BasisDecodeException("Invalid XUASTC partition hash entry");
                        }
                    } else {
                        int blockSizeIndex = XuastcLdrImageHeader.findAstcBlockSizeIndex(
                                header.getBlockWidth(),
                                header.getBlockHeight());
                        int totalUniquePatterns = XuastcAstcConstants.getTotalUniquePatterns(
                                blockSizeIndex,
                                trialMode.getNumberOfPartitions());
                        uniquePatternIndex = decoder.decodeTruncatedBinary(totalUniquePatterns);
                        partHash[partHashIndex(uniquePatternIndex)] = uniquePatternIndex;
                    }
                    partitionSeed = XuastcAstcPartitionSeeds.seedForUniquePattern(
                            header.getBlockWidth(),
                            header.getBlockHeight(),
                            trialMode.getNumberOfPartitions(),
                            uniquePatternIndex);
                }
            }
        }

        boolean dpcmEndpoints = false;
        int totalEndpointValues = XuastcAstcConstants.numCemEndpointValues(actualCem);
        if (mode == XuastcLdrMode.RAW) {
            dpcmEndpoints = decoder.decodeBit(useDpcmEndpointsModel) != 0;
        }
        if (endpoints == null) {
            endpoints = new int[trialMode.getNumberOfPartitions() * totalEndpointValues];
            if (dpcmEndpoints) {
                decodeDpcmEndpoints(
                        decoder,
                        endpointReuseDeltaModel,
                        dpcmEndpointModels,
                        endpointsUseBcModels,
                        blockConfigs,
                        leftState,
                        upperState,
                        blockX,
                        blockY,
                        trialMode,
                        actualCem,
                        totalEndpointValues,
                        endpoints);
            } else {
                XuastcArithmeticDataModel endpointModel =
                        rawEndpointModels[trialMode.getEndpointIseRange()
                                - XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE];
                for (int i = 0; i < endpoints.length; i++) {
                    endpoints[i] = decoder.decodeSymbol(endpointModel);
                }
            }
        }

        boolean blockUsesDct = false;
        if (header.usesDct()) {
            int useDctModelIndex = neighborFlagModelIndex(leftState, upperState, FlagField.USED_WEIGHT_DCT);
            blockUsesDct = decoder.decodeBit(useDctModels[useDctModelIndex]) != 0;
        }
        XuastcLdrDctWeightPlane[] dctWeightPlanes = new XuastcLdrDctWeightPlane[0];
        if (blockUsesDct) {
            dctWeightPlanes = decodeDctWeightPlanes(
                    decoder,
                    trialMode,
                    dctRunLengthModel,
                    dctCoefficientMagnitudeModel,
                    weightMeanModels);
        }
        XuastcLdrWeightGrid weightGrid = blockUsesDct
                ? reconstructDctWeightGrid(header, trialMode, actualCem, endpoints, dctWeightPlanes)
                : decodeRawWeightGrid(decoder, trialMode, rawWeightModels);

        return new XuastcLdrRawBlockConfig(
                blockX,
                blockY,
                configReuseIndex,
                cemIndex,
                subsetIndex,
                ccsIndex,
                gridSizeIndex,
                gridAnisoIndex,
                submodeIndex,
                trialModeIndex,
                trialMode,
                actualCem,
                baseOffsetMode,
                uniquePatternIndex,
                partitionSeed,
                partitionHashUsed,
                dpcmEndpoints,
                endpoints,
                blockUsesDct,
                dctWeightPlanes,
                weightGrid);
    }

    private static XuastcLdrWeightGrid decodeRawWeightGrid(
            XuastcArithmeticDecoder decoder,
            XuastcTrialMode trialMode,
            XuastcArithmeticDataModel[] rawWeightModels) {
        int planeCount = trialMode.getColorComponentSelector() >= 0 ? 2 : 1;
        int totalWeights = trialMode.getGridWidth() * trialMode.getGridHeight();
        int[] deltas = new int[totalWeights * planeCount];
        XuastcArithmeticDataModel model = rawWeightModels[
                trialMode.getWeightIseRange() - XuastcAstcConstants.FIRST_VALID_WEIGHT_ISE_RANGE];
        for (int plane = 0; plane < planeCount; plane++) {
            for (int weight = 0; weight < totalWeights; weight++) {
                deltas[weight * planeCount + plane] = decoder.decodeSymbol(model);
            }
        }
        return new XuastcLdrWeightGrid(
                trialMode.getGridWidth(),
                trialMode.getGridHeight(),
                planeCount,
                decodeRawWeightSymbols(deltas, trialMode.getWeightIseRange(), planeCount));
    }

    static int[] decodeRawWeightSymbols(int[] deltas, int weightIseRange, int planeCount) {
        if (planeCount < 1 || planeCount > 2 || deltas.length % planeCount != 0) {
            throw new BasisDecodeException("Invalid XUASTC raw weight layout");
        }
        int levels = XuastcAstcConstants.getIseLevels(weightIseRange);
        int[] weights = new int[deltas.length];
        int totalWeights = deltas.length / planeCount;
        for (int plane = 0; plane < planeCount; plane++) {
            int previousRank = levels / 2;
            for (int weight = 0; weight < totalWeights; weight++) {
                int index = weight * planeCount + plane;
                if (deltas[index] < 0 || deltas[index] >= levels) {
                    throw new BasisDecodeException("Invalid XUASTC raw weight delta");
                }
                int rank = positiveMod(previousRank + deltas[index], levels);
                previousRank = rank;
                weights[index] = XuastcAstcConstants.weightRankToIse(rank, weightIseRange);
            }
        }
        return weights;
    }

    private static void decodeDpcmEndpoints(
            XuastcArithmeticDecoder decoder,
            XuastcArithmeticDataModel endpointReuseDeltaModel,
            XuastcArithmeticDataModel[] dpcmEndpointModels,
            XuastcArithmeticBitModel[] endpointsUseBcModels,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            BlockState leftState,
            BlockState upperState,
            int blockX,
            int blockY,
            XuastcTrialMode trialMode,
            int actualCem,
            int endpointValuesPerPartition,
            int[] endpoints) {
        int reuseDeltaIndex = decoder.decodeSymbol(endpointReuseDeltaModel);
        int reuseBlockX = blockX + ENDPOINT_REUSE_DELTAS[reuseDeltaIndex][0];
        int reuseBlockY = blockY + ENDPOINT_REUSE_DELTAS[reuseDeltaIndex][1];
        if (reuseBlockX < 0 || reuseBlockY < 0) {
            throw new BasisDecodeException(
                    "Invalid XUASTC endpoint DPCM reuse delta"
                            + " (block=" + blockX + "," + blockY
                            + ", deltaIndex=" + reuseDeltaIndex
                            + ", dx=" + ENDPOINT_REUSE_DELTAS[reuseDeltaIndex][0]
                            + ", dy=" + ENDPOINT_REUSE_DELTAS[reuseDeltaIndex][1] + ")");
        }
        XuastcLdrRawBlockConfig predictedConfig = blockConfigs[reuseBlockY & 7][reuseBlockX];
        if (predictedConfig == null) {
            throw new BasisDecodeException("Invalid XUASTC endpoint DPCM solid-color predictor");
        }

        int bcModelIndex = neighborFlagModelIndex(leftState, upperState, FlagField.FIRST_ENDPOINT_USES_BC);
        boolean[] endpointsUseBlueContraction = new boolean[trialMode.getNumberOfPartitions()];
        if (cemSupportsBlueContraction(actualCem)) {
            for (int partition = 0; partition < endpointsUseBlueContraction.length; partition++) {
                endpointsUseBlueContraction[partition] =
                        decoder.decodeBit(endpointsUseBcModels[bcModelIndex]) != 0;
            }
        }

        int endpointIseRange = trialMode.getEndpointIseRange();
        int[] predictedEndpoints = new int[endpoints.length];
        for (int partition = 0; partition < trialMode.getNumberOfPartitions(); partition++) {
            requantizePredictedEndpoints(
                    predictedConfig,
                    actualCem,
                    endpointIseRange,
                    endpointValuesPerPartition,
                    predictedEndpoints,
                    partition * endpointValuesPerPartition,
                    endpointsUseBlueContraction[partition]);
        }

        int levels = XuastcAstcConstants.getIseLevels(endpointIseRange);
        XuastcArithmeticDataModel dpcmModel =
                dpcmEndpointModels[endpointIseRange - XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE];
        for (int i = 0; i < endpoints.length; i++) {
            int delta = decoder.decodeSymbol(dpcmModel);
            int predictedRank = XuastcAstcConstants.endpointIseToRank(
                    predictedEndpoints[i],
                    endpointIseRange);
            int rank = positiveMod(predictedRank + delta, levels);
            endpoints[i] = XuastcAstcConstants.endpointRankToIse(rank, endpointIseRange);
        }
    }

    private static void requantizePredictedEndpoints(
            XuastcLdrRawBlockConfig predictedConfig,
            int actualCem,
            int endpointIseRange,
            int endpointValues,
            int[] output,
            int outputOffset,
            boolean useBlueContraction) {
        int predictedCem = predictedConfig.getActualColorEndpointMode();
        int[] predictedEndpoints = predictedConfig.getEndpoints();
        int predictedIseRange = predictedConfig.getTrialMode().getEndpointIseRange();
        if (predictedCem != actualCem) {
            int predictedBaseCem = baseCemWithoutAlpha(predictedCem);
            int actualBaseCem = baseCemWithoutAlpha(actualCem);
            if (predictedBaseCem == actualBaseCem && !cemHasAlpha(actualCem)) {
                requantizeSameCemEndpoints(
                        predictedBaseCem,
                        predictedIseRange,
                        predictedEndpoints,
                        endpointIseRange,
                        endpointValues,
                        output,
                        outputOffset);
                return;
            }
            if (predictedBaseCem == actualBaseCem && cemHasAlpha(actualCem) && !cemHasAlpha(predictedCem)) {
                requantizeSameCemEndpoints(
                        predictedBaseCem,
                        predictedIseRange,
                        predictedEndpoints,
                        endpointIseRange,
                        XuastcAstcConstants.numCemEndpointValues(predictedBaseCem),
                        output,
                        outputOffset);
                addOpaqueAlphaEndpoints(actualCem, endpointIseRange, output, outputOffset);
                return;
            }
            if (isBasePlusOffsetCem(actualCem)) {
                int[] colors = decodeEndpointColors(predictedCem, predictedIseRange, predictedEndpoints);
                packBasePlusOffsetPredictedEndpoints(
                        colors,
                        actualCem,
                        endpointIseRange,
                        output,
                        outputOffset,
                        useBlueContraction);
                return;
            }
            int[] converted = convertPredictedEndpoints(
                    predictedCem,
                    predictedIseRange,
                    predictedEndpoints,
                    actualCem,
                    endpointIseRange,
                    useBlueContraction);
            for (int i = 0; i < endpointValues; i++) {
                output[outputOffset + i] = nearestEndpointIse(converted[i], endpointIseRange);
            }
            if (cemSupportsBlueContraction(actualCem)) {
                repackBlueContraction(actualCem, output, outputOffset, endpointIseRange, useBlueContraction);
            }
            return;
        }
        requantizeSameCemEndpoints(
                actualCem,
                predictedIseRange,
                predictedEndpoints,
                endpointIseRange,
                endpointValues,
                output,
                outputOffset);
    }

    private static int baseCemWithoutAlpha(int cem) {
        switch (cem) {
            case XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT:
                return XuastcAstcConstants.CEM_LDR_LUM_DIRECT;
            case XuastcAstcConstants.CEM_LDR_RGBA_DIRECT:
                return XuastcAstcConstants.CEM_LDR_RGB_DIRECT;
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A:
                return XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE;
            case XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET:
                return XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET;
            default:
                return cem;
        }
    }

    private static boolean isBasePlusOffsetCem(int cem) {
        return cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET;
    }

    private static boolean cemHasAlpha(int cem) {
        return cem == XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET;
    }

    private static void addOpaqueAlphaEndpoints(
            int cem,
            int endpointIseRange,
            int[] output,
            int outputOffset) {
        int opaque = nearestEndpointIse(255, endpointIseRange);
        switch (cem) {
            case XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT:
                output[outputOffset + 2] = opaque;
                output[outputOffset + 3] = opaque;
                break;
            case XuastcAstcConstants.CEM_LDR_RGBA_DIRECT:
                output[outputOffset + 6] = opaque;
                output[outputOffset + 7] = opaque;
                break;
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A:
                output[outputOffset + 4] = opaque;
                output[outputOffset + 5] = opaque;
                break;
            case XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET:
                output[outputOffset + 6] = opaque;
                output[outputOffset + 7] = nearestEndpointIse(128, endpointIseRange);
                break;
            default:
                throw new BasisDecodeException("XUASTC CEM does not carry alpha: " + cem);
        }
    }

    private static void requantizeSameCemEndpoints(
            int cem,
            int sourceIseRange,
            int[] sourceEndpoints,
            int destIseRange,
            int endpointValues,
            int[] output,
            int outputOffset) {
        if (sourceIseRange == destIseRange) {
            System.arraycopy(sourceEndpoints, 0, output, outputOffset, endpointValues);
            return;
        }

        if (isBasePlusOffsetCem(cem)) {
            requantizeBasePlusOffsetEndpoints(
                    cem,
                    sourceIseRange,
                    sourceEndpoints,
                    destIseRange,
                    endpointValues,
                    output,
                    outputOffset);
            return;
        }

        int[] sourceValues = new int[endpointValues];
        for (int i = 0; i < endpointValues; i++) {
            sourceValues[i] = XuastcAstcConstants.dequantBiseEndpoint(sourceEndpoints[i], sourceIseRange);
            output[outputOffset + i] = nearestEndpointIse(sourceValues[i], destIseRange);
        }

        if (cem != XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                && cem != XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
            return;
        }

        boolean sourceUsesBlueContraction = sourceValues[1] + sourceValues[3] + sourceValues[5]
                < sourceValues[0] + sourceValues[2] + sourceValues[4];
        int dequantSum0 = dequantEndpointSum(output, outputOffset, destIseRange, 0);
        int dequantSum1 = dequantEndpointSum(output, outputOffset, destIseRange, 1);
        boolean quantUsesBlueContraction = dequantSum1 < dequantSum0;
        if (sourceUsesBlueContraction == quantUsesBlueContraction) {
            return;
        }

        if (dequantSum0 == dequantSum1) {
            if (dequantSum1 != 0) {
                for (int i = 0; i < 3; i++) {
                    int index = outputOffset + 1 + i * 2;
                    int adjusted = applyEndpointRankDelta(output[index], destIseRange, -1);
                    if (adjusted != output[index]) {
                        output[index] = adjusted;
                        break;
                    }
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    int index = outputOffset + i * 2;
                    int adjusted = applyEndpointRankDelta(output[index], destIseRange, 1);
                    if (adjusted != output[index]) {
                        output[index] = adjusted;
                        break;
                    }
                }
            }
        } else {
            swapRgbEndpointPairs(output, outputOffset);
            if (cem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
                int alpha = output[outputOffset + 6];
                output[outputOffset + 6] = output[outputOffset + 7];
                output[outputOffset + 7] = alpha;
            }
        }
    }

    private static void requantizeBasePlusOffsetEndpoints(
            int cem,
            int sourceIseRange,
            int[] sourceEndpoints,
            int destIseRange,
            int endpointValues,
            int[] output,
            int outputOffset) {
        boolean sourceUsesBlueContraction =
                usedBlueContraction(cem, sourceEndpoints, 0, sourceIseRange);
        for (int i = 0; i < endpointValues; i++) {
            int sourceValue = XuastcAstcConstants.dequantBiseEndpoint(sourceEndpoints[i], sourceIseRange);
            int preserveMask = (i & 1) == 0 ? 0x80 : 0xC0;
            output[outputOffset + i] = nearestEndpointIsePreservingMask(
                    sourceValue,
                    destIseRange,
                    preserveMask);
        }
        if (usedBlueContraction(cem, output, outputOffset, destIseRange) != sourceUsesBlueContraction) {
            nudgeBasePlusOffsetBlueContraction(
                    output,
                    outputOffset,
                    destIseRange,
                    sourceUsesBlueContraction);
        }
    }

    private static int nearestEndpointIsePreservingMask(int value, int endpointIseRange, int preserveMask) {
        int clamped = Math.max(0, Math.min(255, value));
        int levels = XuastcAstcConstants.getIseLevels(endpointIseRange);
        int bestIndex = -1;
        int bestError = Integer.MAX_VALUE;
        for (int i = 0; i < levels; i++) {
            int dequantized = XuastcAstcConstants.dequantBiseEndpoint(i, endpointIseRange);
            if ((dequantized & preserveMask) != (clamped & preserveMask)) {
                continue;
            }
            int error = Math.abs(dequantized - clamped);
            if (error < bestError) {
                bestError = error;
                bestIndex = i;
            }
        }
        return bestIndex >= 0 ? bestIndex : nearestEndpointIse(clamped, endpointIseRange);
    }

    private static int dequantEndpointSum(
            int[] endpoints,
            int offset,
            int endpointIseRange,
            int endpointPair) {
        int first = XuastcAstcConstants.dequantBiseEndpoint(
                endpoints[offset + endpointPair],
                endpointIseRange);
        int second = XuastcAstcConstants.dequantBiseEndpoint(
                endpoints[offset + 2 + endpointPair],
                endpointIseRange);
        int third = XuastcAstcConstants.dequantBiseEndpoint(
                endpoints[offset + 4 + endpointPair],
                endpointIseRange);
        return first + second + third;
    }

    private static int[] convertPredictedEndpoints(
            int predictedCem,
            int predictedIseRange,
            int[] predictedEndpoints,
            int actualCem,
            int actualIseRange,
            boolean useBlueContraction) {
        int[] colors = decodeEndpointColors(predictedCem, predictedIseRange, predictedEndpoints);
        if (actualCem == XuastcAstcConstants.CEM_LDR_LUM_DIRECT
                || actualCem == XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT) {
            return packLuminanceEndpoints(colors, predictedCem, actualCem);
        }
        if (actualCem == XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE
                || actualCem == XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A) {
            return packRgbBaseScaleEndpoints(colors, predictedCem, actualCem);
        }
        if (actualCem == XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                || actualCem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
            return packRgbDirectEndpoints(colors, actualCem, actualIseRange, useBlueContraction);
        }
        if (isBasePlusOffsetCem(actualCem)) {
            return packBasePlusOffsetEndpoints8(colors, actualCem, useBlueContraction);
        }
        throw new BasisDecodeException(
                "Unsupported XUASTC endpoint DPCM cross-CEM prediction"
                        + " (predictedCem=" + predictedCem + ", actualCem=" + actualCem + ")");
    }

    private static int[] decodeEndpointColors(int cem, int endpointIseRange, int[] endpoints) {
        return XuastcAstcConstants.decodeLdrEndpoints(cem, endpoints, 0, endpointIseRange);
    }

    private static int[] packLuminanceEndpoints(int[] colors, int predictedCem, int actualCem) {
        int lowLum = (colors[0] + colors[1] + colors[2] + 1) / 3;
        int highLum = (colors[4] + colors[5] + colors[6] + 1) / 3;
        int lowAlpha = colors[3];
        int highAlpha = colors[7];

        if (predictedCem != XuastcAstcConstants.CEM_LDR_LUM_DIRECT
                && predictedCem != XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT
                && lowLum > highLum) {
            int swapLum = lowLum;
            lowLum = highLum;
            highLum = swapLum;
            int swapAlpha = lowAlpha;
            lowAlpha = highAlpha;
            highAlpha = swapAlpha;
        }

        if (actualCem == XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT) {
            return new int[] {lowLum, highLum, lowAlpha, highAlpha};
        }
        return new int[] {lowLum, highLum};
    }

    private static int[] packRgbBaseScaleEndpoints(int[] colors, int predictedCem, int actualCem) {
        int lowR = colors[0];
        int lowG = colors[1];
        int lowB = colors[2];
        int lowA = colors[3];
        int highR = colors[4];
        int highG = colors[5];
        int highB = colors[6];
        int highA = colors[7];
        if (predictedCem != XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE
                && predictedCem != XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A
                && lowR + lowG + lowB > highR + highG + highB) {
            final int[] swap = {lowR, lowG, lowB};
            lowR = highR;
            lowG = highG;
            lowB = highB;
            highR = swap[0];
            highG = swap[1];
            highB = swap[2];
            int swapAlpha = lowA;
            lowA = highA;
            highA = swapAlpha;
        }

        int dot = lowR * highR + lowG * highG + lowB * highB;
        int norm = highR * highR + highG * highG + highB * highB;
        int maxScale = (1024 * 255) / 256;
        int scale = norm > 0 ? (dot * 1024) / norm : maxScale;
        scale = Math.max(0, Math.min(maxScale, scale));
        scale = Math.max(0, Math.min(255, (scale + 2) >> 2));
        if (actualCem == XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A) {
            if (predictedCem != XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE
                    && predictedCem != XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A
                    && lowA > highA) {
                int swapAlpha = lowA;
                lowA = highA;
                highA = swapAlpha;
            }
            return new int[] {highR, highG, highB, scale, lowA, highA};
        }
        return new int[] {highR, highG, highB, scale};
    }

    private static int[] packRgbDirectEndpoints(
            int[] colors,
            int actualCem,
            int endpointIseRange,
            boolean useBlueContraction) {
        int[] packed;
        if (useBlueContraction) {
            int[] low = blueContractEncode(
                    colors[0],
                    colors[1],
                    colors[2],
                    quantizedEndpointValue(colors[2], endpointIseRange));
            int[] high = blueContractEncode(
                    colors[4],
                    colors[5],
                    colors[6],
                    quantizedEndpointValue(colors[6], endpointIseRange));
            if (actualCem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
                packed = new int[] {high[0], low[0], high[1], low[1], high[2], low[2], colors[7], colors[3]};
            } else {
                packed = new int[] {high[0], low[0], high[1], low[1], high[2], low[2]};
            }
        } else {
            if (actualCem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
                packed = new int[] {
                        colors[0], colors[4], colors[1], colors[5], colors[2], colors[6], colors[3], colors[7]
                };
            } else {
                packed = new int[] {colors[0], colors[4], colors[1], colors[5], colors[2], colors[6]};
            }
        }
        return packed;
    }

    private static int[] blueContractEncode(int red, int green, int blue, int encodedBlue) {
        return new int[] {
                Math.max(0, Math.min(255, red * 2 - encodedBlue)),
                Math.max(0, Math.min(255, green * 2 - encodedBlue)),
                blue
        };
    }

    private static void packBasePlusOffsetPredictedEndpoints(
            int[] colors,
            int actualCem,
            int endpointIseRange,
            int[] output,
            int outputOffset,
            boolean useBlueContraction) {
        int[] endpoints8 = packBasePlusOffsetEndpoints8(colors, actualCem, useBlueContraction);
        requantizeBasePlusOffsetEndpoints(
                actualCem,
                XuastcAstcConstants.LAST_VALID_ENDPOINT_ISE_RANGE,
                endpoints8,
                endpointIseRange,
                endpoints8.length,
                output,
                outputOffset);
    }

    private static int[] packBasePlusOffsetEndpoints8(
            int[] colors,
            int actualCem,
            boolean useBlueContraction) {
        int[] low = {colors[0], colors[1], colors[2], colors[3]};
        int[] high = {colors[4], colors[5], colors[6], colors[7]};
        if (useBlueContraction) {
            int[] encodedLow = blueContractEncodeRgba(low);
            int[] encodedHigh = blueContractEncodeRgba(high);
            low = encodedHigh;
            high = encodedLow;
        }

        int redDelta = 0;
        int greenDelta = 0;
        int blueDelta = 0;
        int alphaDelta = 0;
        int lowClamp = -32;
        for (int pass = 0; pass < 4; pass++) {
            redDelta = clamp(high[0] - low[0], lowClamp, 31);
            greenDelta = clamp(high[1] - low[1], lowClamp, 31);
            blueDelta = clamp(high[2] - low[2], lowClamp, 31);
            alphaDelta = clamp(high[3] - low[3], lowClamp, 31);

            int sum = redDelta + greenDelta + blueDelta;
            if ((sum < 0) == useBlueContraction) {
                break;
            }
            if (sum == 0 && useBlueContraction) {
                if (blueDelta > -32) {
                    blueDelta--;
                } else if (redDelta > -32) {
                    redDelta--;
                } else if (greenDelta > -32) {
                    greenDelta--;
                }
                break;
            }
            if (pass == 1) {
                lowClamp = -31;
            }
            int[] swap = low;
            low = high;
            high = swap;
        }

        int[] red = bitTransferSignedEncode(redDelta, low[0]);
        int[] green = bitTransferSignedEncode(greenDelta, low[1]);
        int[] blue = bitTransferSignedEncode(blueDelta, low[2]);
        if (actualCem == XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET) {
            int[] alpha = bitTransferSignedEncode(alphaDelta, low[3]);
            return new int[] {red[0], red[1], green[0], green[1], blue[0], blue[1], alpha[0], alpha[1]};
        }
        return new int[] {red[0], red[1], green[0], green[1], blue[0], blue[1]};
    }

    private static int[] blueContractEncodeRgba(int[] color) {
        return new int[] {
                clamp(color[0] * 2 - color[2], 0, 255),
                clamp(color[1] * 2 - color[2], 0, 255),
                color[2],
                color[3]
        };
    }

    private static int[] bitTransferSignedEncode(int delta, int base) {
        int encodedBase = (base << 1) & 0xFF;
        int encodedDelta = (delta & 0x3F) << 1;
        if ((base & 0x80) != 0) {
            encodedDelta |= 0x80;
        }
        return new int[] {encodedBase, encodedDelta};
    }

    private static int quantizedEndpointValue(int value, int endpointIseRange) {
        return XuastcAstcConstants.dequantBiseEndpoint(
                nearestEndpointIse(value, endpointIseRange),
                endpointIseRange);
    }

    private static void repackBlueContraction(
            int cem,
            int[] endpoints,
            int offset,
            int endpointIseRange,
            boolean useBlueContraction) {
        if (isBasePlusOffsetCem(cem)) {
            nudgeBasePlusOffsetBlueContraction(endpoints, offset, endpointIseRange, useBlueContraction);
            return;
        }
        if (cem != XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                && cem != XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
            throw new BasisDecodeException(
                    "Unsupported XUASTC blue-contraction repack CEM " + cem);
        }
        if (usedBlueContraction(cem, endpoints, offset, endpointIseRange) == useBlueContraction) {
            return;
        }
        swapRgbEndpointPairs(endpoints, offset);
        if (cem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
            int alpha = endpoints[offset + 6];
            endpoints[offset + 6] = endpoints[offset + 7];
            endpoints[offset + 7] = alpha;
        }
        if (usedBlueContraction(cem, endpoints, offset, endpointIseRange) != useBlueContraction) {
            nudgeBlueContraction(cem, endpoints, offset, endpointIseRange, useBlueContraction);
        }
    }

    private static void swapRgbEndpointPairs(int[] values, int offset) {
        for (int i = 0; i < 6; i += 2) {
            int swap = values[offset + i];
            values[offset + i] = values[offset + i + 1];
            values[offset + i + 1] = swap;
        }
    }

    private static void nudgeBlueContraction(
            int cem,
            int[] endpoints,
            int offset,
            int endpointIseRange,
            boolean useBlueContraction) {
        int sum0 = endpointSum(endpoints, offset, endpointIseRange, 0);
        int sum1 = endpointSum(endpoints, offset, endpointIseRange, 1);
        int[] indices;
        int delta;
        if (useBlueContraction) {
            indices = sum1 == 0 ? new int[] {0, 2, 4} : new int[] {1, 3, 5};
            delta = sum1 == 0 ? 1 : -1;
        } else {
            indices = sum0 == 0 ? new int[] {1, 3, 5} : new int[] {0, 2, 4};
            delta = sum0 == 0 ? 1 : -1;
        }
        for (int index : indices) {
            int endpointIndex = offset + index;
            int current = endpoints[endpointIndex];
            int adjusted = applyEndpointRankDelta(current, endpointIseRange, delta);
            if (adjusted != current) {
                endpoints[endpointIndex] = adjusted;
                break;
            }
        }
        if (usedBlueContraction(cem, endpoints, offset, endpointIseRange) == useBlueContraction) {
            return;
        }
        throw new BasisDecodeException("XUASTC blue-contraction repack failed");
    }

    private static void nudgeBasePlusOffsetBlueContraction(
            int[] endpoints,
            int offset,
            int endpointIseRange,
            boolean useBlueContraction) {
        int channelRover = 2;
        for (int tries = 0; tries < 5; tries++) {
            int sum = basePlusOffsetDeltaSum(endpoints, offset, endpointIseRange);
            if ((sum < 0) == useBlueContraction) {
                return;
            }
            int delta = sum < 0 ? 1 : -1;
            for (int j = 0; j < 3; j++) {
                int channel = (channelRover + j) % 3;
                int endpointIndex = offset + 1 + channel * 2;
                int adjusted = applyEndpointRankDeltaPreservingMask(
                        endpoints[endpointIndex],
                        endpointIseRange,
                        delta,
                        0xC0);
                if (adjusted != endpoints[endpointIndex]) {
                    endpoints[endpointIndex] = adjusted;
                    break;
                }
            }
            channelRover++;
        }
    }

    private static int endpointSum(int[] endpoints, int offset, int endpointIseRange, int endpointPair) {
        int first = XuastcAstcConstants.dequantBiseEndpoint(
                endpoints[offset + endpointPair],
                endpointIseRange);
        int second = XuastcAstcConstants.dequantBiseEndpoint(
                endpoints[offset + 2 + endpointPair],
                endpointIseRange);
        int third = XuastcAstcConstants.dequantBiseEndpoint(
                endpoints[offset + 4 + endpointPair],
                endpointIseRange);
        return first + second + third;
    }

    private static int applyEndpointRankDelta(int endpoint, int endpointIseRange, int delta) {
        int levels = XuastcAstcConstants.getIseLevels(endpointIseRange);
        int rank = XuastcAstcConstants.endpointIseToRank(endpoint, endpointIseRange);
        int newRank = Math.max(0, Math.min(levels - 1, rank + delta));
        return XuastcAstcConstants.endpointRankToIse(newRank, endpointIseRange);
    }

    private static int applyEndpointRankDeltaPreservingMask(
            int endpoint,
            int endpointIseRange,
            int delta,
            int preserveMask) {
        int levels = XuastcAstcConstants.getIseLevels(endpointIseRange);
        int currentValue = XuastcAstcConstants.dequantBiseEndpoint(endpoint, endpointIseRange);
        int rank = XuastcAstcConstants.endpointIseToRank(endpoint, endpointIseRange);
        int step = delta < 0 ? -1 : 1;
        for (int candidateRank = rank + step;
                candidateRank >= 0 && candidateRank < levels;
                candidateRank += step) {
            int candidate = XuastcAstcConstants.endpointRankToIse(candidateRank, endpointIseRange);
            int candidateValue = XuastcAstcConstants.dequantBiseEndpoint(candidate, endpointIseRange);
            if ((candidateValue & preserveMask) == (currentValue & preserveMask)) {
                return candidate;
            }
        }
        return endpoint;
    }

    private static int nearestEndpointIse(int value, int endpointIseRange) {
        int clamped = Math.max(0, Math.min(255, value));
        int levels = XuastcAstcConstants.getIseLevels(endpointIseRange);
        int bestIndex = 0;
        int bestError = Integer.MAX_VALUE;
        for (int i = 0; i < levels; i++) {
            int error = Math.abs(XuastcAstcConstants.dequantBiseEndpoint(i, endpointIseRange) - clamped);
            if (error < bestError) {
                bestError = error;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static XuastcLdrDctWeightPlane[] decodeDctWeightPlanes(
            XuastcArithmeticDecoder decoder,
            XuastcTrialMode trialMode,
            XuastcArithmeticDataModel dctRunLengthModel,
            XuastcArithmeticDataModel dctCoefficientMagnitudeModel,
            XuastcArithmeticDataModel[] weightMeanModels) {
        int totalPlanes = trialMode.getColorComponentSelector() >= 0 ? 2 : 1;
        int totalWeights = trialMode.getGridWidth() * trialMode.getGridHeight();
        int dcLevels = getDctDcLevels(trialMode.getWeightIseRange());
        XuastcLdrDctWeightPlane[] planes = new XuastcLdrDctWeightPlane[totalPlanes];
        for (int planeIndex = 0; planeIndex < totalPlanes; planeIndex++) {
            int dcSymbol = decoder.decodeSymbol(
                    weightMeanModels[dcLevels == DCT_MEAN_LEVELS_1 ? 1 : 0]);
            int zigzagOffset = 1;
            ArrayList<Integer> zeroRuns = new ArrayList<Integer>();
            ArrayList<Integer> coefficients = new ArrayList<Integer>();
            while (zigzagOffset < totalWeights) {
                int runLength = decoder.decodeSymbol(dctRunLengthModel);
                if (runLength == DCT_RUN_LENGTH_EOB) {
                    break;
                }
                zigzagOffset += runLength;
                if (zigzagOffset >= totalWeights) {
                    throw new BasisDecodeException("XUASTC DCT run length exceeds weight grid");
                }
                int sign = decoder.getBit();
                int coefficient = decoder.decodeSymbol(dctCoefficientMagnitudeModel) + 1;
                if (sign != 0) {
                    coefficient = -coefficient;
                }
                zeroRuns.add(runLength);
                coefficients.add(coefficient);
                zigzagOffset++;
            }
            planes[planeIndex] = new XuastcLdrDctWeightPlane(
                    dcSymbol,
                    dcLevels,
                    toIntArray(zeroRuns),
                    toIntArray(coefficients));
        }
        return planes;
    }

    private static int getDctDcLevels(int weightIseRange) {
        float scaledWeightCodingScale = weightIseRange <= 5 ? 1.0f / 8.0f : 0.5f;
        return (int) (64.0f * scaledWeightCodingScale) + 1;
    }

    private static XuastcLdrWeightGrid reconstructDctWeightGrid(
            XuastcLdrImageHeader header,
            XuastcTrialMode trialMode,
            int actualCem,
            int[] endpoints,
            XuastcLdrDctWeightPlane[] dctWeightPlanes) {
        if (dctWeightPlanes.length == 0) {
            return new XuastcLdrWeightGrid(
                    trialMode.getGridWidth(),
                    trialMode.getGridHeight(),
                    trialMode.getColorComponentSelector() >= 0 ? 2 : 1,
                    new int[0]);
        }
        int gridWidth = trialMode.getGridWidth();
        int gridHeight = trialMode.getGridHeight();
        int planeCount = trialMode.getColorComponentSelector() >= 0 ? 2 : 1;
        int totalWeights = gridWidth * gridHeight;
        int[] weights = new int[totalWeights * planeCount];
        int[] zigzag = generateZigzagOrder(gridWidth, gridHeight);
        for (int plane = 0; plane < dctWeightPlanes.length; plane++) {
            float[] dctWeights = new float[totalWeights];
            XuastcLdrDctWeightPlane dctPlane = dctWeightPlanes[plane];
            float spanLength = getMaxEndpointSpan(actualCem, endpoints, trialMode, plane);
            float levelScale = computeLevelScale(
                    header.getDctQuality(),
                    spanLength,
                    trialMode.getWeightIseRange());
            int zigzagOffset = 1;
            int[] zeroRuns = dctPlane.getZeroRuns();
            int[] coefficients = dctPlane.getCoefficients();
            for (int i = 0; i < coefficients.length; i++) {
                zigzagOffset += zeroRuns[i];
                if (zigzagOffset >= totalWeights) {
                    throw new BasisDecodeException("XUASTC DCT run length exceeds weight grid");
                }
                int dctIndex = zigzag[zigzagOffset];
                int x = dctIndex % gridWidth;
                int y = dctIndex / gridWidth;
                int quant = sampleQuantTable(
                        header.getDctQuality(),
                        levelScale,
                        header.getBlockWidth(),
                        header.getBlockHeight(),
                        x,
                        y);
                dctWeights[dctIndex] = dequantDeadzone(coefficients[i], quant, x, y);
                zigzagOffset++;
            }
            float[] idctWeights = inverseDct(dctWeights, gridHeight, gridWidth);
            float scaledWeightCodingScale = trialMode.getWeightIseRange() <= 5 ? 1.0f / 8.0f : 0.5f;
            float meanWeight = dctPlane.getDcSymbol() / scaledWeightCodingScale;
            for (int i = 0; i < totalWeights; i++) {
                int value = fastRound(meanWeight + idctWeights[i]);
                value = Math.max(0, Math.min(64, value));
                weights[i * planeCount + plane] =
                        XuastcAstcConstants.nearestWeightIse(value, trialMode.getWeightIseRange());
            }
        }
        return new XuastcLdrWeightGrid(gridWidth, gridHeight, planeCount, weights);
    }

    private static float getMaxEndpointSpan(
            int actualCem,
            int[] endpoints,
            XuastcTrialMode trialMode,
            int planeIndex) {
        int endpointValues = XuastcAstcConstants.numCemEndpointValues(actualCem);
        int colorComponentSelector = trialMode.getColorComponentSelector();
        if (colorComponentSelector >= 0) {
            int[] decoded = XuastcAstcConstants.decodeLdrEndpoints(
                    actualCem, endpoints, 0, trialMode.getEndpointIseRange());
            float span = 0.0f;
            for (int component = 0; component < 4; component++) {
                boolean usesPlane = planeIndex == 1
                        ? component == colorComponentSelector
                        : component != colorComponentSelector;
                if (usesPlane) {
                    float delta = decoded[component + 4] - decoded[component];
                    span += delta * delta;
                }
            }
            return (float) Math.sqrt(span);
        }

        float maxSpan = 0.0f;
        for (int part = 0; part < trialMode.getNumberOfPartitions(); part++) {
            int offset = part * endpointValues;
            int[] decoded = XuastcAstcConstants.decodeLdrEndpoints(
                    actualCem, endpoints, offset, trialMode.getEndpointIseRange());
            float dr = decoded[4] - decoded[0];
            float dg = decoded[5] - decoded[1];
            float db = decoded[6] - decoded[2];
            float da = decoded[7] - decoded[3];
            float span = (float) Math.sqrt(dr * dr + dg * dg + db * db + da * da);
            maxSpan = Math.max(maxSpan, span);
        }
        return maxSpan;
    }

    private static float computeLevelScale(float quality, float spanLength, int weightIseRange) {
        float q = Math.max(1.0f, Math.min(100.0f, quality));
        float levelScale = q < 50.0f ? 5000.0f / q : 200.0f - 2.0f * q;
        levelScale *= 0.01f;
        float adaptiveFactor = 64.0f / Math.max(spanLength, 14.0f);
        adaptiveFactor *= XuastcAstcConstants.getScaleQuantStep(weightIseRange);
        return levelScale * adaptiveFactor;
    }

    private static int sampleQuantTable(
            float quality,
            float levelScale,
            int blockWidth,
            int blockHeight,
            int x,
            int y) {
        if (quality >= 100.0f) {
            return 1;
        }
        int[][] jpeg = {
                {4, 11, 10, 16, 24, 40, 51, 61},
                {12, 12, 14, 19, 26, 58, 60, 55},
                {14, 13, 16, 24, 40, 57, 69, 56},
                {14, 17, 22, 29, 51, 87, 80, 62},
                {18, 22, 37, 56, 68, 109, 103, 77},
                {24, 35, 55, 64, 81, 104, 113, 92},
                {49, 64, 78, 87, 103, 121, 120, 101},
                {72, 92, 95, 98, 112, 100, 103, 99}
        };
        float rx = Math.min(x * (8.0f / blockWidth), 7.0f);
        float ry = Math.min(y * (8.0f / blockHeight), 7.0f);
        int x0 = (int) rx;
        int y0 = (int) ry;
        int x1 = Math.min(x0 + 1, 7);
        int y1 = Math.min(y0 + 1, 7);
        float tx = rx - x0;
        float ty = ry - y0;
        float a = (1.0f - tx) * jpeg[y0][x0] + tx * jpeg[y0][x1];
        float b = (1.0f - tx) * jpeg[y1][x0] + tx * jpeg[y1][x1];
        return Math.max(1, fastRound(((1.0f - ty) * a + ty * b) * levelScale));
    }

    private static float dequantDeadzone(int q, int level, int x, int y) {
        if ((x == 1 && y == 0) || (x == 0 && y == 1)) {
            return q * (float) level;
        }
        if (q == 0 || level <= 0) {
            return 0.0f;
        }
        float magnitude = 0.5f * level + Math.abs(q) * (float) level;
        return q < 0 ? -magnitude : magnitude;
    }

    private static float[] inverseDct(float[] source, int rows, int cols) {
        float[] temp = new float[rows * cols];
        float[] dest = new float[rows * cols];
        float[] rowScale = dctScales(rows);
        float[] colScale = dctScales(cols);
        float[] rowCos = dctCosines(rows);
        float[] colCos = dctCosines(cols);

        for (int col = 0; col < cols; col++) {
            for (int x = 0; x < rows; x++) {
                float sum = 0.0f;
                for (int u = 0; u < rows; u++) {
                    float value = source[u * cols + col];
                    if (value != 0.0f) {
                        sum += value * rowScale[u] * rowCos[u * rows + x];
                    }
                }
                temp[x * cols + col] = sum;
            }
        }

        for (int row = 0; row < rows; row++) {
            for (int y = 0; y < cols; y++) {
                float sum = 0.0f;
                for (int v = 0; v < cols; v++) {
                    float value = temp[row * cols + v];
                    if (value != 0.0f) {
                        sum += value * colScale[v] * colCos[v * cols + y];
                    }
                }
                dest[row * cols + y] = sum;
            }
        }
        return dest;
    }

    private static float[] dctScales(int size) {
        float invSize = 1.0f / size;
        float[] scales = new float[size];
        scales[0] = (float) Math.sqrt(invSize);
        float acScale = (float) Math.sqrt(2.0f * invSize);
        for (int i = 1; i < size; i++) {
            scales[i] = acScale;
        }
        return scales;
    }

    private static float[] dctCosines(int size) {
        float[] cosines = new float[size * size];
        float pi = 3.14159265358979323846f;
        for (int frequency = 0; frequency < size; frequency++) {
            for (int sample = 0; sample < size; sample++) {
                float angle = pi * (float) ((2 * sample + 1) * frequency) / (2.0f * size);
                cosines[frequency * size + sample] = (float) Math.cos(angle);
            }
        }
        return cosines;
    }

    private static int[] generateZigzagOrder(int width, int height) {
        int[] order = new int[width * height];
        int index = 0;
        for (int sum = 0; sum < width + height - 1; sum++) {
            int xStart = sum < height ? 0 : sum - height + 1;
            int xEnd = sum < width ? sum : width - 1;
            if ((sum & 1) != 0) {
                for (int x = xEnd; x >= xStart; x--) {
                    int y = sum - x;
                    order[index++] = x + y * width;
                }
            } else {
                for (int x = xStart; x <= xEnd; x++) {
                    int y = sum - x;
                    order[index++] = x + y * width;
                }
            }
        }
        return order;
    }

    private static int fastRound(float value) {
        return value >= 0.0f ? (int) (value + 0.5f) : (int) (value - 0.5f);
    }

    private static XuastcArithmeticDataModel[] newConfigReuseModels() {
        XuastcArithmeticDataModel[] models = new XuastcArithmeticDataModel[CONFIG_REUSE_MODEL_COUNT];
        for (int i = 0; i < models.length; i++) {
            models[i] = new XuastcArithmeticDataModel(CONFIG_REUSE_MODEL_COUNT);
        }
        return models;
    }

    private static XuastcArithmeticDataModel[] newDataModels(int count, int symbols) {
        XuastcArithmeticDataModel[] models = new XuastcArithmeticDataModel[count];
        for (int i = 0; i < models.length; i++) {
            models[i] = new XuastcArithmeticDataModel(symbols);
        }
        return models;
    }

    private static XuastcArithmeticDataModel[] newRawEndpointModels() {
        XuastcArithmeticDataModel[] models =
                new XuastcArithmeticDataModel[XuastcAstcConstants.TOTAL_ENDPOINT_ISE_RANGES];
        for (int i = 0; i < models.length; i++) {
            int iseRange = XuastcAstcConstants.FIRST_VALID_ENDPOINT_ISE_RANGE + i;
            models[i] = new XuastcArithmeticDataModel(XuastcAstcConstants.getIseLevels(iseRange));
        }
        return models;
    }

    private static XuastcArithmeticDataModel[] newRawWeightModels() {
        XuastcArithmeticDataModel[] models =
                new XuastcArithmeticDataModel[XuastcAstcConstants.TOTAL_WEIGHT_ISE_RANGES];
        for (int i = 0; i < models.length; i++) {
            int iseRange = XuastcAstcConstants.FIRST_VALID_WEIGHT_ISE_RANGE + i;
            models[i] = new XuastcArithmeticDataModel(XuastcAstcConstants.getIseLevels(iseRange));
        }
        return models;
    }

    private static int[] newPartHash() {
        int[] hash = new int[PART_HASH_SIZE];
        fillNegative(hash);
        return hash;
    }

    private static void fillNegative(int[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = -1;
        }
    }

    private static XuastcArithmeticBitModel[] newBitModels(int count) {
        XuastcArithmeticBitModel[] models = new XuastcArithmeticBitModel[count];
        for (int i = 0; i < models.length; i++) {
            models[i] = new XuastcArithmeticBitModel();
        }
        return models;
    }

    private static int[] toIntArray(ArrayList<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static int submodeModelIndex(
            int cemIndex,
            int subsetIndex,
            int ccsIndex,
            int gridSizeIndex,
            int gridAnisoIndex) {
        int index = cemIndex;
        index = index * XuastcAstcConstants.OTM_NUM_SUBSETS + subsetIndex;
        index = index * XuastcAstcConstants.OTM_NUM_CCS + ccsIndex;
        index = index * XuastcAstcConstants.OTM_NUM_GRID_SIZES + gridSizeIndex;
        return index * XuastcAstcConstants.OTM_NUM_GRID_ANISOS + gridAnisoIndex;
    }

    private static final class FullZstdStreams {
        private final BasisBitReader rawBits;
        private final SimpleBitReader modeBytes;
        private final SimpleBitReader solidDpcmBytes;
        private final SimpleBitReader endpointDpcmReuseIndices;
        private final SimpleBitReader useBlueContractionBits;
        private final SimpleBitReader endpointDpcm3Bits;
        private final SimpleBitReader endpointDpcm4Bits;
        private final SimpleBitReader endpointDpcm5Bits;
        private final SimpleBitReader endpointDpcm6Bits;
        private final SimpleBitReader endpointDpcm7Bits;
        private final SimpleBitReader endpointDpcm8Bits;
        private final SimpleBitReader mean0Bits;
        private final SimpleBitReader mean1Bytes;
        private final SimpleBitReader runBytes;
        private final SimpleBitReader coeffBytes;
        private final SimpleBitReader signBits;
        private final SimpleBitReader weight2Bits;
        private final SimpleBitReader weight3Bits;
        private final SimpleBitReader weight4Bits;
        private final SimpleBitReader weight8Bytes;

        private FullZstdStreams(
                byte[] data,
                XuastcLdrFullZstdSectionTable table) {
            this.rawBits = sectionBits(data, table, XuastcLdrFullZstdSectionTable.RAW_BITS);
            this.modeBytes = compressedBits(data, table, XuastcLdrFullZstdSectionTable.MODE_BYTES);
            this.solidDpcmBytes = compressedBits(data, table, XuastcLdrFullZstdSectionTable.SOLID_DPCM_BYTES);
            this.endpointDpcmReuseIndices =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_REUSE_INDICES);
            this.useBlueContractionBits =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.USE_BC_BITS);
            this.endpointDpcm3Bits =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_3BIT);
            this.endpointDpcm4Bits =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_4BIT);
            this.endpointDpcm5Bits =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_5BIT);
            this.endpointDpcm6Bits =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_6BIT);
            this.endpointDpcm7Bits =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_7BIT);
            this.endpointDpcm8Bits =
                    compressedBits(data, table, XuastcLdrFullZstdSectionTable.ENDPOINT_DPCM_8BIT);
            this.mean0Bits = compressedBits(data, table, XuastcLdrFullZstdSectionTable.MEAN0_BITS);
            this.mean1Bytes = compressedBits(data, table, XuastcLdrFullZstdSectionTable.MEAN1_BYTES);
            this.runBytes = compressedBits(data, table, XuastcLdrFullZstdSectionTable.RUN_BYTES);
            this.coeffBytes = compressedBits(data, table, XuastcLdrFullZstdSectionTable.COEFF_BYTES);
            this.signBits = sectionSimpleBits(data, table, XuastcLdrFullZstdSectionTable.SIGN_BITS);
            this.weight2Bits = compressedBits(data, table, XuastcLdrFullZstdSectionTable.WEIGHT2_BITS);
            this.weight3Bits = compressedBits(data, table, XuastcLdrFullZstdSectionTable.WEIGHT3_BITS);
            this.weight4Bits = compressedBits(data, table, XuastcLdrFullZstdSectionTable.WEIGHT4_BITS);
            this.weight8Bytes = compressedBits(data, table, XuastcLdrFullZstdSectionTable.WEIGHT8_BYTES);
        }

        static FullZstdStreams parse(byte[] data, int offset, int length) {
            return new FullZstdStreams(data, XuastcLdrFullZstdSectionTable.parse(data, offset, length));
        }

        SimpleBitReader endpointDeltaReader(int levels) {
            if (levels <= 8) {
                return endpointDpcm3Bits;
            }
            if (levels <= 16) {
                return endpointDpcm4Bits;
            }
            if (levels <= 32) {
                return endpointDpcm5Bits;
            }
            if (levels <= 64) {
                return endpointDpcm6Bits;
            }
            if (levels <= 128) {
                return endpointDpcm7Bits;
            }
            return endpointDpcm8Bits;
        }

        SimpleBitReader weightReader(int levels) {
            if (levels <= 4) {
                return weight2Bits;
            }
            if (levels <= 8) {
                return weight3Bits;
            }
            if (levels <= 16) {
                return weight4Bits;
            }
            return weight8Bytes;
        }

        private static BasisBitReader sectionBits(
                byte[] data,
                XuastcLdrFullZstdSectionTable table,
                int sectionIndex) {
            XuastcLdrFullZstdSectionTable.Section section = table.getSection(sectionIndex);
            return new BasisBitReader(data, section.getByteOffset(), section.getByteLength());
        }

        private static SimpleBitReader sectionSimpleBits(
                byte[] data,
                XuastcLdrFullZstdSectionTable table,
                int sectionIndex) {
            XuastcLdrFullZstdSectionTable.Section section = table.getSection(sectionIndex);
            return new SimpleBitReader(data, section.getByteOffset(), section.getByteLength());
        }

        private static SimpleBitReader compressedBits(
                byte[] data,
                XuastcLdrFullZstdSectionTable table,
                int sectionIndex) {
            XuastcLdrFullZstdSectionTable.Section section = table.getSection(sectionIndex);
            if (section.getByteLength() == 0) {
                return new SimpleBitReader(new byte[0], 0, 0);
            }
            byte[] decompressed = ZstdFrameDecoder.decompress(
                    data,
                    section.getByteOffset(),
                    section.getByteLength());
            return new SimpleBitReader(decompressed, 0, decompressed.length);
        }
    }

    private static final class SimpleBitReader {
        private final byte[] data;
        private final int end;
        private int position;
        private int bitBuffer = 1;

        private SimpleBitReader(byte[] data, int offset, int length) {
            if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
                throw new BasisDecodeException("XUASTC FULL_ZSTD side stream range exceeds input");
            }
            this.data = data;
            this.position = offset;
            this.end = offset + length;
        }

        int getBits(int bits) {
            switch (bits) {
                case 1:
                    return getBits1();
                case 2:
                    return getBits2();
                case 4:
                    return getBits4();
                case 8:
                    return getBits8();
                default:
                    throw new BasisDecodeException("Invalid XUASTC FULL_ZSTD side stream bit width");
            }
        }

        int getBits1() {
            refill();
            int result = bitBuffer & 1;
            bitBuffer >>>= 1;
            return result;
        }

        int getBits2() {
            refill();
            int result = bitBuffer & 3;
            bitBuffer >>>= 2;
            return result;
        }

        int getBits4() {
            refill();
            int result = bitBuffer & 15;
            bitBuffer >>>= 4;
            return result;
        }

        int getBits8() {
            if (position >= end) {
                return 0;
            }
            return Byte.toUnsignedInt(data[position++]);
        }

        private void refill() {
            if (bitBuffer <= 1) {
                int next = position < end ? Byte.toUnsignedInt(data[position++]) : 0;
                bitBuffer = 256 | next;
            }
        }
    }

    private static void consumeImageHeader(XuastcArithmeticDecoder decoder) {
        int marker = decoder.getBits(5);
        if (marker != 1) {
            throw new BasisDecodeException("XUASTC image header marker mismatch");
        }
        decoder.getBits(4);
        decoder.getBit();
        decoder.getBits(16);
        decoder.getBits(16);
        decoder.getBit();
        boolean usesDct = decoder.getBits(1) != 0;
        if (usesDct) {
            decoder.getBits(8);
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static XuastcLdrRawBlockConfig previousBlockConfig(
            XuastcLdrRawBlockConfig[][] blockConfigs,
            int blockX,
            int blockY) {
        if (blockX > 0) {
            return blockConfigs[blockY & 7][blockX - 1];
        }
        if (blockY > 0) {
            return blockConfigs[(blockY - 1) & 7][blockX];
        }
        throw new BasisDecodeException("XUASTC previous block does not exist");
    }

    private static XuastcLdrDecodedBlock previousDecodedBlock(
            XuastcLdrDecodedBlock[][] decodedBlocks,
            int blockX,
            int blockY) {
        XuastcLdrDecodedBlock previous;
        if (blockX > 0) {
            previous = decodedBlocks[blockY & 7][blockX - 1];
        } else if (blockY > 0) {
            previous = decodedBlocks[(blockY - 1) & 7][blockX];
        } else {
            throw new BasisDecodeException("XUASTC previous decoded block does not exist");
        }
        if (previous == null) {
            throw new BasisDecodeException("XUASTC previous decoded block is missing");
        }
        return previous;
    }

    private static int[] decodeSolidColor(
            XuastcArithmeticDecoder decoder,
            XuastcArithmeticDataModel[] solidColorModels,
            XuastcLdrImageHeader header,
            XuastcLdrDecodedBlock[][] decodedBlocks,
            int blockX,
            int blockY) {
        int[] prediction = predictSolidColor(decodedBlocks, blockX, blockY);
        int red = (prediction[0] + decoder.decodeSymbol(solidColorModels[0])) & 0xFF;
        int green = (prediction[1] + decoder.decodeSymbol(solidColorModels[1])) & 0xFF;
        int blue = (prediction[2] + decoder.decodeSymbol(solidColorModels[2])) & 0xFF;
        int alpha = 255;
        if (header.hasAlpha()) {
            alpha = (prediction[3] + decoder.decodeSymbol(solidColorModels[3])) & 0xFF;
        }
        return new int[] {red, green, blue, alpha};
    }

    private static int[] predictSolidColor(
            XuastcLdrDecodedBlock[][] decodedBlocks,
            int blockX,
            int blockY) {
        if (blockX == 0 && blockY == 0) {
            return new int[] {0, 0, 0, 0};
        }
        XuastcLdrDecodedBlock previous = previousDecodedBlock(decodedBlocks, blockX, blockY);
        if (previous.isSolid()) {
            return previous.getSolidRgba();
        }
        XuastcLdrRawBlockConfig config = previous.getConfig();
        int[] endpoints = XuastcAstcConstants.decodeLdrEndpoints(
                config.getActualColorEndpointMode(),
                config.getEndpoints(),
                0,
                config.getTrialMode().getEndpointIseRange());
        return new int[] {
                (endpoints[0] + endpoints[4] + 1) >> 1,
                (endpoints[1] + endpoints[5] + 1) >> 1,
                (endpoints[2] + endpoints[6] + 1) >> 1,
                (endpoints[3] + endpoints[7] + 1) >> 1
        };
    }

    private static NeighborBlock neighborForEndpointReuse(
            XuastcLdrMode mode,
            BlockState leftState,
            BlockState upperState,
            BlockState diagState,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            int blockX,
            int blockY) {
        switch (mode) {
            case REUSE_CFG_ENDPOINTS_LEFT:
                return neighborBlock(leftState, blockConfigs, blockX - 1, blockY);
            case REUSE_CFG_ENDPOINTS_UP:
                return neighborBlock(upperState, blockConfigs, blockX, blockY - 1);
            case REUSE_CFG_ENDPOINTS_DIAG:
                return neighborBlock(diagState, blockConfigs, blockX - 1, blockY - 1);
            default:
                throw new BasisDecodeException("XUASTC mode does not reuse endpoint config: " + mode);
        }
    }

    private static NeighborBlock neighborForConfigReuse(
            int configReuseIndex,
            BlockState leftState,
            BlockState upperState,
            BlockState diagState,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            int blockX,
            int blockY) {
        switch (configReuseIndex) {
            case 0:
                return neighborBlock(leftState, blockConfigs, blockX - 1, blockY);
            case 1:
                return neighborBlock(upperState, blockConfigs, blockX, blockY - 1);
            case 2:
                return neighborBlock(diagState, blockConfigs, blockX - 1, blockY - 1);
            default:
                throw new BasisDecodeException("Invalid XUASTC config reuse index");
        }
    }

    private static NeighborBlock neighborBlock(
            BlockState state,
            XuastcLdrRawBlockConfig[][] blockConfigs,
            int blockX,
            int blockY) {
        if (blockX < 0 || blockY < 0 || state == null || state.trialModeIndex < 0) {
            throw new BasisDecodeException("Invalid XUASTC neighbor config reuse");
        }
        XuastcLdrRawBlockConfig config = blockConfigs[blockY & 7][blockX];
        if (config == null) {
            throw new BasisDecodeException("Invalid XUASTC solid-color neighbor config reuse");
        }
        return new NeighborBlock(state, config);
    }

    private static int neighborFlagModelIndex(
            BlockState leftState,
            BlockState upperState,
            FlagField field) {
        int modelIndex = leftState != null && field.get(leftState) ? 1 : 0;
        if (leftState == null) {
            modelIndex = 1;
        }
        if (upperState != null) {
            modelIndex |= field.get(upperState) ? 2 : 0;
        } else {
            modelIndex |= 2;
        }
        return modelIndex;
    }

    private static int partHashIndex(int uniquePatternIndex) {
        return (int) ((uniquePatternIndex * 2_654_435_769L) & (PART_HASH_SIZE - 1));
    }

    private static boolean cemSupportsBlueContraction(int cem) {
        return cem == XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET;
    }

    private static boolean usedBlueContraction(
            int cem,
            int[] endpoints,
            int offset,
            int endpointIseRange) {
        if (cem == XuastcAstcConstants.CEM_LDR_RGB_DIRECT
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_DIRECT) {
            int r0 = XuastcAstcConstants.dequantBiseEndpoint(endpoints[offset], endpointIseRange);
            int r1 = XuastcAstcConstants.dequantBiseEndpoint(endpoints[offset + 1], endpointIseRange);
            int g0 = XuastcAstcConstants.dequantBiseEndpoint(endpoints[offset + 2], endpointIseRange);
            int g1 = XuastcAstcConstants.dequantBiseEndpoint(endpoints[offset + 3], endpointIseRange);
            int b0 = XuastcAstcConstants.dequantBiseEndpoint(endpoints[offset + 4], endpointIseRange);
            int b1 = XuastcAstcConstants.dequantBiseEndpoint(endpoints[offset + 5], endpointIseRange);
            return r1 + g1 + b1 < r0 + g0 + b0;
        }
        if (cem == XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET
                || cem == XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET) {
            return basePlusOffsetDeltaSum(endpoints, offset, endpointIseRange) < 0;
        }
        return false;
    }

    private static int basePlusOffsetDeltaSum(int[] endpoints, int offset, int endpointIseRange) {
        int red = signedBasePlusOffsetDelta(
                endpoints[offset + 1],
                endpointIseRange);
        int green = signedBasePlusOffsetDelta(
                endpoints[offset + 3],
                endpointIseRange);
        int blue = signedBasePlusOffsetDelta(
                endpoints[offset + 5],
                endpointIseRange);
        return red + green + blue;
    }

    private static int signedBasePlusOffsetDelta(int deltaEndpoint, int endpointIseRange) {
        int delta = XuastcAstcConstants.dequantBiseEndpoint(deltaEndpoint, endpointIseRange);
        int signedDelta = (delta >> 1) & 0x3F;
        if ((signedDelta & 0x20) != 0) {
            signedDelta -= 0x40;
        }
        return signedDelta;
    }

    private static int positiveMod(int value, int modulus) {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    private enum FlagField {
        REUSED_FULL_CONFIG {
            @Override
            boolean get(BlockState state) {
                return state.reusedFullConfig;
            }
        },
        USED_PART_HASH {
            @Override
            boolean get(BlockState state) {
                return state.usedPartHash;
            }
        },
        USED_WEIGHT_DCT {
            @Override
            boolean get(BlockState state) {
                return state.usedWeightDct;
            }
        },
        FIRST_ENDPOINT_USES_BC {
            @Override
            boolean get(BlockState state) {
                return state.firstEndpointUsesBlueContraction;
            }
        };

        abstract boolean get(BlockState state);
    }

    private static final class NeighborBlock {
        private final BlockState state;
        private final XuastcLdrRawBlockConfig config;

        private NeighborBlock(BlockState state, XuastcLdrRawBlockConfig config) {
            this.state = state;
            this.config = config;
        }
    }

    private static final class StreamResult {
        private final XuastcLdrRawBlockConfig[] configs;
        private final XuastcLdrDecodedBlock[] decodedBlocks;

        private StreamResult(
                ArrayList<XuastcLdrRawBlockConfig> configs,
                ArrayList<XuastcLdrDecodedBlock> decodedBlocks) {
            this.configs = configs.toArray(new XuastcLdrRawBlockConfig[0]);
            this.decodedBlocks = decodedBlocks.toArray(new XuastcLdrDecodedBlock[0]);
        }

        private XuastcLdrRawBlockConfig[] getConfigs() {
            return configs.clone();
        }

        private XuastcLdrDecodedBlock[] getDecodedBlocks() {
            return decodedBlocks.clone();
        }
    }

    private static final class BlockState {
        private final boolean wasSolidColor;
        private final boolean usedWeightDct;
        private final boolean firstEndpointUsesBlueContraction;
        private final boolean reusedFullConfig;
        private final boolean usedPartHash;
        private final int trialModeIndex;
        private final int baseCemIndex;
        private final int subsetIndex;
        private final int ccsIndex;
        private final int gridSizeIndex;
        private final int gridAnisoIndex;

        private BlockState(
                boolean wasSolidColor,
                boolean usedWeightDct,
                boolean firstEndpointUsesBlueContraction,
                boolean reusedFullConfig,
                boolean usedPartHash,
                int trialModeIndex,
                int baseCemIndex,
                int subsetIndex,
                int ccsIndex,
                int gridSizeIndex,
                int gridAnisoIndex) {
            this.wasSolidColor = wasSolidColor;
            this.usedWeightDct = usedWeightDct;
            this.firstEndpointUsesBlueContraction = firstEndpointUsesBlueContraction;
            this.reusedFullConfig = reusedFullConfig;
            this.usedPartHash = usedPartHash;
            this.trialModeIndex = trialModeIndex;
            this.baseCemIndex = baseCemIndex;
            this.subsetIndex = subsetIndex;
            this.ccsIndex = ccsIndex;
            this.gridSizeIndex = gridSizeIndex;
            this.gridAnisoIndex = gridAnisoIndex;
        }

        private static BlockState solid(boolean imageUsesDct) {
            return new BlockState(
                    true,
                    imageUsesDct,
                    true,
                    false,
                    true,
                    -1,
                    XuastcAstcConstants.CEM_LDR_RGB_DIRECT,
                    0,
                    0,
                    0,
                    0);
        }

        private static BlockState copyForRun(BlockState previous) {
            if (previous == null) {
                throw new BasisDecodeException("Invalid XUASTC run with no previous block state");
            }
            return new BlockState(
                    previous.wasSolidColor,
                    previous.usedWeightDct,
                    previous.firstEndpointUsesBlueContraction,
                    true,
                    previous.usedPartHash,
                    previous.trialModeIndex,
                    previous.baseCemIndex,
                    previous.subsetIndex,
                    previous.ccsIndex,
                    previous.gridSizeIndex,
                    previous.gridAnisoIndex);
        }

        private static BlockState fromConfig(XuastcLdrRawBlockConfig config) {
            int actualCem = config.getActualColorEndpointMode();
            boolean firstEndpointUsesBlueContraction = cemSupportsBlueContraction(actualCem)
                    && usedBlueContraction(
                            actualCem,
                            config.getEndpoints(),
                            0,
                            config.getTrialMode().getEndpointIseRange());
            return new BlockState(
                    false,
                    config.blockUsesDct(),
                    firstEndpointUsesBlueContraction,
                    config.getConfigReuseIndex() < CONFIG_REUSE_NEIGHBORS,
                    config.isPartitionHashUsed(),
                    config.getTrialModeIndex(),
                    config.getColorEndpointModeIndex(),
                    config.getSubsetIndex(),
                    config.getColorComponentSelectorIndex(),
                    config.getGridSizeIndex(),
                    config.getGridAnisoIndex());
        }
    }
}
