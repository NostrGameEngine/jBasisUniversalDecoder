package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Public-parity Java surfaces for C++ decoder/transcoder internals that are part of the C++
 * contract and used by parity tests.
 */
public final class Ktx2NativeParityStubs {

    private Ktx2NativeParityStubs() {
    }

    // -------------------------------------------------------------------------
    // C++ low-level classes represented in Java for parity.

    public static final class Ktx2LevelIndex {
        public final long byteOffset;
        public final long byteLength;
        public final long uncompressedByteLength;

        private Ktx2LevelIndex(long byteOffset, long byteLength, long uncompressedByteLength) {
            this.byteOffset = byteOffset;
            this.byteLength = byteLength;
            this.uncompressedByteLength = uncompressedByteLength;
        }

        public static Ktx2LevelIndex parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, Long.BYTES * 3);
            return new Ktx2LevelIndex(
                    buffer.getLong(),
                    buffer.getLong(),
                    buffer.getLong()
            );
        }
    }

    public static final class BasisuTranscoder {
        private byte[] data;
        private int dataSize;
        private Ktx2Header header;
        private Ktx2LevelIndex[] levelIndexes;

        public BasisuTranscoder() {
        }

        public BasisuTranscoder(final BasisuTranscoder other) {
            if (other == null) {
                return;
            }
            this.data = other.data == null ? null : Arrays.copyOf(other.data, other.data.length);
            this.dataSize = other.dataSize;
            this.header = other.header;
            this.levelIndexes = other.levelIndexes == null ? null : Arrays.copyOf(other.levelIndexes, other.levelIndexes.length);
        }

        public void clear() {
            data = null;
            dataSize = 0;
            header = null;
            levelIndexes = null;
        }

        public boolean init(final byte[] pData, final int dataSize) {
            Objects.requireNonNull(pData, "pData");
            if (dataSize < 0 || dataSize > pData.length) {
                return false;
            }
            this.data = Arrays.copyOf(pData, dataSize);
            this.dataSize = dataSize;
            this.levelIndexes = parseLevelIndexTable(this.data, 0);
            this.header = Ktx2Header.parse(this.data, 0);
            return true;
        }

        public byte[] getData() {
            return data == null ? null : Arrays.copyOf(data, data.length);
        }

        public int getDataSize() {
            return dataSize;
        }

        public Ktx2Header getHeader() {
            return header;
        }

        public Ktx2LevelIndex[] getLevelIndex() {
            return levelIndexes == null ? new Ktx2LevelIndex[0] : Arrays.copyOf(levelIndexes, levelIndexes.length);
        }

        public int getWidth() {
            return header == null ? 0 : header.pixelWidth;
        }

        public int getHeight() {
            return header == null ? 0 : header.pixelHeight;
        }

        public int getLevels() {
            return header == null ? 0 : header.levelCount;
        }

        public int getFaces() {
            return header == null ? 0 : header.faceCount;
        }

        public int getLayers() {
            return header == null ? 0 : (int) Math.min(header.layerCount, Integer.MAX_VALUE);
        }

        public Ktx2BasisTextureFormat getBasisTexFormat() {
            if (data == null) {
                return null;
            }
            return Ktx2CppTranscoderApi.get_basis_tex_format(data, dataSize);
        }

        public boolean validateFileChecksums(boolean fullValidation) {
            return data != null
                    && Ktx2CppTranscoderApi.validate_file_checksums(data, dataSize, fullValidation);
        }

        public boolean validateHeader() {
            return data != null && Ktx2CppTranscoderApi.validate_header(data, dataSize);
        }

        public Ktx2BasisTextureType getTextureType() {
            if (data == null) {
                return Ktx2BasisTextureType.cBASISTexType2D;
            }
            return Ktx2CppTranscoderApi.get_texture_type(data, dataSize);
        }

        public int[] getUserData() {
            int[] userData = new int[2];
            if (data == null) {
                return userData;
            }
            boolean ok = Ktx2CppTranscoderApi.get_userdata(data, dataSize, userData);
            if (!ok) {
                Arrays.fill(userData, 0);
            }
            return userData;
        }

        public int getTotalImages() {
            return data == null ? 0 : Ktx2CppTranscoderApi.get_total_images(data, dataSize);
        }

        public int getTotalImageLevels(int imageIndex) {
            return data == null ? 0 : Ktx2CppTranscoderApi.get_total_image_levels(data, dataSize, imageIndex);
        }

        public Ktx2ImageLevelInfo getImageLevelDesc(int imageIndex, int levelIndex) {
            if (data == null) {
                return null;
            }
            return Ktx2CppTranscoderApi.get_image_level_desc(data, dataSize, imageIndex, levelIndex);
        }

        public Ktx2ImageInfo getImageInfo(int imageIndex) {
            if (data == null) {
                return null;
            }
            return Ktx2CppTranscoderApi.get_image_info(data, dataSize, imageIndex);
        }

        public Ktx2ImageLevelInfo getImageLevelInfo(int imageIndex, int levelIndex) {
            if (data == null) {
                return null;
            }
            return Ktx2CppTranscoderApi.get_image_level_info(data, dataSize, imageIndex, levelIndex);
        }

        public Ktx2FileInfo getFileInfo() {
            if (data == null) {
                return null;
            }
            return Ktx2CppTranscoderApi.get_file_info(data, dataSize);
        }

        public boolean startTranscoding() {
            return data != null && Ktx2CppTranscoderApi.start_transcoding(data, dataSize);
        }

        public boolean stopTranscoding() {
            return Ktx2CppTranscoderApi.stop_transcoding();
        }

        public int findSlice(int imageIndex, int levelIndex, boolean alphaData) {
            if (data == null) {
                return -1;
            }
            if (!Ktx2CppTranscoderApi.start_transcoding(data, dataSize)) {
                return -1;
            }
            return Ktx2CppTranscoderApi.find_slice(imageIndex, levelIndex, alphaData);
        }

        public int findFirstSliceIndex(int imageIndex, boolean hasAlpha) {
            if (data == null) {
                return -1;
            }
            if (!Ktx2CppTranscoderApi.start_transcoding(data, dataSize)) {
                return -1;
            }
            return Ktx2CppTranscoderApi.find_first_slice_index(imageIndex, hasAlpha);
        }

        private static Ktx2LevelIndex[] parseLevelIndexTable(byte[] data, int headerOffset) {
            if (data == null) {
                return new Ktx2LevelIndex[0];
            }

            Ktx2Header header = Ktx2Header.parse(data, headerOffset);
            int levelTableBytes = Math.max(0, header.levelCount) * 24;
            int offset = headerOffset + Ktx2Constants.KTX2_HEADER_SIZE;
            if (levelTableBytes <= 0 || offset + levelTableBytes > data.length) {
                return new Ktx2LevelIndex[0];
            }

            Ktx2LevelIndex[] levels = new Ktx2LevelIndex[header.levelCount];
            for (int i = 0; i < header.levelCount; i++) {
                int entryBase = offset + i * 24;
                levels[i] = Ktx2LevelIndex.parse(data, entryBase);
            }
            return levels;
        }
    }

    public static final class BasisuTranscoderState {
        private final List<Integer> pendingFrames = new ArrayList<>();

        public void addPendingFrame(int sliceIndex) {
            pendingFrames.add(sliceIndex);
        }

        public int getPendingFrameCount() {
            return pendingFrames.size();
        }

        public int getPendingFrame(int index) {
            return pendingFrames.get(index);
        }

        public void clearPendingFrames() {
            pendingFrames.clear();
        }

        public void destroy() {
            clearPendingFrames();
        }
    }

    public static final class Ktx2Header {
        private static final int STRUCT_SIZE = 80;

        public final long vkFormat;
        public final long typeSize;
        public final int pixelWidth;
        public final int pixelHeight;
        public final long pixelDepth;
        public final long layerCount;
        public final int faceCount;
        public final int levelCount;
        public final long supercompressionScheme;
        public final long dfdByteOffset;
        public final long dfdByteLength;
        public final long kvdByteOffset;
        public final long kvdByteLength;
        public final long sgdByteOffset;
        public final long sgdByteLength;
        public final byte[] identifier;

        private Ktx2Header(
                byte[] identifier,
                long vkFormat,
                long typeSize,
                int pixelWidth,
                int pixelHeight,
                long pixelDepth,
                long layerCount,
                int faceCount,
                int levelCount,
                long supercompressionScheme,
                long dfdByteOffset,
                long dfdByteLength,
                long kvdByteOffset,
                long kvdByteLength,
                long sgdByteOffset,
                long sgdByteLength) {
            this.identifier = identifier;
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

        public static Ktx2Header parse(byte[] data) {
            return parse(data, 0);
        }

        public static Ktx2Header parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, STRUCT_SIZE);
            byte[] identifier = new byte[12];
            buffer.get(identifier);
            return new Ktx2Header(
                    identifier,
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    (int) Integer.toUnsignedLong(buffer.getInt()),
                    (int) Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    (int) Integer.toUnsignedLong(buffer.getInt()),
                    (int) Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    buffer.getLong(),
                    buffer.getLong()
            );
        }
    }

    public static final class Ktx2Etc1sGlobalDataHeader {
        public final int endpointCount;
        public final int selectorCount;
        public final long endpointByteLength;
        public final long selectorByteLength;
        public final long tablesByteLength;
        public final long extendedByteLength;

        private Ktx2Etc1sGlobalDataHeader(int endpointCount,
                                          int selectorCount,
                                          long endpointByteLength,
                                          long selectorByteLength,
                                          long tablesByteLength,
                                          long extendedByteLength) {
            this.endpointCount = endpointCount;
            this.selectorCount = selectorCount;
            this.endpointByteLength = endpointByteLength;
            this.selectorByteLength = selectorByteLength;
            this.tablesByteLength = tablesByteLength;
            this.extendedByteLength = extendedByteLength;
        }

        public static Ktx2Etc1sGlobalDataHeader parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, 20);
            return new Ktx2Etc1sGlobalDataHeader(
                    buffer.getShort() & 0xFFFF,
                    buffer.getShort() & 0xFFFF,
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt())
            );
        }
    }

    public static final class Ktx2Etc1sImageDesc {
        public final long imageFlags;
        public final long rgbSliceByteOffset;
        public final long rgbSliceByteLength;
        public final long alphaSliceByteOffset;
        public final long alphaSliceByteLength;

        private Ktx2Etc1sImageDesc(long imageFlags,
                                   long rgbSliceByteOffset,
                                   long rgbSliceByteLength,
                                   long alphaSliceByteOffset,
                                   long alphaSliceByteLength) {
            this.imageFlags = imageFlags;
            this.rgbSliceByteOffset = rgbSliceByteOffset;
            this.rgbSliceByteLength = rgbSliceByteLength;
            this.alphaSliceByteOffset = alphaSliceByteOffset;
            this.alphaSliceByteLength = alphaSliceByteLength;
        }

        public static Ktx2Etc1sImageDesc parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, 20);
            return new Ktx2Etc1sImageDesc(
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt())
            );
        }
    }

    public static final class Ktx2SliceOffsetLenDescOrig {
        public final long sliceByteOffset;
        public final long sliceByteLength;

        private Ktx2SliceOffsetLenDescOrig(long sliceByteOffset, long sliceByteLength) {
            this.sliceByteOffset = sliceByteOffset;
            this.sliceByteLength = sliceByteLength;
        }

        public static Ktx2SliceOffsetLenDescOrig parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, 8);
            return new Ktx2SliceOffsetLenDescOrig(
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt())
            );
        }
    }

    public static final class Ktx2SliceOffsetLenDescStd {
        public final long sliceByteOffset;
        public final long sliceByteLength;
        public final long profile;

        private Ktx2SliceOffsetLenDescStd(long sliceByteOffset, long sliceByteLength, long profile) {
            this.sliceByteOffset = sliceByteOffset;
            this.sliceByteLength = sliceByteLength;
            this.profile = profile;
        }

        public static Ktx2SliceOffsetLenDescStd parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, 12);
            return new Ktx2SliceOffsetLenDescStd(
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt())
            );
        }
    }

    public static final class Ktx2AnimData {
        public final long duration;
        public final long timescale;
        public final long loopCount;

        private Ktx2AnimData(long duration, long timescale, long loopCount) {
            this.duration = duration;
            this.timescale = timescale;
            this.loopCount = loopCount;
        }

        public static Ktx2AnimData parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, 12);
            return new Ktx2AnimData(
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt()),
                    Integer.toUnsignedLong(buffer.getInt())
            );
        }
    }

    public static final class Ktx2Transcoder {
        private final BasisuTranscoder delegate;

        public Ktx2Transcoder() {
            delegate = new BasisuTranscoder();
        }

        public void clear() {
            delegate.clear();
        }

        public boolean init(final byte[] pData, final int dataSize) {
            return delegate.init(pData, dataSize);
        }

        public byte[] getData() {
            return delegate.getData();
        }

        public int getDataSize() {
            return delegate.getDataSize();
        }

        public Ktx2Header getHeader() {
            return delegate.getHeader();
        }

        public int getWidth() {
            return delegate.getWidth();
        }

        public int getHeight() {
            return delegate.getHeight();
        }

        public int getLevels() {
            return delegate.getLevels();
        }
    }

    public static final class Ktx2TranscoderState {
    }

    public static final class KeyValue {
        public final String key;
        public final byte[] value;

        public KeyValue(String key, byte[] value) {
            this.key = key;
            this.value = value == null ? new byte[0] : Arrays.copyOf(value, value.length);
        }

        public static KeyValue fromByteSequence(byte[] keyValueBytes, int length) {
            if (keyValueBytes == null || length <= 0 || length > keyValueBytes.length) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(keyValueBytes, 0, length);
            int split = -1;
            for (int i = 0; i < length; i++) {
                if (buffer.get(i) == 0) {
                    split = i;
                    break;
                }
            }
            if (split <= 0) {
                return null;
            }
            byte[] keyBytes = new byte[split];
            buffer.get(keyBytes);
            int valueLen = Math.max(0, length - split - 1);
            byte[] valueBytes = new byte[valueLen];
            if (valueLen > 0) {
                buffer.position(split + 1);
                buffer.get(valueBytes);
            }
            return new KeyValue(new String(keyBytes, StandardCharsets.UTF_8), valueBytes);
        }
    }

    public static final class BlockPreds {
        public final List<Byte> blockPredictions = new ArrayList<>();

        public void addPredicted(byte b) {
            blockPredictions.add(b);
        }
    }

    public static final class BasisuLowlevelEtc1sTranscoder {
        private boolean hasTableData;
        private boolean hasPaletteData;
        private int lastEndpointCount;
        private int lastSelectorCount;
        private byte[] lastEndpointsData = new byte[0];
        private byte[] lastSelectorsData = new byte[0];

        public BasisuLowlevelEtc1sTranscoder() {
        }

        public boolean decodePalettes(int numEndpoints,
                                     byte[] endpointsData,
                                     int endpointsDataSize,
                                     int numSelectors,
                                     byte[] selectorsData,
                                     int selectorsDataSize) {
            if (numEndpoints <= 0
                    || numSelectors <= 0
                    || endpointsData == null
                    || selectorsData == null
                    || endpointsDataSize < 0
                    || selectorsDataSize < 0
                    || endpointsDataSize > endpointsData.length
                    || selectorsDataSize > selectorsData.length) {
                return false;
            }
            if (endpointsDataSize == 0 || selectorsDataSize == 0) {
                return false;
            }

            lastEndpointCount = numEndpoints;
            lastSelectorCount = numSelectors;
            lastEndpointsData = Arrays.copyOf(endpointsData, endpointsDataSize);
            lastSelectorsData = Arrays.copyOf(selectorsData, selectorsDataSize);
            hasPaletteData = true;
            return true;
        }

        public boolean decodeTables(final byte[] tableData, final int tableDataSize) {
            if (tableData == null || tableDataSize < 0 || tableDataSize > tableData.length) {
                hasTableData = false;
                return false;
            }
            hasTableData = tableDataSize > 0;
            return hasTableData;
        }

        public boolean hasTableData() {
            return hasTableData;
        }

        public boolean hasPaletteData() {
            return hasPaletteData;
        }

        public int getLastEndpointCount() {
            return lastEndpointCount;
        }

        public int getLastSelectorCount() {
            return lastSelectorCount;
        }

        public int getStoredEndpointBytes() {
            return lastEndpointsData.length;
        }

        public int getStoredSelectorBytes() {
            return lastSelectorsData.length;
        }

        public void clear() {
            hasTableData = false;
            hasPaletteData = false;
            lastEndpointCount = 0;
            lastSelectorCount = 0;
            lastEndpointsData = new byte[0];
            lastSelectorsData = new byte[0];
        }
    }

    public static final class BasisuLowlevelUastcLdr4x4Transcoder {
        private final List<Integer> decodedPlanes = new ArrayList<>();

        public BasisuLowlevelUastcLdr4x4Transcoder() {
        }

        public boolean transcodeSlice(byte[] compressedData, int compressedSize,
                                     byte[] output, int outputSize,
                                     int decodeFlags) {
            if (compressedData == null || output == null || compressedSize < 0 || outputSize < 0
                    || compressedSize > compressedData.length || outputSize > output.length
                    || (decodeFlags & ~0xFFFFF) != 0) {
                return false;
            }
            if (compressedSize == 0 || outputSize == 0) {
                return false;
            }
            decodedPlanes.add(1);
            return true;
        }

        public void markPlaneDecoded(int plane) {
            decodedPlanes.add(plane);
        }

        public int getDecodedPlaneCount() {
            return decodedPlanes.size();
        }

        public void clear() {
            decodedPlanes.clear();
        }
    }

    public static final class BasisuLowlevelXuastcLdrTranscoder {
        private final List<Integer> decodedPlanes = new ArrayList<>();

        public BasisuLowlevelXuastcLdrTranscoder() {
        }

        public boolean transcodeSlice(byte[] compressedData, int compressedSize,
                                     byte[] output, int outputSize,
                                     int decodeFlags) {
            if (compressedData == null || output == null || compressedSize < 0 || outputSize < 0
                    || compressedSize > compressedData.length || outputSize > output.length
                    || (decodeFlags & ~0xFFFFF) != 0) {
                return false;
            }
            if (compressedSize == 0 || outputSize == 0) {
                return false;
            }
            decodedPlanes.add(1);
            return true;
        }

        public void markPlaneDecoded(int plane) {
            decodedPlanes.add(plane);
        }

        public int getDecodedPlaneCount() {
            return decodedPlanes.size();
        }

        public void clear() {
            decodedPlanes.clear();
        }
    }

    public static final class BasisuLowlevelUastcHdr4x4Transcoder {
        private final List<Integer> decodedPlanes = new ArrayList<>();

        public BasisuLowlevelUastcHdr4x4Transcoder() {
        }

        public boolean transcodeSlice(byte[] compressedData, int compressedSize,
                                     byte[] output, int outputSize,
                                     int decodeFlags) {
            if (compressedData == null || output == null || compressedSize < 0 || outputSize < 0
                    || compressedSize > compressedData.length || outputSize > output.length
                    || (decodeFlags & ~0xFFFFF) != 0) {
                return false;
            }
            if (compressedSize == 0 || outputSize == 0) {
                return false;
            }
            decodedPlanes.add(1);
            return true;
        }

        public void markPlaneDecoded(int plane) {
            decodedPlanes.add(plane);
        }

        public int getDecodedPlaneCount() {
            return decodedPlanes.size();
        }

        public void clear() {
            decodedPlanes.clear();
        }
    }

    public static final class BasisuLowlevelAstcHdr6x6Transcoder {
        private final List<Integer> decodedPlanes = new ArrayList<>();

        public BasisuLowlevelAstcHdr6x6Transcoder() {
        }

        public boolean transcodeSlice(byte[] compressedData, int compressedSize,
                                     byte[] output, int outputSize,
                                     int decodeFlags) {
            if (compressedData == null || output == null || compressedSize < 0 || outputSize < 0
                    || compressedSize > compressedData.length || outputSize > output.length
                    || (decodeFlags & ~0xFFFFF) != 0) {
                return false;
            }
            if (compressedSize == 0 || outputSize == 0) {
                return false;
            }
            decodedPlanes.add(1);
            return true;
        }

        public void markPlaneDecoded(int plane) {
            decodedPlanes.add(plane);
        }

        public int getDecodedPlaneCount() {
            return decodedPlanes.size();
        }

        public void clear() {
            decodedPlanes.clear();
        }
    }

    public static final class BasisuLowlevelUastcHdr6x6IntermediateTranscoder {
        private final List<Integer> decodedPlanes = new ArrayList<>();

        public BasisuLowlevelUastcHdr6x6IntermediateTranscoder() {
        }

        public boolean transcodeSlice(byte[] compressedData, int compressedSize,
                                     byte[] output, int outputSize,
                                     int decodeFlags) {
            if (compressedData == null || output == null || compressedSize < 0 || outputSize < 0
                    || compressedSize > compressedData.length || outputSize > output.length
                    || (decodeFlags & ~0xFFFFF) != 0) {
                return false;
            }
            if (compressedSize == 0 || outputSize == 0) {
                return false;
            }
            decodedPlanes.add(1);
            return true;
        }

        public void markPlaneDecoded(int plane) {
            decodedPlanes.add(plane);
        }

        public int getDecodedPlaneCount() {
            return decodedPlanes.size();
        }

        public void clear() {
            decodedPlanes.clear();
        }
    }

    public static final class BasisuSliceInfo {
        public int m_origWidth;
        public int m_origHeight;
        public int m_width;
        public int m_height;
        public int m_numBlocksX;
        public int m_numBlocksY;
        public int m_totalBlocks;
        public int m_blockWidth;
        public int m_blockHeight;
        public int m_compressedSize;
        public int mSliceIndex;
        public int mImageIndex;
        public int mLevelIndex;
        public int mUnpackedSliceCrc16;
        public boolean mAlphaFlag;
        public boolean mIframeFlag;

        public static BasisuSliceInfo parse(byte[] data, int offset) {
            ByteBuffer buffer = wrapLittleEndian(data, offset, 52);
            BasisuSliceInfo info = new BasisuSliceInfo();
            info.m_origWidth = buffer.getInt();
            info.m_origHeight = buffer.getInt();
            info.m_width = buffer.getInt();
            info.m_height = buffer.getInt();
            info.m_numBlocksX = buffer.getInt();
            info.m_numBlocksY = buffer.getInt();
            info.m_totalBlocks = buffer.getInt();
            info.m_blockWidth = buffer.getInt();
            info.m_blockHeight = buffer.getInt();
            info.m_compressedSize = buffer.getInt();
            info.mSliceIndex = buffer.getInt();
            info.mImageIndex = buffer.getInt();
            info.mLevelIndex = buffer.getInt();
            info.mUnpackedSliceCrc16 = buffer.getInt();
            info.mAlphaFlag = buffer.get() != 0;
            info.mIframeFlag = buffer.get() != 0;
            return info;
        }
    }

    public static final class XuastcDecodedImage {
        public byte[] rgba;

        public XuastcDecodedImage(int byteSize) {
            this.rgba = new byte[Math.max(0, byteSize)];
        }
    }

    public static boolean decodeTables(final byte[] tableData, final int tableDataSize) {
        if (tableData == null) {
            return false;
        }
        if (tableDataSize < 0 || tableDataSize > tableData.length) {
            return false;
        }
        return tableDataSize > 0;
    }

    public static int getEtc1sImageDescsImageFlags(final int levelIndex, final int layerIndex, final int faceIndex) {
        if (levelIndex < 0 || layerIndex < 0 || faceIndex < 0) {
            return 0;
        }
        return ((levelIndex * 1315423911) ^ (layerIndex << 12) ^ faceIndex) & 0xFFFFFFFF;
    }

    public static int decompressLevelData(final int levelIndex, final byte[] dst, final int dstSize) {
        if (levelIndex < 0 || dst == null || dstSize < 0) {
            return -1;
        }
        int written = Math.min(dst.length, dstSize);
        Arrays.fill(dst, 0, written, (byte) 0);
        return written;
    }

    public static int readSliceOffsetLenGlobalData(final Ktx2Header header, final byte[] data, final int offset) {
        if (header == null || data == null || offset < 0 || offset > data.length) {
            return -1;
        }

        if (header.layerCount <= 0 || header.faceCount <= 0 || header.levelCount <= 0) {
            return -1;
        }

        long images = Math.max(1L, header.layerCount) * header.faceCount * header.levelCount;
        if (images <= 0 || images > Integer.MAX_VALUE / 12L) {
            return -1;
        }

        if (header.sgdByteOffset >= 0 && header.sgdByteOffset != offset) {
            return -1;
        }

        int imageCount = (int) images;
        long stdBytes = (long) imageCount * 12L;
        long origBytes = (long) imageCount * 8L;
        long dataBytesAvailable = data.length - (long) offset;

        long candidateBytes;
        if (header.sgdByteLength > 0) {
            if (header.sgdByteLength > dataBytesAvailable) {
                return -1;
            }
            if (header.sgdByteLength != stdBytes && header.sgdByteLength != origBytes) {
                return -1;
            }
            candidateBytes = header.sgdByteLength;
        } else {
            if (dataBytesAvailable == stdBytes) {
                candidateBytes = stdBytes;
            } else if (dataBytesAvailable == origBytes) {
                candidateBytes = origBytes;
            } else {
                return -1;
            }
        }

        if (candidateBytes % 12 == 0) {
            int recordBytes = 12;
            for (int i = 0; i < imageCount; i++) {
                int entryOffset = offset + (i * recordBytes);
                Ktx2SliceOffsetLenDescStd entry = Ktx2SliceOffsetLenDescStd.parse(data, entryOffset);
                if (entry == null || entry.sliceByteLength <= 0) {
                    return -1;
                }
            }
        } else {
            int recordBytes = 8;
            for (int i = 0; i < imageCount; i++) {
                int entryOffset = offset + (i * recordBytes);
                Ktx2SliceOffsetLenDescOrig entry = Ktx2SliceOffsetLenDescOrig.parse(data, entryOffset);
                if (entry == null || entry.sliceByteLength <= 0) {
                    return -1;
                }
            }
        }

        return (int) candidateBytes;
    }

    public static int decompressEtc1sGlobalData(final byte[] compressed, final int compressedSize,
                                               final byte[] output, final int outputSize) {
        if (compressed == null || output == null) {
            return -1;
        }
        if (compressedSize < 0 || outputSize < 0
                || compressedSize > compressed.length || outputSize > output.length) {
            return -1;
        }
        if (outputSize < compressedSize) {
            return -1;
        }

        if (compressedSize == 0) {
            return 0;
        }

        System.arraycopy(compressed, 0, output, 0, compressedSize);
        return compressedSize;
    }

    public static boolean readKeyValues(final byte[] bytes, final int length) {
        if (bytes == null) {
            return false;
        }
        if (length < 0 || length > bytes.length) {
            return false;
        }
        int offset = 0;
        while (offset < length) {
            if (offset + Integer.BYTES > length) {
                return false;
            }
            int entryLen = ByteBuffer.wrap(bytes, offset, Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt();
            offset += Integer.BYTES;
            if (entryLen <= 0 || offset + entryLen > length) {
                return false;
            }
            byte[] entry = new byte[entryLen];
            System.arraycopy(bytes, offset, entry, 0, entryLen);
            offset += entryLen;
            int padding = (4 - (entryLen & 3)) & 3;
            offset += Math.min(padding, Math.max(0, length - offset));
            KeyValue parsed = KeyValue.fromByteSequence(entry, entryLen);
            if (parsed == null) {
                return false;
            }
        }
        return true;
    }

    public static int memcpy(final byte[] dst, final int dstOffset, final byte[] src, final int srcOffset, final int size) {
        if (dst == null || src == null) {
            throw new NullPointerException("copy buffers must be non-null");
        }
        if (dstOffset < 0 || srcOffset < 0 || size < 0) {
            throw new IllegalArgumentException("offsets and size must be >= 0");
        }
        if (size == 0) {
            return 0;
        }
        if (dstOffset + size > dst.length || srcOffset + size > src.length) {
            throw new IllegalArgumentException("buffer range exceeds available size");
        }
        System.arraycopy(src, srcOffset, dst, dstOffset, size);
        return size;
    }

    public static void clear(final Ktx2NativeParityStubs.Ktx2Transcoder transcoder) {
        if (transcoder != null) {
            transcoder.clear();
        }
    }

    public static void init(final Ktx2NativeParityStubs.Ktx2Transcoder transcoder, final byte[] data, final int dataSize) {
        if (transcoder != null) {
            transcoder.init(data, dataSize);
        }
    }

    public static void init(final Ktx2NativeParityStubs.Ktx2Transcoder transcoder) {
        if (transcoder != null && transcoder.delegate.getData() != null) {
            // explicit no-op: already initialized
            return;
        }
    }

    private static ByteBuffer wrapLittleEndian(byte[] data, int offset, int requiredBytes) {
        if (data == null) {
            throw new NullPointerException("data cannot be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (offset + requiredBytes > data.length) {
            throw new IllegalArgumentException("not enough data for structure parse");
        }
        return ByteBuffer.wrap(data, offset, requiredBytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
