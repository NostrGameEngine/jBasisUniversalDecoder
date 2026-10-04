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
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "The task invokes a platform-specific external basisu executable.")
public abstract class EncodeBasisTexturesTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getImageExtensions();

    @Input
    public abstract ListProperty<String> getResourceDirectories();

    @Input
    public abstract ListProperty<String> getClasspathResourceDirectories();

    @Input
    public abstract ListProperty<String> getExcludedResourcePaths();

    @Input
    public abstract ListProperty<String> getBasisuArguments();

    @Input
    public abstract Property<Integer> getDimensionAlignment();

    @Input
    @Optional
    public abstract Property<String> getBasisuExecutable();

    @Input
    public abstract Property<Integer> getEncoderTimeoutSeconds();

    @Input
    public abstract Property<Long> getEncoderLogBytes();

    public EncodeBasisTexturesTask() {
        getEncoderTimeoutSeconds().convention(300);
        getEncoderLogBytes().convention(1048576L);
    }

    /** Contents of a user-supplied encoder must also invalidate generated textures. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getConfiguredExecutableInput() {
        String configured = getBasisuExecutable().getOrNull();
        return configured == null || configured.isBlank()
                ? getProject().files() : getProject().files(getProject().file(configured));
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** Image contents must invalidate the task, not just their directory names. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSourceImages() {
        Set<String> extensions = normalizedExtensions();
        Set<String> excludedPaths = normalizedExcludedPaths();
        List<Path> classpathRoots = classpathResourceRoots();
        List<FileTree> trees = new ArrayList<>();
        for (String directory : getResourceDirectories().get()) {
            Path resourceRoot = normalizedProjectPath(directory);
            trees.add(getProject().fileTree(resourceRoot.toFile()).matching(pattern ->
                    pattern.include(element -> element.isDirectory()
                            || (extensions.contains(extension(element.getFile().toPath()))
                            && !excludedPaths.contains(resourcePath(relativeResourcePath(
                                    element.getFile().toPath(), resourceRoot, classpathRoots)))))));
        }
        // Do not skip an empty input set: removing the last image must clean stale output.
        return getProject().files(trees);
    }

    @TaskAction
    public void encode() throws IOException, InterruptedException {
        Path basisu = resolveBasisuExecutable();
        Path outputRoot = getOutputDirectory().get().getAsFile().toPath();
        getProject().delete(outputRoot);
        Files.createDirectories(outputRoot);

        Set<String> extensions = normalizedExtensions();
        Set<String> excludedPaths = normalizedExcludedPaths();
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
                        .filter(path -> !excludedPaths.contains(resourcePath(
                                relativeResourcePath(path, resourceRoot, classpathRoots))))
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
            appendDimensionAlignment(command, source);
            command.add("-output_file");
            command.add(output.toString());
            command.add(source.toString());

            Path log = Files.createTempFile(getTemporaryDir().toPath(), "basisu-", ".log");
            try {
                BoundedEncoderProcess.run(command, log, output,
                        getEncoderTimeoutSeconds().get(), getEncoderLogBytes().get());
                Files.deleteIfExists(log);
            } catch (IOException exception) {
                String diagnostics;
                try {
                    diagnostics = BoundedEncoderProcess.diagnostics(log);
                } catch (IOException diagnosticFailure) {
                    exception.addSuppressed(diagnosticFailure);
                    diagnostics = "Unable to read encoder diagnostics at " + log;
                }
                throw new IOException(exception.getMessage() + "\n" + diagnostics, exception);
            }
        } catch (IOException e) {
            throw new GradleException("Unable to encode " + source, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while encoding " + source, e);
        }
    }

    private void appendDimensionAlignment(List<String> command, Path source) throws IOException {
        int alignment = getDimensionAlignment().getOrElse(1);
        if (alignment < 1) {
            throw new GradleException("dimensionAlignment must be at least 1");
        }
        if (alignment == 1 || hasExplicitResampling(command)) {
            return;
        }

        int[] dimensions = readImageDimensions(source);
        if (dimensions == null) {
            getLogger().info("Cannot determine dimensions for {}; skipping automatic alignment", source);
            return;
        }

        int alignedWidth = alignUp(dimensions[0], alignment);
        int alignedHeight = alignUp(dimensions[1], alignment);
        if (alignedWidth == dimensions[0] && alignedHeight == dimensions[1]) {
            return;
        }
        command.add("-resample");
        command.add(Integer.toString(alignedWidth));
        command.add(Integer.toString(alignedHeight));
    }

    private static boolean hasExplicitResampling(List<String> command) {
        return command.contains("-resample") || command.contains("-resample_factor");
    }

    private static int[] readImageDimensions(Path source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) {
                return null;
            }
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private static int alignUp(int value, int alignment) {
        return Math.multiplyExact(Math.floorDiv(Math.addExact(value, alignment - 1), alignment), alignment);
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

    private Set<String> normalizedExcludedPaths() {
        Set<String> result = new HashSet<>();
        for (String path : getExcludedResourcePaths().getOrElse(java.util.Collections.emptyList())) {
            if (path != null && !path.isBlank()) {
                result.add(path.replace('\\', '/'));
            }
        }
        return result;
    }

    private static String resourcePath(Path path) {
        return path.toString().replace('\\', '/');
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

    /**
     * Resolves the configured or bundled encoder executable for cooperating
     * Gradle plugins.
     *
     * @return executable path
     * @throws IOException if the bundled executable cannot be extracted
     */
    public Path resolveBasisuExecutable() throws IOException {
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
