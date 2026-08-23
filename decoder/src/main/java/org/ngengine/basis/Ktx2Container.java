package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Parsed KTX2 container structure before texture payload transcoding.
 */
public final class Ktx2Container {
    private final Ktx2Header header;
    private final Ktx2LevelIndex[] levels;
    private final Ktx2Dfd dataFormatDescriptor;
    private final Ktx2KeyValueData keyValueData;
    private final Ktx2SupercompressionGlobalData supercompressionGlobalData;

    private Ktx2Container(
            Ktx2Header header,
            Ktx2LevelIndex[] levels,
            Ktx2Dfd dataFormatDescriptor,
            Ktx2KeyValueData keyValueData,
            Ktx2SupercompressionGlobalData supercompressionGlobalData) {
        this.header = header;
        this.levels = Arrays.copyOf(levels, levels.length);
        this.dataFormatDescriptor = dataFormatDescriptor;
        this.keyValueData = keyValueData;
        this.supercompressionGlobalData = supercompressionGlobalData;
    }

    public static Ktx2Container parse(byte[] data) {
        Ktx2Header header = Ktx2Header.parse(data);
        Ktx2LevelIndex[] levels = parseLevelIndex(data, header);
        Ktx2Dfd dfd = header.getDfdByteLength() == 0 ? null : Ktx2Dfd.parse(data, header);
        Ktx2KeyValueData keyValueData = Ktx2KeyValueData.parse(data, header);
        Ktx2SupercompressionGlobalData globalData = dfd == null
                ? null
                : Ktx2SupercompressionGlobalData.parse(data, header, inferBasisTextureFormat(header, dfd));
        return new Ktx2Container(header, levels, dfd, keyValueData, globalData);
    }

    public Ktx2Header getHeader() {
        return header;
    }

    public Ktx2LevelIndex[] getLevels() {
        return Arrays.copyOf(levels, levels.length);
    }

    public Ktx2LevelIndex getLevel(int index) {
        if (index < 0 || index >= levels.length) {
            throw new IndexOutOfBoundsException("level index out of range: " + index);
        }
        return levels[index];
    }

    public Ktx2Dfd getDataFormatDescriptor() {
        return dataFormatDescriptor;
    }

    public Ktx2KeyValueData getKeyValueData() {
        return keyValueData;
    }

    public Ktx2SupercompressionGlobalData getSupercompressionGlobalData() {
        return supercompressionGlobalData;
    }

    public Ktx2BasisTextureFormat getBasisTextureFormat() {
        if (dataFormatDescriptor == null) {
            throw new BasisDecodeException("KTX2 container has no Data Format Descriptor");
        }
        return inferBasisTextureFormat(header, dataFormatDescriptor);
    }

    private static Ktx2LevelIndex[] parseLevelIndex(byte[] data, Ktx2Header header) {
        int levelCount = header.getLevelCount();
        int tableBytes = Math.multiplyExact(levelCount, 24);
        if (data.length < Ktx2Constants.KTX2_HEADER_SIZE + tableBytes) {
            throw new BasisDecodeException("KTX2 payload truncated level index table");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, Ktx2Constants.KTX2_HEADER_SIZE, tableBytes)
                .order(ByteOrder.LITTLE_ENDIAN);
        Ktx2LevelIndex[] levels = new Ktx2LevelIndex[levelCount];
        for (int i = 0; i < levelCount; i++) {
            long byteOffset = readUInt64(buffer, "level byte offset");
            long byteLength = readUInt64(buffer, "level byte length");
            long uncompressedByteLength = readUInt64(buffer, "level uncompressed byte length");
            validateLevelRange(i, byteOffset, byteLength, data.length);
            levels[i] = new Ktx2LevelIndex(byteOffset, byteLength, uncompressedByteLength);
        }
        validateNoLevelOverlap(levels);
        return levels;
    }

    private static Ktx2BasisTextureFormat inferBasisTextureFormat(Ktx2Header header, Ktx2Dfd dfd) {
        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S) {
            return Ktx2BasisTextureFormat.cETC1S;
        }
        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_LDR_4X4) {
            return Ktx2BasisTextureFormat.cUASTC_LDR_4x4;
        }
        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_HDR_4X4) {
            return Ktx2BasisTextureFormat.cUASTC_HDR_4x4;
        }
        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_HDR_6X6_INTERMEDIATE) {
            return Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE;
        }
        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_XUASTC_LDR_INTERMEDIATE) {
            return Ktx2BasisTextureFormat.xuastcLdrFromBlockSize(dfd.getBlockWidth(), dfd.getBlockHeight());
        }
        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC) {
            return inferAstcFormat(header, dfd);
        }
        throw new BasisDecodeException("Unsupported KTX2 DFD color model: " + dfd.getColorModel());
    }

    private static Ktx2BasisTextureFormat inferAstcFormat(Ktx2Header header, Ktx2Dfd dfd) {
        if (isAstcHdrVkFormat(header.getVkFormat())
                && dfd.getBlockWidth() == 6
                && dfd.getBlockHeight() == 6) {
            return Ktx2BasisTextureFormat.cASTC_HDR_6x6;
        }
        if (isAstcLdrVkFormat(header.getVkFormat())) {
            return Ktx2BasisTextureFormat.astcLdrFromBlockSize(dfd.getBlockWidth(), dfd.getBlockHeight());
        }
        throw new BasisDecodeException("Unsupported ASTC KTX2 basis texture format");
    }

    private static boolean isAstcHdrVkFormat(int vkFormat) {
        return vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_6x6_SFLOAT_BLOCK;
    }

    private static boolean isAstcLdrVkFormat(int vkFormat) {
        return vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_4x4_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_4x4_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_5x4_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_5x4_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_5x5_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_5x5_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_6x5_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_6x5_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_6x6_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_6x6_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_8x5_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_8x5_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_8x6_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_8x6_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_8x8_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_8x8_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x5_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x5_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x6_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x6_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x8_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x8_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x10_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_10x10_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_12x10_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_12x10_SRGB_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_12x12_UNORM_BLOCK
                || vkFormat == Ktx2Constants.KTX2_FORMAT_ASTC_12x12_SRGB_BLOCK;
    }

    private static long readUInt64(ByteBuffer buffer, String fieldName) {
        if (buffer.remaining() < Long.BYTES) {
            throw new BasisDecodeException("KTX2 payload missing " + fieldName);
        }
        long value = buffer.getLong();
        if (value < 0) {
            throw new BasisDecodeException("KTX2 payload has invalid unsigned 64-bit field for " + fieldName);
        }
        return value;
    }

    private static void validateLevelRange(int level, long byteOffset, long byteLength, int dataLength) {
        if (byteOffset < Ktx2Constants.KTX2_HEADER_SIZE) {
            throw new BasisDecodeException("KTX2 level data offset overlaps header for level " + level);
        }
        long end = checkedAdd(byteOffset, byteLength);
        if (end > dataLength) {
            throw new BasisDecodeException("KTX2 level range exceeds input buffer for level " + level);
        }
    }

    private static void validateNoLevelOverlap(Ktx2LevelIndex[] levels) {
        for (int left = 0; left < levels.length; left++) {
            long leftStart = levels[left].getByteOffset();
            long leftEnd = checkedAdd(leftStart, levels[left].getByteLength());
            for (int right = left + 1; right < levels.length; right++) {
                long rightStart = levels[right].getByteOffset();
                long rightEnd = checkedAdd(rightStart, levels[right].getByteLength());
                if (leftStart < rightEnd && rightStart < leftEnd) {
                    throw new BasisDecodeException("KTX2 level ranges overlap");
                }
            }
        }
    }

    private static long checkedAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new BasisDecodeException("KTX2 offset/length arithmetic overflow");
        }
        return left + right;
    }
}
