package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Java-side parity facade for a subset of C++ transcoder C API symbols.
 */
public final class Ktx2CppTranscoderApi {
    private static final Object TRANSCRIBER_STATE_LOCK = new Object();
    private static volatile boolean transcoderReady;
    private static final int TRANSCRIPTOR_API_VERSION = 210;
    private static volatile boolean debugPrintfEnabled;
    private static volatile int debugFlags;
    private static final AtomicLong ALLOC_SEQUENCE = new AtomicLong(1);
    private static final Map<Long, byte[]> ALLOCATIONS = new ConcurrentHashMap<>();
    private static final Map<Long, byte[]> WASM_HANDLE_STATE = new ConcurrentHashMap<>();
    private static final Set<Long> WASM_ACTIVE_STATES = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Ktx2TranscoderState> WASM_TRANSCODE_STATES = new ConcurrentHashMap<>();
    private static final AtomicLong WASM_HANDLE_SEQUENCE = new AtomicLong(1_000_000L);
    private static final AtomicLong TRANSCODE_STATE_SEQUENCE = new AtomicLong(2_000_000L);
    private static volatile byte[] activeTranscodingPayload;
    private static volatile Ktx2LevelInfo activeTranscodingInfo;
    private static final Set<Ktx2TranscoderTextureFormat> UASTC_HDR_SUPPORT = Set.of(
            Ktx2TranscoderTextureFormat.cTFASTC_HDR_6x6_RGBA,
            Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA,
            Ktx2TranscoderTextureFormat.cTFBC6H,
            Ktx2TranscoderTextureFormat.cTFRGBA_HALF,
            Ktx2TranscoderTextureFormat.cTFRGB_HALF,
            Ktx2TranscoderTextureFormat.cTFRGB_9E5
    );

    private static final class Ktx2TranscoderState {
        private boolean closed;

        public void destroy() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    private Ktx2CppTranscoderApi() {
    }

    /**
     * Java-managed allocation handle that mirrors C++ pointer-returning allocators.
     */
    public static final class ScopedAllocation implements AutoCloseable {
        private final long handle;
        private final int size;
        private final AtomicBoolean closed;

        private ScopedAllocation(int size) {
            this.handle = basis_alloc(size);
            this.size = size;
            this.closed = new AtomicBoolean(false);
        }

        public static ScopedAllocation allocate(int size) {
            return new ScopedAllocation(size);
        }

        public long handle() {
            return handle;
        }

        public int size() {
            return size;
        }

        public byte[] bytes() {
            if (closed.get()) {
                throw new BasisDecodeException("Allocation already closed: " + handle);
            }
            byte[] payload = ALLOCATIONS.get(handle);
            if (payload == null) {
                throw new BasisDecodeException("Allocation was released or not initialized: " + handle);
            }
            return payload;
        }

        public java.nio.ByteBuffer asReadOnlyBuffer() {
            return java.nio.ByteBuffer.wrap(bytes()).asReadOnlyBuffer();
        }

        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                basis_free(handle);
            }
        }
    }


    public static int basis_get_version() {
        return TRANSCRIPTOR_API_VERSION;
    }

    public static void basis_enable_debug_printf(int flag) {
        debugPrintfEnabled = flag != 0;
    }

    public static boolean basis_get_debug_printf_enabled() {
        return debugPrintfEnabled;
    }

    public static long basis_alloc(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("size exceeds JVM byte[] limit: " + size);
        }
        long handle = ALLOC_SEQUENCE.getAndIncrement();
        ALLOCATIONS.put(handle, new byte[(int) size]);
        return handle;
    }

    public static void basis_free(long handle) {
        ALLOCATIONS.remove(handle);
    }

    public static void basis_init() {
        // No native init required for Java parity facade.
    }

    public static ScopedAllocation allocateScoped(int size) {
        return ScopedAllocation.allocate(size);
    }

    public static int basis_get_bytes_per_block_or_pixel(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.getBytesPerBlockOrPixel();
    }

    public static int basis_get_uncompressed_bytes_per_pixel(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.getUncompressedBytesPerPixel();
    }

    public static String basis_get_format_name(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.toNativeName();
    }

    public static String basis_get_tex_format_name(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.toNativeName();
    }

    public static String basis_get_block_format_name(Ktx2TextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.toNativeName();
    }

    public static String basis_get_texture_type_name(Ktx2BasisTextureType textureType) {
        if (textureType == null) {
            throw new BasisDecodeException("textureType must not be null");
        }
        return textureType.name();
    }

    public static boolean basisu_transcoder_supports_ktx2() {
        return true;
    }

    public static boolean basisu_transcoder_supports_ktx2_zstd() {
        return true;
    }

    public static boolean basis_transcoder_format_has_alpha(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.isHasAlpha();
    }

    public static boolean basis_transcoder_format_is_hdr(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.isHdr();
    }

    public static boolean basis_transcoder_format_is_ldr(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.isLdr();
    }

    public static boolean basis_is_transcoder_texture_format_astc(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        switch (format) {
            case cTFASTC_LDR_4x4_RGBA:
            case cTFASTC_LDR_5x4_RGBA:
            case cTFASTC_LDR_5x5_RGBA:
            case cTFASTC_LDR_6x5_RGBA:
            case cTFASTC_LDR_6x6_RGBA:
            case cTFASTC_LDR_8x5_RGBA:
            case cTFASTC_LDR_8x6_RGBA:
            case cTFASTC_LDR_10x5_RGBA:
            case cTFASTC_LDR_10x6_RGBA:
            case cTFASTC_LDR_8x8_RGBA:
            case cTFASTC_LDR_10x8_RGBA:
            case cTFASTC_LDR_10x10_RGBA:
            case cTFASTC_LDR_12x10_RGBA:
            case cTFASTC_LDR_12x12_RGBA:
                return true;
            default:
                return false;
        }
    }

    public static Ktx2TranscoderTextureFormat basis_get_transcoder_texture_format_from_basis_tex_format(
            Ktx2BasisTextureFormat format) {
        return basis_get_transcoder_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(format);
    }

    public static Ktx2TextureFormat basis_get_basisu_texture_format(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        switch (format) {
            case cTFETC1_RGB:
                return Ktx2TextureFormat.cETC1;
            case cTFBC1_RGB:
                return Ktx2TextureFormat.cBC1;
            case cTFETC2_RGBA:
                return Ktx2TextureFormat.cETC2_RGBA;
            case cTFBC3_RGBA:
                return Ktx2TextureFormat.cBC3;
            case cTFBC4_R:
                return Ktx2TextureFormat.cBC4;
            case cTFBC5_RG:
                return Ktx2TextureFormat.cBC5;
            case cTFBC7_RGBA:
            case cTFBC7_ALT:
                return Ktx2TextureFormat.cBC7;
            case cTFPVRTC1_4_RGB:
                return Ktx2TextureFormat.cPVRTC1_4_RGB;
            case cTFPVRTC1_4_RGBA:
                return Ktx2TextureFormat.cPVRTC1_4_RGBA;
            case cTFPVRTC2_4_RGB:
            case cTFPVRTC2_4_RGBA:
                return Ktx2TextureFormat.cPVRTC2_4_RGBA;
            case cTFATC_RGB:
                return Ktx2TextureFormat.cATC_RGB;
            case cTFATC_RGBA:
                return Ktx2TextureFormat.cATC_RGBA_INTERPOLATED_ALPHA;
            case cTFFXT1_RGB:
                return Ktx2TextureFormat.cFXT1_RGB;
            case cTFETC2_EAC_R11:
                return Ktx2TextureFormat.cETC2_R11_EAC;
            case cTFETC2_EAC_RG11:
                return Ktx2TextureFormat.cETC2_RG11_EAC;
            case cTFBC6H:
                return Ktx2TextureFormat.cBC6HUnsigned;
            case cTFASTC_HDR_4x4_RGBA:
                return Ktx2TextureFormat.cASTC_HDR_4x4;
            case cTFASTC_HDR_6x6_RGBA:
                return Ktx2TextureFormat.cASTC_HDR_6x6;
            case cTFRGB_HALF:
                return Ktx2TextureFormat.cRGB_HALF;
            case cTFRGBA_HALF:
                return Ktx2TextureFormat.cRGBA_HALF;
            case cTFRGB_9E5:
                return Ktx2TextureFormat.cRGB_9E5;
            case cTFRGB565:
                return Ktx2TextureFormat.cRGB565;
            case cTFBGR565:
                return Ktx2TextureFormat.cBGR565;
            case cTFRGBA4444:
                return Ktx2TextureFormat.cRGBA4444;
            case cTFRGBA32:
                return Ktx2TextureFormat.cRGBA32;
            case cTFASTC_LDR_4x4_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_4x4;
            case cTFASTC_LDR_5x4_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_5x4;
            case cTFASTC_LDR_5x5_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_5x5;
            case cTFASTC_LDR_6x5_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_6x5;
            case cTFASTC_LDR_6x6_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_6x6;
            case cTFASTC_LDR_8x5_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_8x5;
            case cTFASTC_LDR_8x6_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_8x6;
            case cTFASTC_LDR_10x5_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_10x5;
            case cTFASTC_LDR_10x6_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_10x6;
            case cTFASTC_LDR_8x8_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_8x8;
            case cTFASTC_LDR_10x8_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_10x8;
            case cTFASTC_LDR_10x10_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_10x10;
            case cTFASTC_LDR_12x10_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_12x10;
            case cTFASTC_LDR_12x12_RGBA:
                return Ktx2TextureFormat.cASTC_LDR_12x12;
            default:
                return Ktx2TextureFormat.cInvalidTextureFormat;
        }
    }

    public static Ktx2TextureFormat basis_get_basis_texture_format(Ktx2TranscoderTextureFormat format) {
        return basis_get_basisu_texture_format(format);
    }

    public static boolean basis_transcoder_format_is_uncompressed(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.isUncompressed();
    }

    public static int basis_get_block_width(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.getBlockWidth();
    }

    public static int basis_get_block_height(Ktx2TranscoderTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("format must not be null");
        }
        return format.getBlockHeight();
    }

    public static Ktx2TranscoderTextureFormat basis_get_transcoder_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(
            Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }

        if (format == Ktx2BasisTextureFormat.cASTC_LDR_4x4 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_4x4) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_5x4 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_5x4) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x4_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_5x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_5x5) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x5_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_6x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_6x5) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_6x5_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_6x6 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_6x6) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_6x6_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_8x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_8x5) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_8x5_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_8x6 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_8x6) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_8x6_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x5) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x5_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x6 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x6) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x6_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_8x8 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_8x8) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_8x8_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x8 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x8) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x8_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x10 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x10) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x10_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_12x10 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_12x10) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_12x10_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_12x12 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_12x12) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_12x12_RGBA;
        }

        if (format == Ktx2BasisTextureFormat.cETC1S || format == Ktx2BasisTextureFormat.cUASTC_LDR_4x4) {
            return Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cUASTC_HDR_4x4) {
            return Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_HDR_6x6 || format == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE) {
            return Ktx2TranscoderTextureFormat.cTFASTC_HDR_6x6_RGBA;
        }

        throw new BasisDecodeException("Unsupported basis_tex_format: " + format);
    }

    public static Ktx2TextureFormat basis_get_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_4x4 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_4x4) {
            return Ktx2TextureFormat.cASTC_LDR_4x4;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_5x4 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_5x4) {
            return Ktx2TextureFormat.cASTC_LDR_5x4;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_5x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_5x5) {
            return Ktx2TextureFormat.cASTC_LDR_5x5;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_6x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_6x5) {
            return Ktx2TextureFormat.cASTC_LDR_6x5;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_6x6 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_6x6) {
            return Ktx2TextureFormat.cASTC_LDR_6x6;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_8x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_8x5) {
            return Ktx2TextureFormat.cASTC_LDR_8x5;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_8x6 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_8x6) {
            return Ktx2TextureFormat.cASTC_LDR_8x6;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x5 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x5) {
            return Ktx2TextureFormat.cASTC_LDR_10x5;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x6 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x6) {
            return Ktx2TextureFormat.cASTC_LDR_10x6;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_8x8 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_8x8) {
            return Ktx2TextureFormat.cASTC_LDR_8x8;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x8 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x8) {
            return Ktx2TextureFormat.cASTC_LDR_10x8;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_10x10 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_10x10) {
            return Ktx2TextureFormat.cASTC_LDR_10x10;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_12x10 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_12x10) {
            return Ktx2TextureFormat.cASTC_LDR_12x10;
        }
        if (format == Ktx2BasisTextureFormat.cASTC_LDR_12x12 || format == Ktx2BasisTextureFormat.cXUASTC_LDR_12x12) {
            return Ktx2TextureFormat.cASTC_LDR_12x12;
        }
        return Ktx2TextureFormat.cInvalidTextureFormat;
    }

    public static boolean basis_tex_format_is_xuastc_ldr(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }
        return format.isXUastcLdr();
    }

    public static boolean basis_tex_format_is_astc_ldr(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }
        return format.isAstcLdr();
    }

    public static boolean basis_tex_format_is_hdr(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }
        return format.isHdr();
    }

    public static boolean basis_tex_format_is_ldr(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }
        return format.isLdr();
    }

    public static int basis_tex_format_get_block_width(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }
        return format.getBlockWidth();
    }

    public static int basis_tex_format_get_block_height(Ktx2BasisTextureFormat format) {
        if (format == null) {
            throw new BasisDecodeException("basis_tex_format must not be null");
        }
        return format.getBlockHeight();
    }

    public static boolean basis_is_format_supported(Ktx2TranscoderTextureFormat targetFormat, Ktx2BasisTextureFormat sourceFormat) {
        if (targetFormat == null || sourceFormat == null) {
            throw new BasisDecodeException("Formats must not be null");
        }

        if (sourceFormat == Ktx2BasisTextureFormat.cASTC_HDR_6x6
                || sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE) {
            return UASTC_HDR_SUPPORT.contains(targetFormat);
        }

        if (sourceFormat == Ktx2BasisTextureFormat.cUASTC_HDR_4x4) {
            return UASTC_HDR_SUPPORT.contains(targetFormat);
        }

        if (sourceFormat == Ktx2BasisTextureFormat.cUASTC_LDR_4x4) {
            switch (targetFormat) {
                case cTFPVRTC2_4_RGB:
                case cTFPVRTC2_4_RGBA:
                case cTFATC_RGB:
                case cTFATC_RGBA:
                case cTFFXT1_RGB:
                case cTFASTC_HDR_4x4_RGBA:
                case cTFASTC_HDR_6x6_RGBA:
                case cTFBC6H:
                case cTFRGBA_HALF:
                case cTFRGB_HALF:
                case cTFRGB_9E5:
                case cTFASTC_LDR_5x4_RGBA:
                case cTFASTC_LDR_5x5_RGBA:
                case cTFASTC_LDR_6x5_RGBA:
                case cTFASTC_LDR_6x6_RGBA:
                case cTFASTC_LDR_8x5_RGBA:
                case cTFASTC_LDR_8x6_RGBA:
                case cTFASTC_LDR_10x5_RGBA:
                case cTFASTC_LDR_10x6_RGBA:
                case cTFASTC_LDR_8x8_RGBA:
                case cTFASTC_LDR_10x8_RGBA:
                case cTFASTC_LDR_10x10_RGBA:
                case cTFASTC_LDR_12x10_RGBA:
                case cTFASTC_LDR_12x12_RGBA:
                    return false;
                default:
                    return true;
            }
        }

        if (sourceFormat.isXUastcLdr() || sourceFormat.isAstcLdr()) {
            if (Set.of(
                    Ktx2TranscoderTextureFormat.cTFBC1_RGB,
                    Ktx2TranscoderTextureFormat.cTFBC3_RGBA,
                    Ktx2TranscoderTextureFormat.cTFBC4_R,
                    Ktx2TranscoderTextureFormat.cTFBC5_RG,
                    Ktx2TranscoderTextureFormat.cTFBC7_RGBA,
                    Ktx2TranscoderTextureFormat.cTFBC7_ALT,
                    Ktx2TranscoderTextureFormat.cTFETC1_RGB,
                    Ktx2TranscoderTextureFormat.cTFETC2_RGBA,
                    Ktx2TranscoderTextureFormat.cTFETC2_EAC_R11,
                    Ktx2TranscoderTextureFormat.cTFETC2_EAC_RG11,
                    Ktx2TranscoderTextureFormat.cTFPVRTC1_4_RGB,
                    Ktx2TranscoderTextureFormat.cTFPVRTC1_4_RGBA,
                    Ktx2TranscoderTextureFormat.cTFRGBA32,
                    Ktx2TranscoderTextureFormat.cTFRGB565,
                    Ktx2TranscoderTextureFormat.cTFBGR565,
                    Ktx2TranscoderTextureFormat.cTFRGBA4444
            ).contains(targetFormat)) {
                return true;
            }

            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_4x4 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_4x4) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_5x4 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_5x4) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x4_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_5x5 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_5x5) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x5_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_6x5 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_6x5) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_6x5_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_6x6 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_6x6) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_6x6_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_8x5 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_8x5) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_8x5_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_8x6 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_8x6) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_8x6_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x5 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_10x5) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x5_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x6 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_10x6) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x6_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_8x8 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_8x8) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_8x8_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x8 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_10x8) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x8_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_10x10 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_10x10) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_10x10_RGBA;
            }
            if (sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_12x10 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_12x10) {
                return targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_12x10_RGBA;
            }
            return sourceFormat == Ktx2BasisTextureFormat.cXUASTC_LDR_12x12 || sourceFormat == Ktx2BasisTextureFormat.cASTC_LDR_12x12
                    ? targetFormat == Ktx2TranscoderTextureFormat.cTFASTC_LDR_12x12_RGBA
                    : false;
        }

        switch (targetFormat) {
            case cTFETC1_RGB:
            case cTFRGBA32:
            case cTFRGB565:
            case cTFBGR565:
            case cTFRGBA4444:
            case cTFBC1_RGB:
            case cTFBC4_R:
            case cTFBC5_RG:
            case cTFBC3_RGBA:
            case cTFBC7_RGBA:
            case cTFBC7_ALT:
            case cTFETC2_RGBA:
            case cTFASTC_LDR_4x4_RGBA:
            case cTFATC_RGB:
            case cTFATC_RGBA:
            case cTFFXT1_RGB:
            case cTFPVRTC1_4_RGB:
            case cTFPVRTC1_4_RGBA:
            case cTFPVRTC2_4_RGB:
            case cTFPVRTC2_4_RGBA:
            case cTFETC2_EAC_R11:
            case cTFETC2_EAC_RG11:
                return true;
            default:
                return false;
        }
    }

    public static int basis_compute_transcoded_image_size_in_bytes(
            Ktx2TranscoderTextureFormat targetFormat,
            int origWidth,
            int origHeight) {
        if (targetFormat == null) {
            throw new BasisDecodeException("target format must not be null");
        }
        if (origWidth <= 0 || origHeight <= 0) {
            throw new IllegalArgumentException("origWidth and origHeight must be positive");
        }

        if (targetFormat.isUncompressed()) {
            return origWidth * origHeight * targetFormat.getUncompressedBytesPerPixel();
        }

        int blockWidth = targetFormat.getBlockWidth();
        int blockHeight = targetFormat.getBlockHeight();
        int blocksX = (origWidth + blockWidth - 1) / blockWidth;
        int blocksY = (origHeight + blockHeight - 1) / blockHeight;

        if (targetFormat == Ktx2TranscoderTextureFormat.cTFPVRTC1_4_RGB
                || targetFormat == Ktx2TranscoderTextureFormat.cTFPVRTC1_4_RGBA) {
            blocksX = Math.max(blocksX, 8 / blockWidth);
            blocksY = Math.max(blocksY, 8 / blockHeight);
        }

        return blocksX * blocksY * targetFormat.getBytesPerBlockOrPixel();
    }

    public static boolean basis_validate_output_buffer_size(
            Ktx2TranscoderTextureFormat targetFormat,
            int outputBlocksBufSizeInBlocksOrPixels,
            int origWidth,
            int origHeight,
            int outputRowPitchInBlocksOrPixels,
            int outputRowsInPixels) {
        if (targetFormat == null) {
            return false;
        }

        if (targetFormat.isUncompressed()) {
            if (outputRowPitchInBlocksOrPixels == 0) {
                outputRowPitchInBlocksOrPixels = origWidth;
            }
            if (outputRowsInPixels == 0) {
                outputRowsInPixels = origHeight;
            }

            return outputBlocksBufSizeInBlocksOrPixels >= outputRowsInPixels * outputRowPitchInBlocksOrPixels;
        }

        int numDstBlocksX = (origWidth + targetFormat.getBlockWidth() - 1) / targetFormat.getBlockWidth();
        int numDstBlocksY = (origHeight + targetFormat.getBlockHeight() - 1) / targetFormat.getBlockHeight();
        return outputBlocksBufSizeInBlocksOrPixels >= numDstBlocksX * numDstBlocksY;
    }

    public static int crc16(byte[] data) {
        return crc16(data, 0);
    }

    public static int crc16(byte[] data, int initialCrc) {
        if (data == null) {
            throw new BasisDecodeException("data must not be null");
        }

        int crc = initialCrc & 0xFFFF;
        crc = (~crc) & 0xFFFF;

        for (byte value : data) {
            int q = (value & 0xFF) ^ (crc >>> 8);
            int k = (q >>> 4) ^ q;
            crc = ((crc << 8) ^ k ^ (k << 5) ^ (k << 12)) & 0xFFFF;
        }

        return (~crc) & 0xFFFF;
    }

    public static int hash_hsieh(byte[] data) {
        if (data == null) {
            throw new BasisDecodeException("data must not be null");
        }
        if (data.length == 0) {
            return 0;
        }

        int h = data.length;
        int bytesLeft = data.length & 3;
        int blocks = data.length >>> 2;
        int index = 0;

        while (blocks-- > 0) {
            int word0 = (data[index] & 0xFF) | ((data[index + 1] & 0xFF) << 8);
            int word1 = (data[index + 2] & 0xFF) | ((data[index + 3] & 0xFF) << 8);

            h += word0;
            int t = (word1 << 11) ^ h;
            h = (h << 16) ^ t;
            index += 4;
            h += h >>> 11;
        }

        switch (bytesLeft) {
            case 1: {
                h += (byte) data[index];
                h ^= h << 10;
                h += h >>> 1;
                break;
            }
            case 2: {
                h += (data[index] & 0xFF) | ((data[index + 1] & 0xFF) << 8);
                h ^= h << 11;
                h += h >>> 17;
                break;
            }
            case 3: {
                h += (data[index] & 0xFF) | ((data[index + 1] & 0xFF) << 8);
                h ^= h << 16;
                h ^= (byte) data[index + 2] << 18;
                h += h >>> 11;
                break;
            }
            default:
                break;
        }

        h ^= h << 3;
        h += h >>> 5;
        h ^= h << 4;
        h += h >>> 17;
        h ^= h << 25;
        h += h >>> 6;

        return h;
    }


    public static void basisu_transcoder_init() {
        synchronized (TRANSCRIBER_STATE_LOCK) {
            transcoderReady = true;
        }
    }

    public static boolean validate_header(byte[] data, int dataSize) {
        if (data == null || dataSize < 0 || dataSize > data.length) {
            return false;
        }
        return parseKtx2OrThrow(data, dataSize) != null;
    }

    public static boolean validate_header_quick(byte[] data, int dataSize) {
        if (!validate_header(data, dataSize)) {
            return false;
        }

        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        if (header == null) {
            return false;
        }

        int levelTableBytes = header.getLevelCount() * 24;
        int levelTableOffset = Ktx2Constants.KTX2_HEADER_SIZE;
        if (dataSize < levelTableOffset + levelTableBytes) {
            return false;
        }

        ByteBuffer levelView = ByteBuffer.wrap(data, levelTableOffset, levelTableBytes).order(ByteOrder.LITTLE_ENDIAN);
        long[] offsets = new long[header.getLevelCount()];
        long[] lengths = new long[header.getLevelCount()];
        for (int level = 0; level < header.getLevelCount(); level++) {
            long levelOffset = levelView.getLong();
            long levelLength = levelView.getLong();
            levelView.getLong();
            offsets[level] = levelOffset;
            lengths[level] = levelLength;

            try {
                validateLevelRange(level, levelOffset, levelLength, dataSize);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        return validateKtx2LevelTable(header, new Ktx2LevelTable(offsets, lengths), dataSize);
    }

    public static boolean validate_file_checksums(
            byte[] data,
            int dataSize,
            boolean fullValidation) {
        if (!validate_header_quick(data, dataSize)) {
            return false;
        }
        if (!fullValidation) {
            return true;
        }

        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2LevelTable levelTable = parseLevelTableOrThrow(header, data, dataSize);
        return validateKtx2LevelTable(header, levelTable, dataSize);
    }
    public static Ktx2BasisTextureType get_texture_type(byte[] data, int dataSize) {
        if (parseKtx2OrThrow(data, dataSize) == null) {
            return null;
        }

        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        return deriveTextureTypeFromHeader(header, data);
    }

    /**
     * Best-effort mirror of C++ logic: infer texture type from container metadata.
     */
    private static Ktx2BasisTextureType deriveTextureTypeFromHeader(Ktx2Header header, byte[] data) {
        int faceCount = header.getFaceCount();
        int layerCount = header.getLayerCount();

        // Use canonical ordering used by the C++ transcoder defaults.
        if (faceCount > 1 && layerCount > 1) {
            return Ktx2BasisTextureType.cBASISTexTypeCubemapArray;
        }

        if (faceCount > 1) {
            return Ktx2BasisTextureType.cBASISTexTypeCubemapArray;
        }

        if (layerCount > 1) {
            return Ktx2BasisTextureType.cBASISTexType2DArray;
        }

        // Video detection in upstream C++ is based on key data available only after init.
        // If KTXanimData exists in metadata, classify as video-like texture frames.
        if (containsKtxAnimData(data, header)) {
            return Ktx2BasisTextureType.cBASISTexTypeVideoFrames;
        }

        return Ktx2BasisTextureType.cBASISTexType2D;
    }

    private static boolean containsKtxAnimData(byte[] data, Ktx2Header header) {
        if (header.getKvdByteLength() == 0 || header.getKvdByteOffset() <= 0
                || header.getKvdByteOffset() + header.getKvdByteLength() > data.length) {
            return false;
        }

        try {
            byte[] kvd = new byte[(int) header.getKvdByteLength()];
            System.arraycopy(
                    data,
                    (int) header.getKvdByteOffset(),
                    kvd,
                    0,
                    (int) header.getKvdByteLength());
            String kvdString = new String(kvd);
            return kvdString.contains("KTXanimData");
        } catch (RuntimeException ignore) {
            return false;
        }
    }

    public static boolean get_userdata(byte[] data, int dataSize, int[] outUserData) {
        if (outUserData == null || outUserData.length < 2) {
            throw new IllegalArgumentException("outUserData must contain at least two elements");
        }

        Ktx2BasisFileHeader basisHeader = parseBasisFileHeader(data, dataSize);
        if (basisHeader != null) {
            outUserData[0] = safeCastUserData(basisHeader.getUserData0());
            outUserData[1] = safeCastUserData(basisHeader.getUserData1());
            return true;
        }

        if (!validate_header(data, dataSize)) {
            return false;
        }

        outUserData[0] = 0;
        outUserData[1] = 0;
        return true;
    }

    public static int get_total_images(byte[] data, int dataSize) {
        Ktx2LevelInfo info = validateAndGetImageInfo(data, dataSize);
        return info == null ? 0 : info.totalImages;
    }

    public static Ktx2BasisTextureFormat get_basis_tex_format(
            byte[] data,
            int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        if (header == null) {
            return null;
        }

        Ktx2DfdInfo dfdInfo = inferDfdInfo(data, dataSize, header);
        if (dfdInfo == null) {
            return Ktx2BasisTextureFormat.cUASTC_LDR_4x4;
        }

        return inferBasisTextureFormat(header, dfdInfo);
    }

    public static long bt_ktx2_open(byte[] data) {
        return bt_ktx2_open(data, data == null ? 0 : data.length);
    }

    public static long bt_ktx2_open(byte[] data, int dataLength) {
        if (data == null || dataLength < 0 || dataLength > data.length) {
            return 0;
        }

        long handle = WASM_HANDLE_SEQUENCE.getAndIncrement();
        WASM_HANDLE_STATE.put(handle, Arrays.copyOf(data, dataLength));
        return handle;
    }

    public static long bt_ktx2_open(long dataMemOfs, int dataLen) {
        byte[] source = ALLOCATIONS.get(dataMemOfs);
        if (source == null || dataLen < 0 || dataLen > source.length) {
            return 0;
        }

        long handle = WASM_HANDLE_SEQUENCE.getAndIncrement();
        WASM_HANDLE_STATE.put(handle, Arrays.copyOf(source, dataLen));
        return handle;
    }

    public static void bt_ktx2_close(long handle) {
        WASM_ACTIVE_STATES.remove(handle);
        WASM_HANDLE_STATE.remove(handle);
    }

    public static long bt_ktx2_create_transcode_state() {
        long handle = TRANSCODE_STATE_SEQUENCE.getAndIncrement();
        WASM_TRANSCODE_STATES.put(handle, new Ktx2TranscoderState());
        return handle;
    }

    public static void bt_ktx2_destroy_transcode_state(long handle) {
        Ktx2TranscoderState state = WASM_TRANSCODE_STATES.remove(handle);
        if (state != null) {
            state.destroy();
        }
        WASM_ACTIVE_STATES.remove(handle);
    }

    public static int bt_ktx2_get_block_width(long handle) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null) {
            return 0;
        }

        Ktx2BasisTextureFormat format = getBasisTextureFormatFromHandle(handle);
        return format == null ? 4 : format.getBlockWidth();
    }

    public static int bt_ktx2_get_block_height(long handle) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null) {
            return 0;
        }

        Ktx2BasisTextureFormat format = getBasisTextureFormatFromHandle(handle);
        return format == null ? 4 : format.getBlockHeight();
    }

    public static int bt_ktx2_has_alpha(long handle) {
        return bt_ktx2_get_level_alpha_flag(handle, 0, 0, 0);
    }

    public static int bt_ktx2_is_srgb(long handle) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null) {
            return 0;
        }

        byte[] payload = handleBytes(handle);
        Ktx2DfdInfo dfdInfo = getDfdInfo(info.header, payload);
        return dfdInfo != null && dfdInfo.getTransferFunc() == Ktx2Constants.KTX2_KHR_DF_TRANSFER_SRGB
                ? 1
                : 0;
    }

    public static int bt_ktx2_is_video(long handle) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null) {
            return 0;
        }

        Ktx2BasisTextureType textureType = getTextureTypeFromHandle(handle);
        return textureType == Ktx2BasisTextureType.cBASISTexTypeVideoFrames ? 1 : 0;
    }

    public static float bt_ktx2_get_ldr_hdr_upconversion_nit_multiplier(long handle) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null) {
            return 0.0f;
        }

        Ktx2BasisTextureFormat format = getBasisTextureFormatFromHandle(handle);
        if (format == null) {
            return 0.0f;
        }

        return format.isHdr() ? 1.0f : 0.0f;
    }

    public static int bt_ktx2_get_level_orig_width(long handle, int levelIndex, int layerIndex, int faceIndex) {
        return getLevelDimension(handle, levelIndex, layerIndex, faceIndex, false, true);
    }

    public static int bt_ktx2_get_level_orig_height(long handle, int levelIndex, int layerIndex, int faceIndex) {
        return getLevelDimension(handle, levelIndex, layerIndex, faceIndex, true, true);
    }

    public static int bt_ktx2_get_level_actual_width(long handle, int levelIndex, int layerIndex, int faceIndex) {
        return getLevelDimension(handle, levelIndex, layerIndex, faceIndex, false, false);
    }

    public static int bt_ktx2_get_level_actual_height(long handle, int levelIndex, int layerIndex, int faceIndex) {
        return getLevelDimension(handle, levelIndex, layerIndex, faceIndex, true, false);
    }

    public static int bt_ktx2_get_level_num_blocks_x(long handle, int levelIndex, int layerIndex, int faceIndex) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null || !validateLevelIndexes(info, levelIndex, layerIndex, faceIndex)) {
            return 0;
        }
        int blockWidth = Math.max(1, bt_ktx2_get_block_width(handle));
        int levelWidth = getLevelDimension(handle, levelIndex, layerIndex, faceIndex, false, false);
        return (levelWidth + blockWidth - 1) / blockWidth;
    }

    public static int bt_ktx2_get_level_num_blocks_y(long handle, int levelIndex, int layerIndex, int faceIndex) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null || !validateLevelIndexes(info, levelIndex, layerIndex, faceIndex)) {
            return 0;
        }
        int blockHeight = Math.max(1, bt_ktx2_get_block_height(handle));
        int levelHeight = getLevelDimension(handle, levelIndex, layerIndex, faceIndex, true, false);
        return (levelHeight + blockHeight - 1) / blockHeight;
    }

    public static int bt_ktx2_get_level_total_blocks(long handle, int levelIndex, int layerIndex, int faceIndex) {
        return Math.max(
                0,
                bt_ktx2_get_level_num_blocks_x(handle, levelIndex, layerIndex, faceIndex)
                        * bt_ktx2_get_level_num_blocks_y(handle, levelIndex, layerIndex, faceIndex));
    }

    public static int bt_ktx2_get_level_alpha_flag(long handle, int levelIndex, int layerIndex, int faceIndex) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null || !validateLevelIndexes(info, levelIndex, layerIndex, faceIndex)) {
            return 0;
        }

        Ktx2DfdInfo dfdInfo = getDfdInfo(info.header, handleBytes(handle));
        if (dfdInfo == null) {
            return 0;
        }

        return dfdInfo.hasAlphaSample() ? 1 : 0;
    }

    public static int bt_ktx2_get_level_iframe_flag(long handle, int levelIndex, int layerIndex, int faceIndex) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        return (info != null && validateLevelIndexes(info, levelIndex, layerIndex, faceIndex)) ? 0 : 0;
    }

    public static boolean bt_ktx2_start_transcoding(long handle) {
        if (!WASM_HANDLE_STATE.containsKey(handle)) {
            return false;
        }

        if (!validateAndGetHandlePayload(handle)) {
            return false;
        }

        WASM_ACTIVE_STATES.add(handle);
        return true;
    }

    // C-API style aliases from encoder/wasm header (bt_ prefix).
    // They intentionally parse the same KTX2 payloads used by this facade.
    public static int bt_ktx2_get_width(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        return header == null ? 0 : header.getPixelWidth();
    }

    public static int bt_ktx2_get_height(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        return header == null ? 0 : header.getPixelHeight();
    }

    public static int bt_ktx2_get_levels(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        return header == null ? 0 : header.getLevelCount();
    }

    public static int bt_ktx2_get_faces(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        return header == null ? 0 : header.getFaceCount();
    }

    public static int bt_ktx2_get_layers(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        return header == null ? 0 : header.getLayerCount();
    }

    public static int bt_ktx2_get_basis_tex_format(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return format == null ? -1 : format.getCode();
    }

    public static int bt_ktx2_is_etc1s(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return (format != null && format == Ktx2BasisTextureFormat.cETC1S) ? 1 : 0;
    }

    public static int bt_ktx2_is_uastc_ldr_4x4(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return (format != null && format == Ktx2BasisTextureFormat.cUASTC_LDR_4x4) ? 1 : 0;
    }

    public static int bt_ktx2_is_hdr(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return format != null && format.isHdr() ? 1 : 0;
    }

    public static int bt_ktx2_is_hdr_4x4(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return (format == Ktx2BasisTextureFormat.cUASTC_HDR_4x4) ? 1 : 0;
    }

    public static int bt_ktx2_is_hdr_6x6(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return (format == Ktx2BasisTextureFormat.cASTC_HDR_6x6
                || format == Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE) ? 1 : 0;
    }

    public static int bt_ktx2_is_ldr(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return format != null && format.isLdr() ? 1 : 0;
    }

    public static int bt_ktx2_is_astc_ldr(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return (format != null && format.isAstcLdr()) ? 1 : 0;
    }

    public static int bt_ktx2_is_xuastc_ldr(byte[] data, int dataSize) {
        Ktx2BasisTextureFormat format = get_basis_tex_format(data, dataSize);
        return (format != null && format.isXUastcLdr()) ? 1 : 0;
    }

    public static int bt_ktx2_get_dfd_color_model(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2DfdInfo dfdInfo = header == null ? null : inferDfdInfo(data, dataSize, header);
        return dfdInfo == null ? -1 : dfdInfo.getColorModel();
    }

    public static int bt_ktx2_get_dfd_color_primaries(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2DfdInfo dfdInfo = header == null ? null : inferDfdInfo(data, dataSize, header);
        return dfdInfo == null ? -1 : dfdInfo.getColorPrimaries();
    }

    public static int bt_ktx2_get_dfd_transfer_func(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2DfdInfo dfdInfo = header == null ? null : inferDfdInfo(data, dataSize, header);
        return dfdInfo == null ? -1 : dfdInfo.getTransferFunc();
    }

    public static int bt_ktx2_get_dfd_flags(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2DfdInfo dfdInfo = header == null ? null : inferDfdInfo(data, dataSize, header);
        return dfdInfo == null ? -1 : dfdInfo.getDfdFlags();
    }

    public static int bt_ktx2_get_dfd_total_samples(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2DfdInfo dfdInfo = header == null ? null : inferDfdInfo(data, dataSize, header);
        if (dfdInfo == null) {
            return -1;
        }
        return dfdInfo.hasAlphaSample() ? 2 : 1;
    }

    public static int bt_ktx2_get_dfd_channel_id0(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2DfdInfo dfdInfo = header == null ? null : inferDfdInfo(data, dataSize, header);
        return dfdInfo == null ? -1 : dfdInfo.getChannel0();
    }

    public static int bt_ktx2_get_dfd_channel_id1(byte[] data, int dataSize) {
        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        Ktx2DfdInfo dfdInfo = header == null ? null : inferDfdInfo(data, dataSize, header);
        return dfdInfo == null ? -1 : dfdInfo.getChannel1();
    }

    public static int get_total_image_levels(byte[] data, int dataSize, int imageIndex) {
        Ktx2LevelInfo info = validateAndGetImageInfo(data, dataSize);
        if (info == null
                || imageIndex < 0
                || imageIndex >= info.totalImages
                || info.levelCount <= 0) {
            return 0;
        }

        return info.levelCount;
    }

    public static Ktx2ImageLevelInfo get_image_level_desc(
            byte[] data,
            int dataSize,
            int imageIndex,
            int levelIndex) {
        return get_image_level_info(data, dataSize, imageIndex, levelIndex);
    }

    public static Ktx2ImageLevelInfo get_image_level_info(
            byte[] data,
            int dataSize,
            int imageIndex,
            int levelIndex) {
        Ktx2LevelInfo info = validateAndGetImageInfo(data, dataSize);
        if (info == null
                || imageIndex < 0
                || imageIndex >= info.totalImages
                || levelIndex < 0
                || levelIndex >= info.levelCount) {
            return null;
        }

        int baseSliceIndex = computeFirstSliceIndex(info, imageIndex, levelIndex);
        Ktx2LevelTable table = parseLevelTableOrThrow(info.header, data, dataSize);
        long offset = table.offsets[levelIndex];
        long length = table.lengths[levelIndex];
        Ktx2DfdInfo dfdInfo = getDfdInfo(info.header, data);
        boolean hasAlpha = dfdInfo != null && dfdInfo.hasAlphaSample();

        int levelWidth = Math.max(1, info.header.getPixelWidth() >> levelIndex);
        int levelHeight = Math.max(1, info.header.getPixelHeight() >> levelIndex);
        int blockWidth = 4;
        int blockHeight = 4;
        int numBlocksX = (levelWidth + blockWidth - 1) / blockWidth;
        int numBlocksY = (levelHeight + blockHeight - 1) / blockHeight;

        return new Ktx2ImageLevelInfo(
                imageIndex,
                levelIndex,
                levelWidth,
                levelHeight,
                numBlocksX * blockWidth,
                numBlocksY * blockHeight,
                blockWidth,
                blockHeight,
                numBlocksX,
                numBlocksY,
                numBlocksX * numBlocksY,
                baseSliceIndex,
                safeIntOffset(offset),
                safeIntLength(length),
                0,
                0,
                hasAlpha,
                false);
    }

    public static Ktx2ImageInfo get_image_info(byte[] data, int dataSize, int imageIndex) {
        Ktx2LevelInfo info = validateAndGetImageInfo(data, dataSize);
        if (info == null
                || imageIndex < 0
                || imageIndex >= info.totalImages) {
            return null;
        }

        int blockWidth = 4;
        int blockHeight = 4;
        int numBlocksX = (info.header.getPixelWidth() + blockWidth - 1) / blockWidth;
        int numBlocksY = (info.header.getPixelHeight() + blockHeight - 1) / blockHeight;
        Ktx2DfdInfo dfdInfo = getDfdInfo(info.header, data);
        boolean hasAlpha = dfdInfo != null && dfdInfo.hasAlphaSample();
        boolean supportsIFrame = info.header.getSupercompressionScheme() == Ktx2SupercompressionScheme.NONE;

        return new Ktx2ImageInfo(
                imageIndex,
                info.levelCount,
                info.header.getPixelWidth(),
                info.header.getPixelHeight(),
                numBlocksX * blockWidth,
                numBlocksY * blockHeight,
                blockWidth,
                blockHeight,
                numBlocksX,
                numBlocksY,
                numBlocksX * numBlocksY,
                computeFirstSliceIndex(info, imageIndex, 0),
                hasAlpha,
                supportsIFrame);
    }

    public static Ktx2FileInfo get_file_info(byte[] data, int dataSize) {
        Ktx2LevelInfo info = validateAndGetImageInfo(data, dataSize);
        if (info == null) {
            return null;
        }

        int[] imageMipmapLevels = new int[info.totalImages];
        for (int i = 0; i < imageMipmapLevels.length; i++) {
            imageMipmapLevels[i] = info.levelCount;
        }

        Ktx2BasisTextureType detectedType = get_texture_type(data, dataSize);
        return new Ktx2FileInfo(
                0,
                Ktx2Constants.KTX2_HEADER_SIZE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                dataSize - Ktx2Constants.KTX2_HEADER_SIZE,
                detectedType == null ? Ktx2BasisTextureType.cBASISTexType2D : detectedType,
                0,
                info.totalImages,
                imageMipmapLevels,
                0,
                0);
    }

    public static boolean start_transcoding(byte[] data, int dataSize) {
        Ktx2LevelInfo info = validateAndGetImageInfo(data, dataSize);
        if (info == null) {
            return false;
        }

        synchronized (TRANSCRIBER_STATE_LOCK) {
            activeTranscodingPayload = Arrays.copyOf(data, dataSize);
            activeTranscodingInfo = info;
            transcoderReady = true;
            return true;
        }
    }

    public static boolean stop_transcoding() {
        synchronized (TRANSCRIBER_STATE_LOCK) {
            boolean hadState = transcoderReady;
            transcoderReady = false;
            activeTranscodingPayload = null;
            activeTranscodingInfo = null;
            return hadState;
        }
    }

    public static int find_slice(int imageIndex, int levelIndex, boolean alphaData) {
        if (!transcoderReady) {
            return -1;
        }

        Ktx2LevelInfo info = activeTranscodingInfo;
        Ktx2DfdInfo dfdInfo = activeTranscodingPayload == null || info == null
                ? null
                : getDfdInfo(info.header, activeTranscodingPayload);
        if (info == null
                || imageIndex < 0
                || imageIndex >= info.totalImages
                || levelIndex < 0
                || levelIndex >= info.levelCount) {
            return -1;
        }

        if (info.header.getSupercompressionScheme() == Ktx2SupercompressionScheme.NONE) {
            // For non-compressed paths, each level maps one-to-one.
            return levelIndex + imageIndex * info.levelCount;
        }

        boolean hasAlpha = dfdInfo != null && dfdInfo.hasAlphaSample();
        Ktx2BasisTextureFormat basisFormat = inferBasisTextureFormatSafely(info.header, dfdInfo);

        boolean usesAlphaSlices = basisFormat == Ktx2BasisTextureFormat.cETC1S && hasAlpha;
        if (usesAlphaSlices && alphaData) {
            return imageIndex * (info.levelCount * 2) + levelIndex + info.levelCount;
        }
        if (usesAlphaSlices && !alphaData) {
            return imageIndex * (info.levelCount * 2) + levelIndex;
        }

        return imageIndex * info.levelCount + levelIndex;
    }

    public static int find_first_slice_index(int imageIndex, boolean hasAlpha) {
        Ktx2LevelInfo info = activeTranscodingInfo;
        if (!transcoderReady || info == null || imageIndex < 0 || imageIndex >= info.totalImages) {
            return -1;
        }

        return find_slice(imageIndex, 0, hasAlpha);
    }

    public static int get_debug_flags() {
        return debugFlags;
    }

    public static void set_debug_flags(int debugFlags) {
        Ktx2DebugFlag.fromMask(debugFlags);
        Ktx2CppTranscoderApi.debugFlags = debugFlags;
    }

    private static Ktx2BasisTextureFormat inferBasisTextureFormat(
            Ktx2Header header,
            Ktx2DfdInfo dfd) {
        if (dfd.isAstcColorModel()) {
            if (!isAstcLdrVkFormat(header.getVkFormat())) {
                throw new BasisDecodeException(
                        "Invalid header vkFormat for ASTC LDR model: " + header.getVkFormat());
            }
            return mapKtx2AstcLdrBlockToFormat(dfd.getBlockWidth(), dfd.getBlockHeight());
        }

        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_ETC1S) {
            return Ktx2BasisTextureFormat.cETC1S;
        }

        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_LDR_4X4) {
            return Ktx2BasisTextureFormat.cUASTC_LDR_4x4;
        }

        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_HDR_4X4) {
            return Ktx2BasisTextureFormat.cUASTC_HDR_4x4;
        }

        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC) {
            if (dfd.getBlockWidth() != 6 || dfd.getBlockHeight() != 6) {
                throw new BasisDecodeException(
                        "Unexpected ASTC block for HDR model: " + dfd.getBlockWidth() + "x" + dfd.getBlockHeight());
            }
            return Ktx2BasisTextureFormat.cASTC_HDR_6x6;
        }

        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_UASTC_HDR_6X6_INTERMEDIATE) {
            return Ktx2BasisTextureFormat.cUASTC_HDR_6x6_INTERMEDIATE;
        }

        if (dfd.getColorModel() == Ktx2Constants.KTX2_KDF_DF_MODEL_XUASTC_LDR_INTERMEDIATE) {
            return mapXUastcLdrSizeToFormat(dfd.getBlockWidth(), dfd.getBlockHeight());
        }

        return Ktx2BasisTextureFormat.cUASTC_LDR_4x4;
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

    private static Ktx2BasisTextureFormat mapKtx2AstcLdrBlockToFormat(int blockWidth, int blockHeight) {
        if (blockWidth == 4 && blockHeight == 4) {
            return Ktx2BasisTextureFormat.cASTC_LDR_4x4;
        }
        if (blockWidth == 5 && blockHeight == 4) {
            return Ktx2BasisTextureFormat.cASTC_LDR_5x4;
        }
        if (blockWidth == 5 && blockHeight == 5) {
            return Ktx2BasisTextureFormat.cASTC_LDR_5x5;
        }
        if (blockWidth == 6 && blockHeight == 5) {
            return Ktx2BasisTextureFormat.cASTC_LDR_6x5;
        }
        if (blockWidth == 6 && blockHeight == 6) {
            return Ktx2BasisTextureFormat.cASTC_LDR_6x6;
        }
        if (blockWidth == 8 && blockHeight == 5) {
            return Ktx2BasisTextureFormat.cASTC_LDR_8x5;
        }
        if (blockWidth == 8 && blockHeight == 6) {
            return Ktx2BasisTextureFormat.cASTC_LDR_8x6;
        }
        if (blockWidth == 10 && blockHeight == 5) {
            return Ktx2BasisTextureFormat.cASTC_LDR_10x5;
        }
        if (blockWidth == 10 && blockHeight == 6) {
            return Ktx2BasisTextureFormat.cASTC_LDR_10x6;
        }
        if (blockWidth == 8 && blockHeight == 8) {
            return Ktx2BasisTextureFormat.cASTC_LDR_8x8;
        }
        if (blockWidth == 10 && blockHeight == 8) {
            return Ktx2BasisTextureFormat.cASTC_LDR_10x8;
        }
        if (blockWidth == 10 && blockHeight == 10) {
            return Ktx2BasisTextureFormat.cASTC_LDR_10x10;
        }
        if (blockWidth == 12 && blockHeight == 10) {
            return Ktx2BasisTextureFormat.cASTC_LDR_12x10;
        }
        if (blockWidth == 12 && blockHeight == 12) {
            return Ktx2BasisTextureFormat.cASTC_LDR_12x12;
        }

        throw new BasisDecodeException("Unsupported ASTC block size " + blockWidth + "x" + blockHeight);
    }

    private static Ktx2BasisTextureFormat mapXUastcLdrSizeToFormat(int blockWidth, int blockHeight) {
        return mapKtx2AstcLdrBlockToFormat(blockWidth, blockHeight);
    }

    private static Ktx2DfdInfo inferDfdInfo(byte[] data, int dataSize, Ktx2Header header) {
        int dfdLength = safeIntLength(header.getDfdByteLength());
        if (dfdLength <= 0) {
            return null;
        }

        long dfdOffset = header.getDfdByteOffset();
        if (dfdOffset < Ktx2Constants.KTX2_HEADER_SIZE || dfdOffset + dfdLength > dataSize) {
            return null;
        }

        if (dfdLength != 44 && dfdLength != 60) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, (int) dfdOffset, dfdLength)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        int declaredLength = buffer.getInt();
        if (declaredLength != dfdLength) {
            return null;
        }

        // Optional check: KTX2 DFD says the DFD body begins after header data, but block dimensions/samples are
        // still required for texture-classification semantics.
        buffer.position(12);
        int dfdBits = buffer.getInt();
        buffer.position(16);
        int texelBlockDimensions = buffer.getInt();
        buffer.position(28);
        int sample0 = buffer.getInt();
        int sample1 = 0;
        if (dfdLength >= 60) {
            buffer.position(44);
            sample1 = buffer.getInt();
        }

        int blockWidth = (texelBlockDimensions & 0xFF) + 1;
        int blockHeight = ((texelBlockDimensions >>> 8) & 0xFF) + 1;
        int transferFunc = (dfdBits >>> 16) & 255;
        if (transferFunc != Ktx2Constants.KTX2_KHR_DF_TRANSFER_LINEAR
                && transferFunc != Ktx2Constants.KTX2_KHR_DF_TRANSFER_SRGB) {
            return null;
        }

        int colorModel = dfdBits & 0xFF;
        int colorPrimaries = (dfdBits >>> 8) & 0xFF;
        int dfdFlags = (dfdBits >>> 24) & 0xFF;
        int channel0 = (sample0 >>> 24) & 0x0F;
        int channel1 = (sample1 >>> 24) & 0x0F;
        return new Ktx2DfdInfo(colorModel, colorPrimaries, transferFunc, dfdFlags, blockWidth, blockHeight, channel0,
                channel1, dfdLength == 60);
    }

    private static boolean validateAndGetHandlePayload(long handle) {
        byte[] payload = handleBytes(handle);
        return payload != null && validate_header(payload, payload.length);
    }

    private static byte[] handleBytes(long handle) {
        return WASM_HANDLE_STATE.get(handle);
    }

    private static Ktx2LevelInfo validateAndGetHandleInfo(long handle) {
        byte[] payload = handleBytes(handle);
        if (payload == null) {
            return null;
        }
        return validateAndGetImageInfo(payload, payload.length);
    }

    private static boolean validateLevelIndexes(
            Ktx2LevelInfo info,
            int levelIndex,
            int layerIndex,
            int faceIndex) {
        if (info == null) {
            return false;
        }

        if (levelIndex < 0 || levelIndex >= info.levelCount) {
            return false;
        }
        if (layerIndex < 0 || layerIndex >= info.header.getLayerCount()) {
            return false;
        }
        return faceIndex >= 0 && faceIndex < info.header.getFaceCount();
    }

    private static int getLevelDimension(
            long handle,
            int levelIndex,
            int layerIndex,
            int faceIndex,
            boolean isHeight,
            boolean useOriginal) {
        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null || !validateLevelIndexes(info, levelIndex, layerIndex, faceIndex)) {
            return 0;
        }

        Ktx2Header header = info.header;
        int base = isHeight ? header.getPixelHeight() : header.getPixelWidth();

        // Keep logical dimensions aligned with the current level pyramid for parity checks.
        int value = Math.max(1, base >> levelIndex);
        return useOriginal ? value : value;
    }

    private static Ktx2BasisTextureFormat getBasisTextureFormatFromHandle(long handle) {
        byte[] payload = handleBytes(handle);
        if (payload == null) {
            return null;
        }

        Ktx2LevelInfo info = validateAndGetHandleInfo(handle);
        if (info == null || info.header == null) {
            return null;
        }

        Ktx2DfdInfo dfdInfo = getDfdInfo(info.header, payload);
        if (dfdInfo == null) {
            return Ktx2BasisTextureFormat.cUASTC_LDR_4x4;
        }

        return inferBasisTextureFormat(info.header, dfdInfo);
    }

    private static Ktx2BasisTextureType getTextureTypeFromHandle(long handle) {
        byte[] payload = handleBytes(handle);
        if (payload == null) {
            return Ktx2BasisTextureType.cBASISTexType2D;
        }
        return get_texture_type(payload, payload.length);
    }

    private static Ktx2DfdInfo getDfdInfo(Ktx2Header header, byte[] data) {
        if (header == null || data == null) {
            return null;
        }
        return inferDfdInfo(data, data.length, header);
    }

    private static Ktx2BasisTextureFormat inferBasisTextureFormatSafely(
            Ktx2Header header,
            Ktx2DfdInfo dfdInfo) {
        if (header == null || dfdInfo == null) {
            return Ktx2BasisTextureFormat.cUASTC_LDR_4x4;
        }

        try {
            return inferBasisTextureFormat(header, dfdInfo);
        } catch (BasisDecodeException exception) {
            return Ktx2BasisTextureFormat.cUASTC_LDR_4x4;
        }
    }

    private static int computeTotalImages(Ktx2Header header) {
        if (header == null) {
            return 0;
        }

        int layerCount = Math.max(1, header.getLayerCount());
        int faceCount = Math.max(1, header.getFaceCount());
        return layerCount * faceCount;
    }

    private static int computeFirstSliceIndex(
            Ktx2LevelInfo info,
            int imageIndex,
            int levelIndex,
            boolean useAlphaSlices) {
        if (info == null || imageIndex < 0 || levelIndex < 0 || info.levelCount <= 0
                || imageIndex >= info.totalImages) {
            return -1;
        }

        int sliceIndexPerImage = info.levelCount * (useAlphaSlices ? 2 : 1);
        int baseSlice = imageIndex * sliceIndexPerImage;
        return useAlphaSlices ? baseSlice + levelIndex + info.levelCount : baseSlice + levelIndex;
    }

    private static int computeFirstSliceIndex(
            Ktx2LevelInfo info,
            int imageIndex,
            int levelIndex) {
        return computeFirstSliceIndex(info, imageIndex, levelIndex, false);
    }
 
    private static Ktx2Header parseKtx2OrThrow(byte[] data, int dataSize) {
        if (data == null || dataSize < Ktx2Constants.KTX2_HEADER_SIZE || dataSize > data.length) {
            return null;
        }

        try {
            byte[] source = Arrays.copyOf(data, dataSize);
            return Ktx2Header.parse(source);
        } catch (BasisDecodeException exception) {
            return null;
        }
    }

    private static Ktx2LevelInfo validateAndGetImageInfo(byte[] data, int dataSize) {
        if (!validate_header(data, dataSize)) {
            return null;
        }

        Ktx2Header header = parseKtx2OrThrow(data, dataSize);
        if (header == null) {
            return null;
        }

        return new Ktx2LevelInfo(header, computeTotalImages(header));
    }

    private static Ktx2BasisFileHeader parseBasisFileHeader(byte[] data, int dataSize) {
        if (data == null || dataSize < Ktx2BasisFileHeader.SIZE_BYTES || dataSize > data.length) {
            return null;
        }

        if (data.length < 2 || data[0] != 'B' || data[1] != 's') {
            return null;
        }

        try {
            return Ktx2BasisFileHeader.parse(ByteBuffer.wrap(data), 0);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int safeCastUserData(long userData) {
        return (int) (userData & 0xFFFFFFFFL);
    }

    private static Ktx2LevelTable parseLevelTableOrThrow(
            Ktx2Header header,
            byte[] data,
            int dataSize) {
        int levelIndexBytes = header.getLevelCount() * 24;
        int levelTableOffset = Ktx2Constants.KTX2_HEADER_SIZE;

        if (dataSize < levelTableOffset + levelIndexBytes) {
            throw new IllegalArgumentException("level index table is truncated");
        }

        long[] offsets = new long[header.getLevelCount()];
        long[] lengths = new long[header.getLevelCount()];
        ByteBuffer buffer = ByteBuffer.wrap(data, levelTableOffset, dataSize - levelTableOffset)
                .order(ByteOrder.LITTLE_ENDIAN);

        for (int level = 0; level < header.getLevelCount(); level++) {
            long byteOffset = readUInt64(buffer, "level byte offset for level " + level);
            long byteLength = readUInt64(buffer, "level byte length for level " + level);
            long uncompressedLength = readUInt64(buffer, "level uncompressed length for level " + level);
            if (uncompressedLength < 0) {
                throw new IllegalArgumentException("negative uncompressed byte length for level index " + level);
            }

            validateLevelRange(level, byteOffset, byteLength, dataSize);
            offsets[level] = byteOffset;
            lengths[level] = byteLength;
        }

        return new Ktx2LevelTable(offsets, lengths);
    }

    private static long readUInt64(ByteBuffer buffer, String fieldName) {
        if (buffer.remaining() < Long.BYTES) {
            throw new IllegalArgumentException("truncated " + fieldName);
        }
        long value = buffer.getLong();
        if (value < 0) {
            throw new IllegalArgumentException("invalid unsigned 64-bit value for " + fieldName);
        }
        return value;
    }

    private static void validateLevelRange(
            int level,
            long byteOffset,
            long byteLength,
            int dataSize) {
        if (byteOffset < 0 || byteLength < 0) {
            throw new IllegalArgumentException("negative offset/length for level index " + level);
        }
        if (byteOffset < Ktx2Constants.KTX2_HEADER_SIZE) {
            throw new IllegalArgumentException("level data offset overlaps header for level index " + level);
        }

        if (byteLength == 0) {
            return;
        }

        long byteEnd;
        try {
            byteEnd = Math.addExact(byteOffset, byteLength);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("level range overflow for level index " + level, overflow);
        }
        if (byteEnd > dataSize) {
            throw new IllegalArgumentException("level range exceeds payload for level index " + level);
        }
    }

    private static boolean validateKtx2LevelTable(
            Ktx2Header header,
            Ktx2LevelTable table,
            int dataSize) {
        if (table.offsets.length != header.getLevelCount()
                || table.lengths.length != header.getLevelCount()) {
            return false;
        }

        // Individual range checks.
        for (int level = 0; level < header.getLevelCount(); level++) {
            long offset = table.offsets[level];
            long length = table.lengths[level];

            try {
                validateLevelRange(level, offset, length, dataSize);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        // Ensure level byte ranges do not overlap in a way that would imply invalid
        // descriptor packing in the same payload.
        for (int left = 0; left < header.getLevelCount(); left++) {
            long leftOffset = table.offsets[left];
            long leftLength = table.lengths[left];
            long leftEnd = leftLength == 0L
                    ? leftOffset
                    : Math.addExact(leftOffset, leftLength);

            for (int right = left + 1; right < header.getLevelCount(); right++) {
                long rightOffset = table.offsets[right];
                long rightLength = table.lengths[right];
                long rightEnd = rightLength == 0L
                        ? rightOffset
                        : Math.addExact(rightOffset, rightLength);

                // Half-open interval overlap: [offset, offset+length)
                if (leftOffset < rightEnd && rightOffset < leftEnd) {
                    return false;
                }
            }
        }

        return true;
    }

    private static int safeIntOffset(long offset) {
        return CppTypeMappings.toIntFromSizeT(offset, "level byte offset");
    }

    private static int safeIntLength(long length) {
        return CppTypeMappings.toIntFromSizeT(length, "level byte length");
    }


    private static final class Ktx2DfdInfo {
        private final int colorModel;
        private final int colorPrimaries;
        private final int transferFunc;
        private final int dfdFlags;
        private final int blockWidth;
        private final int blockHeight;
        private final int channel0;
        private final int channel1;
        private final boolean hasAlphaSample;

        private Ktx2DfdInfo(int colorModel,
                             int colorPrimaries,
                             int transferFunc,
                             int dfdFlags,
                             int blockWidth,
                             int blockHeight,
                             int channel0,
                             int channel1,
                             boolean hasAlphaSample) {
            this.colorModel = colorModel;
            this.colorPrimaries = colorPrimaries;
            this.transferFunc = transferFunc;
            this.dfdFlags = dfdFlags;
            this.blockWidth = blockWidth;
            this.blockHeight = blockHeight;
            this.channel0 = channel0;
            this.channel1 = channel1;
            this.hasAlphaSample = hasAlphaSample;
        }

        private int getColorModel() {
            return colorModel;
        }

        private int getColorPrimaries() {
            return colorPrimaries;
        }

        private int getTransferFunc() {
            return transferFunc;
        }

        private int getDfdFlags() {
            return dfdFlags;
        }

        private int getBlockWidth() {
            return blockWidth;
        }

        private int getBlockHeight() {
            return blockHeight;
        }

        private boolean hasAlphaSample() {
            return hasAlphaSample;
        }

        private int getChannel0() {
            return channel0;
        }

        private int getChannel1() {
            return channel1;
        }

        private boolean isAstcColorModel() {
            return colorModel == Ktx2Constants.KTX2_KDF_DF_MODEL_ASTC;
        }
    }

    private static class Ktx2LevelInfo {
        private final Ktx2Header header;
        private final int levelCount;
        private final int totalImages;

        private Ktx2LevelInfo(Ktx2Header header, int totalImages) {
            this.header = header;
            this.levelCount = header.getLevelCount();
            this.totalImages = totalImages;
        }
    }

    private static class Ktx2LevelTable {
        private final long[] offsets;
        private final long[] lengths;

        private Ktx2LevelTable(long[] offsets, long[] lengths) {
            this.offsets = offsets;
            this.lengths = lengths;
        }
    }

}
