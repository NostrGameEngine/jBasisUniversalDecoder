package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Immutable decode request passed to {@link BasisDecoder} backends.
 */
public final class BasisDecodeRequest {

    public static final int MIN_QUALITY = 0;
    public static final int MAX_QUALITY = 10;

    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 64;

    private final byte[] encodedData;
    private final BasisTranscodeTarget target;
    private final BasisPlatformCapabilities platformCapabilities;
    private final BasisTranscodeTarget preferredFallback;
    private final boolean linearColorSpace;
    private final int qualityLevel;
    private final int threadCount;
    private final int imageIndex;
    private final int decodeFlags;
    private final boolean strictMode;
    private final String transcodePreset;
    private final IntFunction<ByteBuffer> allocator;

    public BasisDecodeRequest(byte[] encodedData,
                             BasisTranscodeTarget target,
                             boolean linearColorSpace,
                             int qualityLevel,
                             int threadCount,
                             boolean strictMode,
                             String transcodePreset,
                             IntFunction<ByteBuffer> allocator) {
        this(encodedData,
                target,
                null,
                null,
                linearColorSpace,
                qualityLevel,
                threadCount,
                0,
                0,
                strictMode,
                transcodePreset,
                allocator);
    }

    public BasisDecodeRequest(byte[] encodedData,
                             BasisTranscodeTarget target,
                             BasisPlatformCapabilities platformCapabilities,
                             BasisTranscodeTarget preferredFallback,
                             boolean linearColorSpace,
                             int qualityLevel,
                             int threadCount,
                             boolean strictMode,
                             String transcodePreset,
                             IntFunction<ByteBuffer> allocator) {
        this(encodedData,
                target,
                platformCapabilities,
                preferredFallback,
                linearColorSpace,
                qualityLevel,
                threadCount,
                0,
                0,
                strictMode,
                transcodePreset,
                allocator);
    }

    public BasisDecodeRequest(byte[] encodedData,
                             BasisTranscodeTarget target,
                             BasisPlatformCapabilities platformCapabilities,
                             BasisTranscodeTarget preferredFallback,
                             boolean linearColorSpace,
                             int qualityLevel,
                             int threadCount,
                             int imageIndex,
                             boolean strictMode,
                             String transcodePreset,
                             IntFunction<ByteBuffer> allocator) {
        this(encodedData,
                target,
                platformCapabilities,
                preferredFallback,
                linearColorSpace,
                qualityLevel,
                threadCount,
                imageIndex,
                0,
                strictMode,
                transcodePreset,
                allocator);
    }

    private BasisDecodeRequest(byte[] encodedData,
                             BasisTranscodeTarget target,
                             BasisPlatformCapabilities platformCapabilities,
                             BasisTranscodeTarget preferredFallback,
                             boolean linearColorSpace,
                             int qualityLevel,
                             int threadCount,
                             int imageIndex,
                             int decodeFlags,
                             boolean strictMode,
                             String transcodePreset,
                             IntFunction<ByteBuffer> allocator) {
        this.encodedData = Objects.requireNonNull(encodedData, "encodedData");
        if (qualityLevel < MIN_QUALITY || qualityLevel > MAX_QUALITY) {
            throw new IllegalArgumentException("qualityLevel must be in [" + MIN_QUALITY
                    + "," + MAX_QUALITY + "]: " + qualityLevel);
        }
        if (threadCount < MIN_THREADS || threadCount > MAX_THREADS) {
            throw new IllegalArgumentException("threadCount must be in [" + MIN_THREADS + ","
                    + MAX_THREADS + "]: " + threadCount);
        }
        if (imageIndex < 0) {
            throw new IllegalArgumentException("imageIndex must be non-negative: " + imageIndex);
        }
        this.target = target;
        Ktx2DecodeFlag.fromMask(decodeFlags);
        this.platformCapabilities = platformCapabilities;
        this.preferredFallback = preferredFallback == null
                ? BasisTranscodeTarget.RGBA8
                : preferredFallback;
        this.linearColorSpace = linearColorSpace;
        this.qualityLevel = qualityLevel;
        this.threadCount = threadCount;
        this.imageIndex = imageIndex;
        this.decodeFlags = decodeFlags;
        this.strictMode = strictMode;
        this.transcodePreset = transcodePreset;
        this.allocator = allocator;
    }

    public byte[] getEncodedData() {
        return encodedData;
    }

    public BasisTranscodeTarget getTarget() {
        return target;
    }

    public BasisPlatformCapabilities getPlatformCapabilities() {
        return platformCapabilities;
    }

    public BasisTranscodeTarget getPreferredFallback() {
        return preferredFallback;
    }

    public boolean isLinearColorSpace() {
        return linearColorSpace;
    }

    public int getQualityLevel() {
        return qualityLevel;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public int getDecodeFlags() {
        return decodeFlags;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public String getTranscodePreset() {
        return transcodePreset;
    }

    public IntFunction<ByteBuffer> getAllocator() {
        return allocator == null ? ByteBuffer::allocate : allocator;
    }

    /**
     * Returns an otherwise identical request targeting another image in the
     * same Basis texture or KTX2 array.
     *
     * @param newImageIndex zero-based image index
     * @return copied request
     */
    public BasisDecodeRequest withImageIndex(int newImageIndex) {
        return new BasisDecodeRequest(
                encodedData,
                target,
                platformCapabilities,
                preferredFallback,
                linearColorSpace,
                qualityLevel,
                threadCount,
                newImageIndex,
                decodeFlags,
                strictMode,
                transcodePreset,
                allocator);
    }

    public static BasisDecodeRequest from(byte[] encodedData) {
        return new Builder(encodedData).build();
    }

    public static Builder builder(byte[] encodedData) {
        return new Builder(encodedData);
    }

    public static final class Builder {
        private final byte[] encodedData;
        private BasisTranscodeTarget target;
        private BasisPlatformCapabilities platformCapabilities;
        private BasisTranscodeTarget preferredFallback = BasisTranscodeTarget.RGBA8;
        private boolean linearColorSpace = true;
        private int qualityLevel = 7;
        private int threadCount = 1;
        private int imageIndex = 0;
        private int decodeFlags = 0;
        private boolean strictMode = false;
        private String transcodePreset = null;
        private IntFunction<ByteBuffer> allocator;

        private Builder(byte[] encodedData) {
            this.encodedData = encodedData;
        }

        public Builder target(BasisTranscodeTarget target) {
            this.target = target;
            return this;
        }

        public Builder platform(BasisPlatformCapabilities platformCapabilities) {
            this.platformCapabilities = platformCapabilities;
            return this;
        }

        public Builder preferredFallback(BasisTranscodeTarget preferredFallback) {
            this.preferredFallback = preferredFallback;
            return this;
        }

        public Builder linearColorSpace(boolean linearColorSpace) {
            this.linearColorSpace = linearColorSpace;
            return this;
        }

        public Builder qualityLevel(int qualityLevel) {
            this.qualityLevel = qualityLevel;
            return this;
        }

        public Builder threadCount(int threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public Builder imageIndex(int imageIndex) {
            this.imageIndex = imageIndex;
            return this;
        }

        public Builder decodeFlags(int decodeFlags) {
            this.decodeFlags = decodeFlags;
            return this;
        }

        public Builder decodeFlags(Ktx2DecodeFlag... decodeFlags) {
            this.decodeFlags = Ktx2DecodeFlag.toMask(decodeFlags);
            return this;
        }

        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        public Builder transcodePreset(String transcodePreset) {
            this.transcodePreset = transcodePreset;
            return this;
        }

        public Builder allocator(IntFunction<ByteBuffer> allocator) {
            this.allocator = allocator;
            return this;
        }

        public BasisDecodeRequest build() {
            return new BasisDecodeRequest(encodedData,
                    target,
                    platformCapabilities,
                    preferredFallback,
                    linearColorSpace,
                    qualityLevel,
                    threadCount,
                    imageIndex,
                    decodeFlags,
                    strictMode,
                    transcodePreset,
                    allocator);
        }
    }
}
