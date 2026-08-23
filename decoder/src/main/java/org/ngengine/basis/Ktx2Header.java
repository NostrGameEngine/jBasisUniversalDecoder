package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parsed KTX2 header fields used by the Java decoder backend.
 */
public final class Ktx2Header {

    private final int vkFormat;
    private final long typeSize;
    private final int pixelWidth;
    private final int pixelHeight;
    private final long pixelDepth;
    private final long layerCount;
    private final int faceCount;
    private final int levelCount;
    private final Ktx2SupercompressionScheme supercompressionScheme;
    private final long dfdByteOffset;
    private final long dfdByteLength;
    private final long kvdByteOffset;
    private final long kvdByteLength;
    private final long sgdByteOffset;
    private final long sgdByteLength;

    private Ktx2Header(int vkFormat,
                      long typeSize,
                      int pixelWidth,
                      int pixelHeight,
                      long pixelDepth,
                      long layerCount,
                      int faceCount,
                      int levelCount,
                      Ktx2SupercompressionScheme supercompressionScheme,
                      long dfdByteOffset,
                      long dfdByteLength,
                      long kvdByteOffset,
                      long kvdByteLength,
                      long sgdByteOffset,
                      long sgdByteLength) {
        this.vkFormat = vkFormat;
        this.typeSize = typeSize;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.pixelDepth = pixelDepth;
        this.layerCount = layerCount;
        this.faceCount = faceCount;
        this.levelCount = levelCount;
        this.supercompressionScheme = supercompressionScheme;
        this.dfdByteOffset = dfdByteOffset;
        this.dfdByteLength = dfdByteLength;
        this.kvdByteOffset = kvdByteOffset;
        this.kvdByteLength = kvdByteLength;
        this.sgdByteOffset = sgdByteOffset;
        this.sgdByteLength = sgdByteLength;
    }

    public static Ktx2Header parse(byte[] source) {
        if (source == null || source.length < Ktx2Constants.KTX2_HEADER_SIZE) {
            throw new BasisDecodeException("Input is too short for a valid KTX2 container");
        }

        ByteBuffer sourceBuffer = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < Ktx2Constants.KTX2_FILE_IDENTIFIER.length; i++) {
            if (source[i] != Ktx2Constants.KTX2_FILE_IDENTIFIER[i]) {
                throw new BasisDecodeException("Input is not a valid KTX2 container");
            }
        }

        sourceBuffer.position(Ktx2Constants.KTX2_FILE_IDENTIFIER.length);
        int vkFormat = sourceBuffer.getInt();
        long typeSize = UnsignedFields.uint32ToLong(sourceBuffer.getInt());
        int pixelWidth = UnsignedFields.uint32ToInt(sourceBuffer.getInt(), "pixelWidth");
        int pixelHeight = UnsignedFields.uint32ToInt(sourceBuffer.getInt(), "pixelHeight");
        long pixelDepth = UnsignedFields.uint32ToLong(sourceBuffer.getInt());
        long layerCount = UnsignedFields.uint32ToLong(sourceBuffer.getInt());
        int faceCount = UnsignedFields.uint32ToInt(sourceBuffer.getInt(), "faceCount");
        int levelCount = UnsignedFields.uint32ToInt(sourceBuffer.getInt(), "levelCount");
        int supercompression = sourceBuffer.getInt();

        long dfdByteOffset = UnsignedFields.uint32ToLong(sourceBuffer.getInt());
        long dfdByteLength = UnsignedFields.uint32ToLong(sourceBuffer.getInt());
        long kvdByteOffset = UnsignedFields.uint32ToLong(sourceBuffer.getInt());
        long kvdByteLength = UnsignedFields.uint32ToLong(sourceBuffer.getInt());
        long sgdByteOffset = UnsignedFields.uint64ToLong(sourceBuffer.getLong(), "sgdByteOffset");
        long sgdByteLength = UnsignedFields.uint64ToLong(sourceBuffer.getLong(), "sgdByteLength");

        Ktx2SupercompressionScheme supercompressionScheme;
        try {
            supercompressionScheme = Ktx2SupercompressionScheme.fromCode(supercompression);
        } catch (IllegalArgumentException ex) {
            throw new BasisDecodeException(
                    "Unsupported KTX2 supercompression code: " + supercompression, ex);
        }

        Ktx2Header header = new Ktx2Header(
                vkFormat,
                typeSize,
                pixelWidth,
                pixelHeight,
                pixelDepth,
                layerCount,
                faceCount,
                levelCount,
                supercompressionScheme,
                dfdByteOffset,
                dfdByteLength,
                kvdByteOffset,
                kvdByteLength,
                sgdByteOffset,
                sgdByteLength);

        header.validate();
        return header;
    }

    private void validate() {
        if (typeSize != 1) {
            throw new BasisDecodeException("KTX2 payload has unsupported typeSize: " + typeSize);
        }
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new BasisDecodeException("KTX2 payload has invalid size: " + pixelWidth
                    + "x" + pixelHeight);
        }
        if (pixelWidth > Ktx2Constants.BASISU_MAX_SUPPORTED_TEXTURE_DIMENSION
                || pixelHeight > Ktx2Constants.BASISU_MAX_SUPPORTED_TEXTURE_DIMENSION) {
            throw new BasisDecodeException("KTX2 payload exceeds supported dimension limit: "
                    + pixelWidth + "x" + pixelHeight);
        }
        if (layerCount < 0) {
            throw new BasisDecodeException("KTX2 payload has malformed layer count: " + layerCount);
        }
        if (layerCount > Integer.MAX_VALUE) {
            throw new BasisDecodeException(
                    "KTX2 payload layerCount exceeds JVM-supported range: " + layerCount);
        }
        if (pixelDepth != 0) {
            throw new BasisDecodeException("Only 2D KTX2 payloads are supported");
        }
        if (faceCount < 1) {
            throw new BasisDecodeException("KTX2 payload has invalid face count: " + faceCount);
        }
        if (levelCount <= 0) {
            throw new BasisDecodeException("KTX2 payload has no mip levels");
        }
        if (levelCount > Ktx2Constants.KTX2_MAX_SUPPORTED_LEVEL_COUNT) {
            throw new BasisDecodeException("KTX2 payload has too many mip levels: " + levelCount);
        }
        if (dfdByteOffset < 0 || dfdByteLength < 0 || kvdByteOffset < 0 || kvdByteLength < 0
                || sgdByteOffset < 0 || sgdByteLength < 0) {
            throw new BasisDecodeException("KTX2 payload contains malformed metadata offsets/lengths");
        }
    }

    public int getVkFormat() {
        return vkFormat;
    }

    public long getTypeSize() {
        return typeSize;
    }

    public int getPixelWidth() {
        return pixelWidth;
    }

    public int getPixelHeight() {
        return pixelHeight;
    }

    public long getPixelDepth() {
        return pixelDepth;
    }

    /**
     * KTX2 encodes single-layer textures with `layerCount == 0`.
     */
    public int getLayerCount() {
        return layerCount == 0 ? 1 : (int) layerCount;
    }

    public long getRawLayerCount() {
        return layerCount;
    }

    public int getFaceCount() {
        return faceCount;
    }

    public int getLevelCount() {
        return levelCount;
    }

    public Ktx2SupercompressionScheme getSupercompressionScheme() {
        return supercompressionScheme;
    }

    public long getDfdByteOffset() {
        return dfdByteOffset;
    }

    public long getDfdByteLength() {
        return dfdByteLength;
    }

    public long getKvdByteOffset() {
        return kvdByteOffset;
    }

    public long getKvdByteLength() {
        return kvdByteLength;
    }

    public long getSgdByteOffset() {
        return sgdByteOffset;
    }

    public long getSgdByteLength() {
        return sgdByteLength;
    }

}
