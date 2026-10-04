package org.ngengine.basis.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
