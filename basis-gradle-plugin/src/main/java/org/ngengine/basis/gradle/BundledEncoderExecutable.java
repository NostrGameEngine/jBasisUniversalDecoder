package org.ngengine.basis.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/** Verifies extracted resources before granting execute permission or exposing the final path. */
final class BundledEncoderExecutable {
    private static final String CHECKSUMS = "/META-INF/native-basis/encoder-sha256.properties";

    private BundledEncoderExecutable() {}

    static String expectedChecksum(String resourcePath) throws IOException {
        try (InputStream input = BundledEncoderExecutable.class.getResourceAsStream(CHECKSUMS)) {
            return expectedChecksum(input, resourcePath);
        }
    }

    static String expectedChecksum(InputStream input, String resourcePath) throws IOException {
        if (input == null) {
            throw new IOException("Missing bundled encoder checksum index: " + CHECKSUMS);
        }
        Properties checksums = new Properties();
        checksums.load(input);
        String checksum = checksums.getProperty(resourcePath);
        requireChecksum(checksum, resourcePath);
        return checksum;
    }

    static void extract(InputStream input, String expectedChecksum, Path output) throws IOException {
        requireChecksum(expectedChecksum, output.toString());
        Files.createDirectories(output.getParent());
        // Never copy into a pre-existing path or follow an old executable's symlink.
        Path temporary = Files.createTempFile(output.getParent(), "basisu-verified-", ".tmp");
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            String actual = sha256(temporary);
            if (!actual.equalsIgnoreCase(expectedChecksum)) {
                throw new IOException("Bundled encoder SHA-256 mismatch for " + output);
            }
            if (!temporary.toFile().setExecutable(true, true) || !Files.isExecutable(temporary)) {
                throw new IOException("Unable to make verified bundled encoder executable: " + output);
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void requireChecksum(String checksum, String resourcePath) throws IOException {
        if (checksum == null || !checksum.matches("[0-9a-fA-F]{64}")) {
            throw new IOException("Missing or invalid bundled encoder SHA-256 for " + resourcePath);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }
}
