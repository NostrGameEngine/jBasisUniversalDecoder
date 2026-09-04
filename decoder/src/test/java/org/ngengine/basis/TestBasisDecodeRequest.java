package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestBasisDecodeRequest {

    @Test
    public void testDefaultBuilderValues() {
        BasisDecodeRequest request = BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                .target(BasisTranscodeTarget.RGBA8)
                .build();

        assertEquals(1, request.getThreadCount());
        assertEquals(false, request.isStrictMode());
        assertEquals(true, request.isLinearColorSpace());
        assertEquals(7, request.getQualityLevel());
        assertEquals(0, request.getImageIndex());
        assertEquals(0, request.getDecodeFlags());
    }

    @Test
    public void testThreadCountIsValidatedInBuilder() {
        assertThrows(IllegalArgumentException.class,
                () -> BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                        .target(BasisTranscodeTarget.RGBA8)
                        .threadCount(0)
                        .build());

        assertThrows(IllegalArgumentException.class,
                () -> BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                        .target(BasisTranscodeTarget.RGBA8)
                        .threadCount(128)
                        .build());
    }

    @Test
    public void testPresetAndStrictModePassThrough() {
        BasisDecodeRequest request = BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                .target(BasisTranscodeTarget.BC7)
                .threadCount(2)
                .imageIndex(3)
                .decodeFlags(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding)
                .strictMode(true)
                .transcodePreset("test")
                .qualityLevel(9)
                .build();

        assertEquals(2, request.getThreadCount());
        assertEquals(3, request.getImageIndex());
        assertEquals(
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding.getCode(),
                request.getDecodeFlags());
        assertEquals(true, request.isStrictMode());
        assertEquals("test", request.getTranscodePreset());
        assertEquals(9, request.getQualityLevel());
        assertEquals(BasisTranscodeTarget.BC7, request.getTarget());
    }

    @Test
    public void testQualityLevelIsValidatedInBuilder() {
        assertThrows(IllegalArgumentException.class,
                () -> BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                        .target(BasisTranscodeTarget.RGBA8)
                        .qualityLevel(-1)
                        .build());

        assertThrows(IllegalArgumentException.class,
                () -> BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                        .target(BasisTranscodeTarget.RGBA8)
                        .qualityLevel(11)
                        .build());
    }

    @Test
    public void testImageIndexIsValidatedInBuilder() {
        assertThrows(IllegalArgumentException.class,
                () -> BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                        .target(BasisTranscodeTarget.RGBA8)
                        .imageIndex(-1)
                        .build());
    }

    @Test
    public void testDecodeFlagsAreValidatedInBuilder() {
        assertThrows(IllegalArgumentException.class,
                () -> BasisDecodeRequest.builder(new byte[] {0x00, 0x01})
                        .target(BasisTranscodeTarget.RGBA8)
                        .decodeFlags(1 << 29)
                        .build());
    }

    @Test
    public void testTargetIsRequired() {
        assertThrows(NullPointerException.class,
                () -> BasisDecodeRequest.builder(new byte[] {0x00, 0x01}).build());
    }
}
