package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TestJavaBoundaryContract {

    private static final Pattern NATIVE_DECLARATION = Pattern.compile(
            "\\b(public|private)\\s+native\\s+"
                    + "[A-Za-z_$][A-Za-z0-9_$<>\\[\\], ]+\\s+"
                    + "[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(");
    private static final Pattern LOADER_CALL = Pattern.compile(
            "(?:System\\.loadLibrary\\(|System\\.load\\(|Runtime\\.getRuntime\\(\\)\\.loadLibrary\\(|"
                    + "Runtime\\.getRuntime\\(\\)\\.load\\()");
    private static final Pattern NON_PORTABLE_NATIVE_CALLS = Pattern.compile(
            "sun\\.misc\\.Unsafe|java\\.nio\\.MappedByteBuffer|java\\.nio\\.channels\\.FileChannel\\.map|"
                    + "FileDescriptor|RandomAccessFile|Runtime\\.getRuntime\\(\\)\\.exec\\(|"
                    + "java\\.nio\\.channels\\.FileChannel\\b|DirectByteBuffer|"
                    + "asDirectBuffer\\(\\)|mmap\\(|munmap\\(");

    @Test
    void noNativeBoundaryCallsInCoreCode() throws IOException {
        Path sourceRoot = Paths.get(
                System.getProperty("user.dir"),
                "src",
                "main",
                "java",
                "org",
                "ngengine",
                "basis");
        List<Path> offenders = new ArrayList<>();

        Files.walk(sourceRoot)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(path -> {
                    String contents = readSource(path);
                    if (NATIVE_DECLARATION.matcher(contents).find() || LOADER_CALL.matcher(contents).find()
                            || NON_PORTABLE_NATIVE_CALLS.matcher(contents).find()) {
                        offenders.add(path);
                    }
                });

        assertTrue(
                offenders.isEmpty(),
                "Native/JNI boundary or non-portable JVM boundary calls detected in core decoder classes: "
                        + offenders);
    }

    private String readSource(Path source) {
        try {
            String text = new String(Files.readAllBytes(source));
            // Remove block and line comments before regex checks to avoid Javadoc false positives.
            text = text.replaceAll("(?s)/\\*.*?\\*/", "");
            text = text.replaceAll("(?m)//.*$", "");
            return text;
        } catch (IOException e) {
            throw new AssertionError("Failed to read source file: " + source, e);
        }
    }
}
