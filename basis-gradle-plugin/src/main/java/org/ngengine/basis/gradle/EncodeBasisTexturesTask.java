package org.ngengine.basis.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class EncodeBasisTexturesTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getImageExtensions();

    @Input
    public abstract ListProperty<String> getResourceDirectories();

    @Input
    public abstract ListProperty<String> getClasspathResourceDirectories();

    @Input
    public abstract ListProperty<String> getBasisuArguments();

    @Input
    @Optional
    public abstract Property<String> getBasisuExecutable();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void encode() throws IOException, InterruptedException {
        Path basisu = resolveBasisuExecutable();
        Path outputRoot = getOutputDirectory().get().getAsFile().toPath();
        getProject().delete(outputRoot);
        Files.createDirectories(outputRoot);

        Set<String> extensions = normalizedExtensions();
        List<Path> classpathRoots = classpathResourceRoots();
        for (String directory : getResourceDirectories().get()) {
            Path resourceRoot = normalizedProjectPath(directory);
            if (!Files.isDirectory(resourceRoot)) {
                continue;
            }
            try (var stream = Files.walk(resourceRoot)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(path -> extensions.contains(extension(path)))
                        .forEach(path -> encodeOne(basisu, resourceRoot, classpathRoots, outputRoot, path));
            }
        }
    }

    private void encodeOne(
            Path basisu,
            Path resourceRoot,
            List<Path> classpathRoots,
            Path outputRoot,
            Path source) {
        Path relative = relativeResourcePath(source, resourceRoot, classpathRoots);
        Path output = outputRoot.resolve(relative.toString() + ".basis");
        try {
            Files.createDirectories(output.getParent());
            List<String> command = new ArrayList<>();
            command.add(basisu.toString());
            command.addAll(getBasisuArguments().get());
            command.add("-output_file");
            command.add(output.toString());
            command.add(source.toString());

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            byte[] processOutput = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GradleException("basisu failed for " + source + "\n"
                        + new String(processOutput, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new GradleException("Unable to encode " + source, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while encoding " + source, e);
        }
    }

    private List<Path> classpathResourceRoots() {
        List<String> directories = getClasspathResourceDirectories().getOrElse(getResourceDirectories().get());
        List<Path> roots = new ArrayList<>();
        for (String directory : directories) {
            Path root = normalizedProjectPath(directory);
            if (Files.isDirectory(root)) {
                roots.add(root);
            }
        }
        roots.sort(Comparator.comparingInt(Path::getNameCount).reversed());
        return roots;
    }

    private Path relativeResourcePath(Path source, Path fallbackRoot, List<Path> classpathRoots) {
        Path normalizedSource = source.toAbsolutePath().normalize();
        for (Path root : classpathRoots) {
            if (normalizedSource.startsWith(root)) {
                return root.relativize(normalizedSource);
            }
        }
        return fallbackRoot.relativize(normalizedSource);
    }

    private Path normalizedProjectPath(String directory) {
        return getProject().file(directory).toPath().toAbsolutePath().normalize();
    }

    private Set<String> normalizedExtensions() {
        Set<String> result = new HashSet<>();
        for (String ext : getImageExtensions().get()) {
            String clean = ext.startsWith(".") ? ext.substring(1) : ext;
            result.add(clean.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    @Internal
    public String getDetectedPlatform() {
        return detectPlatform();
    }

    private Path resolveBasisuExecutable() throws IOException {
        String configuredExecutable = getBasisuExecutable().getOrNull();
        if (configuredExecutable != null && !configuredExecutable.isBlank()) {
            Path executable = getProject().file(configuredExecutable).toPath();
            if (!Files.isRegularFile(executable)) {
                throw new GradleException("Configured basisuExecutable does not exist: " + executable);
            }
            if (!Files.isExecutable(executable)) {
                executable.toFile().setExecutable(true);
            }
            return executable;
        }
        return extractBundledBasisuExecutable();
    }

    private Path extractBundledBasisuExecutable() throws IOException {
        String platform = detectPlatform();
        String executableName = platform.startsWith("windows-") ? "basisu.exe" : "basisu";
        String resource = "/basisu-bin/" + platform + "/" + executableName;
        Path output = getTemporaryDir().toPath().resolve(platform).resolve(executableName);
        Files.createDirectories(output.getParent());

        try (InputStream input = EncodeBasisTexturesTask.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new GradleException("Missing bundled Basis Universal encoder for " + platform
                        + ". Add " + resource + " to the plugin resources.");
            }
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        }
        output.toFile().setExecutable(true);
        return output;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String normalizedArch = arch.equals("aarch64") || arch.equals("arm64") ? "aarch64" : "x86_64";
        if (os.contains("win")) {
            return "windows-" + normalizedArch;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin-" + normalizedArch;
        }
        if (os.contains("linux")) {
            return "linux-" + normalizedArch;
        }
        throw new GradleException("Unsupported OS for bundled basisu: " + os + " " + arch);
    }
}
