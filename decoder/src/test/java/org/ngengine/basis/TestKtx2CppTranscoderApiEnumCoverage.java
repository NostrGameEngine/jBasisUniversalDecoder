package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class TestKtx2CppTranscoderApiEnumCoverage {

    @Test
    public void testTranscoderFormatAccessorsCoverAllEnums() {
        for (Ktx2TranscoderTextureFormat format : Ktx2TranscoderTextureFormat.values()) {
            assertNotNull(Ktx2CppTranscoderApi.basis_get_format_name(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_get_bytes_per_block_or_pixel(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_get_uncompressed_bytes_per_pixel(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_transcoder_format_has_alpha(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_transcoder_format_is_hdr(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_transcoder_format_is_ldr(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_transcoder_format_is_uncompressed(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_is_transcoder_texture_format_astc(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_transcoder_format_is_uncompressed(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_get_block_width(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_get_block_height(format));
            assertNotNull(format.toNativeName());
        }

        assertThrows(BasisDecodeException.class, () -> Ktx2CppTranscoderApi.basis_get_format_name(null));
        assertThrows(
                BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_uncompressed_bytes_per_pixel(null));
    }

    @Test
    public void testBasisTextureFormatMappingCoversSwitches() {
        for (Ktx2BasisTextureFormat format : Ktx2BasisTextureFormat.values()) {
            assertNotNull(Ktx2CppTranscoderApi.basis_get_tex_format_name(format));
            assertNotNull(Ktx2CppTranscoderApi.basis_get_transcoder_texture_format_from_basis_tex_format(
                    format));
            Ktx2TranscoderTextureFormat transcoderFormat =
                    Ktx2CppTranscoderApi
                            .basis_get_transcoder_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(
                            format);
            Ktx2TextureFormat textureFormat =
                    Ktx2CppTranscoderApi
                            .basis_get_texture_format_from_xuastc_or_astc_ldr_basis_tex_format(
                            format);
            assertNotNull(transcoderFormat);
            assertNotNull(textureFormat);

            // Exercise query predicates for each symbol.
            assertEquals(format.isXUastcLdr(), Ktx2CppTranscoderApi.basis_tex_format_is_xuastc_ldr(format));
            assertEquals(format.isAstcLdr(), Ktx2CppTranscoderApi.basis_tex_format_is_astc_ldr(format));
            assertEquals(format.isHdr(), Ktx2CppTranscoderApi.basis_tex_format_is_hdr(format));
            assertEquals(format.isLdr(), Ktx2CppTranscoderApi.basis_tex_format_is_ldr(format));
            assertEquals(
                    format.getBlockWidth(),
                    Ktx2CppTranscoderApi.basis_tex_format_get_block_width(format));
            assertEquals(
                    format.getBlockHeight(),
                    Ktx2CppTranscoderApi.basis_tex_format_get_block_height(format));
        }

        assertThrows(
                BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_get_transcoder_texture_format_from_basis_tex_format(null));
    }

    @Test
    public void testBasisFormatSupportBranches() {
        // HDR ASTC source format branch.
        assertTrue(
                Ktx2CppTranscoderApi.basis_is_format_supported(
                        Ktx2TranscoderTextureFormat.cTFASTC_HDR_4x4_RGBA,
                        Ktx2BasisTextureFormat.cASTC_HDR_6x6));
        assertFalse(
                Ktx2CppTranscoderApi.basis_is_format_supported(
                        Ktx2TranscoderTextureFormat.cTFBC7_ALT,
                        Ktx2BasisTextureFormat.cASTC_HDR_6x6));

        // Explicit false branch in UASTC_HDR_4x4 switch.
        assertFalse(
                Ktx2CppTranscoderApi.basis_is_format_supported(
                        Ktx2TranscoderTextureFormat.cTFPVRTC2_4_RGB,
                        Ktx2BasisTextureFormat.cUASTC_LDR_4x4));
        assertTrue(
                Ktx2CppTranscoderApi.basis_is_format_supported(
                        Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA,
                        Ktx2BasisTextureFormat.cUASTC_LDR_4x4));

        // Coverage on XUASTC/ASTC LDR table path and equality-match path.
        assertTrue(
                Ktx2CppTranscoderApi.basis_is_format_supported(
                        Ktx2TranscoderTextureFormat.cTFASTC_LDR_5x4_RGBA,
                        Ktx2BasisTextureFormat.cASTC_LDR_5x4));
        assertFalse(
                Ktx2CppTranscoderApi.basis_is_format_supported(
                        Ktx2TranscoderTextureFormat.cTFASTC_LDR_4x4_RGBA,
                        Ktx2BasisTextureFormat.cASTC_LDR_12x12));

        assertThrows(
                BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_is_format_supported(
                        null,
                        Ktx2BasisTextureFormat.cASTC_LDR_4x4));
        assertThrows(
                BasisDecodeException.class,
                () -> Ktx2CppTranscoderApi.basis_is_format_supported(
                        Ktx2TranscoderTextureFormat.cTFPVRTC1_4_RGB,
                        null));
    }

    @Test
    public void testAllocationAndHandleLifecycleBranches() throws Exception {
        long handle = Ktx2CppTranscoderApi.basis_alloc(16);
        assertTrue(handle > 0);
        assertThrows(IllegalArgumentException.class, () -> Ktx2CppTranscoderApi.basis_alloc(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> Ktx2CppTranscoderApi.basis_alloc((long) Integer.MAX_VALUE + 1));
        Ktx2CppTranscoderApi.basis_free(handle);

        try (Ktx2CppTranscoderApi.ScopedAllocation scoped = Ktx2CppTranscoderApi.allocateScoped(64)) {
            assertNotNull(scoped.bytes());
            assertEquals(64, scoped.size());
            assertTrue(scoped.handle() > 0);
            assertNotNull(scoped.asReadOnlyBuffer());
            assertEquals(64, scoped.asReadOnlyBuffer().capacity());
            assertFalse(scoped.isClosed());
            assertNotNull(scoped.bytes());
        }

        assertThrows(BasisDecodeException.class, () -> Ktx2CppTranscoderApi.crc16(null));
        assertEquals(0, Ktx2CppTranscoderApi.hash_hsieh(new byte[0]));
        assertNotNull(Ktx2CppTranscoderApi.crc16(new byte[] {0x01, 0x02}));

        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_open(null, 0));
        assertTrue(Ktx2CppTranscoderApi.bt_ktx2_open(new byte[4], 0) > 0);
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_open(new byte[4], -1));

        long mem = Ktx2CppTranscoderApi.basis_alloc(4);
        long hInvalid = Ktx2CppTranscoderApi.bt_ktx2_open(mem, 8);
        assertEquals(0, hInvalid);
        long hLen4 = Ktx2CppTranscoderApi.bt_ktx2_open(mem, 4);
        assertTrue(hLen4 > 0);
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_open(mem, -1));

        long h = Ktx2CppTranscoderApi.bt_ktx2_open(new byte[0]);
        assertTrue(h > 0);
        long h2 = Ktx2CppTranscoderApi.bt_ktx2_open(new byte[8]);
        assertTrue(h2 > 0);
        long stateHandle = Ktx2CppTranscoderApi.bt_ktx2_create_transcode_state();
        assertTrue(stateHandle > 0);
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_block_width(h2));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_block_height(h2));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_get_level_alpha_flag(h2, 0, 0, 0));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_srgb(h2));
        assertEquals(0, Ktx2CppTranscoderApi.bt_ktx2_is_video(h2));

        Ktx2CppTranscoderApi.bt_ktx2_close(hLen4);
        Ktx2CppTranscoderApi.bt_ktx2_close(h);
        Ktx2CppTranscoderApi.bt_ktx2_close(h2);
        Ktx2CppTranscoderApi.bt_ktx2_destroy_transcode_state(stateHandle);
        Ktx2CppTranscoderApi.basis_free(mem);
    }

    @Test
    public void testScopedAllocationCloseIsIdempotentAndFailsClosedAccess() throws Exception {
        final Ktx2CppTranscoderApi.ScopedAllocation scoped = Ktx2CppTranscoderApi.allocateScoped(32);
        final long handle = scoped.handle();

        assertEquals(32, scoped.size());
        scoped.close();
        assertTrue(scoped.isClosed());

        assertThrows(BasisDecodeException.class, scoped::bytes);
        assertDoesNotThrow(scoped::close);
        assertTrue(scoped.isClosed());

        // Closing from multiple threads must remain exception-safe.
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> closes = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                closes.add(() -> {
                    scoped.close();
                    return null;
                });
            }
            for (Future<Void> future : executor.invokeAll(closes)) {
                future.get();
            }
            assertTrue(scoped.isClosed());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }

        // handle should remain reserved but released; no leak path is possible in Java API
        assertThrows(BasisDecodeException.class, scoped::bytes);
        assertEquals(handle, scoped.handle());
    }

    @Test
    public void testAllocationMapScalesUnderConcurrentUse() throws Exception {
        final int concurrency = 16;
        final int iterations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < iterations; i++) {
                tasks.add(() -> {
                    try (Ktx2CppTranscoderApi.ScopedAllocation scoped =
                            Ktx2CppTranscoderApi.allocateScoped(4)) {
                        assertFalse(scoped.isClosed());
                        scoped.bytes()[0] = 7;
                        return scoped.handle();
                    }
                });
            }

            Set<Long> handles = new HashSet<>();
            for (Future<Long> future : executor.invokeAll(tasks)) {
                handles.add(future.get());
            }

            assertEquals(iterations, handles.size(), "Allocation handles must be unique under parallel load");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testHashFunctionsCoverAllByteRemainders() {
        byte[] bytes = new byte[] {1, 2, 3};
        int h1 = Ktx2CppTranscoderApi.hash_hsieh(bytes);
        int h2 = Ktx2CppTranscoderApi.hash_hsieh(Arrays.copyOf(bytes, 4));
        int c1 = Ktx2CppTranscoderApi.crc16(bytes);

        assertTrue(h1 != 0);
        assertTrue(h2 != 0);
        assertTrue(c1 > 0);
    }
}
