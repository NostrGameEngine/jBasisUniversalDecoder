package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;

/**
 * Java parity representation of {@code basist::basis_slice_desc}.
 */
public final class Ktx2BasisSliceDesc {
    public static final int ENCODED_LENGTH_BYTES = 23;

    private final int imageIndex;
    private final int levelIndex;
    private final int flags;
    private final int origWidth;
    private final int origHeight;
    private final int numBlocksX;
    private final int numBlocksY;
    private final long fileOfs;
    private final long fileSize;
    private final int crc16;

    public Ktx2BasisSliceDesc(
            int imageIndex,
            int levelIndex,
            int flags,
            int origWidth,
            int origHeight,
            int numBlocksX,
            int numBlocksY,
            long fileOfs,
            long fileSize,
            int crc16) {
        this.imageIndex = imageIndex;
        this.levelIndex = levelIndex;
        this.flags = flags;
        this.origWidth = origWidth;
        this.origHeight = origHeight;
        this.numBlocksX = numBlocksX;
        this.numBlocksY = numBlocksY;
        this.fileOfs = fileOfs;
        this.fileSize = fileSize;
        this.crc16 = crc16;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public int getLevelIndex() {
        return levelIndex;
    }

    public int getFlags() {
        return flags;
    }

    public int getOriginalWidth() {
        return origWidth;
    }

    public int getOriginalHeight() {
        return origHeight;
    }

    public int getNumBlocksX() {
        return numBlocksX;
    }

    public int getNumBlocksY() {
        return numBlocksY;
    }

    public long getFileOffset() {
        return fileOfs;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getCrc16() {
        return crc16;
    }

    public static Ktx2BasisSliceDesc parse(ByteBuffer buffer, int offset) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative: " + offset);
        }
        int available = buffer.limit() - offset;
        if (available < ENCODED_LENGTH_BYTES) {
            throw new IllegalArgumentException("Slice descriptor requires at least " + ENCODED_LENGTH_BYTES
                    + " bytes at offset " + offset + " but only " + available + " available");
        }

        ByteBuffer readOnly = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        readOnly.position(offset);
        int imageIndex = read24(readOnly);
        int levelIndex = readUInt8(readOnly);
        int flags = readUInt8(readOnly);
        int origWidth = readUInt16(readOnly);
        int origHeight = readUInt16(readOnly);
        int numBlocksX = readUInt16(readOnly);
        int numBlocksY = readUInt16(readOnly);
        long fileOfs = readUInt32(readOnly);
        long fileSize = readUInt32(readOnly);
        int crc16 = readUInt16(readOnly);

        return new Ktx2BasisSliceDesc(imageIndex, levelIndex, flags,
                origWidth, origHeight, numBlocksX, numBlocksY, fileOfs, fileSize, crc16);
    }

    public static int encodedLengthBytes() {
        return ENCODED_LENGTH_BYTES;
    }

    public EnumSet<Ktx2BasisSliceDescFlag> getFlagSet() {
        return Ktx2BasisSliceDescFlag.fromMask(flags);
    }

    public boolean hasAlpha() {
        return getFlagSet().contains(Ktx2BasisSliceDescFlag.cSliceDescFlagsHasAlpha);
    }

    public boolean isIFrame() {
        return getFlagSet().contains(Ktx2BasisSliceDescFlag.cSliceDescFlagsFrameIsIFrame);
    }

    private static int readUInt8(ByteBuffer buffer) {
        return buffer.get() & 0xFF;
    }

    private static int readUInt16(ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    private static int read24(ByteBuffer buffer) {
        return (buffer.get() & 0xFF)
                | ((buffer.get() & 0xFF) << 8)
                | ((buffer.get() & 0xFF) << 16);
    }

    private static long readUInt32(ByteBuffer buffer) {
        return buffer.getInt() & 0xFFFFFFFFL;
    }

    @Override
    public String toString() {
        return "Ktx2BasisSliceDesc{"
                + "imageIndex=" + imageIndex
                + ", levelIndex=" + levelIndex
                + ", flags=" + flags
                + ", origWidth=" + origWidth
                + ", origHeight=" + origHeight
                + ", numBlocksX=" + numBlocksX
                + ", numBlocksY=" + numBlocksY
                + ", fileOfs=" + fileOfs
                + ", fileSize=" + fileSize
                + ", crc16=" + crc16
                + '}';
    }
}
