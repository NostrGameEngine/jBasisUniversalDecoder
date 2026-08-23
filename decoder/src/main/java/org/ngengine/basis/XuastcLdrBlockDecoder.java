package org.ngengine.basis;

/**
 * Decodes a reconstructed XUASTC LDR logical block into RGBA8 texels.
 */
final class XuastcLdrBlockDecoder {
    private static final int RGBA_COMPONENTS = 4;

    private XuastcLdrBlockDecoder() {
    }

    static byte[] decodeRgbaBlock(XuastcLdrImageHeader header, XuastcLdrRawBlockConfig config) {
        validateSupportedBlock(header, config);
        int blockWidth = header.getBlockWidth();
        int blockHeight = header.getBlockHeight();
        XuastcTrialMode trialMode = config.getTrialMode();
        int[] endpoints = decodePartitionEndpoints(config, trialMode);
        int[] weights = decodeWeights(
                config.getWeightGrid(), trialMode.getWeightIseRange(), blockWidth, blockHeight);
        int planeCount = config.getWeightGrid().getPlaneCount();
        int dualPlaneComponent = trialMode.getColorComponentSelector();
        int[] partitions = trialMode.getNumberOfPartitions() == 1
                ? null
                : XuastcAstcPartitioner.computePartitionMap(
                        blockWidth,
                        blockHeight,
                        config.getPartitionSeed(),
                        trialMode.getNumberOfPartitions());

        byte[] rgba = new byte[blockWidth * blockHeight * RGBA_COMPONENTS];
        for (int y = 0; y < blockHeight; y++) {
            for (int x = 0; x < blockWidth; x++) {
                int texel = x + y * blockWidth;
                int partition = partitions == null ? 0 : partitions[texel];
                int endpointOffset = partition * 8;
                int weight0 = weights[texel * planeCount];
                int dst = texel * RGBA_COMPONENTS;
                rgba[dst] = (byte) interpolateColor(
                        endpoints[endpointOffset],
                        endpoints[endpointOffset + 4],
                        weightForComponent(weights, texel, planeCount, dualPlaneComponent, 0, weight0),
                        header.isSrgbDecodeProfile());
                rgba[dst + 1] = (byte) interpolateColor(
                        endpoints[endpointOffset + 1],
                        endpoints[endpointOffset + 5],
                        weightForComponent(weights, texel, planeCount, dualPlaneComponent, 1, weight0),
                        header.isSrgbDecodeProfile());
                rgba[dst + 2] = (byte) interpolateColor(
                        endpoints[endpointOffset + 2],
                        endpoints[endpointOffset + 6],
                        weightForComponent(weights, texel, planeCount, dualPlaneComponent, 2, weight0),
                        header.isSrgbDecodeProfile());
                rgba[dst + 3] = (byte) interpolateColor(
                        endpoints[endpointOffset + 3],
                        endpoints[endpointOffset + 7],
                        weightForComponent(weights, texel, planeCount, dualPlaneComponent, 3, weight0),
                        header.isSrgbDecodeProfile());
            }
        }
        return rgba;
    }

    private static void validateSupportedBlock(XuastcLdrImageHeader header, XuastcLdrRawBlockConfig config) {
        if (!isSupportedLdrCem(config.getActualColorEndpointMode())) {
            throw new BasisDecodeException("Unsupported XUASTC block endpoint CEM");
        }
        int planeCount = config.getWeightGrid().getPlaneCount();
        if (planeCount < 1 || planeCount > 2) {
            throw new BasisDecodeException("XUASTC block decode supports one or two weight planes");
        }
        int numberOfPartitions = config.getTrialMode().getNumberOfPartitions();
        if (numberOfPartitions < 1 || numberOfPartitions > 3) {
            throw new BasisDecodeException("XUASTC block decode supports one to three partitions");
        }
    }

    private static int weightForComponent(
            int[] weights,
            int texel,
            int planeCount,
            int dualPlaneComponent,
            int component,
            int weight0) {
        if (planeCount == 2 && dualPlaneComponent == component) {
            return weights[texel * planeCount + 1];
        }
        return weight0;
    }

    private static boolean isSupportedLdrCem(int cem) {
        switch (cem) {
            case XuastcAstcConstants.CEM_LDR_LUM_DIRECT:
            case XuastcAstcConstants.CEM_LDR_LUM_ALPHA_DIRECT:
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE:
            case XuastcAstcConstants.CEM_LDR_RGB_DIRECT:
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_PLUS_OFFSET:
            case XuastcAstcConstants.CEM_LDR_RGB_BASE_SCALE_PLUS_TWO_A:
            case XuastcAstcConstants.CEM_LDR_RGBA_DIRECT:
            case XuastcAstcConstants.CEM_LDR_RGBA_BASE_PLUS_OFFSET:
                return true;
            default:
                return false;
        }
    }

    private static int[] decodePartitionEndpoints(XuastcLdrRawBlockConfig config, XuastcTrialMode trialMode) {
        int endpointValues = XuastcAstcConstants.numCemEndpointValues(config.getActualColorEndpointMode());
        int[] rawEndpoints = config.getEndpoints();
        int[] decoded = new int[trialMode.getNumberOfPartitions() * 8];
        for (int partition = 0; partition < trialMode.getNumberOfPartitions(); partition++) {
            int[] partitionEndpoints = XuastcAstcConstants.decodeLdrEndpoints(
                    config.getActualColorEndpointMode(),
                    rawEndpoints,
                    partition * endpointValues,
                    trialMode.getEndpointIseRange());
            System.arraycopy(partitionEndpoints, 0, decoded, partition * 8, partitionEndpoints.length);
        }
        return decoded;
    }

    private static int[] decodeWeights(
            XuastcLdrWeightGrid weightGrid,
            int weightIseRange,
            int blockWidth,
            int blockHeight) {
        int planeCount = weightGrid.getPlaneCount();
        int totalWeights = weightGrid.getGridWidth() * weightGrid.getGridHeight();
        int[] weightSymbols = weightGrid.getWeights();
        if (weightSymbols.length != totalWeights * planeCount) {
            throw new BasisDecodeException("Invalid XUASTC weight-grid length");
        }

        int[] result = new int[blockWidth * blockHeight * planeCount];
        for (int plane = 0; plane < planeCount; plane++) {
            int[] planeWeights = new int[totalWeights];
            for (int i = 0; i < totalWeights; i++) {
                planeWeights[i] = XuastcAstcConstants.dequantBiseWeight(
                        weightSymbols[i * planeCount + plane],
                        weightIseRange);
            }
            int[] upsampled = upsampleWeights(
                    blockWidth,
                    blockHeight,
                    weightGrid.getGridWidth(),
                    weightGrid.getGridHeight(),
                    planeWeights);
            for (int i = 0; i < upsampled.length; i++) {
                result[i * planeCount + plane] = upsampled[i];
            }
        }
        return result;
    }

    static int[] upsampleWeights(
            int blockWidth,
            int blockHeight,
            int gridWidth,
            int gridHeight,
            int[] weights) {
        if (gridWidth < 2 || gridHeight < 2 || blockWidth < gridWidth || blockHeight < gridHeight
                || blockWidth > 12 || blockHeight > 12) {
            throw new BasisDecodeException("Invalid XUASTC weight-grid dimensions");
        }
        if (weights.length != gridWidth * gridHeight) {
            throw new BasisDecodeException("Invalid XUASTC weight-grid length");
        }
        if (blockWidth == gridWidth && blockHeight == gridHeight) {
            return weights.clone();
        }

        int[] upsampled = new int[blockWidth * blockHeight];
        int scaleX = (1024 + blockWidth / 2) / (blockWidth - 1);
        int scaleY = (1024 + blockHeight / 2) / (blockHeight - 1);
        int gridYIncrement = scaleY * (gridHeight - 1);
        int gridXIncrement = scaleX * (gridWidth - 1);

        int gridYU = 32;
        for (int texelY = 0; texelY < blockHeight; texelY++) {
            int gridY = gridYU >> 6;
            gridYU += gridYIncrement;
            int sourceY = gridY >> 4;
            int fractionY = gridY & 0xF;

            int gridXU = 32;
            for (int texelX = 0; texelX < blockWidth; texelX++) {
                int gridX = gridXU >> 6;
                gridXU += gridXIncrement;
                int sourceX = gridX >> 4;
                int fractionX = gridX & 0xF;

                int weight11 = (fractionX * fractionY + 8) >> 4;
                int weight10 = fractionY - weight11;
                int weight01 = fractionX - weight11;
                int weight00 = 16 - fractionX - fractionY + weight11;
                int total = 8;
                if (weight00 != 0) {
                    total += weights[sourceX + sourceY * gridWidth] * weight00;
                }
                if (weight01 != 0) {
                    total += weights[sourceX + 1 + sourceY * gridWidth] * weight01;
                }
                if (weight10 != 0) {
                    total += weights[sourceX + (sourceY + 1) * gridWidth] * weight10;
                }
                if (weight11 != 0) {
                    total += weights[sourceX + 1 + (sourceY + 1) * gridWidth] * weight11;
                }
                upsampled[texelX + texelY * blockWidth] = total >> 4;
            }
        }
        return upsampled;
    }

    private static int interpolateColor(int low, int high, int weight, boolean srgb) {
        int low16 = srgb ? (low << 8) | 0x80 : (low << 8) | low;
        int high16 = srgb ? (high << 8) | 0x80 : (high << 8) | high;
        return interpolate(low16, high16, weight) >> 8;
    }

    private static int interpolate(int low, int high, int weight) {
        return (low * (64 - weight) + high * weight + 32) >> 6;
    }
}
