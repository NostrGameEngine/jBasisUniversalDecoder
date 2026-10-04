package org.ngengine.basis.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedEncoderProcessTest {
    @TempDir Path directory;

    @Test
    void successfulProcessProducesOutputAndDiagnostics() throws Exception {
        Path output = directory.resolve("success.basis");
        Path log = directory.resolve("success.log");
        BoundedEncoderProcess.run(command("success", output), log, output, 15, 1048576);
        assertEquals("ok", Files.readString(output));
        assertTrue(BoundedEncoderProcess.diagnostics(log).contains("known-success"));
    }

    @Test
    void nonzeroExitDeletesPartialOutputAndPreservesDiagnostics() throws Exception {
        Path output = directory.resolve("failure.basis");
        Path log = directory.resolve("failure.log");
        IOException failure = assertThrows(IOException.class, () -> BoundedEncoderProcess.run(
                command("failure", output), log, output, 15, 1048576));
        assertTrue(failure.getMessage().contains("status 7"));
        assertFalse(Files.exists(output));
        assertTrue(BoundedEncoderProcess.diagnostics(log).contains("known-failure"));
    }

    @Test
    void wallDeadlineStopsObservedChildAndDeletesPartialOutput() throws Exception {
        Path output = directory.resolve("timeout.basis");
        Path log = directory.resolve("timeout.log");
        long start = System.nanoTime();
        IOException failure = assertThrows(IOException.class, () -> BoundedEncoderProcess.run(
                command("timeout", output), log, output, 3, 1048576));
        assertTrue(failure.getMessage().contains("wall deadline"), failure.toString());
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10));
        assertFalse(Files.exists(output));
        assertChildExited(log);
    }

    @Test
    void diagnosticBurstIsCappedAndDeletesPartialOutput() throws Exception {
        Path output = directory.resolve("noisy.basis");
        Path log = directory.resolve("noisy.log");
        IOException failure = assertThrows(IOException.class, () -> BoundedEncoderProcess.run(
                command("noisy", output), log, output, 15, 20000));
        assertTrue(failure.getMessage().contains("diagnostic output limit"));
        assertFalse(Files.exists(output));
        assertEquals(20000, Files.size(log));
        String diagnostic = BoundedEncoderProcess.diagnostics(log);
        assertTrue(diagnostic.length() < 17000);
        assertTrue(diagnostic.contains("prefix truncated"));
    }

    @Test
    void interruptionPreservesFlagAndCleansProcessOutputAndObservedChild() throws Exception {
        Path output = directory.resolve("interrupted.basis");
        Path log = directory.resolve("interrupted.log");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRetained = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try {
                BoundedEncoderProcess.run(command("timeout", output), log, output, 15, 1048576);
            } catch (Throwable exception) {
                failure.set(exception);
                interruptRetained.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ((!Files.exists(log) || Files.size(log) == 0) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(Files.exists(log) && Files.size(log) > 0, "Fixture did not announce its child");
            // Let the process monitor observe the child before interrupting it.
            Thread.sleep(100);
        } finally {
            worker.interrupt();
            worker.join(6000);
        }
        assertFalse(worker.isAlive());
        assertTrue(failure.get() instanceof InterruptedException, String.valueOf(failure.get()));
        assertTrue(interruptRetained.get());
        assertFalse(Files.exists(output));
        assertChildExited(log);
    }

    @Test
    void preexistingInterruptPreventsLaunchAndPreservesFlag() throws Exception {
        Path output = directory.resolve("pre-interrupted.basis");
        Path log = directory.resolve("pre-interrupted.log");
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> BoundedEncoderProcess.run(
                    command("success", output), log, output, 15, 1048576));
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(output));
            assertFalse(Files.exists(log));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void emptySuccessfulOutputAndInvalidLimitsAreRejected() throws Exception {
        Path output = directory.resolve("empty.basis");
        Path log = directory.resolve("empty.log");
        IOException failure = assertThrows(IOException.class, () -> BoundedEncoderProcess.run(
                command("empty", output), log, output, 15, 1048576));
        assertTrue(failure.getMessage().contains("nonempty output"));
        assertFalse(Files.exists(output));
        assertThrows(IllegalArgumentException.class, () -> BoundedEncoderProcess.run(
                command("success", output), log, output, 0, 1048576));
        assertThrows(IllegalArgumentException.class, () -> BoundedEncoderProcess.run(
                command("success", output), log, output, 86401, 1048576));
        assertThrows(IllegalArgumentException.class, () -> BoundedEncoderProcess.run(
                command("success", output), log, output, 15, 0));
    }

    private static List<String> command(String mode, Path output) {
        String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        String classpath;
        try {
            classpath = Path.of(EncoderProcessFixture.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toString();
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
        return Arrays.asList(java, "-cp", classpath, EncoderProcessFixture.class.getName(), mode,
                output.toString(), java, classpath);
    }

    private static void assertChildExited(Path log) throws IOException {
        long pid = Long.parseLong(Files.readString(log).lines().findFirst().orElseThrow());
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "Observed fixture child remains alive");
    }
}
