package org.ngengine.basis.gradle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasisTextureEncoderPluginFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void nonExecutableCustomEncoderIsRejectedWithoutChangingItsPermissions() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeFalse(isWindows(), "Executable mode regression requires POSIX permissions");
        writeProject(false, true);
        Path executable = projectDir.resolve("basisu-fake");
        Files.setPosixFilePermissions(executable, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var before = Files.getPosixFilePermissions(executable);
        byte[] content = Files.readAllBytes(executable);
        var result = GradleRunner.create().withProjectDir(projectDir.toFile())
                .withArguments("encodeBasisTextures", "--stacktrace", "--max-workers=1")
                .withPluginClasspath().buildAndFail();
        assertTrue(result.getOutput().contains("Configured basisuExecutable is not executable"));
        assertEquals(before, Files.getPosixFilePermissions(executable));
        assertArrayEquals(content, Files.readAllBytes(executable));
    }

    @Test
    void bundledEncoderCanBeResolvedWithoutRunningIt() throws IOException {
        writeProject(false, false);
        Files.writeString(projectDir.resolve("build.gradle"),
                "\ntasks.register('resolveOnly') { doLast {\n"
                        + "  def encoder = tasks.named('encodeBasisTextures').get().resolveBasisuExecutable().toFile()\n"
                        + "  assert encoder.isFile() && encoder.length() > 0 && encoder.canExecute()\n"
                        + "} }\n", java.nio.file.StandardOpenOption.APPEND);
        var result = GradleRunner.create().withProjectDir(projectDir.toFile())
                .withArguments("resolveOnly", "--stacktrace", "--max-workers=1")
                .withPluginClasspath().build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveOnly").getOutcome());
    }

    @Test
    void generatedBasisFilesAreAddedToJavaResourcesWithoutChangingSources() throws IOException {
        writeProject(false, true);

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("processResources", "--stacktrace")
                .withPluginClasspath()
                .build();

        assertTrue(result.task(":encodeBasisTextures").getOutcome() == TaskOutcome.SUCCESS);
        assertTrue(Files.isRegularFile(projectDir.resolve("build/resources/main/textures/diffuse.png")));
        assertTrue(Files.isRegularFile(projectDir.resolve("build/resources/main/textures/diffuse.png.basis")));
        assertTrue(Files.isRegularFile(projectDir.resolve("src/main/resources/textures/diffuse.png")));
        assertFalse(Files.exists(projectDir.resolve("src/main/resources/textures/diffuse.png.basis")));
    }

    @Test
    void originalResourcesCanBeExcludedWhenEncodedCopyExists() throws IOException {
        writeProject(true, true);

        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("processResources", "--stacktrace")
                .withPluginClasspath()
                .build();

        assertFalse(Files.exists(projectDir.resolve("build/resources/main/textures/diffuse.png")));
        assertTrue(Files.isRegularFile(projectDir.resolve("build/resources/main/textures/diffuse.png.basis")));
    }

    @Test
    void bundledBasisuExecutableCanEncodeAResourceImage() throws IOException {
        writeProject(false, false);

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("encodeBasisTextures", "--stacktrace")
                .withPluginClasspath()
                .build();

        assertTrue(result.task(":encodeBasisTextures").getOutcome() == TaskOutcome.SUCCESS);
        Path encoded = projectDir.resolve("build/generated/basis-textures/resources/textures/diffuse.png.basis");
        assertTrue(Files.size(encoded) > 0L);
    }

    @Test
    void filteredResourceDirectoriesKeepClasspathRelativePathsAndOnlyExcludeEncodedOriginals() throws IOException {
        writeProject(true, true, "    resourceDirectories = ['src/main/resources/textures']\n");
        Path icon = projectDir.resolve("src/main/resources/icons/logo.png");
        Files.createDirectories(icon.getParent());
        writePng(icon);

        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("processResources", "--stacktrace")
                .withPluginClasspath()
                .build();

        assertFalse(Files.exists(projectDir.resolve("build/resources/main/textures/diffuse.png")));
        assertTrue(Files.isRegularFile(projectDir.resolve("build/resources/main/textures/diffuse.png.basis")));
        assertTrue(Files.isRegularFile(projectDir.resolve("build/resources/main/icons/logo.png")));
        assertFalse(Files.exists(projectDir.resolve("build/resources/main/icons/logo.png.basis")));
    }

    @Test
    void imageAdditionModificationAndRemovalInvalidateTaskButUnrelatedResourcesDoNot() throws IOException {
        writeProject(false, true);
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("encodeBasisTextures", "--stacktrace", "--max-workers=1")
                .withPluginClasspath();
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":encodeBasisTextures").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":encodeBasisTextures").getOutcome());
        Files.writeString(projectDir.resolve("src/main/resources/readme.txt"), "not an image");
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":encodeBasisTextures").getOutcome());

        Path added = projectDir.resolve("src/main/resources/textures/added.PNG");
        writePng(added);
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":encodeBasisTextures").getOutcome());
        Path generated = projectDir.resolve("build/generated/basis-textures/resources/textures/added.PNG.basis");
        assertTrue(Files.isRegularFile(generated));

        BufferedImage replacement = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        replacement.setRGB(0, 0, 0xff448855);
        ImageIO.write(replacement, "PNG", added.toFile());
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":encodeBasisTextures").getOutcome());
        Files.delete(added);
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":encodeBasisTextures").getOutcome());
        assertFalse(Files.exists(generated));
        Files.delete(projectDir.resolve("src/main/resources/textures/diffuse.png"));
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":encodeBasisTextures").getOutcome());
        assertFalse(Files.exists(projectDir.resolve("build/generated/basis-textures/resources/textures/diffuse.png.basis")));
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":encodeBasisTextures").getOutcome());
    }

    @Test
    void changingOnlyCustomExecutableBytesInvalidatesActualEncoderTask() throws IOException {
        writeProject(false, true);
        Path source = projectDir.resolve("src/main/resources/textures/diffuse.png");
        byte[] original = Files.readAllBytes(source);
        GradleRunner runner = GradleRunner.create().withProjectDir(projectDir.toFile())
                .withArguments("encodeBasisTextures", "--stacktrace", "--max-workers=1")
                .withPluginClasspath();
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":encodeBasisTextures").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":encodeBasisTextures").getOutcome());
        Path executable = projectDir.resolve(isWindows() ? "basisu-fake.bat" : "basisu-fake");
        Files.writeString(executable, Files.readString(executable) + (isWindows() ? "rem byte-input-changed\r\n" : "# byte-input-changed\n"));
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":encodeBasisTextures").getOutcome());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void failedEncoderRemovesPartialTextureAndReportsItsDiagnostics() throws IOException {
        writeProject(false, true, "    encoderTimeoutSeconds = 15\n    encoderLogBytes = 4096\n");
        Path executable = projectDir.resolve(isWindows() ? "basisu-fake.bat" : "basisu-fake");
        Files.writeString(executable, Files.readString(executable)
                + (isWindows() ? "echo known-failure\r\nexit /b 7\r\n" : "printf 'known-failure\\n'\nexit 7\n"));
        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("encodeBasisTextures", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail();
        assertEquals(TaskOutcome.FAILED, result.task(":encodeBasisTextures").getOutcome());
        assertTrue(result.getOutput().contains("Encoder exited with status 7"));
        assertTrue(result.getOutput().contains("known-failure"));
        assertFalse(Files.exists(projectDir.resolve(
                "build/generated/basis-textures/resources/textures/diffuse.png.basis")));
    }

    @Test
    void bundledEncoderPreservesCompleteRectangularDdsMipChain() throws Exception {
        writeProject(false, false, "    imageExtensions = ['dds']\n"
                + "    resourceDirectories = ['src/main/resources/dds']\n"
                + "    dimensionAlignment = 1\n"
                + "    basisuArguments = ['-basis', '-uastc', '-linear', '-quiet', '-max_threads', '1', '-no_multithreading']\n"
                + "    encoderTimeoutSeconds = 120\n");
        Path source = projectDir.resolve("src/main/resources/dds/rectangular.dds");
        Files.createDirectories(source.getParent());
        try (InputStream input = getClass().getResourceAsStream("/fixtures/rgba8-complete17x9.dds")) {
            Files.copy(input, source);
        }
        byte[] original = Files.readAllBytes(source); // Exactly 912 bytes, 17x9 and all five authored mips.
        var result = GradleRunner.create().withProjectDir(projectDir.toFile())
                .withArguments("encodeBasisTextures", "--stacktrace", "--max-workers=1")
                .withPluginClasspath().build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":encodeBasisTextures").getOutcome());
        Path encoded = projectDir.resolve("build/generated/basis-textures/resources/dds/rectangular.dds.basis");
        assertTrue(Files.size(encoded) > 0);
        // Pinned basisu_file_headers.h layout: one UASTC slice per authored level.
        try (RandomAccessFile file = new RandomAccessFile(encoded.toFile(), "r")) {
            byte[] header = new byte[77];
            file.readFully(header);
            ByteBuffer view = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x4273, Short.toUnsignedInt(view.getShort(0)));
            assertEquals(5, (header[14] & 0xff) | ((header[15] & 0xff) << 8) | ((header[16] & 0xff) << 16));
            assertEquals(1, (header[17] & 0xff) | ((header[18] & 0xff) << 8) | ((header[19] & 0xff) << 16));
            assertEquals(1, header[20] & 0xff); // UASTC LDR4x4.
            assertEquals(0, Short.toUnsignedInt(view.getShort(21)) & (1 | 2 | 16)); // No ETC1S, flip or sRGB flag.
            long descriptions = Integer.toUnsignedLong(view.getInt(65));
            assertTrue(descriptions >= 77 && descriptions + 5L * 23 <= file.length());
            int[][] dimensions = {{17, 9}, {8, 4}, {4, 2}, {2, 1}, {1, 1}};
            file.seek(descriptions);
            for (int level = 0; level < dimensions.length; level++) {
                byte[] slice = new byte[23];
                file.readFully(slice);
                ByteBuffer description = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN);
                assertEquals(level, slice[3] & 0xff);
                assertEquals(dimensions[level][0], Short.toUnsignedInt(description.getShort(5)));
                assertEquals(dimensions[level][1], Short.toUnsignedInt(description.getShort(7)));
            }
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void bundledUastcEncoderPreservesRequestedTransferFunction() throws IOException {
        for (boolean linear : new boolean[] {false, true}) {
            writeProject(false, false, "    basisuArguments = ['-basis', '-uastc', '-quiet'"
                    + (linear ? ", '-linear'" : "") + "]\n");
            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withArguments("encodeBasisTextures", "--stacktrace")
                    .withPluginClasspath()
                    .build();
            Path encoded = projectDir.resolve(
                    "build/generated/basis-textures/resources/textures/diffuse.png.basis");
            try (RandomAccessFile file = new RandomAccessFile(encoded.toFile(), "r")) {
                file.seek(21);
                int flags = file.readUnsignedByte() | (file.readUnsignedByte() << 8);
                assertEquals(linear ? 0 : 16, flags & 16);
            }
        }
    }

    private void writeProject(boolean excludeOriginals, boolean useFakeBasisu) throws IOException {
        writeProject(excludeOriginals, useFakeBasisu, "");
    }

    private void writeProject(boolean excludeOriginals, boolean useFakeBasisu, String extraBasisConfig)
            throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(projectDir.resolve("build.gradle"), ""
                + "plugins {\n"
                + "    id 'java'\n"
                + "    id 'org.ngengine.basis.texture-encoder'\n"
                + "}\n"
                + "basisTextures {\n"
                + basisuExecutableLine(useFakeBasisu)
                + "    imageExtensions = ['png']\n"
                + "    basisuArguments = ['-basis', '-quiet']\n"
                + extraBasisConfig
                + "    excludeOriginalsWhenEncoded = " + excludeOriginals + "\n"
                + "}\n",
                StandardCharsets.UTF_8);

        Path texture = projectDir.resolve("src/main/resources/textures/diffuse.png");
        Files.createDirectories(texture.getParent());
        writePng(texture);
    }

    private String basisuExecutableLine(boolean useFakeBasisu) throws IOException {
        if (!useFakeBasisu) {
            return "";
        }
        return "    basisuExecutable = '" + fakeBasisu().toString().replace("\\", "\\\\") + "'\n";
    }

    private static void writePng(Path texture) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int red = x * 64;
                int green = y * 64;
                int blue = (x + y) * 32;
                image.setRGB(x, y, (255 << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", texture.toFile());
    }

    private Path fakeBasisu() throws IOException {
        Path executable = projectDir.resolve(isWindows() ? "basisu-fake.bat" : "basisu-fake");
        if (isWindows()) {
            Files.writeString(executable, ""
                    + "@echo off\r\n"
                    + "set out=\r\n"
                    + ":loop\r\n"
                    + "if \"%1\"==\"\" goto done\r\n"
                    + "if \"%1\"==\"-output_file\" (\r\n"
                    + "  shift\r\n"
                    + "  set out=%1\r\n"
                    + ")\r\n"
                    + "shift\r\n"
                    + "goto loop\r\n"
                    + ":done\r\n"
                    + "echo fake-basisu>\"%out%\"\r\n",
                    StandardCharsets.UTF_8);
        } else {
            Files.writeString(executable, ""
                    + "#!/bin/sh\n"
                    + "out=''\n"
                    + "while [ \"$#\" -gt 0 ]; do\n"
                    + "  if [ \"$1\" = '-output_file' ]; then\n"
                    + "    shift\n"
                    + "    out=\"$1\"\n"
                    + "  fi\n"
                    + "  shift\n"
                    + "done\n"
                    + "printf 'fake-basisu\\n' > \"$out\"\n",
                    StandardCharsets.UTF_8);
        }
        executable.toFile().setExecutable(true);
        return executable;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows");
    }
}
