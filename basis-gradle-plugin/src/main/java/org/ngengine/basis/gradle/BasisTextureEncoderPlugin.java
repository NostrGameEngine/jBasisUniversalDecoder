package org.ngengine.basis.gradle;

import java.io.File;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public class BasisTextureEncoderPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        BasisTextureEncoderExtension extension = project.getExtensions().create(
                "basisTextures",
                getExtensionType());

        TaskProvider<EncodeBasisTexturesTask> encodeTask = project.getTasks().register(
                "encodeBasisTextures",
                EncodeBasisTexturesTask.class,
                task -> {
                    task.getImageExtensions().set(extension.getImageExtensions());
                    task.getResourceDirectories().set(extension.getResourceDirectories());
                    task.getExcludedResourcePaths().set(extension.getExcludedResourcePaths());
                    task.getDimensionAlignment().set(extension.getDimensionAlignment());
                    task.getBasisuArguments().set(extension.getBasisuArguments());
                    task.getBasisuExecutable().set(extension.getBasisuExecutable());
                    task.getEncoderTimeoutSeconds().set(extension.getEncoderTimeoutSeconds());
                    task.getEncoderLogBytes().set(extension.getEncoderLogBytes());
                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                            .dir("generated/basis-textures/resources"));
                });

        project.getPlugins().withType(JavaPlugin.class, plugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.named("main", sourceSet -> {
                encodeTask.configure(task -> task.getClasspathResourceDirectories().set(
                        sourceSet.getResources().getSrcDirs().stream()
                                .map(File::getPath)
                                .collect(Collectors.toList())));
                sourceSet.getResources().srcDir(encodeTask.flatMap(EncodeBasisTexturesTask::getOutputDirectory));
            });

            project.getTasks().named("processResources", Copy.class, task -> {
                task.dependsOn(encodeTask);
                task.exclude(details -> extension.getExcludeOriginalsWhenEncoded().get()
                        && hasImageExtension(details.getFile().getName(), extension)
                        && hasEncodedSibling(encodeTask, details.getRelativePath().getPathString()));
            });
        });

        configureAdditionalEncoding(project, extension, encodeTask);
    }

    /**
     * Supplies the extension type. Derived plugins can add settings while
     * retaining the standard {@code basisTextures} DSL.
     *
     * @return extension implementation type
     */
    protected Class<? extends BasisTextureEncoderExtension> getExtensionType() {
        return BasisTextureEncoderExtension.class;
    }

    /**
     * Hook for plugins that cooperate with the ordinary image encoder.
     *
     * @param project target project
     * @param extension configured encoder extension
     * @param encodeTask ordinary image encoding task
     */
    protected void configureAdditionalEncoding(
            Project project,
            BasisTextureEncoderExtension extension,
            TaskProvider<EncodeBasisTexturesTask> encodeTask) {
        // Default plugin has no additional encoding stages.
    }

    private static boolean hasEncodedSibling(
            TaskProvider<EncodeBasisTexturesTask> encodeTask,
            String relativePath) {
        return encodeTask.get()
                .getOutputDirectory()
                .file(relativePath + ".basis")
                .get()
                .getAsFile()
                .isFile();
    }

    private static boolean hasImageExtension(String fileName, BasisTextureEncoderExtension extension) {
        String lowerName = fileName.toLowerCase(java.util.Locale.ROOT);
        for (String rawExtension : extension.getImageExtensions().get()) {
            String clean = rawExtension.startsWith(".") ? rawExtension.substring(1) : rawExtension;
            if (lowerName.endsWith("." + clean.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
