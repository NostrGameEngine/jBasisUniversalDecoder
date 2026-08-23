package org.ngengine.basis.viewer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.ngengine.basis.BasisDecodeException;
import org.ngengine.basis.BasisDecodeRequest;
import org.ngengine.basis.BasisDecodeResult;
import org.ngengine.basis.BasisDecoder;
import org.ngengine.basis.BasisDecoderFactory;
import org.ngengine.basis.BasisTranscodeTarget;

final class ViewerDocumentLoader {
    private static final List<BasisTranscodeTarget> DISPLAYABLE_TARGETS = List.of(
            BasisTranscodeTarget.RGBA8,
            BasisTranscodeTarget.RGB565,
            BasisTranscodeTarget.BGR565,
            BasisTranscodeTarget.RGBA4444,
            BasisTranscodeTarget.RGB_HALF,
            BasisTranscodeTarget.RGBA_HALF,
            BasisTranscodeTarget.RGB_9E5);

    private ViewerDocumentLoader() {
    }

    static ViewerDocument load(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        if (isBasisUniversal(file, data)) {
            return loadBasisUniversal(file, data);
        }
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException("Unsupported image/container file: " + file);
        }
        List<ViewerImage> images = List.of(new ViewerImage("Original image", image, "ImageIO decoded image"));
        return new ViewerDocument(file, "ImageIO image", images, List.of());
    }

    private static ViewerDocument loadBasisUniversal(Path file, byte[] data) {
        BasisDecoder decoder = BasisDecoderFactory.createDefault();
        List<ViewerImage> images = new ArrayList<>();
        List<String> skippedTargets = new ArrayList<>();
        for (BasisTranscodeTarget target : DISPLAYABLE_TARGETS) {
            try {
                BasisDecodeResult result = decoder.decode(BasisDecodeRequest.builder(data)
                        .target(target)
                        .build());
                BufferedImage image = DecodedImageRenderer.toBufferedImage(result);
                images.add(new ViewerImage(
                        target.name(),
                        image,
                        result.getImageFormat()
                                + ", " + result.getWidth() + "x" + result.getHeight()
                                + ", images=" + result.getImageCount()
                                + ", levels=" + result.getLevelCount()
                                + ", colorSpace=" + result.getColorSpace()));
            } catch (BasisDecodeException | IllegalArgumentException exception) {
                skippedTargets.add(target.name() + ": " + exception.getMessage());
            }
        }
        if (images.isEmpty()) {
            throw new BasisDecodeException("No AWT-displayable decode target succeeded for " + file);
        }
        return new ViewerDocument(file, "Basis Universal/KTX2", images, skippedTargets);
    }

    private static boolean isBasisUniversal(Path file, byte[] data) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".basis") || name.endsWith(".ktx") || name.endsWith(".ktx2")) {
            return true;
        }
        return data.length >= 12
                && (isKtx2(data) || isBasis(data));
    }

    private static boolean isKtx2(byte[] data) {
        byte[] magic = {
                (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBasis(byte[] data) {
        return data.length >= 2 && data[0] == 's' && data[1] == 'B';
    }
}
