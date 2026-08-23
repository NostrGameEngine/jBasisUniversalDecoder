package org.ngengine.basis;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Java parity representation of {@code basist::basis_slice_desc_flags} as bitflags.
 */
public enum Ktx2BasisSliceDescFlag {
    cSliceDescFlagsHasAlpha(1, "Slice contains alpha data."),
    cSliceDescFlagsFrameIsIFrame(2, "Video frame is an I-frame."),
    ;

    private final int mask;
    private final String description;

    Ktx2BasisSliceDescFlag(int mask, String description) {
        this.mask = mask;
        this.description = description;
    }

    public int getMask() {
        return mask;
    }

    public String getDescription() {
        return description;
    }

    public static EnumSet<Ktx2BasisSliceDescFlag> fromMask(int mask) {
        EnumSet<Ktx2BasisSliceDescFlag> result = EnumSet.noneOf(Ktx2BasisSliceDescFlag.class);
        for (Ktx2BasisSliceDescFlag flag : values()) {
            if ((mask & flag.mask) != 0) {
                result.add(flag);
            }
        }
        return result;
    }

    public static int toMask(EnumSet<Ktx2BasisSliceDescFlag> flags) {
        Objects.requireNonNull(flags, "flags");
        return flags.stream().mapToInt(Ktx2BasisSliceDescFlag::getMask).sum();
    }

    public static int toMask(Ktx2BasisSliceDescFlag first, Ktx2BasisSliceDescFlag... rest) {
        Objects.requireNonNull(first, "first");
        EnumSet<Ktx2BasisSliceDescFlag> flags = EnumSet.of(first);
        for (Ktx2BasisSliceDescFlag flag : rest) {
            Objects.requireNonNull(flag, "rest");
            flags.add(flag);
        }
        return toMask(flags);
    }
}