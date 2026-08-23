package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class TestNoNativeDependencyPolicy {

    @Test
    public void testCoreJavaDecoderDoesNotContainDirectJniBindings() throws IOException {
        Path sourceRoot = Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
        String[] forbiddenLiterals = {
            "System.loadLibrary",
            "System.load(",
            "com.sun.jna",
            "JNR",
            "JNI_OnLoad"
        };

        AtomicLong hits = new AtomicLong();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String content;
                        try {
                            content = Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException("Cannot read source file: " + path, e);
                        }
                        for (String forbidden : forbiddenLiterals) {
                            if (content.contains(forbidden)) {
                                hits.incrementAndGet();
                                break;
                            }
                        }
                    });
        }

        assertEquals(0, hits.get(),
                "Core decoder Java sources must not contain direct JVM native/JNI loading hooks.");
    }

    @Test
    public void testCoreJavaSourcesContainNoNativeMethodOrFallbackSelectors() throws IOException {
        Path sourceRoot = Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
        Pattern nativeMethodPattern = Pattern.compile(
                "^(?:[ \\t]*(?:private|public|protected)[ \\t]+)?(?:static[ \\t]+)?native[ \\t]+");
        Pattern nativeLoaderPattern = Pattern.compile("\\bSystem\\.(loadLibrary|load)\\b");
        String[] forbiddenRuntimeSelectors = {
                "allowNativeProcessFallback",
                "nativeDecoderCommand",
                "BASISU_NATIVE_DECODER_COMMAND",
                "fallbackToNative",
                "useNative",
                "hasNative",
                "--allow-native-fallback"
        };

        List<String> violations = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String content;
                        try {
                            content = Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException("Cannot read source file: " + path, e);
                        }

                        String[] lines = content.split("\\r?\\n|\\r+");
                        for (String line : lines) {
                            if (nativeMethodPattern.matcher(line).find()) {
                                violations.add(path + " -> contains native method declaration");
                                break;
                            }
                            if (nativeLoaderPattern.matcher(line).find()) {
                                violations.add(path + " -> contains native loader usage");
                                break;
                            }
                        }

                        for (String forbidden : forbiddenRuntimeSelectors) {
                            if (content.contains(forbidden)) {
                                violations.add(path + " -> contains fallback token: " + forbidden);
                            }
                        }
                    });
        }

        assertTrue(violations.isEmpty(),
                "No native method declarations or native-fallback selectors are allowed in main Java sources."
                        + " Found:\n"
                        + String.join("\n", violations));
    }

    @Test
    public void testMainTreeContainsNoNativeBinaryArtifacts() throws IOException {
        Path mainRoot = Paths.get(System.getProperty("user.dir")).resolve("src");
        String[] nativeExtensions = {
            ".so",
            ".dll",
            ".dylib",
            ".jnilib"
        };

        AtomicLong hits = new AtomicLong();
        try (Stream<Path> stream = Files.walk(mainRoot)) {
            stream.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        for (String ext : nativeExtensions) {
                            if (name.endsWith(ext)) {
                                hits.incrementAndGet();
                                break;
                            }
                        }
                    });
        }

        assertEquals(0, hits.get(),
                "No native binary artifacts should be shipped in src/ for core path Java parity.");
    }

    @Test
    public void testPureJavaFactoryPathContainsNoLegacyOrNativeFallbackBranchAtRuntime() throws IOException {
        BasisDecoder decoder = BasisDecoderFactory.createDefault();
        assertTrue(decoder != null);
        assertTrue(decoder.getBackendName().contains("Java"));
        assertTrue(decoder.getBackendName().toLowerCase(java.util.Locale.ENGLISH).contains("java"));
        assertFalse(decoder.getBackendName().toLowerCase(java.util.Locale.ENGLISH).contains("native"));

        Path sourceRoot = Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
        Pattern legacyFactoryFallback = Pattern.compile("native|process|fallback", Pattern.CASE_INSENSITIVE);
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            final boolean[] seenFallbackPath = {false};
            stream.filter(path -> path.toString().endsWith("BasisDecoderFactory.java"))
                    .findFirst()
                    .ifPresent(path -> {
                        String content;
                        try {
                            content = Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException("Cannot read factory file: " + path, e);
                        }
                        // Legacy native factory selection is intentionally forbidden.
                        seenFallbackPath[0] = legacyFactoryFallback.matcher(content).find()
                                && content.toLowerCase(java.util.Locale.ENGLISH).contains("if");
                    });
            assertEquals(
                    false,
                    seenFallbackPath[0],
                    "BasisDecoderFactory should not contain legacy native runtime fallback conditionals.");
        }
    }
}
