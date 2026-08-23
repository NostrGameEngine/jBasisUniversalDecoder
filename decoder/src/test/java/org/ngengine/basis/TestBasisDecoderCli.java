package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.ngengine.basis.cli.BasisDecoderCli;

public class TestBasisDecoderCli {

    @Test
    public void testHelpIsPrinted() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{"--help"},
                new PrintStream(outBuffer), new PrintStream(errBuffer));

        String output = outBuffer.toString().trim();
        assertEquals(0, code);
        assertTrue(output.contains("Usage: java -cp"));
        assertTrue(output.contains("--input"));
        assertTrue(output.contains("--output"));
        assertTrue(output.contains("--threads"));
        assertTrue(output.contains("--image-index"));
        assertTrue(output.contains("--strict"));
        assertTrue(output.contains("--preset"));
    }

    @Test
    public void testDecodeFixtureToOutputFile() throws Exception {
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        Path input = Files.createTempFile("basis-decoder-cli-in-", ".ktx2");
        Path output = Files.createTempFile("basis-decoder-cli-out-", ".raw");
        Files.write(input, payload);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        try {
            int code = BasisDecoderCli.run(new String[]{
                    "--input", input.toString(),
                    "--output", output.toString(),
                    "--format", "RGBA8",
                    "--linear-color-space", "true"
            }, new PrintStream(outBuffer), new PrintStream(errBuffer));

            assertEquals(0, code);
            assertTrue(errBuffer.toString().trim().isEmpty(),
                    "Unexpected stderr: " + errBuffer.toString().trim());
            assertEquals(4, Files.size(output));
            assertTrue(outBuffer.toString().trim().startsWith("OK "));

            byte[] raw = Files.readAllBytes(output);
            assertArrayEquals(new byte[]{9, 8, 7, 6}, raw);
        } finally {
            cleanup(input);
            cleanup(output);
        }
    }

    @Test
    public void testDecodeSupportsThreadsStrictAndPresetOptions() throws Exception {
        byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
        Path input = Files.createTempFile("basis-decoder-cli-threads-", ".ktx2");
        Path output = Files.createTempFile("basis-decoder-cli-out-threads-", ".raw");
        Files.write(input, payload);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        try {
            int code = BasisDecoderCli.run(new String[]{
                    "--input", input.toString(),
                    "--output", output.toString(),
                    "--format", "RGBA8",
                    "--linear-color-space", "true",
                    "--threads", "4",
                    "--strict", "true",
                    "--preset", "high_quality",
                    "--quality", "10",
            }, new PrintStream(outBuffer), new PrintStream(errBuffer));

            assertEquals(0, code);
            assertEquals(0, errBuffer.size());
            assertEquals(4, Files.size(output));
            assertTrue(outBuffer.toString().trim().startsWith("OK "));
        } finally {
            cleanup(input);
            cleanup(output);
        }
    }

    @Test
    public void testDecodeBasisArrayImageIndexToOutputFile() throws Exception {
        byte[] payload = loadFixture("fixtures/basis/kodim20_21_array.basis");
        Path input = Files.createTempFile("basis-decoder-cli-array-in-", ".basis");
        Path output = Files.createTempFile("basis-decoder-cli-array-out-", ".raw");
        Files.write(input, payload);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        try {
            int code = BasisDecoderCli.run(new String[]{
                    "--input", input.toString(),
                    "--output", output.toString(),
                    "--format", "RGBA8",
                    "--image-index", "1"
            }, new PrintStream(outBuffer), new PrintStream(errBuffer));

            assertEquals(0, code);
            assertEquals(0, errBuffer.size());
            assertTrue(outBuffer.toString().trim().startsWith("OK format=RGBA8"));
            assertArrayEquals(loadFixture("fixtures/native_gold/kodim20_21_array_image1.dat"),
                    Files.readAllBytes(output));
        } finally {
            cleanup(input);
            cleanup(output);
        }
    }

    @Test
    public void testInvalidThreadsOptionRejected() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{
                "--input", "fixtures/uncompressed-rgba8-ktx2.bin",
                "--output", "/tmp/bad-threads.out",
                "--threads", "0"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer));

        assertEquals(2, code);
        assertTrue(errBuffer.toString().contains("--threads must be in range"));
    }

    @Test
    public void testInvalidQualityOptionRejected() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{
                "--input", "fixtures/uncompressed-rgba8-ktx2.bin",
                "--output", "/tmp/bad-quality.out",
                "--quality", "999"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer));

        assertEquals(2, code);
        assertTrue(errBuffer.toString().contains("--quality must be in range"));
    }

    @Test
    public void testInvalidFormatReturnsValidationError() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{
                "--input", "missing.ktx2",
                "--output", "x",
                "--format", "BAD"
        },
                new PrintStream(outBuffer), new PrintStream(errBuffer));

        String errText = errBuffer.toString().trim();

        assertEquals(2, code);
        assertTrue(errText.contains("Unsupported --format value"));
    }

    @Test
    public void testUnknownOptionRejected() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{
                "--input", "fixtures/uncompressed-rgba8-ktx2.bin",
                "--output", "/tmp/unknown-option.out",
                "--no-such-flag", "42"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer));

        String errText = errBuffer.toString().trim();

        assertEquals(2, code);
        assertTrue(errText.contains("Unsupported option"));
    }

    @Test
    public void testHelpContainsAllSupportedOptions() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{"--help"},
                new PrintStream(outBuffer), new PrintStream(errBuffer));

        String usage = outBuffer.toString();

        assertEquals(0, code);
        assertTrue(usage.contains("--input"));
        assertTrue(usage.contains("--output"));
        assertTrue(usage.contains("--format"));
        assertTrue(usage.contains("--linear-color-space"));
        assertTrue(usage.contains("--quality"));
        assertTrue(usage.contains("--threads"));
        assertTrue(usage.contains("--image-index"));
        assertTrue(usage.contains("--strict"));
        assertTrue(usage.contains("--preset"));
    }

    @Test
    public void testInvalidBooleanOptionRejected() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{
                "--input", "fixtures/uncompressed-rgba8-ktx2.bin",
                "--output", "/tmp/bad-bool.out",
                "--linear-color-space", "yes"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer));

        assertEquals(2, code);
        assertTrue(errBuffer.toString().contains("Expected boolean value for --linear-color-space"));
    }

    @Test
    public void testDefaultsMatchRequestContract() throws Exception {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        Path input = null;
        Path output = null;
        try {
            byte[] payload = loadFixture("fixtures/uncompressed-rgba8-ktx2.bin");
            input = Files.createTempFile("basis-decoder-cli-defaults-in-", ".ktx2");
            output = Files.createTempFile("basis-decoder-cli-defaults-out-", ".raw");
            Files.write(input, payload);

            int code = BasisDecoderCli.run(new String[]{
                    "--input", input.toString(),
                    "--output", output.toString(),
                    "--format", "RGBA8"
            }, new PrintStream(outBuffer), new PrintStream(errBuffer));

            String errText = errBuffer.toString().trim();

            assertEquals(0, code);
            assertTrue(errText.isEmpty(), "Unexpected stderr: " + errText);
        } finally {
            cleanup(input);
            cleanup(output);
        }
    }

    @Test
    public void testMissingRequiredArgumentReturnsUsage() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int code = BasisDecoderCli.run(new String[]{"--input", "foo"},
                new PrintStream(outBuffer), new PrintStream(errBuffer));

        String errText = errBuffer.toString().trim();

        assertEquals(2, code);
        assertTrue(errText.contains("--input and --output are required"));
    }

    @Test
    public void testSupercompressedFailsWithJavaOnlyBackend() throws IOException {
        byte[] payload = loadFixture("fixtures/supercompressed-unsupported-ktx2.bin");
        Path input = Files.createTempFile("basis-decoder-cli-in-unsupported-", ".ktx2");
        Path output = Files.createTempFile("basis-decoder-cli-out-error-", ".raw");
        Files.write(input, payload);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        try {
            int code = BasisDecoderCli.run(new String[]{
                    "--input", input.toString(),
                    "--output", output.toString(),
            }, new PrintStream(outBuffer), new PrintStream(errBuffer));

            assertEquals(1, code);
            assertTrue(errBuffer.toString().contains("Error:"));
        } finally {
            cleanup(input);
            cleanup(output);
        }
    }

    private static void cleanup(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort in tests
        }
    }

    private static byte[] loadFixture(String name) {
        try (InputStream stream = TestBasisDecoderCli.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[1024];
            int read;
            while ((read = stream.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Could not read fixture: " + name, e);
        }
    }
}
