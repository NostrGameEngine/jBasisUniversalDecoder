package org.ngengine.basis.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledEncoderExecutableTest {
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    @TempDir Path directory;

    @Test
    void verifiesAllSixPlatformResourcesWithoutExecutingThem() throws Exception {
        List<String> platforms = Arrays.asList("darwin-aarch64", "darwin-x86_64", "linux-aarch64",
                "linux-x86_64", "windows-aarch64", "windows-x86_64");
        for (String platform : platforms) {
            String resource = platform + (platform.startsWith("windows-") ? "/basisu.exe" : "/basisu");
            Path output = directory.resolve(resource);
            try (InputStream input = getClass().getResourceAsStream("/basisu-bin/" + resource)) {
                BundledEncoderExecutable.extract(input, BundledEncoderExecutable.expectedChecksum(resource), output);
            }
            assertTrue(Files.size(output) > 0);
            assertTrue(Files.isExecutable(output));
        }
    }

    @Test
    void validExtractReplacesOldBytesAndMakesExecutable() throws Exception {
        Path output = directory.resolve("basisu");
        Files.writeString(output, "stale");
        BundledEncoderExecutable.extract(bytes("abc"), ABC_SHA256, output);
        assertEquals("abc", Files.readString(output));
        assertTrue(Files.isExecutable(output));
        assertFileCount(1);
    }

    @Test
    void mismatchFailsWithoutInstallingCorruptedBytesOrKeepingTemporaryFiles() throws Exception {
        Path output = directory.resolve("basisu");
        IOException failure = assertThrows(IOException.class,
                () -> BundledEncoderExecutable.extract(bytes("tampered"), ABC_SHA256, output));
        assertTrue(failure.getMessage().contains("SHA-256 mismatch"));
        assertFalse(Files.exists(output));
        assertFileCount(0);
    }

    @Test
    void failedCopyKeepsExistingOutputAndRemovesTemporaryFile() throws Exception {
        Path output = directory.resolve("basisu");
        Files.writeString(output, "previous");
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("broken-resource"); }
        };
        assertThrows(IOException.class, () -> BundledEncoderExecutable.extract(broken, ABC_SHA256, output));
        assertEquals("previous", Files.readString(output));
        assertFileCount(1);
    }

    @Test
    void missingOrInvalidChecksumIndexFailsClosed() {
        assertThrows(IOException.class, () -> BundledEncoderExecutable.expectedChecksum(null, "linux-x86_64/basisu"));
        assertThrows(IOException.class, () -> BundledEncoderExecutable.expectedChecksum(bytes(""), "linux-x86_64/basisu"));
        assertThrows(IOException.class, () -> BundledEncoderExecutable.expectedChecksum(
                bytes("linux-x86_64/basisu=invalid\n"), "linux-x86_64/basisu"));
        assertThrows(IOException.class, () -> BundledEncoderExecutable.extract(bytes("abc"), null, directory.resolve("basisu")));
    }

    private void assertFileCount(long expected) throws IOException {
        try (var files = Files.list(directory)) {
            assertEquals(expected, files.count());
        }
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
