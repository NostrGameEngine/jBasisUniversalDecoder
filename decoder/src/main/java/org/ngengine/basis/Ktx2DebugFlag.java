package org.ngengine.basis;

import java.util.EnumSet;
import java.util.Set;

/**
 * Java parity mapping for {@code basist::debug_flags_t} from basisu_transcoder.h.
 */
public enum Ktx2DebugFlag {
    cDebugFlagVisCRs(1),
    cDebugFlagVisBC1Sels(2),
    cDebugFlagVisBC1Endpoints(4);

    private final int code;

    Ktx2DebugFlag(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static int toMask(Set<Ktx2DebugFlag> flags) {
        int mask = 0;
        for (Ktx2DebugFlag flag : flags) {
            mask |= flag.code;
        }
        return mask;
    }

    public static int toMask(Ktx2DebugFlag... flags) {
        int mask = 0;
        for (Ktx2DebugFlag flag : flags) {
            if (flag != null) {
                mask |= flag.code;
            }
        }
        return mask;
    }

    public static EnumSet<Ktx2DebugFlag> fromMask(int mask) {
        int recognized = 0;
        EnumSet<Ktx2DebugFlag> result = EnumSet.noneOf(Ktx2DebugFlag.class);

        for (Ktx2DebugFlag flag : values()) {
            if ((mask & flag.code) == flag.code && flag.code != 0) {
                result.add(flag);
                recognized |= flag.code;
            }
        }

        if ((mask & ~recognized) != 0) {
            throw new IllegalArgumentException("Unknown debug flag bits: " + mask);
        }

        return result;
    }

    public static boolean isSet(int mask, Ktx2DebugFlag flag) {
        return (mask & flag.code) == flag.code;
    }
}
