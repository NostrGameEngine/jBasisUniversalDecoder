package org.ngengine.basis.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Runs an encoder with a deadline, bounded diagnostics and failure cleanup.
 * Process snapshots can only track descendants observed before they exit or reparent.
 */
final class BoundedEncoderProcess {
    private static final int DIAGNOSTIC_BYTES = 16384;
    private static final long POLL_MILLIS = 50;
    private static final long CLEANUP_SECONDS = 5;

    private BoundedEncoderProcess() {}

    static void run(List<String> command, Path log, Path output, int timeoutSeconds, long logBytes)
            throws IOException, InterruptedException {
        if (timeoutSeconds < 1 || timeoutSeconds > 86400 || logBytes < 1) {
            throw new IllegalArgumentException("Encoder timeout must be 1..86400 seconds and log limit must be positive");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Interrupted before encoder launch");
        }
        Files.createDirectories(log.toAbsolutePath().getParent());
        Process process = null;
        Thread reader = null;
        Set<ProcessHandle> descendants = new LinkedHashSet<>();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        CountDownLatch readComplete = new CountDownLatch(1);
        Throwable failure = null;
        boolean complete = false;
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            InputStream processOutput = process.getInputStream();
            reader = new Thread(() -> drain(processOutput, log, logBytes, readFailure, readComplete),
                    "basisu-diagnostics-" + process.pid());
            reader.setDaemon(true);
            reader.start();
            while (true) {
                observe(process, descendants);
                checkReadFailure(readFailure);
                long remaining = remaining(deadline, timeoutSeconds);
                if (process.waitFor(Math.min(TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS), remaining),
                        TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
            if (descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                throw new IOException("Encoder exited while observed descendants remained alive");
            }
            if (!readComplete.await(remaining(deadline, timeoutSeconds), TimeUnit.NANOSECONDS)) {
                throw new IOException("Encoder exceeded wall deadline of " + timeoutSeconds + " seconds");
            }
            checkReadFailure(readFailure);
            if (process.exitValue() != 0) {
                throw new IOException("Encoder exited with status " + process.exitValue());
            }
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                throw new IOException("Encoder did not produce a nonempty output file");
            }
            complete = true;
        } catch (IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } catch (InterruptedException exception) {
            failure = exception;
            interrupted = true;
            throw exception;
        } finally {
            boolean priorInterrupt = Thread.interrupted();
            IOException cleanupFailure = null;
            try {
                if (process != null) {
                    cleanup(process, descendants, reader);
                }
            } catch (IOException exception) {
                cleanupFailure = exception;
                complete = false;
                if (failure != null) {
                    failure.addSuppressed(exception);
                }
            }
            if (!complete) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException exception) {
                    if (failure != null) {
                        failure.addSuppressed(exception);
                    } else if (cleanupFailure != null) {
                        cleanupFailure.addSuppressed(exception);
                    } else {
                        cleanupFailure = exception;
                    }
                }
            }
            if (interrupted || priorInterrupt) {
                Thread.currentThread().interrupt();
            }
            if (cleanupFailure != null && failure == null) {
                throw cleanupFailure;
            }
        }
    }

    static String diagnostics(Path log) throws IOException {
        if (!Files.isRegularFile(log)) {
            return "No encoder diagnostic log was created.";
        }
        try (InputStream input = Files.newInputStream(log)) {
            byte[] prefix = input.readNBytes(DIAGNOSTIC_BYTES);
            String text = new String(prefix, StandardCharsets.UTF_8);
            return Files.size(log) > prefix.length ? text + "\n[diagnostic prefix truncated; log: " + log + "]" : text;
        }
    }

    private static long remaining(long deadline, int timeoutSeconds) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new IOException("Encoder exceeded wall deadline of " + timeoutSeconds + " seconds");
        }
        return remaining;
    }

    private static void checkReadFailure(AtomicReference<IOException> failure) throws IOException {
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private static void drain(InputStream input, Path log, long limit,
            AtomicReference<IOException> failure, CountDownLatch complete) {
        try (InputStream source = input; OutputStream destination = Files.newOutputStream(log)) {
            byte[] buffer = new byte[8192];
            long written = 0;
            int count;
            while ((count = source.read(buffer)) != -1) {
                int accepted = (int) Math.min(count, limit - written);
                destination.write(buffer, 0, accepted);
                written += accepted;
                if (accepted != count) {
                    throw new IOException("Encoder exceeded diagnostic output limit of " + limit + " bytes");
                }
            }
        } catch (IOException exception) {
            failure.set(exception);
        } finally {
            complete.countDown();
        }
    }

    private static void observe(Process process, Set<ProcessHandle> descendants) {
        descendants.removeIf(child -> !child.isAlive());
        try (Stream<ProcessHandle> children = process.descendants()) {
            children.forEach(descendants::add);
        }
    }

    private static void cleanup(Process process, Set<ProcessHandle> descendants, Thread reader) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLEANUP_SECONDS);
        observe(process, descendants);
        descendants.forEach(ProcessHandle::destroy);
        try {
            if (process.isAlive() && !process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.destroy();
                process.waitFor(250, TimeUnit.MILLISECONDS);
            }
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
            while (descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            if (reader != null && reader.isAlive()) {
                reader.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            }
            // Closing a pipe while its reader is blocked can also block on the stream lock.
            if (reader == null || !reader.isAlive()) {
                process.getInputStream().close();
            }
            process.getErrorStream().close();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during encoder cleanup", exception);
        }
        if (process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)
                || (reader != null && reader.isAlive())) {
            throw new IOException("Encoder cleanup could not confirm exit of the process and observed descendants");
        }
    }
}
