package org.ngengine.basis.viewer;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.ngengine.basis.BasisDecodeException;
import org.ngengine.basis.BasisDecodeResult;
import org.ngengine.basis.BasisImageFormat;

final class DecodedImageRenderer {

    private DecodedImageRenderer() {
    }

    static BufferedImage toBufferedImage(BasisDecodeResult result) {
        ByteBuffer data = result.getPixelData().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        BufferedImage image = new BufferedImage(
                result.getWidth(),
                result.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        BasisImageFormat format = result.getImageFormat();
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                image.setRGB(x, y, readArgb(data, format));
            }
        }
        return image;
    }

    private static int readArgb(ByteBuffer data, BasisImageFormat format) {
        switch (format) {
            case RGBA8:
                return argb(
                        Byte.toUnsignedInt(data.get()),
                        Byte.toUnsignedInt(data.get()),
                        Byte.toUnsignedInt(data.get()),
                        Byte.toUnsignedInt(data.get()));
            case RGB565:
                return rgb565ToArgb(Short.toUnsignedInt(data.getShort()), false);
            case BGR565:
                return rgb565ToArgb(Short.toUnsignedInt(data.getShort()), true);
            case RGBA4444:
                return rgba4444ToArgb(Short.toUnsignedInt(data.getShort()));
            case RGB_HALF:
                return argb(
                        floatToByte(halfToFloat(Short.toUnsignedInt(data.getShort()))),
                        floatToByte(halfToFloat(Short.toUnsignedInt(data.getShort()))),
                        floatToByte(halfToFloat(Short.toUnsignedInt(data.getShort()))),
                        255);
            case RGBA_HALF:
                return argb(
                        floatToByte(halfToFloat(Short.toUnsignedInt(data.getShort()))),
                        floatToByte(halfToFloat(Short.toUnsignedInt(data.getShort()))),
                        floatToByte(halfToFloat(Short.toUnsignedInt(data.getShort()))),
                        floatToByte(halfToFloat(Short.toUnsignedInt(data.getShort()))));
            case RGB_9E5:
                return rgb9e5ToArgb(data.getInt());
            default:
                throw new BasisDecodeException("Cannot render " + format + " with AWT");
        }
    }

    private static int rgb565ToArgb(int packed, boolean bgr) {
        int first = (packed >>> 11) & 0x1F;
        int green = (packed >>> 5) & 0x3F;
        int last = packed & 0x1F;
        int red = bgr ? last : first;
        int blue = bgr ? first : last;
        return argb(expand5(red), expand6(green), expand5(blue), 255);
    }

    private static int rgba4444ToArgb(int packed) {
        int red = (packed >>> 12) & 0xF;
        int green = (packed >>> 8) & 0xF;
        int blue = (packed >>> 4) & 0xF;
        int alpha = packed & 0xF;
        return argb(expand4(red), expand4(green), expand4(blue), expand4(alpha));
    }

    private static int rgb9e5ToArgb(int packed) {
        int mantissaR = packed & 0x1FF;
        int mantissaG = (packed >>> 9) & 0x1FF;
        int mantissaB = (packed >>> 18) & 0x1FF;
        int exponent = (packed >>> 27) & 0x1F;
        float scale = (float) Math.pow(2.0, exponent - 24);
        return argb(
                floatToByte(mantissaR * scale),
                floatToByte(mantissaG * scale),
                floatToByte(mantissaB * scale),
                255);
    }

    private static float halfToFloat(int half) {
        int sign = (half >>> 15) & 0x1;
        int exponent = (half >>> 10) & 0x1F;
        int mantissa = half & 0x3FF;
        float value;
        if (exponent == 0) {
            value = mantissa == 0 ? 0.0f : (float) Math.scalb(mantissa / 1024.0f, -14);
        } else if (exponent == 31) {
            value = mantissa == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
        } else {
            value = (float) Math.scalb(1.0f + mantissa / 1024.0f, exponent - 15);
        }
        return sign == 0 ? value : -value;
    }

    private static int floatToByte(float value) {
        if (Float.isNaN(value) || value <= 0.0f) {
            return 0;
        }
        if (value >= 1.0f || Float.isInfinite(value)) {
            return 255;
        }
        return Math.round(value * 255.0f);
    }

    private static int expand4(int value) {
        return (value << 4) | value;
    }

    private static int expand5(int value) {
        return (value << 3) | (value >>> 2);
    }

    private static int expand6(int value) {
        return (value << 2) | (value >>> 4);
    }

    private static int argb(int red, int green, int blue, int alpha) {
        return ((alpha & 0xFF) << 24)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
    }
}
