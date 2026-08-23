package org.ngengine.basis;

import java.util.EnumSet;
import java.util.Set;

/**
 * Java parity mapping for {@code basist::basisu_decode_flags} from basisu_transcoder.h.
 */
public enum Ktx2DecodeFlag {
    cDecodeFlagsPVRTCDecodeToNextPow2(2),
    cDecodeFlagsTranscodeAlphaDataToOpaqueFormats(4),
    cDecodeFlagsBC1ForbidThreeColorBlocks(8),
    cDecodeFlagsOutputHasAlphaIndices(16),
    cDecodeFlagsHighQuality(32),
    cDecodeFlagsNoETC1SChromaFiltering(64),
    cDecodeFlagsNoDeblockFiltering(128),
    cDecodeFlagsStrongerDeblockFiltering(256),
    cDecodeFlagsForceDeblockFiltering(512),
    cDecodeFlagXUASTCLDRDisableFastBC7Transcoding(1024);

    private final int code;

    Ktx2DecodeFlag(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static int toMask(Set<Ktx2DecodeFlag> flags) {
        int mask = 0;
        for (Ktx2DecodeFlag flag : flags) {
            mask |= flag.code;
        }
        return mask;
    }

    public static int toMask(Ktx2DecodeFlag... flags) {
        int mask = 0;
        for (Ktx2DecodeFlag flag : flags) {
            if (flag != null) {
                mask |= flag.code;
            }
        }
        return mask;
    }

    public static EnumSet<Ktx2DecodeFlag> fromMask(int mask) {
        int recognized = 0;
        EnumSet<Ktx2DecodeFlag> result = EnumSet.noneOf(Ktx2DecodeFlag.class);

        for (Ktx2DecodeFlag flag : values()) {
            if ((mask & flag.code) == flag.code && flag.code != 0) {
                result.add(flag);
                recognized |= flag.code;
            }
        }

        if ((mask & ~recognized) != 0) {
            throw new IllegalArgumentException("Unknown decode flag bits: " + mask);
        }

        return result;
    }

    public static boolean isSet(int mask, Ktx2DecodeFlag flag) {
        return (mask & flag.code) == flag.code;
    }
}
