package org.ngengine.basis;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Java parity representation of {@code basist::basis_header_flags} as bitflags.
 */
public enum Ktx2BasisHeaderFlag {
    cBASISHeaderFlagETC1S(1, "Always set for ETC1S files."),
    cBASISHeaderFlagYFlipped(2, "Source image was Y-flipped before encoding."),
    cBASISHeaderFlagHasAlphaSlices(4, "At least one slice includes alpha data."),
    cBASISHeaderFlagUsesGlobalCodebook(8, "Uses a global codebook."),
    cBASISHeaderFlagSRGB(16, "Texture data is perceptual/sRGB."),
    ;

    private final int mask;
    private final String description;

    Ktx2BasisHeaderFlag(int mask, String description) {
        this.mask = mask;
        this.description = description;
    }

    public int getMask() {
        return mask;
    }

    public String getDescription() {
        return description;
    }

    public static EnumSet<Ktx2BasisHeaderFlag> fromMask(int mask) {
        EnumSet<Ktx2BasisHeaderFlag> result = EnumSet.noneOf(Ktx2BasisHeaderFlag.class);
        for (Ktx2BasisHeaderFlag flag : values()) {
            if ((mask & flag.mask) != 0) {
                result.add(flag);
            }
        }
        return result;
    }

    public static int toMask(EnumSet<Ktx2BasisHeaderFlag> flags) {
        Objects.requireNonNull(flags, "flags");
        return flags.stream().mapToInt(Ktx2BasisHeaderFlag::getMask).sum();
    }

    public static int toMask(Ktx2BasisHeaderFlag first, Ktx2BasisHeaderFlag... rest) {
        Objects.requireNonNull(first, "first");
        EnumSet<Ktx2BasisHeaderFlag> flags = EnumSet.of(first);
        for (Ktx2BasisHeaderFlag flag : rest) {
            Objects.requireNonNull(flag, "rest");
            flags.add(flag);
        }
        return toMask(flags);
    }
}