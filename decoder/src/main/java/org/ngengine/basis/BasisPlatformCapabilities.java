package org.ngengine.basis;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Ordered texture targets available on a platform.
 */
public final class BasisPlatformCapabilities {
    private final BasisTranscodeTarget[] preferredTargets;
    private final EnumSet<BasisTranscodeTarget> supportedTargets;

    private BasisPlatformCapabilities(BasisTranscodeTarget[] preferredTargets) {
        if (preferredTargets.length == 0) {
            throw new IllegalArgumentException("preferredTargets must not be empty");
        }
        this.preferredTargets = Arrays.copyOf(preferredTargets, preferredTargets.length);
        this.supportedTargets = EnumSet.noneOf(BasisTranscodeTarget.class);
        for (BasisTranscodeTarget target : this.preferredTargets) {
            this.supportedTargets.add(Objects.requireNonNull(target, "target"));
        }
    }

    public static BasisPlatformCapabilities rgba8Only() {
        return of(BasisTranscodeTarget.RGBA8);
    }

    public static BasisPlatformCapabilities teavmRgba8() {
        return rgba8Only();
    }

    public static BasisPlatformCapabilities androidEtc2() {
        return of(
                BasisTranscodeTarget.ETC2,
                BasisTranscodeTarget.ETC2_NO_ALPHA,
                BasisTranscodeTarget.ETC1,
                BasisTranscodeTarget.RGBA8);
    }

    public static BasisPlatformCapabilities androidAstc() {
        return of(
                BasisTranscodeTarget.ASTC_HDR_4X4,
                BasisTranscodeTarget.ASTC_HDR_6X6,
                BasisTranscodeTarget.ASTC_LDR_4X4,
                BasisTranscodeTarget.ASTC_LDR_5X4,
                BasisTranscodeTarget.ASTC_LDR_5X5,
                BasisTranscodeTarget.ASTC_LDR_6X5,
                BasisTranscodeTarget.ASTC_LDR_6X6,
                BasisTranscodeTarget.ASTC_LDR_8X5,
                BasisTranscodeTarget.ASTC_LDR_8X6,
                BasisTranscodeTarget.ASTC_LDR_8X8,
                BasisTranscodeTarget.ASTC_LDR_10X5,
                BasisTranscodeTarget.ASTC_LDR_10X6,
                BasisTranscodeTarget.ASTC_LDR_10X8,
                BasisTranscodeTarget.ASTC_LDR_10X10,
                BasisTranscodeTarget.ASTC_LDR_12X10,
                BasisTranscodeTarget.ASTC_LDR_12X12,
                BasisTranscodeTarget.ETC2,
                BasisTranscodeTarget.ETC2_NO_ALPHA,
                BasisTranscodeTarget.ETC1,
                BasisTranscodeTarget.RGBA8);
    }

    public static BasisPlatformCapabilities androidEtc1() {
        return of(BasisTranscodeTarget.ETC1, BasisTranscodeTarget.RGBA8);
    }

    public static BasisPlatformCapabilities desktopBptc() {
        return of(
                BasisTranscodeTarget.BC6H,
                BasisTranscodeTarget.BC7,
                BasisTranscodeTarget.BC1,
                BasisTranscodeTarget.BC3,
                BasisTranscodeTarget.RGBA8);
    }

    public static BasisPlatformCapabilities desktopBcn() {
        return desktopBptc();
    }

    public static BasisPlatformCapabilities desktopS3tc() {
        return of(BasisTranscodeTarget.BC1, BasisTranscodeTarget.BC3, BasisTranscodeTarget.RGBA8);
    }

    public static BasisPlatformCapabilities of(BasisTranscodeTarget firstTarget,
            BasisTranscodeTarget... remainingTargets) {
        Objects.requireNonNull(firstTarget, "firstTarget");
        BasisTranscodeTarget[] targets = new BasisTranscodeTarget[remainingTargets.length + 1];
        targets[0] = firstTarget;
        for (int i = 0; i < remainingTargets.length; i++) {
            targets[i + 1] = Objects.requireNonNull(remainingTargets[i], "target");
        }
        return new BasisPlatformCapabilities(targets);
    }

    public boolean supports(BasisTranscodeTarget target) {
        return target != null && supportedTargets.contains(target);
    }

    public BasisTranscodeTarget[] getPreferredTargets() {
        return Arrays.copyOf(preferredTargets, preferredTargets.length);
    }

    BasisTranscodeTarget selectTarget(BasisTranscodeTarget[] implementedTargets,
            BasisTranscodeTarget fallback,
            boolean alphaRequired) {
        Objects.requireNonNull(implementedTargets, "implementedTargets");
        EnumSet<BasisTranscodeTarget> implemented = EnumSet.noneOf(BasisTranscodeTarget.class);
        for (BasisTranscodeTarget target : implementedTargets) {
            implemented.add(Objects.requireNonNull(target, "implemented target"));
        }
        for (BasisTranscodeTarget preferred : preferredTargets) {
            if (implemented.contains(preferred) && isAllowed(preferred, alphaRequired)) {
                return preferred;
            }
        }
        if (fallback != null && supportedTargets.contains(fallback)
                && implemented.contains(fallback)
                && isAllowed(fallback, alphaRequired)) {
            return fallback;
        }
        throw new BasisDecodeException(
                "No implemented Basis transcode target is supported by the platform profile");
    }

    private static boolean isAllowed(BasisTranscodeTarget target, boolean alphaRequired) {
        return !alphaRequired || target.supportsAlpha();
    }
}
