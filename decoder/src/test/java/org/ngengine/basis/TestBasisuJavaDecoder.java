package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

public class TestBasisuJavaDecoder {

    private static final int[] ETC1_RAW_SELECTOR_TO_INDEX = {2, 3, 1, 0};
    private static final int[][] ETC1_INTENSITY_TABLES = {
        {-8, -2, 2, 8},
        {-17, -5, 5, 17},
        {-29, -9, 9, 29},
        {-42, -13, 13, 42},
        {-60, -18, 18, 60},
        {-80, -24, 24, 80},
        {-106, -33, 33, 106},
        {-183, -47, 47, 183}
    };
    private static final int[][] EAC_MODIFIER_TABLE = {
        {-3, -6, -9, -15, 2, 5, 8, 14},
        {-3, -7, -10, -13, 2, 6, 9, 12},
        {-2, -5, -8, -13, 1, 4, 7, 12},
        {-2, -4, -6, -13, 1, 3, 5, 12},
        {-3, -6, -8, -12, 2, 5, 7, 11},
        {-3, -7, -9, -11, 2, 6, 8, 10},
        {-4, -7, -8, -11, 3, 6, 7, 10},
        {-3, -5, -8, -11, 2, 4, 7, 10},
        {-2, -6, -8, -10, 1, 5, 7, 9},
        {-2, -5, -8, -10, 1, 4, 7, 9},
        {-2, -4, -8, -10, 1, 3, 7, 9},
        {-2, -5, -7, -10, 1, 4, 6, 9},
        {-3, -4, -7, -10, 2, 3, 6, 9},
        {-1, -2, -3, -10, 0, 1, 2, 9},
        {-4, -6, -8, -9, 3, 5, 7, 8},
        {-3, -5, -7, -9, 2, 4, 6, 8}
    };
    private static final int[] ETC1S_MIP_RGBA_SIZES = {
        1_572_864, 393_216, 98_304, 24_576, 6_144, 1_536, 384, 96, 24, 4
    };
    private static final int[] ETC1S_MIP_ETC2_SIZES = {
        393_216, 98_304, 24_576, 6_144, 1_536, 384, 96, 32, 16, 16
    };
    private static final int[] ETC1S_MIP_BC1_SIZES = {
        196_608, 49_152, 12_288, 3_072, 768, 192, 48, 16, 8, 8
    };
    private static final int[] ETC1S_CUBEMAP_MIP_ETC2_SIZES = {
        262_144, 65_536, 16_384, 4_096, 1_024, 256, 64, 16, 16, 16
    };
    private static final int[] ETC1S_CUBEMAP_MIP_BC1_SIZES = {
        131_072, 32_768, 8_192, 2_048, 512, 128, 32, 8, 8, 8
    };
    private static final int[] ETC1S_CUBEMAP_ARRAY_BC1_SIZES = {
        2_048
    };
    private static final int[] ETC1S_ARRAY_RGBA_SIZES = {
        1_572_864
    };
    private static final int[] ETC1S_ARRAY_BC1_SIZES = {
        196_608
    };
    private static final int[] XUASTC_6X6_MIP_ASTC_SIZES = {
        176_128, 44_032, 11_264, 2_816, 768, 192, 64, 16, 16, 16
    };
    private static final int[] XUASTC_6X6_CUBEMAP_MIP_ASTC_SIZES = {
        118_336, 29_584, 7_744, 1_936, 576, 144, 64, 16, 16, 16
    };

    @Test
    public void testDecodeUncompressedKtx2Fixture() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(2, result.getWidth());
        assertEquals(2, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(BasisColorSpace.Linear, result.getColorSpace());
        assertEquals(4, result.getPixelData().remaining());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(4, result.getMipMapSizes()[0]);

        byte[] raw = bytesToArray(result.getPixelData());
        assertEquals((byte) 9, raw[0]);
        assertEquals((byte) 8, raw[1]);
        assertEquals((byte) 7, raw[2]);
        assertEquals((byte) 6, raw[3]);
    }

    @Test
    public void testDecodeDeflateSupercompressedKtx2Level() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = deflateUncompressedKtx2Fixture(loadFixture("fixtures/uncompressed-rgba8-ktx2.bin"));

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(4, result.getPixelData().remaining());
        assertArrayEquals(new byte[]{9, 8, 7, 6}, bytesToArray(result.getPixelData()));
    }

    @Test
    public void testRejectsSupercompressedPayload() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/supercompressed-unsupported-ktx2.bin");
        BasisDecodeException ex = assertThrows(BasisDecodeException.class,
                () -> decoder.decode(BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
        assertTrue(ex.getMessage().contains("Unsupported KTX2 supercompression"));
    }

    @Test
    public void testRejectsUnsupportedVkFormat() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        patchVkFormat(payload, 12345);

        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testDecodesEtc1sKtx2AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());

        byte[] expected = loadFixture("fixtures/native_gold/kodim23.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testDecodesEtc1sKtx2AlphaSliceAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_alpha.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(1_572_864, result.getMipMapSizes()[0]);

        byte[] expected = loadFixture("fixtures/native_gold/kodim23_alpha.dat");
        byte[] decoded = toArray(result.getPixelData());
        assertArrayEquals(expected, decoded);
        assertTrue(hasTransparentPixels(decoded));
    }

    @Test
    public void testDecodesEtc1sKtx2MipChainAgainstNativeGold() {
        assertEtc1sMipChain(
                BasisTranscodeTarget.RGBA8,
                BasisImageFormat.RGBA8,
                ETC1S_MIP_RGBA_SIZES,
                "fixtures/native_gold/kodim23_mip.dat");
    }

    @Test
    public void testTranscodesEtc1sKtx2MipChainToEtc2AgainstNativeGold() {
        assertEtc1sMipChain(
                BasisTranscodeTarget.ETC2,
                BasisImageFormat.ETC2,
                ETC1S_MIP_ETC2_SIZES,
                "fixtures/native_gold/kodim23_mip_etc2.dat");
    }

    @Test
    public void testTranscodesEtc1sKtx2MipChainToBc1AgainstNativeGold() {
        assertEtc1sMipChain(
                BasisTranscodeTarget.BC1,
                BasisImageFormat.BC1,
                ETC1S_MIP_BC1_SIZES,
                "fixtures/native_gold/kodim23_mip_bc1.dat");
    }

    @Test
    public void testDecodesEtc1sKtx2ArrayAgainstNativeGold() {
        assertEtc1sArray(
                BasisTranscodeTarget.RGBA8,
                BasisImageFormat.RGBA8,
                ETC1S_ARRAY_RGBA_SIZES,
                "fixtures/native_gold/kodim_array_image1.dat");
    }

    @Test
    public void testTranscodesEtc1sKtx2ArrayToBc1AgainstNativeGold() {
        assertEtc1sArray(
                BasisTranscodeTarget.BC1,
                BasisImageFormat.BC1,
                ETC1S_ARRAY_BC1_SIZES,
                "fixtures/native_gold/kodim_array_image1_bc1.dat");
    }

    @Test
    public void testDecodesEtc1sKtx2ArrayMipChainAgainstNativeGold() {
        assertEtc1sArrayMipChain(
                BasisTranscodeTarget.RGBA8,
                BasisImageFormat.RGBA8,
                ETC1S_MIP_RGBA_SIZES,
                "fixtures/native_gold/kodim_array_mip_image1.dat");
    }

    @Test
    public void testTranscodesEtc1sKtx2ArrayMipChainToEtc2AgainstNativeGold() {
        assertEtc1sArrayMipChain(
                BasisTranscodeTarget.ETC2,
                BasisImageFormat.ETC2,
                ETC1S_MIP_ETC2_SIZES,
                "fixtures/native_gold/kodim_array_mip_image1_etc2.dat");
    }

    @Test
    public void testTranscodesEtc1sKtx2ArrayMipChainToBc1AgainstNativeGold() {
        assertEtc1sArrayMipChain(
                BasisTranscodeTarget.BC1,
                BasisImageFormat.BC1,
                ETC1S_MIP_BC1_SIZES,
                "fixtures/native_gold/kodim_array_mip_image1_bc1.dat");
    }

    @Test
    public void testRejectsOutOfRangeKtx2ImageIndex() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_array.ktx2");

        BasisDecodeException exception = assertThrows(BasisDecodeException.class,
                () -> decoder.decode(BasisDecodeRequest.builder(payload)
                        .target(BasisTranscodeTarget.RGBA8)
                        .imageIndex(2)
                        .allocator(ByteBuffer::allocate)
                        .build()));

        assertTrue(exception.getMessage().contains("KTX2 image index out of range"));
    }

    @Test
    public void testDecodesEtc1sKtx2CubemapFaceFiveAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_cubemap.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .imageIndex(5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(6, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(new int[] {262_144}, result.getMipMapSizes());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_cubemap_face5.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2CubemapFaceFiveToBc1AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_cubemap.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .imageIndex(5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(6, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(new int[] {32_768}, result.getMipMapSizes());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_cubemap_face5_bc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2CubemapArrayLayerOneFaceFiveToBc1AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_cubemap_array.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .imageIndex(11)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(64, result.getWidth());
        assertEquals(64, result.getHeight());
        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(12, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(ETC1S_CUBEMAP_ARRAY_BC1_SIZES, result.getMipMapSizes());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_cubemap_array_image11_bc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2CubemapMipFaceFiveToEtc2AgainstNativeGold() {
        assertEtc1sCubemapMipChain(
                BasisTranscodeTarget.ETC2,
                BasisImageFormat.ETC2,
                ETC1S_CUBEMAP_MIP_ETC2_SIZES,
                "fixtures/native_gold/kodim_cubemap_mip_face5_etc2.dat");
    }

    @Test
    public void testTranscodesEtc1sKtx2CubemapMipFaceFiveToBc1AgainstNativeGold() {
        assertEtc1sCubemapMipChain(
                BasisTranscodeTarget.BC1,
                BasisImageFormat.BC1,
                ETC1S_CUBEMAP_MIP_BC1_SIZES,
                "fixtures/native_gold/kodim_cubemap_mip_face5_bc1.dat");
    }

    @Test
    public void testExplicitBc1TargetTranscodesEtc1s() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(196_608, result.getPixelData().remaining());
        byte[] expected = loadFixture("fixtures/native_gold/kodim23_bc1.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testExplicitEtc2TargetTranscodesEtc1s() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(393_216, result.getPixelData().remaining());

        byte[] expectedBlocks = loadFixture("fixtures/native_gold/kodim23_etc2.dat");
        assertArrayEquals(expectedBlocks, toArray(result.getPixelData()));
        byte[] rgba = decodeEtc2RgbaToRgba(
                toArray(result.getPixelData()),
                result.getWidth(),
                result.getHeight());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23.dat"), rgba);
    }

    @Test
    public void testTranscodesEtc1sKtx2ToEtc2NoAlphaBlocksAgainstNativeGoldRgba() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2_NO_ALPHA)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2_NO_ALPHA, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        byte[] expectedBlocks = loadFixture("fixtures/native_gold/kodim23_etc1.dat");
        assertArrayEquals(expectedBlocks, toArray(result.getPixelData()));
        byte[] rgba = decodeEtc1ToRgba(toArray(result.getPixelData()), result.getWidth(), result.getHeight());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23.dat"), rgba);
    }

    @Test
    public void testTranscodesEtc1sKtx2ToEtc2BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);

        byte[] expectedBlocks = loadFixture("fixtures/native_gold/kodim23_etc2.dat");
        assertArrayEquals(expectedBlocks, toArray(result.getPixelData()));
        byte[] rgba = decodeEtc2RgbaToRgba(
                toArray(result.getPixelData()),
                result.getWidth(),
                result.getHeight());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23.dat"), rgba);
    }

    @Test
    public void testTranscodesEtc1sKtx2AlphaSliceToEtc2BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_alpha.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);

        byte[] blocks = toArray(result.getPixelData());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_alpha_etc2.dat"), blocks);
        byte[] rgba = decodeEtc2RgbaToRgba(blocks, result.getWidth(), result.getHeight());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_alpha.dat"), rgba);
        assertTrue(hasTransparentPixels(rgba));
    }

    @Test
    public void testTranscodesEtc1sKtx2ToBc3BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC3)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC3, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_bc3.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2ToBc7BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC7)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC7, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_bc7.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2AlphaSliceToBc3BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_alpha.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC3)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC3, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_alpha_bc3.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2AlphaSliceToBc7BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_alpha.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC7)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC7, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_alpha_bc7.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2ToBc4BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC4)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC4, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_bc4.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2ToEtc2EacR11Blocks() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2_EAC_R11)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2_EAC_R11, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);
        assertEquals(196_608, result.getPixelData().remaining());
    }

    @Test
    public void testTranscodesEtc1sKtx2AlphaSliceToEtc2EacRg11Blocks() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_alpha.ktx2");

        final BasisDecodeResult r11Result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2_EAC_R11)
                .allocator(ByteBuffer::allocate)
                .build());
        final BasisDecodeResult rg11Result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2_EAC_RG11)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2_EAC_RG11, rg11Result.getImageFormat());
        assertEquals(1, rg11Result.getMipMapSizes().length);
        assertEquals(393_216, rg11Result.getMipMapSizes()[0]);
        byte[] r11 = toArray(r11Result.getPixelData());
        byte[] rg11 = toArray(rg11Result.getPixelData());
        assertEacRg11ChannelZero(r11, rg11);
        assertTrue(hasNonOpaqueEacChannelOne(rg11));
    }

    @Test
    public void testTranscodesEtc1sKtx2AlphaSliceToBc5BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_alpha.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC5, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_alpha_bc5.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testExplicitBc3TargetTranscodesEtc1sKtx2Alpha() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_alpha.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC3)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC3, result.getImageFormat());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23_alpha_bc3.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2ToEtc1BlocksAgainstNativeGoldRgba() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC1, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        byte[] expectedBlocks = loadFixture("fixtures/native_gold/kodim23_etc1.dat");
        assertArrayEquals(expectedBlocks, toArray(result.getPixelData()));
        byte[] rgba = decodeEtc1ToRgba(toArray(result.getPixelData()), result.getWidth(), result.getHeight());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim23.dat"), rgba);
    }

    @Test
    public void testTranscodesEtc1sKtx2ToBc1BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        byte[] expectedBlocks = loadFixture("fixtures/native_gold/kodim23_bc1.dat");
        assertArrayEquals(expectedBlocks, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sKtx2ToPackedPixelTargets() {
        assertPackedTargetMatchesRgbaPacking(
                "fixtures/ktx2/kodim23.ktx2",
                BasisTranscodeTarget.RGB565,
                BasisImageFormat.RGB565,
                768,
                512);
        assertPackedTargetMatchesRgbaPacking(
                "fixtures/ktx2/kodim23.ktx2",
                BasisTranscodeTarget.BGR565,
                BasisImageFormat.BGR565,
                768,
                512);
        assertPackedTargetMatchesRgbaPacking(
                "fixtures/ktx2/kodim23.ktx2",
                BasisTranscodeTarget.RGBA4444,
                BasisImageFormat.RGBA4444,
                768,
                512);
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToRgba4444() {
        assertPackedTargetMatchesRgbaPacking(
                "fixtures/basis/kodim20_alpha.basis",
                BasisTranscodeTarget.RGBA4444,
                BasisImageFormat.RGBA4444,
                768,
                512);
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToBc7BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC7)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC7, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);

        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha_bc7.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testDecodesXuastcFullArithKtx2AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(512, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());

        byte[] expected = loadFixture("fixtures/native_gold/base_xuastc_arith.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testDecodesXuastcFullZstdKtx2AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(512, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());

        byte[] expected = loadFixture("fixtures/native_gold/base_xuastc_zstd.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcFullArithKtx2ToAstc5x4AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_arith.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_5X4)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(512, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ASTC_LDR_5X4, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(210_944, result.getMipMapSizes()[0]);

        byte[] expected = loadFixture("fixtures/native_gold/base_xuastc_arith_astc5x4.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcFullZstdKtx2ToAstc5x4AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_5X4)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(512, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ASTC_LDR_5X4, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(210_944, result.getMipMapSizes()[0]);

        byte[] expected = loadFixture("fixtures/native_gold/base_xuastc_zstd_astc5x4.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcFullArithKtx2ToAstc6x6AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/tough_xuastc6x6_arith.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_6X6)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(1118, result.getWidth());
        assertEquals(1105, result.getHeight());
        assertEquals(BasisImageFormat.ASTC_LDR_6X6, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(553_520, result.getMipMapSizes()[0]);

        byte[] expected = loadFixture("fixtures/native_gold/tough_xuastc6x6_arith_astc6x6.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcFullZstdKtx2ToAstc6x6AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/tough_xuastc6x6_zstd.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_6X6)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(1118, result.getWidth());
        assertEquals(1105, result.getHeight());
        assertEquals(BasisImageFormat.ASTC_LDR_6X6, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(553_520, result.getMipMapSizes()[0]);

        byte[] expected = loadFixture("fixtures/native_gold/tough_xuastc6x6_zstd_astc6x6.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcArithMipChainToAstc6x6AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_xuastc6x6_mip_arith.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_6X6)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ASTC_LDR_6X6, result.getImageFormat());
        assertEquals(XUASTC_6X6_MIP_ASTC_SIZES.length, result.getLevelCount());
        assertArrayEquals(XUASTC_6X6_MIP_ASTC_SIZES, result.getMipMapSizes());
        byte[] expected = loadFixture("fixtures/native_gold/kodim23_xuastc6x6_mip_arith_astc6x6.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcArithArrayMipChainImageOneToAstc6x6AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_xuastc6x6_array_mip_arith.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_6X6)
                .imageIndex(1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ASTC_LDR_6X6, result.getImageFormat());
        assertEquals(2, result.getImageCount());
        assertEquals(XUASTC_6X6_MIP_ASTC_SIZES.length, result.getLevelCount());
        assertArrayEquals(XUASTC_6X6_MIP_ASTC_SIZES, result.getMipMapSizes());
        byte[] expected = loadFixture(
                "fixtures/native_gold/kodim_xuastc6x6_array_mip_arith_image1_astc6x6.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcArithCubemapMipFaceFiveToAstc6x6AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_xuastc6x6_cubemap_mip_arith.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_6X6)
                .imageIndex(5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(512, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ASTC_LDR_6X6, result.getImageFormat());
        assertEquals(6, result.getImageCount());
        assertEquals(XUASTC_6X6_CUBEMAP_MIP_ASTC_SIZES.length, result.getLevelCount());
        assertArrayEquals(XUASTC_6X6_CUBEMAP_MIP_ASTC_SIZES, result.getMipMapSizes());
        byte[] expected = loadFixture(
                "fixtures/native_gold/kodim_xuastc6x6_cubemap_mip_arith_face5_astc6x6.dat");
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesXuastcRemainingAstcBlockSizesAgainstNativeGold() {
        String[][] cases = {
            {"4x4", "ASTC_LDR_4X4"},
            {"5x5", "ASTC_LDR_5X5"},
            {"6x5", "ASTC_LDR_6X5"},
            {"8x5", "ASTC_LDR_8X5"},
            {"8x6", "ASTC_LDR_8X6"},
            {"8x8", "ASTC_LDR_8X8"},
            {"10x5", "ASTC_LDR_10X5"},
            {"10x6", "ASTC_LDR_10X6"},
            {"10x8", "ASTC_LDR_10X8"},
            {"10x10", "ASTC_LDR_10X10"},
            {"12x10", "ASTC_LDR_12X10"},
            {"12x12", "ASTC_LDR_12X12"}
        };
        for (String[] astcCase : cases) {
            assertXuastcAstcNativeGold(astcCase[0], astcCase[1], "arith");
            assertXuastcAstcNativeGold(astcCase[0], astcCase[1], "zstd");
        }
    }

    private static void assertXuastcAstcNativeGold(String blockSize, String targetName, String profile) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        BasisTranscodeTarget target = BasisTranscodeTarget.valueOf(targetName);
        byte[] payload = loadFixture("fixtures/ktx2/tough_xuastc" + blockSize + "_" + profile + ".ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(1118, result.getWidth(), blockSize + " " + profile);
        assertEquals(1105, result.getHeight(), blockSize + " " + profile);
        assertEquals(target.getImageFormat(), result.getImageFormat(), blockSize + " " + profile);
        assertEquals(1, result.getMipMapSizes().length, blockSize + " " + profile);
        byte[] expected = loadFixture("fixtures/native_gold/tough_xuastc" + blockSize
                + "_astc" + blockSize + ".dat");
        assertEquals(expected.length, result.getMipMapSizes()[0], blockSize + " " + profile);
        assertArrayEquals(expected, toArray(result.getPixelData()), blockSize + " " + profile);
    }

    @Test
    public void testExplicitAstcTargetTranscodesMatchingXuastc5x4() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_5X4)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ASTC_LDR_5X4, result.getImageFormat());
        assertEquals(loadFixture("fixtures/native_gold/base_xuastc_zstd_astc5x4.dat").length,
                result.getPixelData().remaining());
    }

    @Test
    public void testExplicitBc7TargetTranscodesXuastc() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC7)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC7, result.getImageFormat());
        assertEquals(262_144, result.getPixelData().remaining());
    }

    @Test
    public void testExplicitBc7TargetTranscodesUastc() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_uastc4x4.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC7)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC7, result.getImageFormat());
        assertEquals(4_096, result.getPixelData().remaining());
    }

    @Test
    public void testExplicitEtc2TargetTranscodesXuastc() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(262_144, result.getPixelData().remaining());
    }

    @Test
    public void testDecodesKtx2UastcLdrAstcAgainstNativeGold() {
        assertKtx2UastcLdrAstcNativeGold("kodim_uastc4x4.ktx2");
    }

    @Test
    public void testDecodesZstdKtx2UastcLdrAstcAgainstNativeGold() {
        assertKtx2UastcLdrAstcNativeGold("kodim_uastc4x4_zstd.ktx2");
    }

    @Test
    public void testDecodesKtx2UastcLdrRgbaAgainstNativeGold() {
        assertKtx2UastcLdrRgbaNativeGold("kodim_uastc4x4.ktx2");
    }

    @Test
    public void testDecodesZstdKtx2UastcLdrRgbaAgainstNativeGold() {
        assertKtx2UastcLdrRgbaNativeGold("kodim_uastc4x4_zstd.ktx2");
    }

    @Test
    public void testTranscodesUastcAndXuastcLdrToPackedPixelTargets() {
        assertPackedTargetMatchesRgbaPacking(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.RGB565,
                BasisImageFormat.RGB565,
                64,
                64);
        assertPackedTargetMatchesRgbaPacking(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.RGBA4444,
                BasisImageFormat.RGBA4444,
                512,
                512);
    }

    @Test
    public void testTranscodesUastcAndXuastcLdrToEtc2EacTargets() {
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.ETC2_EAC_R11,
                BasisImageFormat.ETC2_EAC_R11,
                64,
                64,
                2_048);
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.ETC2_EAC_RG11,
                BasisImageFormat.ETC2_EAC_RG11,
                64,
                64,
                4_096);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.ETC2_EAC_RG11,
                BasisImageFormat.ETC2_EAC_RG11,
                512,
                512,
                262_144);
    }

    @Test
    public void testTranscodesUastcAndXuastcLdrToEtcColorTargets() {
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.ETC1,
                BasisImageFormat.ETC1,
                64,
                64,
                2_048);
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.ETC2_NO_ALPHA,
                BasisImageFormat.ETC2_NO_ALPHA,
                64,
                64,
                2_048);
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.ETC2,
                BasisImageFormat.ETC2,
                64,
                64,
                4_096);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.ETC1,
                BasisImageFormat.ETC1,
                512,
                512,
                131_072);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.ETC2_NO_ALPHA,
                BasisImageFormat.ETC2_NO_ALPHA,
                512,
                512,
                131_072);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.ETC2,
                BasisImageFormat.ETC2,
                512,
                512,
                262_144);
    }

    @Test
    public void testTranscodesUastcAndXuastcLdrToBcTargets() {
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.BC1,
                BasisImageFormat.BC1,
                64,
                64,
                2_048);
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.BC3,
                BasisImageFormat.BC3,
                64,
                64,
                4_096);
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.BC4,
                BasisImageFormat.BC4,
                64,
                64,
                2_048);
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.BC5,
                BasisImageFormat.BC5,
                64,
                64,
                4_096);
        assertBlockTargetShape(
                "fixtures/ktx2/kodim_uastc4x4.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                64,
                64,
                4_096);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc4x4_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc4x4_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.BC1,
                BasisImageFormat.BC1,
                512,
                512,
                131_072);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.BC3,
                BasisImageFormat.BC3,
                512,
                512,
                262_144);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.BC5,
                BasisImageFormat.BC5,
                512,
                512,
                262_144);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                512,
                512,
                262_144);
        assertBlockTargetShape(
                "fixtures/ktx2/base_xuastc_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                512,
                512,
                262_144,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc5x5_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc8x5_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc6x5_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc6x6_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc8x6_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc5x5_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc6x5_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc8x5_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc6x6_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc8x6_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc8x8_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc8x8_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc10x5_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc10x6_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc10x8_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc10x10_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc12x10_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering);
        assertBlockTargetShape(
                "fixtures/ktx2/tough_xuastc12x12_zstd.ktx2",
                BasisTranscodeTarget.BC7,
                BasisImageFormat.BC7,
                1118,
                1105,
                1_240_960,
                Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering);
    }

    @Test
    public void testRejectsMismatchedExplicitAstcBlockSizeForXuastc() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/base_xuastc_zstd.ktx2");

        BasisDecodeException ex = assertThrows(BasisDecodeException.class,
                () -> decoder.decode(BasisDecodeRequest.builder(payload)
                        .target(BasisTranscodeTarget.ASTC_LDR_4X4)
                        .allocator(ByteBuffer::allocate)
                        .build()));

        assertTrue(ex.getMessage().contains("Unsupported transcode target"));
    }

    private static void assertKtx2UastcLdrAstcNativeGold(String fixtureName) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/" + fixtureName);

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ASTC_LDR_4X4)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(64, result.getWidth(), fixtureName);
        assertEquals(64, result.getHeight(), fixtureName);
        assertEquals(BasisImageFormat.ASTC_LDR_4X4, result.getImageFormat(), fixtureName);
        assertEquals(1, result.getMipMapSizes().length, fixtureName);
        byte[] expected = loadFixture("fixtures/native_gold/kodim_uastc4x4_astc4x4.dat");
        assertEquals(expected.length, result.getMipMapSizes()[0], fixtureName);
        assertArrayEquals(expected, toArray(result.getPixelData()), fixtureName);
    }

    private static void assertKtx2UastcLdrRgbaNativeGold(String fixtureName) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/" + fixtureName);

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(64, result.getWidth(), fixtureName);
        assertEquals(64, result.getHeight(), fixtureName);
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat(), fixtureName);
        assertEquals(1, result.getMipMapSizes().length, fixtureName);
        byte[] expected = loadFixture("fixtures/native_gold/kodim_uastc4x4.rgba8.dat");
        assertEquals(expected.length, result.getMipMapSizes()[0], fixtureName);
        assertArrayEquals(expected, toArray(result.getPixelData()), fixtureName);
    }

    @Test
    public void testDecodesEtc1sBasisFixture() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(1_572_864, result.getMipMapSizes()[0]);
        assertEquals("9175d8d40127071392510353394d75ba25fca7ab54b6224b3cb68e4aef3cf51f",
                sha256Hex(toArray(result.getPixelData())));
    }

    @Test
    public void testDecodesEtc1sBasisArrayImageAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_21_array.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .imageIndex(1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(1_572_864, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_21_array_image1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testRejectsOutOfRangeBasisImageIndex() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_21_array.basis");

        BasisDecodeException exception = assertThrows(BasisDecodeException.class,
                () -> decoder.decode(BasisDecodeRequest.builder(payload)
                        .target(BasisTranscodeTarget.RGBA8)
                        .imageIndex(2)
                        .allocator(ByteBuffer::allocate)
                        .build()));

        assertTrue(exception.getMessage().contains("Basis image index out of range"));
    }

    @Test
    public void testDecodesEtc1sBasisCubemapImageAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim_cubemap.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .imageIndex(5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(262_144, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_cubemap_basis_image5.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisCubemapImageToEtc2AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim_cubemap.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .imageIndex(5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(65_536, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_cubemap_basis_image5_etc2.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisCubemapImageToBc1AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim_cubemap.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .imageIndex(5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(32_768, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_cubemap_basis_image5_bc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testDecodesEtc1sBasisMipChainAgainstNativeGold() {
        assertBasisEtc1sMipChain(
                BasisTranscodeTarget.RGBA8,
                BasisImageFormat.RGBA8,
                ETC1S_MIP_RGBA_SIZES,
                "fixtures/native_gold/kodim20_mip.dat");
    }

    @Test
    public void testTranscodesEtc1sBasisMipChainToEtc2AgainstNativeGold() {
        assertBasisEtc1sMipChain(
                BasisTranscodeTarget.ETC2,
                BasisImageFormat.ETC2,
                ETC1S_MIP_ETC2_SIZES,
                "fixtures/native_gold/kodim20_mip_etc2.dat");
    }

    @Test
    public void testTranscodesEtc1sBasisMipChainToBc1AgainstNativeGold() {
        assertBasisEtc1sMipChain(
                BasisTranscodeTarget.BC1,
                BasisImageFormat.BC1,
                ETC1S_MIP_BC1_SIZES,
                "fixtures/native_gold/kodim20_mip_bc1.dat");
    }

    @Test
    public void testTranscodesEtc1sBasisVideoIFrameToEtc2AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim_video.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .imageIndex(0)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(2, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(new int[] {393_216}, result.getMipMapSizes());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_video_iframe0_etc2.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisVideoIFrameToBc1AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim_video.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .imageIndex(0)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(2, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(new int[] {196_608}, result.getMipMapSizes());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_video_iframe0_bc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisVideoPFrameToBc1AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim_video.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .imageIndex(1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(2, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(new int[] {196_608}, result.getMipMapSizes());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_video_pframe1_bc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisVideoPFrameToEtc2AgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim_video.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .imageIndex(1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(2, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(new int[] {393_216}, result.getMipMapSizes());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim_video_pframe1_etc2.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testEtc1sVideoStateGrowsBeyondSixteenLevels() {
        Etc1sRgbaDecoder.VideoState state = new Etc1sRgbaDecoder.VideoState();

        int[] levelTwenty = state.previousFrameIndices(false, 20, 7);
        levelTwenty[3] = 42;

        assertEquals(7, levelTwenty.length);
        assertEquals(42, state.previousFrameIndices(false, 20, 3)[3]);
        assertEquals(9, state.previousFrameIndices(true, 21, 9).length);
        assertEquals(0, state.previousFrameIndices(true, 20, 4)[3]);

        BasisDecodeException thrown = assertThrows(
                BasisDecodeException.class,
                () -> state.previousFrameIndices(false, -1, 1));
        assertTrue(thrown.getMessage().contains("malformed"));
    }

    @Test
    public void testDecodesEtc1sBasisAlphaSliceAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.RGBA8, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(1_572_864, result.getMipMapSizes()[0]);

        byte[] decoded = toArray(result.getPixelData());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha.dat"), decoded);
        assertTrue(hasTransparentPixels(decoded));
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToEtc2BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);

        byte[] blocks = toArray(result.getPixelData());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha_etc2.dat"), blocks);
        byte[] rgba = decodeEtc2RgbaToRgba(blocks, result.getWidth(), result.getHeight());
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha.dat"), rgba);
        assertTrue(hasTransparentPixels(rgba));
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToEtc1BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC1, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha_etc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToEtc2NoAlphaBlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2_NO_ALPHA)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2_NO_ALPHA, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha_etc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToBc1BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha_bc1.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToBc3BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC3)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC3, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);

        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha_bc3.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToBc5BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.BC5, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);

        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_alpha_bc5.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisFixtureToEtc1Blocks() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ETC1, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        byte[] rgba = decodeEtc1ToRgba(toArray(result.getPixelData()), result.getWidth(), result.getHeight());
        assertEquals("9175d8d40127071392510353394d75ba25fca7ab54b6224b3cb68e4aef3cf51f",
                sha256Hex(rgba));
    }

    @Test
    public void testTranscodesEtc1sBasisFixtureToEtc2Blocks() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ETC2, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);

        byte[] rgba = decodeEtc2RgbaToRgba(
                toArray(result.getPixelData()),
                result.getWidth(),
                result.getHeight());
        assertEquals("9175d8d40127071392510353394d75ba25fca7ab54b6224b3cb68e4aef3cf51f",
                sha256Hex(rgba));
    }

    @Test
    public void testTranscodesEtc1sBasisFixtureToEtc2NoAlphaBlocks() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2_NO_ALPHA)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.ETC2_NO_ALPHA, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);

        byte[] rgba = decodeEtc1ToRgba(toArray(result.getPixelData()), result.getWidth(), result.getHeight());
        assertEquals("9175d8d40127071392510353394d75ba25fca7ab54b6224b3cb68e4aef3cf51f",
                sha256Hex(rgba));
    }

    @Test
    public void testTranscodesEtc1sBasisFixtureToBc1Blocks() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.BC1, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);
        assertEquals("ec85f55bfc9810f39829251932552827caa2c0024d712a23f4085ab949394df2",
                sha256Hex(toArray(result.getPixelData())));
    }

    @Test
    public void testTranscodesEtc1sBasisFixtureToBc3BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC3)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.BC3, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_bc3.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisFixtureToBc7BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC7)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.BC7, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(393_216, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_bc7.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisFixtureToBc4BlocksAgainstNativeGold() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.BC4)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(BasisImageFormat.BC4, result.getImageFormat());
        assertEquals(1, result.getMipMapSizes().length);
        assertEquals(196_608, result.getMipMapSizes()[0]);
        assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_bc4.dat"),
                toArray(result.getPixelData()));
    }

    @Test
    public void testTranscodesEtc1sBasisAlphaSliceToEtc2EacRg11Blocks() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_alpha.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.ETC2_EAC_RG11)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(BasisImageFormat.ETC2_EAC_RG11, result.getImageFormat());
        assertArrayEquals(new int[] {393_216}, result.getMipMapSizes());
        assertTrue(hasNonOpaqueEacChannelOne(toArray(result.getPixelData())));
    }

    @Test
    public void testRejectsUnsupportedTypeSize() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        ByteBuffer header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(16, 4);

        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testRejectsLevelOffsetAfterPayload() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        ByteBuffer header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        header.putLong(Ktx2Constants.KTX2_HEADER_SIZE, Integer.MAX_VALUE);

        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testRejectsUnsigned64OverflowInLevelOffset() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        ByteBuffer header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        header.putLong(Ktx2Constants.KTX2_HEADER_SIZE, Long.MIN_VALUE);

        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testRejectsUnsigned32WidthAndHeightOverflow() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payloadOverflow = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        ByteBuffer header = ByteBuffer.wrap(payloadOverflow).order(ByteOrder.LITTLE_ENDIAN);
        // width and height are parsed as unsigned 32-bit, then reduced to signed int domain where needed.
        header.putInt(20, -1); // pixelWidth -> 0xffffffff
        header.putInt(24, -2); // pixelHeight -> 0xfffffffe

        assertThrows(
                BasisDecodeException.class,
                () -> decoder.decode(BasisDecodeRequest.from(
                        payloadOverflow, BasisTranscodeTarget.RGBA8)));

        // Restore payload and verify 32-bit boundary rejection through the parser facade.
        byte[] payloadBoundary = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        header = ByteBuffer.wrap(payloadBoundary).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(20, Integer.MIN_VALUE); // 0x80000000 in unsigned space -> exceeds signed int range
        assertThrows(
                BasisDecodeException.class,
                () -> decoder.decode(BasisDecodeRequest.from(
                        payloadBoundary, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testRejectsUnsigned64SgdOverflowInHeader() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        ByteBuffer header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        // SGD offset field is at byte 64 in the 80-byte KTX2 header.
        header.putLong(64, Long.MIN_VALUE);

        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testResourceUsageIsStableAcrossManyPureJavaDecodes() {
        Path procFd = Paths.get("/proc/self/fd");
        if (!Files.isDirectory(procFd)) {
            return;
        }

        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");

        long baseline = countOpenFileDescriptors(procFd);
        for (int i = 0; i < 1000; i++) {
            BasisDecodeResult result = decoder.decode(
                    BasisDecodeRequest.builder(payload)
                            .target(BasisTranscodeTarget.RGBA8)
                            .allocator(ByteBuffer::allocate)
                            .build());
            assertEquals(4, result.getPixelData().remaining());
        }
        long after = countOpenFileDescriptors(procFd);
        assertTrue(
                after <= baseline + 2,
                "Potential FD leak detected: baseline=" + baseline + " after=" + after);
    }

    @Test
    public void testConcurrentDecodesAreDeterministicAndIndependent() throws Exception {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");

        byte[] expected = toArray(decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build()).getPixelData());

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            java.util.List<Callable<byte[]>> tasks = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                tasks.add(() -> {
                    BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                            .target(BasisTranscodeTarget.RGBA8)
                            .allocator(ByteBuffer::allocate)
                            .build());
                    assertEquals(4, result.getPixelData().remaining());
                    return toArray(result.getPixelData());
                });
            }

            for (Future<byte[]> future : executor.invokeAll(tasks)) {
                assertTrue(Arrays.equals(expected, future.get()), "Concurrent decode output mismatch");
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS),
                    "Concurrent decode tasks did not terminate within timeout");
        }
    }

    @Test
    public void testRejectsEmptyPayload() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();

        assertThrows(BasisDecodeException.class,
                () -> decoder.decode(BasisDecodeRequest.from(
                        new byte[0], BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testBatchDecodeScale_StableAcrossIterationsAndBenchmarkReport() throws Exception {
        final int iterations = 50;
        final String fixture = "fixtures/uncompressed-rgba8-ktx2.bin";
        byte[] payload = loadFixture(fixture);
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();

        byte[] referencePayload = null;
        long totalBytes = 0;
        long startNanos = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                    .target(BasisTranscodeTarget.RGBA8)
                    .allocator(ByteBuffer::allocate)
                    .build());

            assertEquals(1, result.getMipMapSizes().length);
            assertEquals(4, result.getPixelData().remaining());

            byte[] decoded = toArray(result.getPixelData());
            totalBytes += decoded.length;

            if (referencePayload == null) {
                referencePayload = Arrays.copyOf(decoded, decoded.length);
            } else {
                assertArrayEquals(referencePayload, decoded);
            }
        }

        long elapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        double throughputMiBPerSec = (totalBytes / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);

        Path reportPath = Path.of(System.getProperty("user.dir"), "build", "reports",
                "decoder-batch-benchmark.csv");
        Files.createDirectories(reportPath.getParent());

        String manifestLine = String.format(
                "%s,%d,%d,%d,%.6f\n",
                fixture,
                iterations,
                totalBytes,
                elapsedMs,
                throughputMiBPerSec);

        String header = "fixture,iters,totalBytes,elapsedMs,throughputMiBPerSec\n";
        if (!Files.exists(reportPath)) {
            Files.writeString(reportPath, header + manifestLine, StandardCharsets.UTF_8);
        } else {
            Files.writeString(reportPath,
                    manifestLine,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        }

        assertTrue(Files.exists(reportPath));
        assertTrue(Files.size(reportPath) > 0);
        assertTrue(throughputMiBPerSec > 0.0, "Benchmark throughput must be > 0 MiB/s");
    }

    private static long countOpenFileDescriptors(Path procFd) {
        try (Stream<Path> entries = Files.list(procFd)) {
            return entries.count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanup(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // best effort cleanup
            }
        }
    }

    @Test
    public void testRejectsCorruptedMetadataRanges() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        ByteBuffer header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(56, 1); // kvd byteLength
        // Invalid for a non-empty range before the KTX2 header section.
        header.putInt(60, 1);

        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testRejectsTruncatedLevelIndexTable() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = Arrays.copyOf(
                loadFixture("fixtures/uncompressed-rgba8-ktx2.bin"),
                Ktx2Constants.KTX2_HEADER_SIZE + 12);

        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    @Test
    public void testRejectsInvalidMagic() {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/invalid.ktx2");
        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.from(payload, BasisTranscodeTarget.RGBA8)));
    }

    private static void patchVkFormat(byte[] payload, int vkFormat) {
        if (payload.length < 16) {
            throw new IllegalArgumentException("fixture is unexpectedly short");
        }
        ByteBuffer header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(12, vkFormat);
    }

    private static byte[] bytesToArray(ByteBuffer data) {
        ByteBuffer duplicated = data.asReadOnlyBuffer();
        duplicated.rewind();
        byte[] result = new byte[duplicated.remaining()];
        duplicated.get(result);
        return result;
    }

    private static byte[] toArray(ByteBuffer data) {
        ByteBuffer duplicate = data.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] out = new byte[duplicate.remaining()];
        duplicate.get(out);
        return out;
    }

    private static boolean hasTransparentPixels(byte[] rgba) {
        for (int i = 3; i < rgba.length; i += 4) {
            if (Byte.toUnsignedInt(rgba[i]) != 255) {
                return true;
            }
        }
        return false;
    }

    private static void assertEacRg11ChannelZero(byte[] r11, byte[] rg11) {
        assertEquals(r11.length * 2, rg11.length);
        for (int block = 0; block < r11.length / 8; block++) {
            for (int i = 0; i < 8; i++) {
                assertEquals(r11[block * 8 + i], rg11[block * 16 + i], "block " + block + " byte " + i);
            }
        }
    }

    private static boolean hasNonOpaqueEacChannelOne(byte[] rg11) {
        byte[] opaque = {(byte) 0xFF, 0x1D, (byte) 0x92, 0x49, 0x24, (byte) 0x92, 0x49, 0x24};
        for (int block = 0; block < rg11.length / 16; block++) {
            for (int i = 0; i < opaque.length; i++) {
                if (rg11[block * 16 + 8 + i] != opaque[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertBlockTargetShape(
            String fixture,
            BasisTranscodeTarget target,
            BasisImageFormat imageFormat,
            int width,
            int height,
            int expectedBytes) {
        assertBlockTargetShape(
                fixture,
                target,
                imageFormat,
                width,
                height,
                expectedBytes,
                new Ktx2DecodeFlag[0]);
    }

    private static void assertBlockTargetShape(
            String fixture,
            BasisTranscodeTarget target,
            BasisImageFormat imageFormat,
            int width,
            int height,
            int expectedBytes,
            Ktx2DecodeFlag... decodeFlags) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture(fixture);

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .decodeFlags(decodeFlags)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(width, result.getWidth(), fixture + " " + target);
        assertEquals(height, result.getHeight(), fixture + " " + target);
        assertEquals(imageFormat, result.getImageFormat(), fixture + " " + target);
        assertArrayEquals(new int[] {expectedBytes}, result.getMipMapSizes(), fixture + " " + target);
        assertEquals(expectedBytes, result.getPixelData().remaining(), fixture + " " + target);
    }

    private static void assertPackedTargetMatchesRgbaPacking(
            String fixture,
            BasisTranscodeTarget target,
            BasisImageFormat expectedFormat,
            int expectedWidth,
            int expectedHeight) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture(fixture);

        final BasisDecodeResult packedResult = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .allocator(ByteBuffer::allocate)
                .build());
        final BasisDecodeResult rgbaResult = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(expectedWidth, packedResult.getWidth(), fixture + " " + target);
        assertEquals(expectedHeight, packedResult.getHeight(), fixture + " " + target);
        assertEquals(expectedFormat, packedResult.getImageFormat(), fixture + " " + target);
        assertArrayEquals(
                new int[] {expectedWidth * expectedHeight * 2},
                packedResult.getMipMapSizes(),
                fixture + " " + target);
        assertArrayEquals(
                packRgba(toArray(rgbaResult.getPixelData()), target),
                toArray(packedResult.getPixelData()),
                fixture + " " + target);
    }

    private static byte[] packRgba(byte[] rgba, BasisTranscodeTarget target) {
        assertEquals(0, rgba.length % 4);
        byte[] packed = new byte[rgba.length / 2];
        for (int in = 0, out = 0; in < rgba.length; in += 4, out += 2) {
            int r = Byte.toUnsignedInt(rgba[in]);
            int g = Byte.toUnsignedInt(rgba[in + 1]);
            int b = Byte.toUnsignedInt(rgba[in + 2]);
            int a = Byte.toUnsignedInt(rgba[in + 3]);
            int value;
            if (target == BasisTranscodeTarget.RGB565) {
                value = (mul8(r, 31) << 11) | (mul8(g, 63) << 5) | mul8(b, 31);
            } else if (target == BasisTranscodeTarget.BGR565) {
                value = (mul8(b, 31) << 11) | (mul8(g, 63) << 5) | mul8(r, 31);
            } else {
                value = (mul8(r, 15) << 12)
                        | (mul8(g, 15) << 8)
                        | (mul8(b, 15) << 4)
                        | mul8(a, 15);
            }
            packed[out] = (byte) value;
            packed[out + 1] = (byte) (value >>> 8);
        }
        return packed;
    }

    private static int mul8(int value, int quantizedMax) {
        int v = value * quantizedMax + 128;
        return (v + (v >>> 8)) >>> 8;
    }

    private static byte[] deflateUncompressedKtx2Fixture(byte[] payload) {
        byte[] rawLevel = Arrays.copyOfRange(payload, 104, payload.length);
        Deflater deflater = new Deflater();
        deflater.setInput(rawLevel);
        deflater.finish();
        byte[] temp = new byte[64];
        int compressedLength = deflater.deflate(temp);
        deflater.end();

        byte[] compressed = Arrays.copyOf(temp, compressedLength);
        byte[] result = new byte[104 + compressed.length];
        System.arraycopy(payload, 0, result, 0, 104);
        ByteBuffer header = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(44, Ktx2Constants.KTX2_SS_DEFLATE);
        header.putLong(88, compressed.length);
        header.putLong(96, rawLevel.length);
        System.arraycopy(compressed, 0, result, 104, compressed.length);
        return result;
    }

    private static void assertEtc1sMipChain(
            BasisTranscodeTarget target,
            BasisImageFormat imageFormat,
            int[] expectedMipSizes,
            String goldFixture) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim23_mip.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(imageFormat, result.getImageFormat());
        assertEquals(1, result.getImageCount());
        assertEquals(expectedMipSizes.length, result.getLevelCount());
        assertArrayEquals(expectedMipSizes, result.getMipMapSizes());
        byte[] expected = loadFixture(goldFixture);
        assertEquals(expected.length, result.getPixelData().remaining());
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    private static void assertEtc1sArray(
            BasisTranscodeTarget target,
            BasisImageFormat imageFormat,
            int[] expectedImageLevelSizes,
            String goldFixture) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_array.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .imageIndex(1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(imageFormat, result.getImageFormat());
        assertEquals(2, result.getImageCount());
        assertEquals(1, result.getLevelCount());
        assertArrayEquals(expectedImageLevelSizes, result.getMipMapSizes());
        byte[] expected = loadFixture(goldFixture);
        assertEquals(expected.length, result.getPixelData().remaining());
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    private static void assertEtc1sArrayMipChain(
            BasisTranscodeTarget target,
            BasisImageFormat imageFormat,
            int[] expectedMipSizes,
            String goldFixture) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_array_mip.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .imageIndex(1)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(imageFormat, result.getImageFormat());
        assertEquals(2, result.getImageCount());
        assertEquals(expectedMipSizes.length, result.getLevelCount());
        assertArrayEquals(expectedMipSizes, result.getMipMapSizes());
        byte[] expected = loadFixture(goldFixture);
        assertEquals(expected.length, result.getPixelData().remaining());
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    private static void assertEtc1sCubemapMipChain(
            BasisTranscodeTarget target,
            BasisImageFormat imageFormat,
            int[] expectedMipSizes,
            String goldFixture) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/ktx2/kodim_cubemap_mip.ktx2");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .imageIndex(5)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(512, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(imageFormat, result.getImageFormat());
        assertEquals(6, result.getImageCount());
        assertEquals(expectedMipSizes.length, result.getLevelCount());
        assertArrayEquals(expectedMipSizes, result.getMipMapSizes());
        byte[] expected = loadFixture(goldFixture);
        assertEquals(expected.length, result.getPixelData().remaining());
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    private static void assertBasisEtc1sMipChain(
            BasisTranscodeTarget target,
            BasisImageFormat imageFormat,
            int[] expectedMipSizes,
            String goldFixture) {
        BasisuJavaDecoder decoder = new BasisuJavaDecoder();
        byte[] payload = loadFixture("fixtures/basis/kodim20_mip.basis");

        BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(payload)
                .target(target)
                .allocator(ByteBuffer::allocate)
                .build());

        assertEquals(768, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(imageFormat, result.getImageFormat());
        assertEquals(1, result.getImageCount());
        assertEquals(expectedMipSizes.length, result.getLevelCount());
        assertArrayEquals(expectedMipSizes, result.getMipMapSizes());
        byte[] expected = loadFixture(goldFixture);
        assertEquals(expected.length, result.getPixelData().remaining());
        assertArrayEquals(expected, toArray(result.getPixelData()));
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }

    private static byte[] decodeEtc1ToRgba(byte[] blocks, int width, int height) {
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        assertEquals(blocksX * blocksY * 8, blocks.length);
        byte[] rgba = new byte[width * height * 4];
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                decodeEtc1Block(blocks, (blockY * blocksX + blockX) * 8, rgba, width, height, blockX, blockY);
            }
        }
        return rgba;
    }

    private static byte[] decodeEtc2RgbaToRgba(byte[] blocks, int width, int height) {
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        assertEquals(blocksX * blocksY * 16, blocks.length);
        byte[] colorBlocks = new byte[blocksX * blocksY * 8];
        for (int i = 0; i < blocksX * blocksY; i++) {
            System.arraycopy(blocks, i * 16 + 8, colorBlocks, i * 8, 8);
        }
        byte[] rgba = decodeEtc1ToRgba(colorBlocks, width, height);
        for (int blockY = 0; blockY < blocksY; blockY++) {
            for (int blockX = 0; blockX < blocksX; blockX++) {
                int blockOffset = (blockY * blocksX + blockX) * 16;
                decodeEacA8Block(blocks, blockOffset, rgba, width, height, blockX, blockY);
            }
        }
        return rgba;
    }

    private static void decodeEacA8Block(
            byte[] blocks,
            int blockOffset,
            byte[] rgba,
            int width,
            int height,
            int blockX,
            int blockY) {
        int base = Byte.toUnsignedInt(blocks[blockOffset]);
        int tableMultiplier = Byte.toUnsignedInt(blocks[blockOffset + 1]);
        int table = tableMultiplier & 15;
        int multiplier = tableMultiplier >>> 4;
        long selectors = 0;
        for (int i = 0; i < 6; i++) {
            selectors = (selectors << 8) | Byte.toUnsignedLong(blocks[blockOffset + 2 + i]);
        }
        int maxX = Math.min(4, width - blockX * 4);
        int maxY = Math.min(4, height - blockY * 4);
        for (int y = 0; y < maxY; y++) {
            for (int x = 0; x < maxX; x++) {
                int selector = (int) ((selectors >>> (45 - (y + x * 4) * 3)) & 7);
                int alpha = clamp255(base + multiplier * EAC_MODIFIER_TABLE[table][selector]);
                rgba[((blockY * 4 + y) * width + blockX * 4 + x) * 4 + 3] = (byte) alpha;
            }
        }
    }

    private static void decodeEtc1Block(
            byte[] blocks,
            int blockOffset,
            byte[] rgba,
            int width,
            int height,
            int blockX,
            int blockY) {
        int red = expand5((Byte.toUnsignedInt(blocks[blockOffset]) >>> 3) & 31);
        int green = expand5((Byte.toUnsignedInt(blocks[blockOffset + 1]) >>> 3) & 31);
        int blue = expand5((Byte.toUnsignedInt(blocks[blockOffset + 2]) >>> 3) & 31);
        int table = (Byte.toUnsignedInt(blocks[blockOffset + 3]) >>> 5) & 7;
        int maxX = Math.min(4, width - blockX * 4);
        int maxY = Math.min(4, height - blockY * 4);
        for (int y = 0; y < maxY; y++) {
            for (int x = 0; x < maxX; x++) {
                int selector = etc1SelectorIndex(blocks, blockOffset, x, y);
                int modifier = ETC1_INTENSITY_TABLES[table][selector];
                int pixelOffset = ((blockY * 4 + y) * width + blockX * 4 + x) * 4;
                rgba[pixelOffset] = (byte) clamp255(red + modifier);
                rgba[pixelOffset + 1] = (byte) clamp255(green + modifier);
                rgba[pixelOffset + 2] = (byte) clamp255(blue + modifier);
                rgba[pixelOffset + 3] = (byte) 255;
            }
        }
    }

    private static int etc1SelectorIndex(byte[] blocks, int blockOffset, int x, int y) {
        int bitIndex = x * 4 + y;
        int byteOffset = blockOffset + 7 - (bitIndex >>> 3);
        int bitOffset = bitIndex & 7;
        int lsb = (Byte.toUnsignedInt(blocks[byteOffset]) >>> bitOffset) & 1;
        int msb = (Byte.toUnsignedInt(blocks[byteOffset - 2]) >>> bitOffset) & 1;
        int rawSelector = lsb | (msb << 1);
        return ETC1_RAW_SELECTOR_TO_INDEX[rawSelector];
    }

    private static int expand5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int clamp255(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }

    private static byte[] loadFixture(String name) {
        try (InputStream stream = TestBasisuJavaDecoder.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                Path sourceTreePath = Paths.get("src/test/resources").resolve(name);
                if (Files.exists(sourceTreePath)) {
                    return Files.readAllBytes(sourceTreePath);
                }
                throw new RuntimeException("Fixture not found: " + name);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[1024];
            int read;
            while ((read = stream.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Could not read fixture: " + name, exception);
        }
    }
}
