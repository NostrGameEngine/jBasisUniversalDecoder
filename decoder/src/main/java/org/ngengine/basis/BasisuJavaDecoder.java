package org.ngengine.basis;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Java implementation of Basis Universal/KTX2 decode + transcode path.
 *
 * This class currently implements the decoder surface directly in Java.
 * It is intentionally strict and rejects payloads/features it does not implement.
 */
public final class BasisuJavaDecoder implements BasisDecoder {
    private static final BasisTranscodeTarget[] JAVA_OUTPUT_TARGETS = {
        BasisTranscodeTarget.RGBA8,
        BasisTranscodeTarget.RGBA4444,
        BasisTranscodeTarget.RGB565,
        BasisTranscodeTarget.BGR565
    };
    private static final BasisTranscodeTarget[] ETC1S_OUTPUT_TARGETS = {
        BasisTranscodeTarget.BC3,
        BasisTranscodeTarget.BC5,
        BasisTranscodeTarget.BC4,
        BasisTranscodeTarget.BC1,
        BasisTranscodeTarget.ETC2,
        BasisTranscodeTarget.ETC2_NO_ALPHA,
        BasisTranscodeTarget.ETC2_EAC_R11,
        BasisTranscodeTarget.ETC2_EAC_RG11,
        BasisTranscodeTarget.ETC1,
        BasisTranscodeTarget.RGBA8,
        BasisTranscodeTarget.RGBA4444,
        BasisTranscodeTarget.RGB565,
        BasisTranscodeTarget.BGR565
    };
    private static final BasisTranscodeTarget[] HDR_ASTC_OUTPUT_TARGETS = {
        BasisTranscodeTarget.ASTC_HDR_4X4,
        BasisTranscodeTarget.ASTC_HDR_6X6
    };

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getBackendName() {
        return "BasisuJavaDecoder";
    }

    @Override
    public BasisDecodeResult decode(BasisDecodeRequest request) {
        if (request == null) {
            throw new BasisDecodeException("Decode request must not be null");
        }

        byte[] source = request.getEncodedData();
        if (isKtx2(source)) {
            return decodeKtx2(request, source);
        }
        if (isBasis(source)) {
            BasisContainer container = BasisContainer.parse(source);
            if (isBasisEtc1sEtc1Request(container, request)) {
                return decodeBasisEtc1sEtc1(request, source, container);
            }
            if (isBasisEtc1sRgbaRequest(container, request)) {
                return decodeBasisEtc1sRgba(request, source, container);
            }
            if (isBasisUastcLdrRequest(container, request)) {
                return decodeBasisUastcLdr(request, source, container);
            }
            if (isBasisHdrAstcRequest(container, request)) {
                return decodeBasisHdrAstc(request, source, container);
            }
            if (container.getTextureFormat().isHdr()) {
                throw unsupportedTarget(selectHdrAstcJavaTarget(container.getTextureFormat(), request));
            }
            if (container.getTextureFormat() == Ktx2BasisTextureFormat.cETC1S) {
                throw unsupportedTarget(selectBasisEtc1sJavaTarget(container, request));
            }
            throw new BasisDecodeException(
                    "Unsupported Basis texture format "
                            + container.getTextureFormat());
        }
        throw new BasisDecodeException("Input is not a supported Basis or KTX2 container");
    }

    private static BasisDecodeResult decodeKtx2(BasisDecodeRequest request, byte[] source) {
        Ktx2Container container = Ktx2Container.parse(source);
        if (isEtc1sEtc1Request(container, request)) {
            return decodeKtx2Etc1sEtc1(request, source, container);
        }
        if (isEtc1sRgbaRequest(container, request)) {
            return decodeKtx2Etc1sRgba(request, source, container);
        }
        if (isXuastcLdrAstcRequest(container, request)) {
            return decodeKtx2XuastcLdrAstc(request, source, container);
        }
        if (isUastcLdrAstcRequest(container, request)) {
            return decodeKtx2UastcLdrAstc(request, source, container);
        }
        if (isUastcLdrRgbaRequest(container, request)) {
            return decodeKtx2UastcLdrRgba(request, source, container);
        }
        if (isXuastcLdrRgbaRequest(container, request)) {
            return decodeKtx2XuastcLdrRgba(request, source, container);
        }
        if (isKtx2HdrAstcRequest(container, request)) {
            return decodeKtx2HdrAstc(request, source, container);
        }
        if (container.getDataFormatDescriptor() != null && container.getBasisTextureFormat().isHdr()) {
            throw unsupportedTarget(selectHdrAstcJavaTarget(container.getBasisTextureFormat(), request));
        }
        BasisTranscodeTarget selectedTranscodeTarget = container.getDataFormatDescriptor() != null
                && container.getBasisTextureFormat() == Ktx2BasisTextureFormat.cETC1S
                ? selectKtx2Etc1sJavaTarget(container, request)
                : selectXuastcOrJavaTarget(container, request);
        if (isBasisSupercompressedPayload(container)
                && selectedTranscodeTarget != BasisTranscodeTarget.RGBA8) {
            throw unsupportedTarget(selectedTranscodeTarget);
        }

        Ktx2Header header = container.getHeader();
        if (!header.getSupercompressionScheme().isSupportedByJavaDecoder()) {
            String textureFormat = container.getDataFormatDescriptor() == null
                    ? "unknown"
                    : container.getBasisTextureFormat().name();
            String detail = unsupportedTextureDetail(source, container);
            throw new BasisDecodeException(
                    "Unsupported KTX2 supercompression "
                            + header.getSupercompressionScheme()
                            + " and texture format "
                            + textureFormat
                            + detail);
        }

        BasisTranscodeTarget selectedTarget = selectExplicitJavaTarget(request, true);
        BasisImageFormat imageFormat = resolveImageFormat(header.getVkFormat(), selectedTarget);
        if (imageFormat == null) {
            throw new BasisDecodeException(
                    "Unsupported KTX2 payload format " + header.getVkFormat());
        }

        final BasisColorSpace colorSpace =
                resolveColorSpace(header.getVkFormat(), request.isLinearColorSpace());

        validateMetadataOffsets(header, source.length);

        int levelCount = header.getLevelCount();
        int imageCount = ktx2ImageCount(header);
        validatedKtx2ImageIndex(request, imageCount);
        int[] mipMapSizes = new int[levelCount];
        byte[][] decodedLevels = new byte[levelCount][];
        long totalSize = 0;
        for (int i = 0; i < levelCount; i++) {
            Ktx2LevelIndex level = container.getLevel(i);
            decodedLevels[i] = ktx2LevelPayload(source, header, level);
            mipMapSizes[i] = decodedLevels[i].length;
            totalSize = checkedAdd(totalSize, decodedLevels[i].length);
        }

        int totalSizeInt = checkedToInt(totalSize);

        ByteBuffer decoded = request.getAllocator().apply(totalSizeInt);
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();

        return new BasisDecodeResult(
                header.getPixelWidth(),
                header.getPixelHeight(),
                decoded,
                imageFormat,
                mipMapSizes,
                colorSpace,
                imageCount,
                levelCount);
    }

    private static boolean isEtc1sRgbaRequest(Ktx2Container container, BasisDecodeRequest request) {
        if (container.getHeader().getSupercompressionScheme() != Ktx2SupercompressionScheme.BASISLZ
                || container.getDataFormatDescriptor() == null
                || container.getBasisTextureFormat() != Ktx2BasisTextureFormat.cETC1S) {
            return false;
        }
        BasisTranscodeTarget target = selectKtx2Etc1sJavaTarget(container, request);
        return target == null || isRgbaOrPackedTarget(target);
    }

    private static boolean isEtc1sEtc1Request(Ktx2Container container, BasisDecodeRequest request) {
        if (container.getHeader().getSupercompressionScheme() != Ktx2SupercompressionScheme.BASISLZ
                || container.getDataFormatDescriptor() == null
                || container.getBasisTextureFormat() != Ktx2BasisTextureFormat.cETC1S) {
            return false;
        }
        return isEtc1sBlockTarget(selectKtx2Etc1sJavaTarget(container, request));
    }

    private static boolean isBasisEtc1sRgbaRequest(BasisContainer container, BasisDecodeRequest request) {
        return container.getTextureFormat() == Ktx2BasisTextureFormat.cETC1S
                && isRgbaOrPackedTarget(selectBasisEtc1sJavaTarget(container, request));
    }

    private static boolean isBasisEtc1sEtc1Request(BasisContainer container, BasisDecodeRequest request) {
        return container.getTextureFormat() == Ktx2BasisTextureFormat.cETC1S
                && isEtc1sBlockTarget(selectBasisEtc1sJavaTarget(container, request));
    }

    private static boolean isBasisUastcLdrRequest(BasisContainer container, BasisDecodeRequest request) {
        if (container.getTextureFormat() != Ktx2BasisTextureFormat.cUASTC_LDR_4x4) {
            return false;
        }
        BasisTranscodeTarget target = selectUastcLdrJavaTarget(request);
        return isUastcLdrJavaTarget(target) || target == BasisTranscodeTarget.ASTC_LDR_4X4;
    }

    private static boolean isBasisHdrAstcRequest(BasisContainer container, BasisDecodeRequest request) {
        Ktx2BasisTextureFormat format = container.getTextureFormat();
        return matchingHdrAstcTarget(format) != null
                && isHdrAstcJavaTarget(format, selectHdrAstcJavaTarget(format, request));
    }

    private static boolean isXuastcLdrRgbaRequest(Ktx2Container container, BasisDecodeRequest request) {
        Ktx2SupercompressionScheme scheme = container.getHeader().getSupercompressionScheme();
        if ((scheme != Ktx2SupercompressionScheme.BASISLZ && scheme != Ktx2SupercompressionScheme.XUASTC_LDR)
                || container.getDataFormatDescriptor() == null
                || !container.getBasisTextureFormat().isXUastcLdr()) {
            return false;
        }
        BasisTranscodeTarget target = selectXuastcJavaTarget(container, request);
        return target == null
                || isXuastcLdrJavaTarget(target)
                || isXuastcBc7Target(container, target)
                || isXuastcFastBc7Target(container, request, target);
    }

    private static boolean isXuastcLdrAstcRequest(Ktx2Container container, BasisDecodeRequest request) {
        Ktx2SupercompressionScheme scheme = container.getHeader().getSupercompressionScheme();
        if ((scheme != Ktx2SupercompressionScheme.BASISLZ && scheme != Ktx2SupercompressionScheme.XUASTC_LDR)
                || container.getDataFormatDescriptor() == null
                || !container.getBasisTextureFormat().isXUastcLdr()) {
            return false;
        }
        return selectXuastcJavaTarget(container, request).isAstcLdr();
    }

    private static boolean isUastcLdrAstcRequest(Ktx2Container container, BasisDecodeRequest request) {
        if (container.getDataFormatDescriptor() == null
                || container.getBasisTextureFormat() != Ktx2BasisTextureFormat.cUASTC_LDR_4x4) {
            return false;
        }
        Ktx2SupercompressionScheme scheme = container.getHeader().getSupercompressionScheme();
        if (scheme != Ktx2SupercompressionScheme.NONE
                && scheme != Ktx2SupercompressionScheme.ZSTANDARD
                && scheme != Ktx2SupercompressionScheme.DEFLATE) {
            return false;
        }
        return selectUastcLdrJavaTarget(request).isAstcLdr();
    }

    private static boolean isUastcLdrRgbaRequest(Ktx2Container container, BasisDecodeRequest request) {
        if (container.getDataFormatDescriptor() == null
                || container.getBasisTextureFormat() != Ktx2BasisTextureFormat.cUASTC_LDR_4x4) {
            return false;
        }
        Ktx2SupercompressionScheme scheme = container.getHeader().getSupercompressionScheme();
        if (scheme != Ktx2SupercompressionScheme.NONE
                && scheme != Ktx2SupercompressionScheme.ZSTANDARD
                && scheme != Ktx2SupercompressionScheme.DEFLATE) {
            return false;
        }
        return isUastcLdrJavaTarget(selectUastcLdrJavaTarget(request));
    }

    private static boolean isKtx2HdrAstcRequest(Ktx2Container container, BasisDecodeRequest request) {
        if (container.getDataFormatDescriptor() == null
                || matchingHdrAstcTarget(container.getBasisTextureFormat()) == null) {
            return false;
        }
        Ktx2SupercompressionScheme scheme = container.getHeader().getSupercompressionScheme();
        if (container.getBasisTextureFormat() == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE) {
            return isHdr6x6IntermediateScheme(scheme)
                    && isHdrAstcJavaTarget(container.getBasisTextureFormat(),
                    selectHdrAstcJavaTarget(container.getBasisTextureFormat(), request));
        }
        if (scheme != Ktx2SupercompressionScheme.NONE
                && scheme != Ktx2SupercompressionScheme.ZSTANDARD
                && scheme != Ktx2SupercompressionScheme.DEFLATE) {
            return false;
        }
        return isHdrAstcJavaTarget(container.getBasisTextureFormat(),
                selectHdrAstcJavaTarget(container.getBasisTextureFormat(), request));
    }

    private static boolean isHdr6x6IntermediateScheme(Ktx2SupercompressionScheme scheme) {
        return scheme == Ktx2SupercompressionScheme.UASTC_HDR_6X6I
                || scheme == Ktx2SupercompressionScheme.BASISLZ;
    }

    private static BasisDecodeResult decodeKtx2Etc1sRgba(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container) {
        return decodeKtx2Etc1s(request, source, container, selectKtx2Etc1sJavaTarget(container, request));
    }

    private static BasisDecodeResult decodeKtx2Etc1sEtc1(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container) {
        return decodeKtx2Etc1s(request, source, container, selectKtx2Etc1sJavaTarget(container, request));
    }

    private static BasisDecodeResult decodeKtx2Etc1s(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container,
            BasisTranscodeTarget outputTarget) {
        boolean blockOutput = isEtc1sBlockTarget(outputTarget);
        Ktx2Etc1sGlobalData globalData = requireEtc1sGlobalData(container);
        Etc1sCodebookTables tables = Etc1sCodebookTables.decode(
                source,
                checkedToInt(globalData.getTablesByteOffset()),
                checkedToInt(globalData.getTablesByteLength()));
        Etc1sPalettes palettes = Etc1sPalettes.decode(
                globalData.getEndpointCount(),
                source,
                checkedToInt(globalData.getEndpointsByteOffset()),
                checkedToInt(globalData.getEndpointsByteLength()),
                globalData.getSelectorCount(),
                source,
                checkedToInt(globalData.getSelectorsByteOffset()),
                checkedToInt(globalData.getSelectorsByteLength()));

        Ktx2Header header = container.getHeader();
        Ktx2Etc1sImageDesc[] imageDescriptions = globalData.getImageDescriptors();
        int levelCount = header.getLevelCount();
        int imageCount = ktx2ImageCount(header);
        int selectedImageIndex = validatedKtx2ImageIndex(request, imageCount);
        int totalImageLevelCount = Math.multiplyExact(imageCount, levelCount);
        if (imageDescriptions.length < totalImageLevelCount) {
            throw new BasisDecodeException(
                    "KTX2 ETC1S payload has fewer image descriptors than image levels");
        }

        int[] mipMapSizes = new int[levelCount];
        byte[][] decodedLevels = new byte[levelCount][];
        long totalSize = 0;
        for (int levelIndex = 0; levelIndex < levelCount; levelIndex++) {
            int imageLevelIndex = imageLevelIndex(selectedImageIndex, levelIndex, imageCount);
            Ktx2Etc1sImageDesc imageDesc = imageDescriptions[imageLevelIndex];
            Ktx2LevelIndex level = container.getLevel(levelIndex);
            int width = Math.max(1, header.getPixelWidth() >>> levelIndex);
            int height = Math.max(1, header.getPixelHeight() >>> levelIndex);
            int sliceOffset = checkedToInt(level.getByteOffset() + imageDesc.getRgbSliceByteOffset());
            int sliceLength = checkedToInt(imageDesc.getRgbSliceByteLength());
            if (outputTarget == BasisTranscodeTarget.BC7) {
                if (imageDesc.getAlphaSliceByteLength() == 0) {
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc7Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                } else {
                    int alphaSliceOffset = checkedToInt(level.getByteOffset()
                            + imageDesc.getAlphaSliceByteOffset());
                    int alphaSliceLength = checkedToInt(imageDesc.getAlphaSliceByteLength());
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc7Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            source,
                            alphaSliceOffset,
                            alphaSliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                }
            } else if (outputTarget == BasisTranscodeTarget.BC3) {
                if (imageDesc.getAlphaSliceByteLength() == 0) {
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc3Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                } else {
                    int alphaSliceOffset = checkedToInt(level.getByteOffset()
                            + imageDesc.getAlphaSliceByteOffset());
                    int alphaSliceLength = checkedToInt(imageDesc.getAlphaSliceByteLength());
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc3Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            source,
                            alphaSliceOffset,
                            alphaSliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                }
            } else if (outputTarget == BasisTranscodeTarget.BC5) {
                if (imageDesc.getAlphaSliceByteLength() == 0) {
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc5Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                } else {
                    int alphaSliceOffset = checkedToInt(level.getByteOffset()
                            + imageDesc.getAlphaSliceByteOffset());
                    int alphaSliceLength = checkedToInt(imageDesc.getAlphaSliceByteLength());
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc5Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            source,
                            alphaSliceOffset,
                            alphaSliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                }
            } else if (outputTarget == BasisTranscodeTarget.BC4) {
                decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc4Blocks(
                        source,
                        sliceOffset,
                        sliceLength,
                        width,
                        height,
                        tables,
                        palettes);
            } else if (outputTarget == BasisTranscodeTarget.BC1) {
                decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeBc1Blocks(
                        source,
                        sliceOffset,
                        sliceLength,
                        width,
                        height,
                        tables,
                        palettes);
            } else if (outputTarget == BasisTranscodeTarget.ETC2) {
                if (imageDesc.getAlphaSliceByteLength() == 0) {
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeEtc2RgbaBlocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                } else {
                    int alphaSliceOffset = checkedToInt(level.getByteOffset()
                            + imageDesc.getAlphaSliceByteOffset());
                    int alphaSliceLength = checkedToInt(imageDesc.getAlphaSliceByteLength());
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeEtc2RgbaBlocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            source,
                            alphaSliceOffset,
                            alphaSliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                }
            } else if (outputTarget == BasisTranscodeTarget.ETC2_EAC_R11) {
                decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeEtc2EacR11Blocks(
                        source,
                        sliceOffset,
                        sliceLength,
                        width,
                        height,
                        tables,
                        palettes);
            } else if (outputTarget == BasisTranscodeTarget.ETC2_EAC_RG11) {
                if (imageDesc.getAlphaSliceByteLength() == 0) {
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeEtc2EacRg11Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                } else {
                    int alphaSliceOffset = checkedToInt(level.getByteOffset()
                            + imageDesc.getAlphaSliceByteOffset());
                    int alphaSliceLength = checkedToInt(imageDesc.getAlphaSliceByteLength());
                    decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeEtc2EacRg11Blocks(
                            source,
                            sliceOffset,
                            sliceLength,
                            source,
                            alphaSliceOffset,
                            alphaSliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                }
            } else if (blockOutput) {
                decodedLevels[levelIndex] = Etc1sRgbaDecoder.decodeEtc1Blocks(
                        source,
                        sliceOffset,
                        sliceLength,
                        width,
                        height,
                        tables,
                        palettes);
            } else {
                byte[] rgbaLevel = Etc1sRgbaDecoder.decode(
                        source,
                        sliceOffset,
                        sliceLength,
                        width,
                        height,
                        tables,
                        palettes);
                if (imageDesc.getAlphaSliceByteLength() != 0) {
                    int alphaSliceOffset = checkedToInt(level.getByteOffset()
                            + imageDesc.getAlphaSliceByteOffset());
                    int alphaSliceLength = checkedToInt(imageDesc.getAlphaSliceByteLength());
                    byte[] alphaLevel = Etc1sRgbaDecoder.decode(
                            source,
                            alphaSliceOffset,
                            alphaSliceLength,
                            width,
                            height,
                            tables,
                            palettes);
                    applyEtc1sAlphaSlice(rgbaLevel, alphaLevel);
                }
                decodedLevels[levelIndex] = convertRgbaLevel(rgbaLevel, outputTarget);
            }
            mipMapSizes[levelIndex] = decodedLevels[levelIndex].length;
            totalSize = checkedAdd(totalSize, decodedLevels[levelIndex].length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();
        return new BasisDecodeResult(
                header.getPixelWidth(),
                header.getPixelHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                resolveColorSpace(header.getVkFormat(), request.isLinearColorSpace()),
                imageCount,
                levelCount);
    }

    private static void applyEtc1sAlphaSlice(byte[] rgbaLevel, byte[] alphaLevel) {
        if (rgbaLevel.length != alphaLevel.length || rgbaLevel.length % 4 != 0) {
            throw new BasisDecodeException("KTX2 ETC1S alpha slice dimensions do not match RGB slice");
        }
        for (int i = 0; i < rgbaLevel.length; i += 4) {
            rgbaLevel[i + 3] = alphaLevel[i];
        }
    }

    private static BasisDecodeResult decodeKtx2XuastcLdrRgba(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container) {
        return decodeKtx2XuastcLdr(request, source, container, selectXuastcJavaTarget(container, request));
    }

    private static BasisDecodeResult decodeKtx2XuastcLdrAstc(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container) {
        BasisTranscodeTarget outputTarget = selectXuastcJavaTarget(container, request);
        BasisTranscodeTarget matchingTarget = astcTargetForXuastc(container);
        if (matchingTarget == null || outputTarget != matchingTarget) {
            throw unsupportedTarget(outputTarget);
        }
        return decodeKtx2XuastcLdr(request, source, container, outputTarget);
    }

    private static BasisDecodeResult decodeKtx2XuastcLdr(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container,
            BasisTranscodeTarget outputTarget) {
        Ktx2SupercompressionGlobalData globalData = container.getSupercompressionGlobalData();
        if (globalData == null || globalData.getSliceRanges().length == 0) {
            throw new BasisDecodeException("KTX2 XUASTC payload is missing supercompression slice data");
        }

        Ktx2Header header = container.getHeader();
        int levelCount = header.getLevelCount();
        int imageCount = ktx2ImageCount(header);
        int selectedImageIndex = validatedKtx2ImageIndex(request, imageCount);
        int totalImageLevelCount = Math.multiplyExact(imageCount, levelCount);
        Ktx2SliceRange[] sliceRanges = globalData.getSliceRanges();
        if (sliceRanges.length < totalImageLevelCount) {
            throw new BasisDecodeException("KTX2 XUASTC payload has fewer slices than image levels");
        }

        byte[][] decodedLevels = new byte[levelCount][];
        int[] mipMapSizes = new int[levelCount];
        long totalSize = 0;
        BasisColorSpace sourceColorSpace = BasisColorSpace.Linear;
        for (int levelIndex = 0; levelIndex < levelCount; levelIndex++) {
            int imageLevelIndex = imageLevelIndex(selectedImageIndex, levelIndex, imageCount);
            Ktx2LevelIndex level = container.getLevel(levelIndex);
            Ktx2SliceRange slice = sliceRanges[imageLevelIndex];
            int sliceOffset = checkedToInt(level.getByteOffset() + slice.getByteOffset());
            int sliceLength = checkedToInt(slice.getByteLength());
            XuastcLdrImageHeader imageHeader = XuastcLdrImageHeader.parse(source, sliceOffset, sliceLength);
            if (levelIndex == 0 && imageHeader.isSrgbDecodeProfile()) {
                sourceColorSpace = BasisColorSpace.sRGB;
            }
            if (outputTarget.isAstcLdr()) {
                decodedLevels[levelIndex] = XuastcAstcBlockPacker.packImage(source, sliceOffset, sliceLength);
            } else if (isXuastcFastBc7Target(container, request, outputTarget)) {
                decodedLevels[levelIndex] = XuastcBc7FastTranscoder.packImage(
                        source,
                        sliceOffset,
                        sliceLength);
            } else if (isXuastcBc7Target(container, outputTarget)) {
                decodedLevels[levelIndex] = decodeKtx2XuastcBc7Level(
                        request,
                        source,
                        sliceOffset,
                        sliceLength,
                        imageHeader);
            } else {
                byte[] rgbaLevel = XuastcLdrImageDecoder.decodeRgba(source, sliceOffset, sliceLength);
                decodedLevels[levelIndex] = convertRgbaLevel(
                        rgbaLevel,
                        imageHeader.getWidth(),
                        imageHeader.getHeight(),
                        outputTarget);
            }
            mipMapSizes[levelIndex] = decodedLevels[levelIndex].length;
            totalSize = checkedAdd(totalSize, decodedLevels[levelIndex].length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();

        return new BasisDecodeResult(
                header.getPixelWidth(),
                header.getPixelHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                request.isLinearColorSpace() ? sourceColorSpace : BasisColorSpace.sRGB,
                imageCount,
                levelCount);
    }

    private static byte[] decodeKtx2XuastcBc7Level(
            BasisDecodeRequest request,
            byte[] source,
            int sliceOffset,
            int sliceLength,
            XuastcLdrImageHeader imageHeader) {
        boolean highQuality = Ktx2DecodeFlag.isSet(
                request.getDecodeFlags(),
                Ktx2DecodeFlag.cDecodeFlagsHighQuality);
        if (usesXuastcDeblockFiltering(imageHeader.getBlockWidth(), imageHeader.getBlockHeight(), request)) {
            XuastcLdrImageDecoder.DecodedImage rgbaLevel = XuastcLdrImageDecoder
                    .decodeRgbaPaddedToBlockSize(source, sliceOffset, sliceLength);
            byte[] filtered = xuastcDeblockFilter(
                    rgbaLevel.getRgba(),
                    rgbaLevel.getStrideWidth(),
                    rgbaLevel.getSourceHeight(),
                    imageHeader.getBlockWidth(),
                    imageHeader.getBlockHeight(),
                    usesStrongerXuastcDeblockFiltering(
                            imageHeader.getBlockWidth(),
                            imageHeader.getBlockHeight(),
                            request));
            return Bc7Mode6RgbBlockPacker.packAutoRgba(
                    filtered,
                    rgbaLevel.getStrideWidth(),
                    rgbaLevel.getSourceHeight(),
                    rgbaLevel.getVisibleWidth(),
                    rgbaLevel.getVisibleHeight(),
                    highQuality);
        }

        XuastcLdrImageDecoder.DecodedImage rgbaLevel = XuastcLdrImageDecoder
                .decodeRgbaPaddedToVisibleHeight(source, sliceOffset, sliceLength);
        return Bc7Mode6RgbBlockPacker.packAutoRgba(
                rgbaLevel.getRgba(),
                rgbaLevel.getStrideWidth(),
                rgbaLevel.getSourceHeight(),
                rgbaLevel.getVisibleWidth(),
                rgbaLevel.getVisibleHeight(),
                highQuality);
    }

    private static BasisDecodeResult decodeKtx2UastcLdrAstc(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container) {
        return decodeKtx2UastcLdr(request, source, container, BasisTranscodeTarget.ASTC_LDR_4X4);
    }

    private static BasisDecodeResult decodeKtx2UastcLdrRgba(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container) {
        return decodeKtx2UastcLdr(request, source, container, selectUastcLdrJavaTarget(request));
    }

    private static BasisDecodeResult decodeKtx2UastcLdr(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container,
            BasisTranscodeTarget outputTarget) {
        BasisTranscodeTarget requestedTarget = selectUastcLdrJavaTarget(request);
        if (requestedTarget != outputTarget) {
            throw unsupportedTarget(requestedTarget);
        }
        Ktx2Header header = container.getHeader();
        int levelCount = header.getLevelCount();
        int imageCount = ktx2ImageCount(header);
        int selectedImageIndex = validatedKtx2ImageIndex(request, imageCount);

        byte[][] decodedLevels = new byte[levelCount][];
        int[] mipMapSizes = new int[levelCount];
        long totalSize = 0;
        for (int levelIndex = 0; levelIndex < levelCount; levelIndex++) {
            Ktx2LevelIndex level = container.getLevel(levelIndex);
            int width = Math.max(1, header.getPixelWidth() >>> levelIndex);
            int height = Math.max(1, header.getPixelHeight() >>> levelIndex);
            byte[] uastcBlocks = ktx2UastcImagePayload(
                    source,
                    header,
                    level,
                    width,
                    height,
                    imageCount,
                    selectedImageIndex);
            if (outputTarget.isAstcLdr()) {
                decodedLevels[levelIndex] = UastcLdrAstcTranscoder.transcodeToAstc(
                        uastcBlocks,
                        width,
                        height);
            } else if (isEtcColorTarget(outputTarget)) {
                decodedLevels[levelIndex] = UastcLdrAstcTranscoder.transcodeToEtc(
                        uastcBlocks,
                        width,
                        height,
                        outputTarget);
            } else if (isEacTarget(outputTarget)) {
                decodedLevels[levelIndex] = UastcLdrAstcTranscoder.transcodeToEac(
                        uastcBlocks,
                        width,
                        height,
                        outputTarget);
            } else if (outputTarget == BasisTranscodeTarget.BC1) {
                decodedLevels[levelIndex] = UastcLdrAstcTranscoder.transcodeToBc1(
                        uastcBlocks,
                        width,
                        height);
            } else if (outputTarget == BasisTranscodeTarget.BC3) {
                decodedLevels[levelIndex] = UastcLdrAstcTranscoder.transcodeToBc3(
                        uastcBlocks,
                        width,
                        height);
            } else if (outputTarget == BasisTranscodeTarget.BC7) {
                decodedLevels[levelIndex] = UastcLdrAstcTranscoder.transcodeToBc7(
                        uastcBlocks,
                        width,
                        height);
            } else if (isBc4Bc5Target(outputTarget)) {
                decodedLevels[levelIndex] = UastcLdrAstcTranscoder.transcodeToBc4Bc5(
                        uastcBlocks,
                        width,
                        height,
                        outputTarget);
            } else {
                byte[] rgbaLevel = UastcLdrAstcTranscoder.transcodeToRgba(uastcBlocks, width, height);
                decodedLevels[levelIndex] = convertRgbaLevel(rgbaLevel, width, height, outputTarget);
            }
            mipMapSizes[levelIndex] = decodedLevels[levelIndex].length;
            totalSize = checkedAdd(totalSize, decodedLevels[levelIndex].length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();
        return new BasisDecodeResult(
                header.getPixelWidth(),
                header.getPixelHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                resolveKtx2BasisColorSpace(container, request.isLinearColorSpace()),
                imageCount,
                levelCount);
    }

    private static byte[] ktx2UastcImagePayload(
            byte[] source,
            Ktx2Header header,
            Ktx2LevelIndex level,
            int width,
            int height,
            int imageCount,
            int selectedImageIndex) {
        return ktx2BlockImagePayload(
                source,
                header,
                level,
                width,
                height,
                imageCount,
                selectedImageIndex,
                4,
                4);
    }

    private static byte[] ktx2BlockImagePayload(
            byte[] source,
            Ktx2Header header,
            Ktx2LevelIndex level,
            int width,
            int height,
            int imageCount,
            int selectedImageIndex,
            int blockWidth,
            int blockHeight) {
        byte[] levelPayload = ktx2LevelPayload(source, header, level);
        int blocksX = Math.max(1, (width + blockWidth - 1) / blockWidth);
        int blocksY = Math.max(1, (height + blockHeight - 1) / blockHeight);
        int imageBytes = Math.multiplyExact(Math.multiplyExact(blocksX, blocksY), 16);
        int expectedLevelBytes = Math.multiplyExact(imageBytes, imageCount);
        if (levelPayload.length != expectedLevelBytes) {
            throw new BasisDecodeException(
                    "KTX2 block-compressed level decoded size does not match image count");
        }
        int offset = Math.multiplyExact(selectedImageIndex, imageBytes);
        byte[] imagePayload = new byte[imageBytes];
        System.arraycopy(levelPayload, offset, imagePayload, 0, imageBytes);
        return imagePayload;
    }

    private static byte[] ktx2LevelPayload(byte[] source, Ktx2Header header, Ktx2LevelIndex level) {
        int offset = checkedToInt(level.getByteOffset());
        int length = checkedToInt(level.getByteLength());
        if (header.getSupercompressionScheme() == Ktx2SupercompressionScheme.NONE) {
            byte[] payload = new byte[length];
            System.arraycopy(source, offset, payload, 0, length);
            return payload;
        }
        if (header.getSupercompressionScheme() == Ktx2SupercompressionScheme.ZSTANDARD) {
            byte[] payload = ZstdFrameDecoder.decode(source, offset, length);
            int expectedLength = checkedToInt(level.getUncompressedByteLength());
            if (payload.length != expectedLength) {
                throw new BasisDecodeException("KTX2 Zstd level decoded size mismatch");
            }
            return payload;
        }
        if (header.getSupercompressionScheme() == Ktx2SupercompressionScheme.DEFLATE) {
            int expectedLength = checkedToInt(level.getUncompressedByteLength());
            byte[] payload = inflateDeflateLevel(source, offset, length, expectedLength);
            if (payload.length != expectedLength) {
                throw new BasisDecodeException("KTX2 Deflate level decoded size mismatch");
            }
            return payload;
        }
        throw new BasisDecodeException("Unsupported KTX2 level supercompression "
                + header.getSupercompressionScheme());
    }

    private static byte[] inflateDeflateLevel(byte[] source, int offset, int length, int expectedLength) {
        try {
            return inflateWithMode(source, offset, length, expectedLength, false);
        } catch (BasisDecodeException first) {
            return inflateWithMode(source, offset, length, expectedLength, true);
        }
    }

    private static byte[] inflateWithMode(
            byte[] source,
            int offset,
            int length,
            int expectedLength,
            boolean nowrap) {
        Inflater inflater = new Inflater(nowrap);
        try {
            inflater.setInput(source, offset, length);
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(expectedLength, 32));
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read > 0) {
                    output.write(buffer, 0, read);
                    if (output.size() > expectedLength) {
                        throw new BasisDecodeException("KTX2 Deflate output exceeds declared level size");
                    }
                    continue;
                }
                if (inflater.needsDictionary()) {
                    throw new BasisDecodeException("KTX2 Deflate level requires an unsupported dictionary");
                }
                if (inflater.needsInput()) {
                    throw new BasisDecodeException("KTX2 Deflate level ended before stream completion");
                }
                throw new BasisDecodeException("KTX2 Deflate decoder made no progress");
            }
            return output.toByteArray();
        } catch (DataFormatException exception) {
            throw new BasisDecodeException("Invalid KTX2 Deflate level data", exception);
        } finally {
            inflater.end();
        }
    }

    private static BasisDecodeResult decodeKtx2HdrAstc(
            BasisDecodeRequest request,
            byte[] source,
            Ktx2Container container) {
        Ktx2Header header = container.getHeader();
        Ktx2BasisTextureFormat sourceFormat = container.getBasisTextureFormat();
        BasisTranscodeTarget outputTarget = selectHdrAstcJavaTarget(sourceFormat, request);
        BasisTranscodeTarget astcTarget = matchingHdrAstcTarget(sourceFormat);
        if (!isHdrAstcJavaTarget(sourceFormat, outputTarget)) {
            throw unsupportedTarget(outputTarget);
        }

        int levelCount = header.getLevelCount();
        int imageCount = ktx2ImageCount(header);
        int selectedImageIndex = validatedKtx2ImageIndex(request, imageCount);
        byte[][] decodedLevels = new byte[levelCount][];
        int[] mipMapSizes = new int[levelCount];
        long totalSize = 0;
        for (int levelIndex = 0; levelIndex < levelCount; levelIndex++) {
            Ktx2LevelIndex level = container.getLevel(levelIndex);
            int width = Math.max(1, header.getPixelWidth() >>> levelIndex);
            int height = Math.max(1, header.getPixelHeight() >>> levelIndex);
            if (sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE) {
                decodedLevels[levelIndex] = decodeKtx2UastcHdr6x6IntermediateLevel(
                        source,
                        container,
                        level,
                        width,
                        height,
                        imageCount,
                        selectedImageIndex,
                        levelIndex);
            } else {
                decodedLevels[levelIndex] = ktx2BlockImagePayload(
                        source,
                        header,
                        level,
                        width,
                        height,
                        imageCount,
                        selectedImageIndex,
                        astcTarget.getBlockWidth(),
                        astcTarget.getBlockHeight());
            }
            if (outputTarget != astcTarget) {
                decodedLevels[levelIndex] = transcodeHdrAstcLevel(
                        decodedLevels[levelIndex],
                        width,
                        height,
                        astcTarget.getBlockWidth(),
                        astcTarget.getBlockHeight(),
                        outputTarget,
                        request.getDecodeFlags());
            }
            mipMapSizes[levelIndex] = decodedLevels[levelIndex].length;
            totalSize = checkedAdd(totalSize, decodedLevels[levelIndex].length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();
        return new BasisDecodeResult(
                header.getPixelWidth(),
                header.getPixelHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                BasisColorSpace.Linear,
                imageCount,
                levelCount);
    }

    private static byte[] decodeKtx2UastcHdr6x6IntermediateLevel(
            byte[] source,
            Ktx2Container container,
            Ktx2LevelIndex level,
            int width,
            int height,
            int imageCount,
            int selectedImageIndex,
            int levelIndex) {
        Ktx2SupercompressionGlobalData globalData = container.getSupercompressionGlobalData();
        if (globalData == null || globalData.getSliceRanges().length == 0) {
            throw new BasisDecodeException("KTX2 UASTC HDR 6x6 payload is missing slice data");
        }
        int imageLevelIndex = imageLevelIndex(selectedImageIndex, levelIndex, imageCount);
        Ktx2SliceRange[] sliceRanges = globalData.getSliceRanges();
        if (imageLevelIndex >= sliceRanges.length) {
            throw new BasisDecodeException("KTX2 UASTC HDR 6x6 payload has fewer slices than image levels");
        }
        Ktx2SliceRange slice = sliceRanges[imageLevelIndex];
        int sliceOffset = checkedToInt(level.getByteOffset() + slice.getByteOffset());
        int sliceLength = checkedToInt(slice.getByteLength());
        return SixBySixHighDynamicRangeIntermediateTranscoder.transcodeToAstc(
                source,
                sliceOffset,
                sliceLength,
                width,
                height);
    }

    private static BasisDecodeResult decodeBasisEtc1sRgba(
            BasisDecodeRequest request,
            byte[] source,
            BasisContainer container) {
        return decodeBasisEtc1s(request, source, container, selectBasisEtc1sJavaTarget(container, request));
    }

    private static BasisDecodeResult decodeBasisEtc1sEtc1(
            BasisDecodeRequest request,
            byte[] source,
            BasisContainer container) {
        return decodeBasisEtc1s(request, source, container, selectBasisEtc1sJavaTarget(container, request));
    }

    private static BasisDecodeResult decodeBasisEtc1s(
            BasisDecodeRequest request,
            byte[] source,
            BasisContainer container,
            BasisTranscodeTarget outputTarget) {
        boolean blockOutput = isEtc1sBlockTarget(outputTarget);
        Ktx2BasisFileHeader header = container.getHeader();
        boolean hasAlphaSlices = Ktx2BasisHeaderFlag.fromMask(header.getFlags())
                .contains(Ktx2BasisHeaderFlag.cBASISHeaderFlagHasAlphaSlices);

        Etc1sCodebookTables tables = Etc1sCodebookTables.decode(
                source,
                checkedToInt(header.getTablesFileOffset()),
                checkedToInt(header.getTablesFileSize()));
        Etc1sPalettes palettes = Etc1sPalettes.decode(
                checkedToInt(header.getTotalEndpoints()),
                source,
                checkedToInt(header.getEndpointCodebookFileOffset()),
                checkedToInt(header.getEndpointCodebookFileSize()),
                checkedToInt(header.getTotalSelectors()),
                source,
                checkedToInt(header.getSelectorCodebookFileOffset()),
                checkedToInt(header.getSelectorCodebookFileSize()));

        Ktx2BasisSliceDesc[] imageSlices = basisUastcImageSlices(container, request.getImageIndex());
        Ktx2BasisSliceDesc[] alphaSlices = hasAlphaSlices
                ? basisImageAlphaSlices(container, request.getImageIndex(), imageSlices)
                : null;
        if (container.getTextureType() == Ktx2BasisTextureType.cBASISTexTypeVideoFrames) {
            return decodeBasisEtc1sVideo(
                    request,
                    source,
                    container,
                    outputTarget,
                    hasAlphaSlices,
                    tables,
                    palettes,
                    imageSlices.length);
        }
        byte[][] decodedLevels = new byte[imageSlices.length][];
        int[] mipMapSizes = new int[imageSlices.length];
        long totalSize = 0;
        for (int i = 0; i < imageSlices.length; i++) {
            Ktx2BasisSliceDesc slice = imageSlices[i];
            decodedLevels[i] = decodeBasisEtc1sLevel(
                    source,
                    slice,
                    alphaSlices == null ? null : alphaSlices[i],
                    outputTarget,
                    blockOutput,
                    tables,
                    palettes,
                    null);
            mipMapSizes[i] = decodedLevels[i].length;
            totalSize = checkedAdd(totalSize, decodedLevels[i].length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();

        Ktx2BasisSliceDesc levelZero = imageSlices[0];
        return new BasisDecodeResult(
                levelZero.getOriginalWidth(),
                levelZero.getOriginalHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                resolveBasisColorSpace(header, request.isLinearColorSpace()),
                container.getImageCount(),
                imageSlices.length);
    }

    private static BasisDecodeResult decodeBasisUastcLdr(
            BasisDecodeRequest request,
            byte[] source,
            BasisContainer container) {
        BasisTranscodeTarget outputTarget = selectUastcLdrJavaTarget(request);
        Ktx2BasisSliceDesc[] imageSlices = basisUastcImageSlices(container, request.getImageIndex());
        byte[][] decodedLevels = new byte[imageSlices.length][];
        int[] mipMapSizes = new int[imageSlices.length];
        long totalSize = 0;
        for (int i = 0; i < imageSlices.length; i++) {
            Ktx2BasisSliceDesc slice = imageSlices[i];
            byte[] uastcBlocks = basisSlicePayload(source, slice);
            if (outputTarget.isAstcLdr()) {
                decodedLevels[i] = UastcLdrAstcTranscoder.transcodeToAstc(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight());
            } else if (isEtcColorTarget(outputTarget)) {
                decodedLevels[i] = UastcLdrAstcTranscoder.transcodeToEtc(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        outputTarget);
            } else if (isEacTarget(outputTarget)) {
                decodedLevels[i] = UastcLdrAstcTranscoder.transcodeToEac(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        outputTarget);
            } else if (outputTarget == BasisTranscodeTarget.BC1) {
                decodedLevels[i] = UastcLdrAstcTranscoder.transcodeToBc1(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight());
            } else if (outputTarget == BasisTranscodeTarget.BC3) {
                decodedLevels[i] = UastcLdrAstcTranscoder.transcodeToBc3(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight());
            } else if (outputTarget == BasisTranscodeTarget.BC7) {
                decodedLevels[i] = UastcLdrAstcTranscoder.transcodeToBc7(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight());
            } else if (isBc4Bc5Target(outputTarget)) {
                decodedLevels[i] = UastcLdrAstcTranscoder.transcodeToBc4Bc5(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        outputTarget);
            } else {
                byte[] rgbaLevel = UastcLdrAstcTranscoder.transcodeToRgba(
                        uastcBlocks,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight());
                decodedLevels[i] = convertRgbaLevel(
                        rgbaLevel,
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        outputTarget);
            }
            mipMapSizes[i] = decodedLevels[i].length;
            totalSize = checkedAdd(totalSize, decodedLevels[i].length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();

        Ktx2BasisSliceDesc levelZero = imageSlices[0];
        return new BasisDecodeResult(
                levelZero.getOriginalWidth(),
                levelZero.getOriginalHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                resolveBasisColorSpace(container.getHeader(), request.isLinearColorSpace()),
                container.getImageCount(),
                imageSlices.length);
    }

    private static byte[] basisSlicePayload(byte[] source, Ktx2BasisSliceDesc slice) {
        int offset = checkedToInt(slice.getFileOffset());
        int length = checkedToInt(slice.getFileSize());
        byte[] payload = new byte[length];
        System.arraycopy(source, offset, payload, 0, length);
        return payload;
    }

    private static BasisDecodeResult decodeBasisHdrAstc(
            BasisDecodeRequest request,
            byte[] source,
            BasisContainer container) {
        Ktx2BasisTextureFormat sourceFormat = container.getTextureFormat();
        BasisTranscodeTarget outputTarget = selectHdrAstcJavaTarget(sourceFormat, request);
        BasisTranscodeTarget astcTarget = matchingHdrAstcTarget(sourceFormat);
        if (!isHdrAstcJavaTarget(sourceFormat, outputTarget)) {
            throw unsupportedTarget(outputTarget);
        }

        Ktx2BasisSliceDesc[] imageSlices = basisUastcImageSlices(container, request.getImageIndex());
        byte[][] decodedLevels = new byte[imageSlices.length][];
        int[] mipMapSizes = new int[imageSlices.length];
        long totalSize = 0;
        for (int i = 0; i < imageSlices.length; i++) {
            Ktx2BasisSliceDesc slice = imageSlices[i];
            if (sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE) {
                decodedLevels[i] = SixBySixHighDynamicRangeIntermediateTranscoder.transcodeToAstc(
                        source,
                        checkedToInt(slice.getFileOffset()),
                        checkedToInt(slice.getFileSize()),
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight());
            } else {
                decodedLevels[i] = basisSlicePayload(source, slice);
            }
            if (outputTarget != astcTarget) {
                decodedLevels[i] = transcodeHdrAstcLevel(
                        decodedLevels[i],
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        astcTarget.getBlockWidth(),
                        astcTarget.getBlockHeight(),
                        outputTarget,
                        request.getDecodeFlags());
            }
            mipMapSizes[i] = decodedLevels[i].length;
            totalSize = checkedAdd(totalSize, decodedLevels[i].length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();

        Ktx2BasisSliceDesc levelZero = imageSlices[0];
        return new BasisDecodeResult(
                levelZero.getOriginalWidth(),
                levelZero.getOriginalHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                BasisColorSpace.Linear,
                container.getImageCount(),
                imageSlices.length);
    }

    private static BasisDecodeResult decodeBasisEtc1sVideo(
            BasisDecodeRequest request,
            byte[] source,
            BasisContainer container,
            BasisTranscodeTarget outputTarget,
            boolean hasAlphaSlices,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            int levelCount) {
        int selectedImageIndex = request.getImageIndex();
        Etc1sRgbaDecoder.VideoState videoState = new Etc1sRgbaDecoder.VideoState();
        byte[][] decodedLevels = new byte[levelCount][];
        int[] mipMapSizes = new int[levelCount];
        long totalSize = 0;

        for (int levelIndex = 0; levelIndex < levelCount; levelIndex++) {
            byte[] decodedLevel = null;
            for (int imageIndex = 0; imageIndex <= selectedImageIndex; imageIndex++) {
                Ktx2BasisSliceDesc[] frameSlices = basisImageSlices(container, imageIndex);
                if (frameSlices.length != levelCount) {
                    throw new BasisDecodeException("Basis ETC1S video frame mip level count changed");
                }
                Ktx2BasisSliceDesc[] alphaSlices = hasAlphaSlices
                        ? basisImageAlphaSlices(container, imageIndex, frameSlices)
                        : null;
                decodedLevel = decodeBasisEtc1sLevel(
                        source,
                        frameSlices[levelIndex],
                        alphaSlices == null ? null : alphaSlices[levelIndex],
                        outputTarget,
                        isEtc1sBlockTarget(outputTarget),
                        tables,
                        palettes,
                        videoState);
            }
            decodedLevels[levelIndex] = decodedLevel;
            mipMapSizes[levelIndex] = decodedLevel.length;
            totalSize = checkedAdd(totalSize, decodedLevel.length);
        }

        ByteBuffer decoded = request.getAllocator().apply(checkedToInt(totalSize));
        if (decoded == null) {
            throw new BasisDecodeException("Allocator returned null ByteBuffer");
        }
        for (byte[] decodedLevel : decodedLevels) {
            decoded.put(decodedLevel);
        }
        decoded.flip();

        Ktx2BasisSliceDesc levelZero = basisImageSlices(container, selectedImageIndex)[0];
        return new BasisDecodeResult(
                levelZero.getOriginalWidth(),
                levelZero.getOriginalHeight(),
                decoded,
                outputTarget.getImageFormat(),
                mipMapSizes,
                resolveBasisColorSpace(container.getHeader(), request.isLinearColorSpace()),
                container.getImageCount(),
                levelCount);
    }

    private static byte[] decodeBasisEtc1sLevel(
            byte[] source,
            Ktx2BasisSliceDesc slice,
            Ktx2BasisSliceDesc alphaSlice,
            BasisTranscodeTarget outputTarget,
            boolean blockOutput,
            Etc1sCodebookTables tables,
            Etc1sPalettes palettes,
            Etc1sRgbaDecoder.VideoState videoState) {
        int levelIndex = slice.getLevelIndex();
        if (outputTarget == BasisTranscodeTarget.BC7) {
            if (alphaSlice == null) {
                return Etc1sRgbaDecoder.decodeBc7Blocks(
                        source,
                        checkedToInt(slice.getFileOffset()),
                        checkedToInt(slice.getFileSize()),
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        tables,
                        palettes,
                        videoState,
                        levelIndex,
                        false);
            }
            return Etc1sRgbaDecoder.decodeBc7Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    source,
                    checkedToInt(alphaSlice.getFileOffset()),
                    checkedToInt(alphaSlice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex);
        }
        if (outputTarget == BasisTranscodeTarget.BC3) {
            if (alphaSlice == null) {
                return Etc1sRgbaDecoder.decodeBc3Blocks(
                        source,
                        checkedToInt(slice.getFileOffset()),
                        checkedToInt(slice.getFileSize()),
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        tables,
                        palettes,
                        videoState,
                        levelIndex);
            }
            return Etc1sRgbaDecoder.decodeBc3Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    source,
                    checkedToInt(alphaSlice.getFileOffset()),
                    checkedToInt(alphaSlice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex);
        }
        if (outputTarget == BasisTranscodeTarget.BC5) {
            if (alphaSlice == null) {
                return Etc1sRgbaDecoder.decodeBc5Blocks(
                        source,
                        checkedToInt(slice.getFileOffset()),
                        checkedToInt(slice.getFileSize()),
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        tables,
                        palettes,
                        videoState,
                        levelIndex);
            }
            return Etc1sRgbaDecoder.decodeBc5Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    source,
                    checkedToInt(alphaSlice.getFileOffset()),
                    checkedToInt(alphaSlice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex);
        }
        if (outputTarget == BasisTranscodeTarget.BC4) {
            return Etc1sRgbaDecoder.decodeBc4Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex,
                    false);
        }
        if (outputTarget == BasisTranscodeTarget.BC1) {
            return Etc1sRgbaDecoder.decodeBc1Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex,
                    false);
        }
        if (outputTarget == BasisTranscodeTarget.ETC2) {
            if (alphaSlice == null) {
                return Etc1sRgbaDecoder.decodeEtc2RgbaBlocks(
                        source,
                        checkedToInt(slice.getFileOffset()),
                        checkedToInt(slice.getFileSize()),
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        tables,
                        palettes,
                        videoState,
                        levelIndex,
                        false);
            }
            return Etc1sRgbaDecoder.decodeEtc2RgbaBlocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    source,
                    checkedToInt(alphaSlice.getFileOffset()),
                    checkedToInt(alphaSlice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex);
        }
        if (outputTarget == BasisTranscodeTarget.ETC2_EAC_R11) {
            return Etc1sRgbaDecoder.decodeEtc2EacR11Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex,
                    false);
        }
        if (outputTarget == BasisTranscodeTarget.ETC2_EAC_RG11) {
            if (alphaSlice == null) {
                return Etc1sRgbaDecoder.decodeEtc2EacRg11Blocks(
                        source,
                        checkedToInt(slice.getFileOffset()),
                        checkedToInt(slice.getFileSize()),
                        slice.getOriginalWidth(),
                        slice.getOriginalHeight(),
                        tables,
                        palettes,
                        videoState,
                        levelIndex);
            }
            return Etc1sRgbaDecoder.decodeEtc2EacRg11Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    source,
                    checkedToInt(alphaSlice.getFileOffset()),
                    checkedToInt(alphaSlice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex);
        }
        if (blockOutput) {
            return Etc1sRgbaDecoder.decodeEtc1Blocks(
                    source,
                    checkedToInt(slice.getFileOffset()),
                    checkedToInt(slice.getFileSize()),
                    slice.getOriginalWidth(),
                    slice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex,
                    false);
        }

        byte[] decodedLevel = Etc1sRgbaDecoder.decode(
                source,
                checkedToInt(slice.getFileOffset()),
                checkedToInt(slice.getFileSize()),
                slice.getOriginalWidth(),
                slice.getOriginalHeight(),
                tables,
                palettes,
                videoState,
                levelIndex,
                false);
        if (alphaSlice != null) {
            byte[] alphaLevel = Etc1sRgbaDecoder.decode(
                    source,
                    checkedToInt(alphaSlice.getFileOffset()),
                    checkedToInt(alphaSlice.getFileSize()),
                    alphaSlice.getOriginalWidth(),
                    alphaSlice.getOriginalHeight(),
                    tables,
                    palettes,
                    videoState,
                    levelIndex,
                    true);
            applyEtc1sAlphaSlice(decodedLevel, alphaLevel);
        }
        return convertRgbaLevel(decodedLevel, outputTarget);
    }

    private static boolean isRgbaOrPackedTarget(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.RGBA8 || isPackedPixelTarget(target);
    }

    private static boolean isRgbaPackedOrEacTarget(BasisTranscodeTarget target) {
        return isRgbaOrPackedTarget(target) || isEacTarget(target);
    }

    private static boolean isUastcLdrJavaTarget(BasisTranscodeTarget target) {
        return isRgbaPackedOrEacTarget(target)
                || isEtcColorTarget(target)
                || target == BasisTranscodeTarget.BC1
                || target == BasisTranscodeTarget.BC3
                || target == BasisTranscodeTarget.BC7
                || isBc4Bc5Target(target);
    }

    private static boolean isXuastcLdrJavaTarget(BasisTranscodeTarget target) {
        return isRgbaPackedOrEacTarget(target)
                || isEtcColorTarget(target)
                || target == BasisTranscodeTarget.BC1
                || target == BasisTranscodeTarget.BC3
                || isBc4Bc5Target(target);
    }

    private static boolean isXuastcGenericBc7Target(
            Ktx2Container container,
            BasisDecodeRequest request,
            BasisTranscodeTarget target) {
        if (target != BasisTranscodeTarget.BC7) {
            return false;
        }
        if (Ktx2DecodeFlag.isSet(
                request.getDecodeFlags(),
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding)) {
            return isValidatedXuastcGenericBc7TextureFormat(container.getBasisTextureFormat());
        }
        return isValidatedXuastcGenericBc7TextureFormat(container.getBasisTextureFormat())
                && !isFastXuastcBc7TextureFormat(container.getBasisTextureFormat());
    }

    private static boolean isXuastcBc7Target(
            Ktx2Container container,
            BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.BC7
                && isValidatedXuastcGenericBc7TextureFormat(container.getBasisTextureFormat());
    }

    private static boolean isXuastcFastBc7Target(
            Ktx2Container container,
            BasisDecodeRequest request,
            BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.BC7
                && isFastXuastcBc7TextureFormat(container.getBasisTextureFormat())
                && !usesXuastcDeblockFiltering(container.getBasisTextureFormat(), request)
                && !Ktx2DecodeFlag.isSet(
                        request.getDecodeFlags(),
                        Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding)
                && !Ktx2DecodeFlag.isSet(request.getDecodeFlags(), Ktx2DecodeFlag.cDecodeFlagsHighQuality);
    }

    private static boolean isFastXuastcBc7TextureFormat(Ktx2BasisTextureFormat textureFormat) {
        return textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_4x4
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_6x6
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_8x6;
    }

    private static boolean isValidatedXuastcGenericBc7TextureFormat(Ktx2BasisTextureFormat textureFormat) {
        return textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_4x4
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_5x4
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_5x5
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_6x5
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_6x6
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_8x5
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_8x6
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_8x8
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x5
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x6
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x8
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x10
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_12x10
                || textureFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_12x12;
    }

    private static boolean usesXuastcDeblockFiltering(
            Ktx2BasisTextureFormat textureFormat,
            BasisDecodeRequest request) {
        return usesXuastcDeblockFiltering(
                textureFormat.getBlockWidth(),
                textureFormat.getBlockHeight(),
                request);
    }

    private static boolean usesXuastcDeblockFiltering(
            int blockWidth,
            int blockHeight,
            BasisDecodeRequest request) {
        if (Ktx2DecodeFlag.isSet(request.getDecodeFlags(), Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering)) {
            return false;
        }
        return Ktx2DecodeFlag.isSet(
                        request.getDecodeFlags(),
                        Ktx2DecodeFlag.cDecodeFlagsForceDeblockFiltering)
                || blockWidth > 8
                || blockHeight > 6;
    }

    private static boolean usesStrongerXuastcDeblockFiltering(
            int blockWidth,
            int blockHeight,
            BasisDecodeRequest request) {
        return Ktx2DecodeFlag.isSet(
                        request.getDecodeFlags(),
                        Ktx2DecodeFlag.cDecodeFlagsStrongerDeblockFiltering)
                || blockWidth > 8
                || blockHeight > 8;
    }

    private static byte[] xuastcDeblockFilter(
            byte[] source,
            int width,
            int height,
            int blockWidth,
            int blockHeight,
            boolean strongerFiltering) {
        byte[] horizontal = source.clone();
        int skipThreshold = strongerFiltering ? 48 : 24;
        for (int y = 0; y < height; y++) {
            for (int x = blockWidth; x < width; x += blockWidth) {
                int left = pixelOffset(width, x - 1, y);
                int right = pixelOffset(width, x, y);
                if (shouldSkipDeblock(source, left, right, skipThreshold)) {
                    continue;
                }
                int farLeft = pixelOffsetClamped(width, height, x - 2, y);
                int farRight = pixelOffsetClamped(width, height, x + 1, y);
                for (int c = 0; c < 4; c++) {
                    int ll = Byte.toUnsignedInt(source[farLeft + c]);
                    int l = Byte.toUnsignedInt(source[left + c]);
                    int r = Byte.toUnsignedInt(source[right + c]);
                    int rr = Byte.toUnsignedInt(source[farRight + c]);
                    if (strongerFiltering) {
                        horizontal[left + c] = (byte) ((3 * l + 2 * r + ll + 3) / 6);
                        horizontal[right + c] = (byte) ((3 * r + 2 * l + rr + 3) / 6);
                    } else {
                        horizontal[left + c] = (byte) ((5 * l + 2 * r + ll + 4) / 8);
                        horizontal[right + c] = (byte) ((5 * r + 2 * l + rr + 4) / 8);
                    }
                }
            }
        }

        byte[] filtered = horizontal.clone();
        for (int x = 0; x < width; x++) {
            for (int y = blockHeight; y < height; y += blockHeight) {
                int up = pixelOffset(width, x, y - 1);
                int down = pixelOffset(width, x, y);
                if (shouldSkipDeblock(horizontal, up, down, skipThreshold)) {
                    continue;
                }
                int farUp = pixelOffsetClamped(width, height, x, y - 2);
                int farDown = pixelOffsetClamped(width, height, x, y + 1);
                for (int c = 0; c < 4; c++) {
                    int uu = Byte.toUnsignedInt(horizontal[farUp + c]);
                    int u = Byte.toUnsignedInt(horizontal[up + c]);
                    int d = Byte.toUnsignedInt(horizontal[down + c]);
                    int dd = Byte.toUnsignedInt(horizontal[farDown + c]);
                    if (strongerFiltering) {
                        filtered[up + c] = (byte) ((3 * u + 2 * d + uu + 3) / 6);
                        filtered[down + c] = (byte) ((3 * d + 2 * u + dd + 3) / 6);
                    } else {
                        filtered[up + c] = (byte) ((5 * u + 2 * d + uu + 4) / 8);
                        filtered[down + c] = (byte) ((5 * d + 2 * u + dd + 4) / 8);
                    }
                }
            }
        }
        return filtered;
    }

    private static boolean shouldSkipDeblock(
            byte[] rgba,
            int firstOffset,
            int secondOffset,
            int skipThreshold) {
        if (skipThreshold >= 256) {
            return false;
        }
        for (int c = 0; c < 4; c++) {
            int delta = Math.abs(Byte.toUnsignedInt(rgba[firstOffset + c])
                    - Byte.toUnsignedInt(rgba[secondOffset + c]));
            if (delta > skipThreshold) {
                return true;
            }
        }
        return false;
    }

    private static int pixelOffset(int width, int x, int y) {
        return (y * width + x) * 4;
    }

    private static int pixelOffsetClamped(int width, int height, int x, int y) {
        int clampedX = Math.max(0, Math.min(width - 1, x));
        int clampedY = Math.max(0, Math.min(height - 1, y));
        return pixelOffset(width, clampedX, clampedY);
    }

    private static boolean isPackedPixelTarget(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.RGB565
                || target == BasisTranscodeTarget.BGR565
                || target == BasisTranscodeTarget.RGBA4444;
    }

    private static boolean isBc4Bc5Target(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.BC4 || target == BasisTranscodeTarget.BC5;
    }

    private static boolean isEacTarget(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.ETC2_EAC_R11
                || target == BasisTranscodeTarget.ETC2_EAC_RG11;
    }

    private static boolean isEtcColorTarget(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.ETC1
                || target == BasisTranscodeTarget.ETC2
                || target == BasisTranscodeTarget.ETC2_NO_ALPHA;
    }

    private static byte[] convertRgbaLevel(
            byte[] rgbaLevel,
            int width,
            int height,
            BasisTranscodeTarget outputTarget) {
        if (isEacTarget(outputTarget)) {
            return EacLdrBlockPacker.packRgba(rgbaLevel, width, height, outputTarget);
        }
        if (isEtcColorTarget(outputTarget)) {
            return Etc1LdrBlockPacker.packRgba(rgbaLevel, width, height, outputTarget);
        }
        if (isBc4Bc5Target(outputTarget)) {
            return Bc4LdrBlockPacker.packRgba(rgbaLevel, width, height, outputTarget);
        }
        if (outputTarget == BasisTranscodeTarget.BC1) {
            return Bc1LdrBlockPacker.packRgba(rgbaLevel, width, height);
        }
        if (outputTarget == BasisTranscodeTarget.BC3) {
            return Bc3LdrBlockPacker.packRgba(rgbaLevel, width, height);
        }
        if (outputTarget == BasisTranscodeTarget.BC7) {
            return Bc7Mode6RgbBlockPacker.packAutoRgba(rgbaLevel, width, height);
        }
        return convertRgbaLevel(rgbaLevel, outputTarget);
    }

    private static byte[] convertRgbaLevel(byte[] rgbaLevel, BasisTranscodeTarget outputTarget) {
        if (outputTarget == BasisTranscodeTarget.RGBA8) {
            return rgbaLevel;
        }
        if (!isPackedPixelTarget(outputTarget)) {
            throw unsupportedTarget(outputTarget);
        }
        if (rgbaLevel.length % 4 != 0) {
            throw new BasisDecodeException("RGBA level size is not pixel aligned");
        }
        byte[] packed = new byte[rgbaLevel.length / 2];
        for (int in = 0, out = 0; in < rgbaLevel.length; in += 4, out += 2) {
            int r = Byte.toUnsignedInt(rgbaLevel[in]);
            int g = Byte.toUnsignedInt(rgbaLevel[in + 1]);
            int b = Byte.toUnsignedInt(rgbaLevel[in + 2]);
            int a = Byte.toUnsignedInt(rgbaLevel[in + 3]);
            int value;
            if (outputTarget == BasisTranscodeTarget.RGB565) {
                value = (mul8(r, 31) << 11) | (mul8(g, 63) << 5) | mul8(b, 31);
            } else if (outputTarget == BasisTranscodeTarget.BGR565) {
                value = (mul8(b, 31) << 11) | (mul8(g, 63) << 5) | mul8(r, 31);
            } else {
                value = (mul8(r, 15) << 12)
                        | (mul8(g, 15) << 8)
                        | (mul8(b, 15) << 4)
                        | mul8(a, 15);
            }
            packed[out] = (byte) value;
            packed[out + 1] = (byte) (value >>> 8);
        }
        return packed;
    }

    private static int mul8(int value, int quantizedMax) {
        int v = value * quantizedMax + 128;
        return (v + (v >>> 8)) >>> 8;
    }

    private static boolean isEtc1sBlockTarget(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.ETC2
                || target == BasisTranscodeTarget.BC7
                || target == BasisTranscodeTarget.BC3
                || target == BasisTranscodeTarget.BC5
                || target == BasisTranscodeTarget.BC4
                || target == BasisTranscodeTarget.BC1
                || target == BasisTranscodeTarget.ETC1
                || target == BasisTranscodeTarget.ETC2_EAC_R11
                || target == BasisTranscodeTarget.ETC2_EAC_RG11
                || target == BasisTranscodeTarget.ETC2_NO_ALPHA;
    }

    private static Ktx2BasisSliceDesc[] basisImageSlices(BasisContainer container, int imageIndex) {
        if (imageIndex < 0 || imageIndex >= container.getImageCount()) {
            throw new BasisDecodeException("Basis image index out of range: " + imageIndex);
        }
        int maxLevel = -1;
        for (Ktx2BasisSliceDesc slice : container.getSlices()) {
            if (slice.getImageIndex() == imageIndex && !slice.hasAlpha()) {
                maxLevel = Math.max(maxLevel, slice.getLevelIndex());
            }
        }
        if (maxLevel < 0) {
            throw new BasisDecodeException("Basis ETC1S image " + imageIndex + " has no RGB slices");
        }

        Ktx2BasisSliceDesc[] imageSlices = new Ktx2BasisSliceDesc[maxLevel + 1];
        for (Ktx2BasisSliceDesc slice : container.getSlices()) {
            if (slice.getImageIndex() == imageIndex && !slice.hasAlpha()) {
                int level = slice.getLevelIndex();
                if (imageSlices[level] != null) {
                    throw new BasisDecodeException(
                            "Basis ETC1S image " + imageIndex + " has duplicate mip levels");
                }
                imageSlices[level] = slice;
            }
        }
        for (int i = 0; i < imageSlices.length; i++) {
            if (imageSlices[i] == null) {
                throw new BasisDecodeException(
                        "Basis ETC1S image " + imageIndex + " has missing mip level " + i);
            }
        }
        return imageSlices;
    }

    private static Ktx2BasisSliceDesc[] basisUastcImageSlices(BasisContainer container, int imageIndex) {
        try {
            return basisImageSlices(container, imageIndex);
        } catch (BasisDecodeException exception) {
            if (!exception.getMessage().contains("has no RGB slices")) {
                throw exception;
            }
        }

        int maxLevel = -1;
        for (Ktx2BasisSliceDesc slice : container.getSlices()) {
            if (slice.getImageIndex() == imageIndex && slice.hasAlpha()) {
                maxLevel = Math.max(maxLevel, slice.getLevelIndex());
            }
        }
        if (maxLevel < 0) {
            throw new BasisDecodeException("Basis UASTC image " + imageIndex + " has no decodable slices");
        }

        Ktx2BasisSliceDesc[] imageSlices = new Ktx2BasisSliceDesc[maxLevel + 1];
        for (Ktx2BasisSliceDesc slice : container.getSlices()) {
            if (slice.getImageIndex() == imageIndex && slice.hasAlpha()) {
                int level = slice.getLevelIndex();
                if (imageSlices[level] != null) {
                    throw new BasisDecodeException(
                            "Basis UASTC image " + imageIndex + " has duplicate mip levels");
                }
                imageSlices[level] = slice;
            }
        }
        for (int i = 0; i < imageSlices.length; i++) {
            if (imageSlices[i] == null) {
                throw new BasisDecodeException(
                        "Basis UASTC image " + imageIndex + " has missing mip level " + i);
            }
        }
        return imageSlices;
    }

    private static Ktx2BasisSliceDesc[] basisImageAlphaSlices(
            BasisContainer container,
            int imageIndex,
            Ktx2BasisSliceDesc[] rgbSlices) {
        Ktx2BasisSliceDesc[] alphaSlices = new Ktx2BasisSliceDesc[rgbSlices.length];
        for (Ktx2BasisSliceDesc slice : container.getSlices()) {
            if (slice.getImageIndex() == imageIndex && slice.hasAlpha()) {
                int level = slice.getLevelIndex();
                if (level < 0 || level >= alphaSlices.length) {
                    throw new BasisDecodeException("Basis ETC1S alpha slice has invalid mip level");
                }
                if (alphaSlices[level] != null) {
                    throw new BasisDecodeException(
                            "Basis ETC1S image " + imageIndex + " has duplicate alpha mip levels");
                }
                Ktx2BasisSliceDesc rgbSlice = rgbSlices[level];
                if (slice.getOriginalWidth() != rgbSlice.getOriginalWidth()
                        || slice.getOriginalHeight() != rgbSlice.getOriginalHeight()) {
                    throw new BasisDecodeException(
                            "Basis ETC1S alpha slice dimensions do not match RGB slice");
                }
                alphaSlices[level] = slice;
            }
        }
        for (int i = 0; i < alphaSlices.length; i++) {
            if (alphaSlices[i] == null) {
                throw new BasisDecodeException(
                        "Basis ETC1S image " + imageIndex + " has missing alpha mip level " + i);
            }
        }
        return alphaSlices;
    }

    private static Ktx2Etc1sGlobalData requireEtc1sGlobalData(Ktx2Container container) {
        Ktx2SupercompressionGlobalData globalData = container.getSupercompressionGlobalData();
        if (globalData == null || globalData.getEtc1sGlobalData() == null) {
            throw new BasisDecodeException("KTX2 ETC1S payload is missing supercompression global data");
        }
        return globalData.getEtc1sGlobalData();
    }

    private static String unsupportedTextureDetail(byte[] source, Ktx2Container container) {
        if (container.getDataFormatDescriptor() == null
                || !container.getBasisTextureFormat().isXUastcLdr()
                || container.getSupercompressionGlobalData() == null
                || container.getSupercompressionGlobalData().getSliceRanges().length == 0) {
            return "";
        }
        Ktx2SliceRange slice = container.getSupercompressionGlobalData().getSliceRanges()[0];
        XuastcLdrPayload payload = XuastcLdrPayload.parse(
                source,
                checkedToInt(container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                checkedToInt(slice.getByteLength()));
        XuastcLdrImageHeader imageHeader = XuastcLdrImageHeader.parse(
                source,
                checkedToInt(container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                checkedToInt(slice.getByteLength()));
        return " ("
                + payload.getSyntax()
                + " "
                + imageHeader.getWidth()
                + "x"
                + imageHeader.getHeight()
                + " ASTC "
                + imageHeader.getBlockWidth()
                + "x"
                + imageHeader.getBlockHeight()
                + xuastcModeDetail(source, container, slice, payload)
                + ")";
    }

    private static String xuastcModeDetail(
            byte[] source,
            Ktx2Container container,
            Ktx2SliceRange slice,
            XuastcLdrPayload payload) {
        if (payload.getSyntax() != XuastcLdrSyntax.FULL_ARITH) {
            return "";
        }
        XuastcLdrModeStreamSummary summary = XuastcLdrModeStreamReader.summarize(
                source,
                checkedToInt(container.getLevel(0).getByteOffset() + slice.getByteOffset()),
                checkedToInt(slice.getByteLength()));
        if (summary.getFirstUnsupportedMode() == null) {
            return " modeStream=complete";
        }
        return " modeStream="
                + summary.getFirstUnsupportedMode()
                + "@"
                + summary.getFirstUnsupportedBlockX()
                + ","
                + summary.getFirstUnsupportedBlockY();
    }

    private static boolean isKtx2(byte[] source) {
        if (source.length < Ktx2Constants.KTX2_FILE_IDENTIFIER.length) {
            return false;
        }
        for (int i = 0; i < Ktx2Constants.KTX2_FILE_IDENTIFIER.length; i++) {
            if (source[i] != Ktx2Constants.KTX2_FILE_IDENTIFIER[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBasis(byte[] source) {
        if (source.length < Short.BYTES) {
            return false;
        }
        int signature = Byte.toUnsignedInt(source[0]) | (Byte.toUnsignedInt(source[1]) << 8);
        return signature == Ktx2Constants.cBASISSigValue;
    }

    private static BasisImageFormat resolveImageFormat(int vkFormat,
            BasisTranscodeTarget forcedTarget) {
        boolean supportedVk = vkFormat == Ktx2Constants.VK_FORMAT_R8G8B8A8_UNORM
                || vkFormat == Ktx2Constants.VK_FORMAT_R8G8B8A8_SRGB
                || vkFormat == Ktx2Constants.VK_FORMAT_B8G8R8A8_UNORM
                || vkFormat == Ktx2Constants.VK_FORMAT_B8G8R8A8_SRGB;
        if (supportedVk
                && (forcedTarget == null || forcedTarget.getImageFormat() == BasisImageFormat.RGBA8)) {
            return BasisImageFormat.RGBA8;
        }

        return null;
    }

    private static boolean isBasisSupercompressedPayload(Ktx2Container container) {
        return container.getDataFormatDescriptor() != null
                && (container.getBasisTextureFormat() == Ktx2BasisTextureFormat.cETC1S
                || container.getBasisTextureFormat().isXUastcLdr());
    }

    private static BasisDecodeException unsupportedTarget(BasisTranscodeTarget target) {
        return new BasisDecodeException("Unsupported transcode target "
                + target
                + " for the pure Java decoder");
    }

    private static BasisTranscodeTarget selectJavaTarget(BasisDecodeRequest request, boolean alphaRequired) {
        return selectJavaTarget(request, alphaRequired, JAVA_OUTPUT_TARGETS);
    }

    private static BasisTranscodeTarget selectKtx2Etc1sJavaTarget(
            Ktx2Container container,
            BasisDecodeRequest request) {
        return selectJavaTarget(request, ktx2Etc1sAlphaRequired(container, request), ETC1S_OUTPUT_TARGETS);
    }

    private static BasisTranscodeTarget selectBasisEtc1sJavaTarget(
            BasisContainer container,
            BasisDecodeRequest request) {
        return selectJavaTarget(request, basisEtc1sAlphaRequired(container), ETC1S_OUTPUT_TARGETS);
    }

    private static boolean ktx2Etc1sAlphaRequired(Ktx2Container container, BasisDecodeRequest request) {
        Ktx2Etc1sGlobalData globalData = requireEtc1sGlobalData(container);
        Ktx2Etc1sImageDesc[] imageDescriptions = globalData.getImageDescriptors();
        int levelCount = container.getHeader().getLevelCount();
        int imageCount = ktx2ImageCount(container.getHeader());
        int selectedImageIndex = validatedKtx2ImageIndex(request, imageCount);
        if (imageDescriptions.length < Math.multiplyExact(imageCount, levelCount)) {
            throw new BasisDecodeException(
                    "KTX2 ETC1S payload has fewer image descriptors than image levels");
        }
        for (int levelIndex = 0; levelIndex < levelCount; levelIndex++) {
            if (imageDescriptions[imageLevelIndex(selectedImageIndex, levelIndex, imageCount)]
                    .getAlphaSliceByteLength() != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean basisEtc1sAlphaRequired(BasisContainer container) {
        return Ktx2BasisHeaderFlag.fromMask(container.getHeader().getFlags())
                .contains(Ktx2BasisHeaderFlag.cBASISHeaderFlagHasAlphaSlices);
    }

    private static BasisTranscodeTarget selectXuastcOrJavaTarget(
            Ktx2Container container,
            BasisDecodeRequest request) {
        if (container.getDataFormatDescriptor() != null
                && container.getBasisTextureFormat().isXUastcLdr()) {
            return selectXuastcJavaTarget(container, request);
        }
        return selectJavaTarget(request, true);
    }

    private static BasisTranscodeTarget selectXuastcJavaTarget(
            Ktx2Container container,
            BasisDecodeRequest request) {
        BasisTranscodeTarget matchingAstcTarget = astcTargetForXuastc(container);
        boolean includeBc7 = isXuastcFastBc7Target(container, request, BasisTranscodeTarget.BC7)
                || isXuastcBc7Target(container, BasisTranscodeTarget.BC7);
        BasisTranscodeTarget[] targets;
        if (matchingAstcTarget == null) {
            targets = JAVA_OUTPUT_TARGETS;
        } else if (includeBc7) {
            targets = new BasisTranscodeTarget[] {
                matchingAstcTarget,
                BasisTranscodeTarget.RGBA8,
                BasisTranscodeTarget.ETC2,
                BasisTranscodeTarget.ETC1,
                BasisTranscodeTarget.ETC2_NO_ALPHA,
                BasisTranscodeTarget.BC1,
                BasisTranscodeTarget.BC3,
                BasisTranscodeTarget.BC7,
                BasisTranscodeTarget.BC5,
                BasisTranscodeTarget.BC4,
                BasisTranscodeTarget.ETC2_EAC_R11,
                BasisTranscodeTarget.ETC2_EAC_RG11,
                BasisTranscodeTarget.RGBA4444,
                BasisTranscodeTarget.RGB565,
                BasisTranscodeTarget.BGR565
            };
        } else {
            targets = new BasisTranscodeTarget[] {
                matchingAstcTarget,
                BasisTranscodeTarget.RGBA8,
                BasisTranscodeTarget.ETC2,
                BasisTranscodeTarget.ETC1,
                BasisTranscodeTarget.ETC2_NO_ALPHA,
                BasisTranscodeTarget.BC1,
                BasisTranscodeTarget.BC3,
                BasisTranscodeTarget.BC5,
                BasisTranscodeTarget.BC4,
                BasisTranscodeTarget.ETC2_EAC_R11,
                BasisTranscodeTarget.ETC2_EAC_RG11,
                BasisTranscodeTarget.RGBA4444,
                BasisTranscodeTarget.RGB565,
                BasisTranscodeTarget.BGR565
            };
        }
        return selectJavaTarget(request, true, targets);
    }

    private static BasisTranscodeTarget selectUastcLdrJavaTarget(BasisDecodeRequest request) {
        return selectJavaTarget(
                request,
                true,
                new BasisTranscodeTarget[] {
                    BasisTranscodeTarget.ASTC_LDR_4X4,
                    BasisTranscodeTarget.RGBA8,
                    BasisTranscodeTarget.ETC2,
                    BasisTranscodeTarget.ETC1,
                    BasisTranscodeTarget.ETC2_NO_ALPHA,
                    BasisTranscodeTarget.BC1,
                    BasisTranscodeTarget.BC3,
                    BasisTranscodeTarget.BC5,
                    BasisTranscodeTarget.BC4,
                    BasisTranscodeTarget.ETC2_EAC_R11,
                    BasisTranscodeTarget.ETC2_EAC_RG11,
                    BasisTranscodeTarget.RGBA4444,
                    BasisTranscodeTarget.RGB565,
                    BasisTranscodeTarget.BGR565
                });
    }

    private static BasisTranscodeTarget selectHdrAstcJavaTarget(
            Ktx2BasisTextureFormat sourceFormat,
            BasisDecodeRequest request) {
        BasisTranscodeTarget matchingTarget = matchingHdrAstcTarget(sourceFormat);
        if (matchingTarget == null) {
            if (request.getTarget() != null) {
                return request.getTarget();
            }
            if (request.getPreferredFallback() != null) {
                return request.getPreferredFallback();
            }
            if (request.getPlatformCapabilities() != null
                    && request.getPlatformCapabilities().supports(BasisTranscodeTarget.ASTC_HDR_6X6)) {
                return BasisTranscodeTarget.ASTC_HDR_6X6;
            }
            return BasisTranscodeTarget.ASTC_HDR_6X6;
        }
        if (request.getTarget() != null) {
            return request.getTarget();
        }
        if (request.getPlatformCapabilities() != null) {
            return request.getPlatformCapabilities()
                    .selectTarget(new BasisTranscodeTarget[] {matchingTarget}, matchingTarget, false);
        }
        return matchingTarget;
    }

    private static boolean isHdrAstcJavaTarget(
            Ktx2BasisTextureFormat sourceFormat,
            BasisTranscodeTarget target) {
        BasisTranscodeTarget matchingTarget = matchingHdrAstcTarget(sourceFormat);
        return target == matchingTarget
                || isHdrCpuTarget(target)
                || isHdrBc6hTarget(sourceFormat, target);
    }

    private static boolean isHdrCpuTarget(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.RGB_HALF
                || target == BasisTranscodeTarget.RGBA_HALF
                || target == BasisTranscodeTarget.RGB_9E5;
    }

    private static boolean isHdrBc6hTarget(
            Ktx2BasisTextureFormat sourceFormat,
            BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.BC6H
                && (sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_4x4
                || sourceFormat == Ktx2BasisTextureFormat.cASTC_HDR_6x6
                || sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE);
    }

    private static byte[] transcodeHdrAstcLevel(
            byte[] astc,
            int width,
            int height,
            int blockWidth,
            int blockHeight,
            BasisTranscodeTarget outputTarget,
            int decodeFlags) {
        if (outputTarget == BasisTranscodeTarget.BC6H && blockWidth == 4 && blockHeight == 4) {
            return Bc6hUastcHdr4x4Transcoder.transcode(astc, width, height);
        }
        if (outputTarget == BasisTranscodeTarget.BC6H && blockWidth == 6 && blockHeight == 6) {
            boolean highQuality = Ktx2DecodeFlag.isSet(decodeFlags, Ktx2DecodeFlag.cDecodeFlagsHighQuality);
            return Bc6hFastBlockEncoder.transcodeAstc6x6ToBc6h(astc, width, height, highQuality);
        }
        switch (outputTarget) {
            case RGB_HALF:
                return AstcHdrBlockDecoder.decodeToRgbHalf(astc, width, height, blockWidth, blockHeight);
            case RGBA_HALF:
                return AstcHdrBlockDecoder.decodeToRgbaHalf(astc, width, height, blockWidth, blockHeight);
            case RGB_9E5:
                return AstcHdrBlockDecoder.decodeToRgb9e5(astc, width, height, blockWidth, blockHeight);
            default:
                throw unsupportedTarget(outputTarget);
        }
    }

    private static BasisTranscodeTarget matchingHdrAstcTarget(Ktx2BasisTextureFormat sourceFormat) {
        if (sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_4x4) {
            return BasisTranscodeTarget.ASTC_HDR_4X4;
        }
        if (sourceFormat == Ktx2BasisTextureFormat.cASTC_HDR_6x6) {
            return BasisTranscodeTarget.ASTC_HDR_6X6;
        }
        if (sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE) {
            return BasisTranscodeTarget.ASTC_HDR_6X6;
        }
        return null;
    }

    private static BasisTranscodeTarget astcTargetForXuastc(Ktx2Container container) {
        Ktx2BasisTextureFormat format = container.getBasisTextureFormat();
        if (format == null || !format.isXUastcLdr()) {
            return null;
        }
        return BasisTranscodeTarget.astcLdrForBlockSize(
                format.getBlockWidth(),
                format.getBlockHeight());
    }

    private static BasisTranscodeTarget selectJavaTarget(
            BasisDecodeRequest request,
            boolean alphaRequired,
            BasisTranscodeTarget[] implementedTargets) {
        if (request.getTarget() != null) {
            return request.getTarget();
        }
        if (request.getPlatformCapabilities() != null) {
            return request.getPlatformCapabilities()
                    .selectTarget(implementedTargets, request.getPreferredFallback(), alphaRequired);
        }
        return request.getPreferredFallback() == null
                ? BasisTranscodeTarget.RGBA8
                : request.getPreferredFallback();
    }

    private static BasisTranscodeTarget selectExplicitJavaTarget(
            BasisDecodeRequest request,
            boolean alphaRequired) {
        if (request.getTarget() != null) {
            return request.getTarget();
        }
        if (request.getPlatformCapabilities() != null) {
            return request.getPlatformCapabilities()
                    .selectTarget(JAVA_OUTPUT_TARGETS, request.getPreferredFallback(), alphaRequired);
        }
        return null;
    }

    private static BasisColorSpace resolveBasisColorSpace(
            Ktx2BasisFileHeader header,
            boolean linearRequested) {
        BasisColorSpace sourceColor = Ktx2BasisHeaderFlag.fromMask(header.getFlags())
                .contains(Ktx2BasisHeaderFlag.cBASISHeaderFlagSRGB)
                ? BasisColorSpace.sRGB
                : BasisColorSpace.Linear;
        return linearRequested ? sourceColor : BasisColorSpace.sRGB;
    }

    private static BasisColorSpace resolveColorSpace(int vkFormat, boolean linearRequested) {
        BasisColorSpace sourceColor = (vkFormat == Ktx2Constants.VK_FORMAT_R8G8B8A8_SRGB
                || vkFormat == Ktx2Constants.VK_FORMAT_B8G8R8A8_SRGB)
                ? BasisColorSpace.sRGB
                : BasisColorSpace.Linear;
        return linearRequested ? sourceColor : BasisColorSpace.sRGB;
    }

    private static BasisColorSpace resolveKtx2BasisColorSpace(
            Ktx2Container container,
            boolean linearRequested) {
        Ktx2Dfd dfd = container.getDataFormatDescriptor();
        BasisColorSpace sourceColor = dfd != null
                && dfd.getTransferFunction() == Ktx2Constants.KTX2_KHR_DF_TRANSFER_SRGB
                ? BasisColorSpace.sRGB
                : BasisColorSpace.Linear;
        return linearRequested ? sourceColor : BasisColorSpace.sRGB;
    }

    private static void validateMetadataOffsets(Ktx2Header header, int sourceLength) {
        validateMetadataRange("DFD", header.getDfdByteOffset(), header.getDfdByteLength(), sourceLength);
        validateMetadataRange("KVD", header.getKvdByteOffset(), header.getKvdByteLength(), sourceLength);
        validateMetadataRange(
                "SGD",
                header.getSgdByteOffset(),
                header.getSgdByteLength(),
                sourceLength);
    }

    private static void validateMetadataRange(String name, long offset, long length, int sourceLength) {
        if (length == 0) {
            if (offset != 0) {
                throw new BasisDecodeException("KTX2 payload has invalid "
                        + name
                        + " metadata offset for zero-length range: "
                        + offset);
            }
            return;
        }

        if (offset < Ktx2Constants.KTX2_HEADER_SIZE) {
            throw new BasisDecodeException("KTX2 payload has "
                    + name
                    + " metadata offset before header region");
        }

        long end = checkedAdd(offset, length);
        if (end > sourceLength) {
            throw new BasisDecodeException("KTX2 payload has malformed " + name + " metadata range");
        }
    }

    private static int ktx2ImageCount(Ktx2Header header) {
        return Math.multiplyExact(header.getLayerCount(), header.getFaceCount());
    }

    private static int validatedKtx2ImageIndex(BasisDecodeRequest request, int imageCount) {
        int imageIndex = request.getImageIndex();
        if (imageIndex < 0 || imageIndex >= imageCount) {
            throw new BasisDecodeException("KTX2 image index out of range: " + imageIndex);
        }
        return imageIndex;
    }

    private static int imageLevelIndex(int imageIndex, int levelIndex, int imageCount) {
        return Math.addExact(Math.multiplyExact(levelIndex, imageCount), imageIndex);
    }

    private static int checkedToInt(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new BasisDecodeException("KTX2 payload size exceeds supported Java decoding limit");
        }
        return (int) value;
    }

    private static long checkedAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new BasisDecodeException("KTX2 payload offset/length arithmetic overflow");
        }
        return left + right;
    }

}
