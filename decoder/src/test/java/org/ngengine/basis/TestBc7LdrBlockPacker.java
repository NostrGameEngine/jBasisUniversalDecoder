package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestBc7LdrBlockPacker {

    @Test
    public void testImagePackingEntrypointsEmitExpectedBlockCounts() {
        final byte[] lowVariance = rgbaBytes(8, 4, (x, y, c) -> {
            if (c == 3) {
                return 255;
            }
            return 96 + x + y + c;
        });
        final byte[] gradient = rgbaBytes(8, 4, (x, y, c) -> {
            if (c == 3) {
                return 255;
            }
            return (x * 37 + y * 19 + c * 41) & 0xFF;
        });
        final byte[] alphaGradient = rgbaBytes(8, 4, (x, y, c) ->
                c == 3 ? 48 + x * 19 + y * 23 : (x * 31 + y * 17 + c * 29) & 0xFF);
        final byte[] dualPlane = rgbaBytes(8, 4, (x, y, c) -> {
            if (c == 0) {
                return ((x + y) & 1) == 0 ? 120 : 135;
            }
            if (c == 3) {
                return 255;
            }
            return 64 + x * 28 + y * 18 - (c == 2 ? 2 : 0);
        });

        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packTrivialRgb(
                lowVariance, 8, 4).length});
        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packMode6Rgb(
                gradient, 8, 4).length});
        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packAutoRgb(
                gradient, 8, 4).length});
        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packAutoRgba(
                alphaGradient, 8, 4).length});
        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packMode7Rgba(
                alphaGradient, 8, 4).length});
        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packMode1Or3Rgb(
                gradient, 8, 4).length});
        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packMode0Or2Rgb(
                gradient, 8, 4).length});
        assertArrayEquals(new int[] {32}, new int[] {Bc7Mode6RgbBlockPacker.packMode4DualPlaneRgb(
                dualPlane, 8, 4).length});
        assertThrows(BasisDecodeException.class, () -> Bc7Mode6RgbBlockPacker.packMode5DualPlaneRgb(
                dualPlane, 8, 4));
    }

    @Test
    public void testPackMode5SolidMatchesReferenceLayout() {
        assertPackMode5Solid(
                new byte[] {
                    0x20, 0x00, 0x4d, (byte) 0xe0, (byte) 0x9f, (byte) 0xfd, (byte) 0xef,
                    (byte) 0xed, (byte) 0xad, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa,
                    0x00, 0x00, 0x00, 0x00
                },
                17,
                85,
                204,
                123);
        assertPackMode5Solid(
                new byte[] {
                    0x20, (byte) 0xd6, 0x3f, 0x00, 0x00, 0x00, (byte) 0xfc, (byte) 0xff,
                    (byte) 0xaf, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa,
                    0x00, 0x00, 0x00, 0x00
                },
                200,
                0,
                0,
                255);
        assertPackMode5Solid(
                new byte[] {
                    0x20, (byte) 0x80, (byte) 0xc3, (byte) 0xfe, 0x0f, (byte) 0xfa,
                    0x03, 0x01, (byte) 0xad, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa,
                    0x00, 0x00, 0x00, 0x00
                },
                5,
                250,
                127,
                64);
    }

    @Test
    public void testPackMode4DualPlaneRgbEmitsStableReferenceLayout() {
        int[] rgba = new int[16 * 4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int base = (y * 4 + x) * 4;
                rgba[base] = ((x + y) & 1) == 0 ? 120 : 135;
                rgba[base + 1] = 64 + x * 28 + y * 18;
                rgba[base + 2] = 62 + x * 28 + y * 18;
                rgba[base + 3] = 255;
            }
        }

        byte[] output = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode4DualPlaneRgbBlock(rgba, output, 0);

        assertArrayEquals(
                new byte[] {
                    (byte) 0xb0, (byte) 0xff, 0x23, (byte) 0x8c,
                    (byte) 0xb0, 0x17, (byte) 0x9a, 0x67,
                    (byte) 0x98, 0x67, (byte) 0xc8, 0x18,
                    (byte) 0xb1, 0x5a, 0x3d, (byte) 0xfa
                },
                output);
    }

    @Test
    public void testPackMode5DualPlaneRgbEmitsStableReferenceLayout() {
        int[] greenPlaneMode5Rgba = {
            40, 74, 237, 255, 40, 74, 237, 255, 40, 73, 237, 255, 40, 73, 237, 255,
            41, 77, 237, 255, 41, 77, 237, 255, 41, 76, 237, 255, 41, 76, 237, 255,
            41, 77, 237, 255, 41, 77, 237, 255, 41, 76, 237, 255, 41, 76, 237, 255,
            44, 79, 241, 255, 44, 79, 241, 255, 40, 75, 237, 255, 40, 75, 237, 255
        };

        byte[] mode5 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode5DualPlaneRgbBlock(greenPlaneMode5Rgba, mode5, 0);
        assertArrayEquals(
                new byte[] {
                    (byte) 0xa0, 0x14, (byte) 0xcb, (byte) 0xff,
                    0x6f, (byte) 0xc7, 0x2b, 0x3d,
                    0x01, 0x00, 0x00, 0x1e,
                    0x00, 0x5a, 0x5a, 0x5f
                },
                mode5);

        byte[] auto = new byte[16];
        Bc7Mode6RgbBlockPacker.packAutoRgbBlock(greenPlaneMode5Rgba, auto, 0);
        assertArrayEquals(mode5, auto);
    }

    @Test
    public void testPackMode6RgbEmitsStableReferenceLayout() {
        int[] residualXuastc5x5Rgba = {
            0, 33, 222, 255, 0, 41, 214, 255, 0, 40, 215, 255, 0, 48, 207, 255,
            0, 33, 222, 255, 0, 41, 214, 255, 0, 40, 215, 255, 0, 48, 207, 255,
            0, 33, 222, 255, 0, 41, 214, 255, 0, 40, 215, 255, 0, 40, 215, 255,
            0, 33, 222, 255, 0, 41, 214, 255, 0, 40, 215, 255, 0, 40, 215, 255
        };

        byte[] mode6 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode6RgbBlock(residualXuastc5x5Rgba, mode6, 0);

        assertArrayEquals(
                new byte[] {
                    0x40, 0x00, 0x00, (byte) 0x82,
                    0x79, (byte) 0xa3, (byte) 0xff, 0x7f,
                    (byte) 0x82, (byte) 0xf7, (byte) 0x81, (byte) 0xf7,
                    (byte) 0x81, 0x77, (byte) 0x81, 0x77
                },
                mode6);
    }

    @Test
    public void testPackMode1Or3RgbEmitsStableReferenceLayouts() {
        int[] mode1Rgba = new int[16 * 4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int base = (y * 4 + x) * 4;
                if (x < 2) {
                    mode1Rgba[base] = 30 + x * 5 + y * 8;
                    mode1Rgba[base + 1] = 70 + x * 7 + y * 4;
                    mode1Rgba[base + 2] = 190 - x * 6 + y * 3;
                } else {
                    mode1Rgba[base] = 190 + x * 8 - y * 4;
                    mode1Rgba[base + 1] = 60 + x * 3 + y * 7;
                    mode1Rgba[base + 2] = 40 + x * 5 + y * 8;
                }
                mode1Rgba[base + 3] = 255;
            }
        }

        byte[] mode1 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode1Or3RgbBlock(mode1Rgba, mode1, 0);
        assertArrayEquals(
                new byte[] {
                    0x02, (byte) 0x87, 0x13, (byte) 0xd3,
                    0x51, 0x65, 0x41, 0x2e,
                    0x3c, 0x31, (byte) 0x93, 0x5b,
                    0x2b, (byte) 0xd9, (byte) 0xc9, 0x0f
                },
                mode1);

        int[] cancellationMode1Rgba = {
            0, 117, 138, 255, 0, 93, 162, 255, 0, 93, 162, 255, 0, 78, 177, 255,
            0, 117, 138, 255, 0, 93, 162, 255, 0, 93, 162, 255, 0, 78, 177, 255,
            0, 97, 158, 255, 0, 97, 158, 255, 0, 97, 158, 255, 0, 82, 173, 255,
            0, 97, 158, 255, 0, 97, 158, 255, 0, 97, 158, 255, 0, 82, 173, 255
        };
        byte[] cancellationMode1 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode1Or3RgbBlock(cancellationMode1Rgba, cancellationMode1, 0);
        assertArrayEquals(
                new byte[] {
                    0x22, 0x00, 0x00, 0x00,
                    0x1d, 0x45, 0x61, 0x22,
                    (byte) 0xbb, (byte) 0x9e, 0x40, 0x1e,
                    (byte) 0xe4, 0x49, (byte) 0x82, 0x3c
                },
                cancellationMode1);

        int[] mode3Rgba = new int[16 * 4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int base = (y * 4 + x) * 4;
                if (((x + y) & 1) == 0) {
                    mode3Rgba[base] = 104;
                    mode3Rgba[base + 1] = 110;
                    mode3Rgba[base + 2] = 116;
                } else {
                    mode3Rgba[base] = 96;
                    mode3Rgba[base + 1] = 112;
                    mode3Rgba[base + 2] = 124;
                }
                mode3Rgba[base + 3] = 255;
            }
        }

        byte[] mode3 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode1Or3RgbBlock(mode3Rgba, mode3, 0);
        assertArrayEquals(
                new byte[] {
                    0x28, (byte) 0xd2, 0x68, 0x30,
                    (byte) 0xd8, (byte) 0xed, (byte) 0x86, (byte) 0xc3,
                    (byte) 0xe9, 0x74, 0x3e, 0x1f,
                    0x00, 0x00, 0x00, 0x00
                },
                mode3);

        int[] nearlySymmetricMode3Rgba = {
            138, 52, 170, 255, 138, 52, 170, 255, 105, 0, 150, 255, 107, 0, 148, 255,
            130, 52, 178, 255, 130, 52, 178, 255, 101, 0, 154, 255, 107, 0, 148, 255,
            130, 52, 178, 255, 130, 52, 178, 255, 101, 0, 154, 255, 107, 0, 148, 255,
            130, 52, 178, 255, 130, 52, 178, 255, 101, 0, 154, 255, 107, 0, 148, 255
        };
        byte[] nearlySymmetricMode3 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode1Or3RgbBlock(nearlySymmetricMode3Rgba, nearlySymmetricMode3, 0);
        assertArrayEquals(
                new byte[] {
                    0x08, 0x14, (byte) 0x83, 0x35,
                    (byte) 0x99, 0x46, 0x03, 0x00,
                    0x54, (byte) 0xb3, (byte) 0xca, 0x26,
                    0x20, 0x7e, 0x7e, 0x7e
                },
                nearlySymmetricMode3);

        int[] residualXuastc6x6Mode3Rgba = {
            16, 166, 97, 255, 16, 166, 97, 255, 16, 166, 97, 255, 1, 217, 108, 255,
            16, 166, 97, 255, 16, 166, 97, 255, 16, 166, 97, 255, 2, 216, 108, 255,
            10, 165, 87, 255, 10, 165, 87, 255, 10, 165, 87, 255, 0, 215, 107, 255,
            10, 165, 87, 255, 10, 165, 87, 255, 10, 165, 87, 255, 0, 215, 107, 255
        };
        byte[] residualXuastc6x6Mode3 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode1Or3RgbBlock(residualXuastc6x6Mode3Rgba, residualXuastc6x6Mode3, 0);
        assertArrayEquals(
                new byte[] {
                    0x18, 0x20, 0x0a, (byte) 0x80,
                    (byte) 0xc0, 0x54, (byte) 0xba, 0x66,
                    (byte) 0xc7, 0x56, 0x35, (byte) 0x9b,
                    (byte) 0x81, (byte) 0x81, 0x7f, 0x7e
                },
                residualXuastc6x6Mode3);

        int[] residualXuastc8x6Mode1Rgba = {
            255, 0, 0, 255, 255, 0, 0, 255, 255, 0, 0, 255, 255, 0, 0, 255,
            255, 0, 0, 255, 255, 0, 0, 255, 251, 0, 4, 255, 215, 0, 40, 255,
            255, 0, 0, 255, 255, 0, 0, 255, 235, 0, 20, 255, 80, 0, 175, 255,
            255, 0, 0, 255, 255, 0, 0, 255, 227, 0, 28, 255, 20, 0, 235, 255
        };
        byte[] residualXuastc8x6Mode1 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode1Or3RgbBlock(residualXuastc8x6Mode1Rgba, residualXuastc8x6Mode1, 0);
        assertArrayEquals(
                new byte[] {
                    0x22, (byte) 0xbf, 0x5d, (byte) 0xe4,
                    0x00, 0x00, 0x00, (byte) 0x80,
                    (byte) 0xa2, 0x1f, 0x00, 0x00,
                    (byte) 0xc8, 0x01, 0x0a, 0x38
                },
                residualXuastc8x6Mode1);

        int[] residualXuastc8x6Mode3Rgba = {
            41, 188, 242, 255, 41, 188, 242, 255, 41, 188, 242, 255, 41, 188, 242, 255,
            0, 191, 235, 255, 0, 191, 235, 255, 0, 191, 235, 255, 0, 191, 235, 255,
            0, 195, 231, 255, 0, 195, 231, 255, 0, 195, 231, 255, 0, 193, 233, 255,
            0, 195, 231, 255, 0, 195, 231, 255, 0, 195, 231, 255, 0, 195, 231, 255
        };
        byte[] residualXuastc8x6Mode3 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode1Or3RgbBlock(residualXuastc8x6Mode3Rgba, residualXuastc8x6Mode3, 0);
        assertArrayEquals(
                new byte[] {
                    (byte) 0xe8, 0x54, 0x2a, 0x00,
                    (byte) 0x80, (byte) 0xd7, 0x1b, (byte) 0xfe,
                    (byte) 0xe6, (byte) 0xf3, (byte) 0xf3, 0x3a,
                    0x03, (byte) 0xfe, (byte) 0x81, 0x00
                },
                residualXuastc8x6Mode3);
    }

    @Test
    public void testPackMode0Or2RgbEmitsStableReferenceLayouts() {
        int[] mode0Rgba = {
            165, 43, 177, 255, 140, 81, 255, 255, 0, 161, 216, 255, 19, 74, 254, 255,
            180, 54, 255, 255, 185, 15, 255, 255, 18, 139, 184, 255, 0, 154, 215, 255,
            206, 113, 212, 255, 207, 103, 192, 255, 193, 58, 181, 255, 255, 10, 204, 255,
            204, 118, 228, 255, 205, 25, 176, 255, 238, 0, 184, 255, 255, 0, 185, 255
        };

        byte[] mode0 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode0Or2RgbBlock(mode0Rgba, mode0, 0);
        assertArrayEquals(
                new byte[] {
                    0x47, (byte) 0xf9, 0x17, 0x10,
                    0x0c, 0x26, (byte) 0xc9, (byte) 0x99,
                    (byte) 0xb5, 0x7f, (byte) 0xf9, 0x58,
                    0x08, (byte) 0xfe, (byte) 0xe3, 0x0c
                },
                mode0);

        int[] mode2Rgba = new int[16 * 4];
        int[][] colors = {
            {30, 80, 180},
            {190, 50, 60},
            {80, 210, 80}
        };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int base = (y * 4 + x) * 4;
                int subset = x < 2 ? 0 : y < 2 ? 1 : 2;
                mode2Rgba[base] = colors[subset][0] + x * 3 - y * 2;
                mode2Rgba[base + 1] = colors[subset][1] + x * 2 + y * 3;
                mode2Rgba[base + 2] = colors[subset][2] + x * 4 + y * 2;
                mode2Rgba[base + 3] = 255;
            }
        }

        byte[] mode2 = new byte[16];
        Bc7Mode6RgbBlockPacker.packMode0Or2RgbBlock(mode2Rgba, mode2, 0);
        assertArrayEquals(
                new byte[] {
                    0x2c, (byte) 0xc8, (byte) 0xc0, 0x58,
                    0x29, (byte) 0xb5, (byte) 0x8e, (byte) 0xd9,
                    (byte) 0xdb, (byte) 0xde, (byte) 0x84, (byte) 0xd8,
                    (byte) 0x82, 0x2b, (byte) 0xd4, 0x5e
                },
                mode2);
    }

    private static void assertPackMode5Solid(byte[] expected, int r, int g, int b, int a) {
        byte[] output = new byte[16];
        Bc7LdrBlockPacker.packMode5Solid(output, 0, r, g, b, a);
        assertArrayEquals(expected, output);
    }

    private static byte[] rgbaBytes(int width, int height, PixelFunction function) {
        byte[] rgba = new byte[width * height * 4];
        int out = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int c = 0; c < 4; c++) {
                    rgba[out++] = (byte) function.value(x, y, c);
                }
            }
        }
        return rgba;
    }

    private interface PixelFunction {
        int value(int x, int y, int component);
    }
}
