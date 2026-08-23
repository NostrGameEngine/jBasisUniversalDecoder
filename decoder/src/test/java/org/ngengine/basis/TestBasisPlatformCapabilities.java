package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TestBasisPlatformCapabilities {

    @Test
    public void exposesOrderedTargetsWithoutSharingMutableState() {
        BasisPlatformCapabilities capabilities = BasisPlatformCapabilities.desktopBcn();

        BasisTranscodeTarget[] preferred = capabilities.getPreferredTargets();
        preferred[0] = BasisTranscodeTarget.RGBA8;

        assertArrayEquals(
                new BasisTranscodeTarget[] {
                    BasisTranscodeTarget.BC6H,
                    BasisTranscodeTarget.BC7,
                    BasisTranscodeTarget.BC1,
                    BasisTranscodeTarget.BC3,
                    BasisTranscodeTarget.RGBA8
                },
                capabilities.getPreferredTargets());
    }

    @Test
    public void selectsOnlyImplementedTargets() {
        BasisPlatformCapabilities capabilities = BasisPlatformCapabilities.androidEtc2();

        BasisTranscodeTarget selected = capabilities.selectTarget(
                new BasisTranscodeTarget[] {
                    BasisTranscodeTarget.RGBA8
                },
                BasisTranscodeTarget.RGBA8,
                true);

        assertEquals(BasisTranscodeTarget.RGBA8, selected);
        assertTrue(capabilities.supports(BasisTranscodeTarget.ETC2));
    }

    @Test
    public void selectsEtc2NoAlphaBeforeEtc1WhenImplemented() {
        BasisPlatformCapabilities capabilities = BasisPlatformCapabilities.androidEtc2();

        BasisTranscodeTarget selected = capabilities.selectTarget(
                new BasisTranscodeTarget[] {
                    BasisTranscodeTarget.ETC2_NO_ALPHA,
                    BasisTranscodeTarget.ETC1,
                    BasisTranscodeTarget.RGBA8
                },
                BasisTranscodeTarget.RGBA8,
                false);

        assertEquals(BasisTranscodeTarget.ETC2_NO_ALPHA, selected);
    }

    @Test
    public void selectsAstcForAndroidAstcWhenImplemented() {
        BasisPlatformCapabilities capabilities = BasisPlatformCapabilities.androidAstc();

        BasisTranscodeTarget selected = capabilities.selectTarget(
                new BasisTranscodeTarget[] {
                    BasisTranscodeTarget.ASTC_LDR_5X4,
                    BasisTranscodeTarget.RGBA8
                },
                BasisTranscodeTarget.RGBA8,
                true);

        assertEquals(BasisTranscodeTarget.ASTC_LDR_5X4, selected);
    }

    @Test
    public void selectsBc3ForDesktopS3tcWhenAlphaIsRequired() {
        BasisPlatformCapabilities capabilities = BasisPlatformCapabilities.desktopS3tc();

        BasisTranscodeTarget selected = capabilities.selectTarget(
                new BasisTranscodeTarget[] {
                    BasisTranscodeTarget.BC3,
                    BasisTranscodeTarget.BC1,
                    BasisTranscodeTarget.RGBA8
                },
                BasisTranscodeTarget.RGBA8,
                true);

        assertEquals(BasisTranscodeTarget.BC3, selected);
        assertTrue(capabilities.supports(BasisTranscodeTarget.BC3));
    }

    @Test
    public void mapsAstcTargetByBlockSize() {
        assertEquals(BasisTranscodeTarget.ASTC_LDR_4X4,
                BasisTranscodeTarget.astcLdrForBlockSize(4, 4));
        assertEquals(BasisTranscodeTarget.ASTC_LDR_12X12,
                BasisTranscodeTarget.astcLdrForBlockSize(12, 12));
        assertEquals(5, BasisTranscodeTarget.ASTC_LDR_5X4.getBlockWidth());
        assertEquals(4, BasisTranscodeTarget.ASTC_LDR_5X4.getBlockHeight());
    }
}
