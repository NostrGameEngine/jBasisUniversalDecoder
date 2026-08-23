package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasisuReferenceBc7SolidParityTest {

    @TempDir
    Path tempDir;

    @Test
    void mode5SolidBlocksMatchOfficialXuastcBc7Transcode() throws Exception {
        Path inputPng = tempDir.resolve("solid.png");
        writeSolidPng(inputPng, 17, 85, 204, 123);

        Path encodedKtx2 = tempDir.resolve("solid-xuastc.ktx2");
        encodeXuastc(inputPng, encodedKtx2);

        Path officialDir = Files.createDirectories(tempDir.resolve("official-bc7"));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-unpack",
                "-ktx_only",
                "-format_only",
                "6",
                encodedKtx2.toString()),
                officialDir);

        byte[] officialPayload = readFirstKtxLevelPayload(newestKtx(officialDir));
        byte[] expectedBlock = new byte[16];
        Bc7LdrBlockPacker.packMode5Solid(expectedBlock, 0, 17, 85, 204, 123);
        assertEquals(16 * 16, officialPayload.length);
        for (int offset = 0; offset < officialPayload.length; offset += 16) {
            byte[] actualBlock = new byte[16];
            System.arraycopy(officialPayload, offset, actualBlock, 0, actualBlock.length);
            assertArrayEquals(expectedBlock, actualBlock, "BC7 block at byte " + offset);
        }
    }

    @Test
    void trivialMode6RgbBlocksMatchOfficialXuastcBc7Transcode() throws Exception {
        Path inputPng = tempDir.resolve("low-variance.png");
        writeLowVariancePng(inputPng);

        Path encodedKtx2 = tempDir.resolve("low-variance-xuastc.ktx2");
        encodeXuastc(inputPng, encodedKtx2);

        Path officialDir = Files.createDirectories(tempDir.resolve("official-bc7-mode6"));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-no_fast_xuastc_ldr_bc7_transcoding",
                "-unpack",
                "-ktx_only",
                "-format_only",
                "6",
                encodedKtx2.toString()),
                officialDir);

        byte[] encodedBytes = Files.readAllBytes(encodedKtx2);
        BasisDecodeResult javaRgba = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(BasisTranscodeTarget.RGBA8)
                        .build());
        byte[] javaBc7 = Bc7Mode6RgbBlockPacker.packTrivialRgb(
                readBuffer(javaRgba.getPixelData()),
                javaRgba.getWidth(),
                javaRgba.getHeight());

        assertArrayEquals(readFirstKtxLevelPayload(newestKtx(officialDir)), javaBc7);
    }

    @Test
    void leastSquaresMode6RgbBlocksMatchOfficialXuastcBc7Transcode() throws Exception {
        Path inputPng = tempDir.resolve("correlated-gradient.png");
        writeCorrelatedGradientPng(inputPng);

        Path encodedKtx2 = tempDir.resolve("correlated-gradient-xuastc.ktx2");
        encodeXuastc(inputPng, encodedKtx2);

        Path officialDir = Files.createDirectories(tempDir.resolve("official-bc7-mode6-ls"));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-no_fast_xuastc_ldr_bc7_transcoding",
                "-unpack",
                "-ktx_only",
                "-format_only",
                "6",
                encodedKtx2.toString()),
                officialDir);

        byte[] encodedBytes = Files.readAllBytes(encodedKtx2);
        BasisDecodeResult javaRgba = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(BasisTranscodeTarget.RGBA8)
                        .build());
        byte[] javaBc7 = Bc7Mode6RgbBlockPacker.packMode6Rgb(
                readBuffer(javaRgba.getPixelData()),
                javaRgba.getWidth(),
                javaRgba.getHeight());

        assertArrayEquals(readFirstKtxLevelPayload(newestKtx(officialDir)), javaBc7);
    }

    @Test
    void mode5DualPlaneRgbBlocksMatchOfficialXuastcBc7Transcode() throws Exception {
        Path inputPng = tempDir.resolve("dual-plane-mode5.png");
        writeDualPlaneMode5Png(inputPng);

        Path encodedKtx2 = tempDir.resolve("dual-plane-mode5-xuastc.ktx2");
        encodeXuastc(inputPng, encodedKtx2);

        Path officialDir = Files.createDirectories(tempDir.resolve("official-bc7-mode5-dual-plane"));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-no_fast_xuastc_ldr_bc7_transcoding",
                "-unpack",
                "-ktx_only",
                "-format_only",
                "6",
                encodedKtx2.toString()),
                officialDir);

        byte[] encodedBytes = Files.readAllBytes(encodedKtx2);
        BasisDecodeResult javaRgba = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(BasisTranscodeTarget.RGBA8)
                        .build());
        byte[] javaBc7 = Bc7Mode6RgbBlockPacker.packMode5DualPlaneRgb(
                readBuffer(javaRgba.getPixelData()),
                javaRgba.getWidth(),
                javaRgba.getHeight());

        assertArrayEquals(readFirstKtxLevelPayload(newestKtx(officialDir)), javaBc7);
    }

    @Test
    void mode1Or3SubsetRgbBlocksMatchOfficialXuastcBc7Transcode() throws Exception {
        Path inputPng = tempDir.resolve("subset-mode13.png");
        writeSubsetMode13Png(inputPng);

        Path encodedKtx2 = tempDir.resolve("subset-mode13-xuastc.ktx2");
        encodeXuastc(inputPng, encodedKtx2);

        Path officialDir = Files.createDirectories(tempDir.resolve("official-bc7-mode13"));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-no_fast_xuastc_ldr_bc7_transcoding",
                "-unpack",
                "-ktx_only",
                "-format_only",
                "6",
                encodedKtx2.toString()),
                officialDir);

        byte[] encodedBytes = Files.readAllBytes(encodedKtx2);
        BasisDecodeResult javaRgba = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(BasisTranscodeTarget.RGBA8)
                        .build());
        byte[] javaBc7 = Bc7Mode6RgbBlockPacker.packMode1Or3Rgb(
                readBuffer(javaRgba.getPixelData()),
                javaRgba.getWidth(),
                javaRgba.getHeight());

        assertArrayEquals(readFirstKtxLevelPayload(newestKtx(officialDir)), javaBc7);
    }

    @Test
    void mode0Or2SubsetRgbBlocksMatchOfficialXuastcBc7Transcode() throws Exception {
        assertMode0Or2SubsetRgbBlocksMatchOfficial("subset-mode0", true);
        assertMode0Or2SubsetRgbBlocksMatchOfficial("subset-mode2", false);
    }

    @Test
    void autoRgbBlocksMatchOfficialXuastcBc7Transcode() throws Exception {
        assertAutoRgbBlocksMatchOfficial("auto-low-variance", FixtureKind.LOW_VARIANCE);
        assertAutoRgbBlocksMatchOfficial("auto-correlated-gradient", FixtureKind.CORRELATED_GRADIENT);
        assertAutoRgbBlocksMatchOfficial("auto-dual-plane-mode5", FixtureKind.DUAL_PLANE_MODE5);
        assertAutoRgbBlocksMatchOfficial("auto-subset-mode13", FixtureKind.SUBSET_MODE13);
        assertAutoRgbBlocksMatchOfficial("auto-subset-mode0", FixtureKind.SUBSET_MODE0);
        assertAutoRgbBlocksMatchOfficial("auto-subset-mode2", FixtureKind.SUBSET_MODE2);
    }


    private static void encodeXuastc(Path inputPng, Path encodedKtx2) throws IOException, InterruptedException {
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-ktx2",
                "-xuastc_ldr_4x4",
                "-xuastc_zstd",
                "-output_file",
                encodedKtx2.toString(),
                inputPng.toString()));
        assertTrue(Files.isRegularFile(encodedKtx2), "basisu did not create " + encodedKtx2);
    }

    private static void writeSolidPng(Path output, int r, int g, int b, int a) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int argb = (a << 24) | (r << 16) | (g << 8) | b;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writeLowVariancePng(Path output) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int v = 112 + (x & 3) + (y & 3);
                image.setRGB(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writeCorrelatedGradientPng(Path output) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int v = 36 + (x & 3) * 20 + (y & 3) * 14;
                image.setRGB(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writeDualPlaneMode5Png(Path output) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int localX = x & 3;
                int localY = y & 3;
                int red = ((localX + localY) & 1) == 0 ? 124 : 132;
                int green = 126 + localX * 3 + localY * 2;
                int blue = 125 + localX * 3 + localY * 2;
                image.setRGB(x, y, 0xFF000000 | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writeSubsetMode13Png(Path output) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int localX = x & 3;
                int localY = y & 3;
                int red;
                int green;
                int blue;
                if (localX < 2) {
                    red = 30 + localX * 5 + localY * 8;
                    green = 70 + localX * 7 + localY * 4;
                    blue = 190 - localX * 6 + localY * 3;
                } else {
                    red = 190 + localX * 8 - localY * 4;
                    green = 60 + localX * 3 + localY * 7;
                    blue = 40 + localX * 5 + localY * 8;
                }
                image.setRGB(x, y, 0xFF000000 | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private void assertMode0Or2SubsetRgbBlocksMatchOfficial(String stem, boolean mode0)
            throws IOException, InterruptedException {
        Path inputPng = tempDir.resolve(stem + ".png");
        if (mode0) {
            writeSubsetMode0Png(inputPng);
        } else {
            writeSubsetMode2Png(inputPng);
        }

        Path encodedKtx2 = tempDir.resolve(stem + "-xuastc.ktx2");
        encodeXuastc(inputPng, encodedKtx2);

        Path officialDir = Files.createDirectories(tempDir.resolve("official-bc7-" + stem));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-no_fast_xuastc_ldr_bc7_transcoding",
                "-unpack",
                "-ktx_only",
                "-format_only",
                "6",
                encodedKtx2.toString()),
                officialDir);

        byte[] encodedBytes = Files.readAllBytes(encodedKtx2);
        BasisDecodeResult javaRgba = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(BasisTranscodeTarget.RGBA8)
                        .build());
        byte[] javaBc7 = Bc7Mode6RgbBlockPacker.packMode0Or2Rgb(
                readBuffer(javaRgba.getPixelData()),
                javaRgba.getWidth(),
                javaRgba.getHeight());

        assertArrayEquals(readFirstKtxLevelPayload(newestKtx(officialDir)), javaBc7);
    }

    private void assertAutoRgbBlocksMatchOfficial(String stem, FixtureKind fixtureKind)
            throws IOException, InterruptedException {
        Path inputPng = tempDir.resolve(stem + ".png");
        writeFixture(inputPng, fixtureKind);

        Path encodedKtx2 = tempDir.resolve(stem + "-xuastc.ktx2");
        encodeXuastc(inputPng, encodedKtx2);

        Path officialDir = Files.createDirectories(tempDir.resolve("official-bc7-" + stem));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-no_fast_xuastc_ldr_bc7_transcoding",
                "-unpack",
                "-ktx_only",
                "-format_only",
                "6",
                encodedKtx2.toString()),
                officialDir);

        byte[] encodedBytes = Files.readAllBytes(encodedKtx2);
        BasisDecodeResult javaRgba = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(BasisTranscodeTarget.RGBA8)
                        .build());
        byte[] javaBc7 = Bc7Mode6RgbBlockPacker.packAutoRgb(
                readBuffer(javaRgba.getPixelData()),
                javaRgba.getWidth(),
                javaRgba.getHeight());

        assertArrayEquals(readFirstKtxLevelPayload(newestKtx(officialDir)), javaBc7, stem);
    }

    private static void writeFixture(Path output, FixtureKind fixtureKind) throws IOException {
        switch (fixtureKind) {
            case LOW_VARIANCE:
                writeLowVariancePng(output);
                break;
            case CORRELATED_GRADIENT:
                writeCorrelatedGradientPng(output);
                break;
            case DUAL_PLANE_MODE5:
                writeDualPlaneMode5Png(output);
                break;
            case SUBSET_MODE13:
                writeSubsetMode13Png(output);
                break;
            case SUBSET_MODE0:
                writeSubsetMode0Png(output);
                break;
            case SUBSET_MODE2:
                writeSubsetMode2Png(output);
                break;
            default:
                throw new AssertionError("Unhandled fixture kind: " + fixtureKind);
        }
    }

    private static void writeSubsetMode0Png(Path output) throws IOException {
        int[] block = {
            165, 43, 177, 140, 81, 255, 0, 161, 216, 19, 74, 254,
            180, 54, 255, 185, 15, 255, 18, 139, 184, 0, 154, 215,
            206, 113, 212, 207, 103, 192, 193, 58, 181, 255, 10, 204,
            204, 118, 228, 205, 25, 176, 238, 0, 184, 255, 0, 185
        };
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int base = ((y & 3) * 4 + (x & 3)) * 3;
                int red = block[base];
                int green = block[base + 1];
                int blue = block[base + 2];
                image.setRGB(x, y, 0xFF000000 | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writeSubsetMode2Png(Path output) throws IOException {
        int[][] colors = {
            {30, 80, 180},
            {190, 50, 60},
            {80, 210, 80}
        };
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int localX = x & 3;
                int localY = y & 3;
                int subset = localX < 2 ? 0 : localY < 2 ? 1 : 2;
                int red = colors[subset][0] + localX * 3 - localY * 2;
                int green = colors[subset][1] + localX * 2 + localY * 3;
                int blue = colors[subset][2] + localX * 4 + localY * 2;
                image.setRGB(x, y, 0xFF000000 | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static Path basisuExecutable() {
        String configured = System.getProperty("basisu.executable");
        assertTrue(configured != null && !configured.isBlank(), "Missing basisu.executable system property");
        Path executable = Path.of(configured);
        assertTrue(Files.isExecutable(executable), "basisu executable is not available: " + executable);
        return executable;
    }

    private static Path newestKtx(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ktx"))
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new AssertionError("basisu did not unpack a KTX in " + directory));
        }
    }

    private static byte[] readFirstKtxLevelPayload(Path ktx) throws IOException {
        byte[] data = Files.readAllBytes(ktx);
        byte[] identifier = {
            (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        for (int i = 0; i < identifier.length; i++) {
            assertEquals(identifier[i], data[i], "KTX identifier byte " + i);
        }
        ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x04030201, header.getInt(12), "KTX endianness");
        int keyValueBytes = header.getInt(60);
        int imageSizeOffset = 64 + keyValueBytes;
        int imageSize = header.getInt(imageSizeOffset);
        byte[] payload = new byte[imageSize];
        System.arraycopy(data, imageSizeOffset + 4, payload, 0, imageSize);
        return payload;
    }

    private static byte[] readBuffer(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] data = new byte[copy.remaining()];
        copy.get(data);
        return data;
    }

    private static void run(List<String> command) throws IOException, InterruptedException {
        run(command, null);
    }

    private static void run(List<String> command, Path directory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .directory(directory == null ? null : directory.toFile());
        Process process = processBuilder.start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("Command failed (" + exitCode + "): "
                    + String.join(" ", command) + "\n" + new String(output, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private enum FixtureKind {
        LOW_VARIANCE,
        CORRELATED_GRADIENT,
        DUAL_PLANE_MODE5,
        SUBSET_MODE13,
        SUBSET_MODE0,
        SUBSET_MODE2
    }
}
