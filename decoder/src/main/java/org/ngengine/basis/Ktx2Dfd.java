package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parsed KTX2 Data Format Descriptor fields needed to select a Basis transcoder path.
 */
public final class Ktx2Dfd {
    private final int colorModel;
    private final int colorPrimaries;
    private final int transferFunction;
    private final int flags;
    private final int blockWidth;
    private final int blockHeight;
    private final int channel0;
    private final int channel1;
    private final boolean alphaSamplePresent;

    private Ktx2Dfd(
            int colorModel,
            int colorPrimaries,
            int transferFunction,
            int flags,
            int blockWidth,
            int blockHeight,
            int channel0,
            int channel1,
            boolean alphaSamplePresent) {
        this.colorModel = colorModel;
        this.colorPrimaries = colorPrimaries;
        this.transferFunction = transferFunction;
        this.flags = flags;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.channel0 = channel0;
        this.channel1 = channel1;
        this.alphaSamplePresent = alphaSamplePresent;
    }

    public static Ktx2Dfd parse(byte[] data, Ktx2Header header) {
        if (data == null) {
            throw new BasisDecodeException("KTX2 payload must not be null");
        }
        if (header == null) {
            throw new BasisDecodeException("KTX2 header must not be null");
        }

        int length = UnsignedFields.sizeToInt(header.getDfdByteLength(), "DFD byte length");
        if (length != 44 && length != 60) {
            throw new BasisDecodeException("Unsupported KTX2 DFD length: " + length);
        }

        int offset = UnsignedFields.sizeToInt(header.getDfdByteOffset(), "DFD byte offset");
        validateRange(data.length, offset, length, "DFD");

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        int declaredLength = buffer.getInt();
        if (declaredLength != length) {
            throw new BasisDecodeException("KTX2 DFD declared length does not match header length");
        }

        buffer.position(12);
        int dfdBits = buffer.getInt();
        int transferFunc = (dfdBits >>> 16) & 0xFF;
        if (transferFunc != Ktx2Constants.KTX2_KHR_DF_TRANSFER_LINEAR
                && transferFunc != Ktx2Constants.KTX2_KHR_DF_TRANSFER_SRGB) {
            throw new BasisDecodeException("Unsupported KTX2 DFD transfer function: " + transferFunc);
        }

        buffer.position(16);
        int texelBlockDimensions = buffer.getInt();
        int blockWidth = (texelBlockDimensions & 0xFF) + 1;
        int blockHeight = ((texelBlockDimensions >>> 8) & 0xFF) + 1;

        buffer.position(28);
        int sample0 = buffer.getInt();
        int sample1 = 0;
        if (length >= 60) {
            buffer.position(44);
            sample1 = buffer.getInt();
        }
        return new Ktx2Dfd(
                dfdBits & 0xFF,
                (dfdBits >>> 8) & 0xFF,
                transferFunc,
                (dfdBits >>> 24) & 0xFF,
                blockWidth,
                blockHeight,
                (sample0 >>> 24) & 0x0F,
                (sample1 >>> 24) & 0x0F,
                length == 60);
    }

    public int getColorModel() {
        return colorModel;
    }

    public int getColorPrimaries() {
        return colorPrimaries;
    }

    public int getTransferFunction() {
        return transferFunction;
    }

    public int getFlags() {
        return flags;
    }

    public int getBlockWidth() {
        return blockWidth;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public int getChannel0() {
        return channel0;
    }

    public int getChannel1() {
        return channel1;
    }

    public boolean hasAlphaSample() {
        return alphaSamplePresent;
    }

    private static void validateRange(int dataLength, int offset, int length, String fieldName) {
        if (offset < Ktx2Constants.KTX2_HEADER_SIZE || offset > dataLength || length > dataLength - offset) {
            throw new BasisDecodeException("KTX2 " + fieldName + " range extends beyond input buffer");
        }
    }
}
