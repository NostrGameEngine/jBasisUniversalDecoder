package org.ngengine.basis.gradle;

import java.nio.file.Files;
import java.nio.file.Path;

/** Portable child process for exercising real pipe, timeout and cleanup behavior. */
public final class EncoderProcessFixture {
    private EncoderProcessFixture() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path output = Path.of(args[1]);
        if (mode.equals("sleep")) {
            Thread.sleep(30000);
            return;
        }
        Files.writeString(output, mode.equals("empty") ? "" : "partial");
        if (mode.equals("failure")) {
            System.out.println("known-failure");
            System.exit(7);
        } else if (mode.equals("noisy")) {
            System.out.write(new byte[65536]);
        } else if (mode.equals("timeout")) {
            Process child = new ProcessBuilder(args[2], "-cp", args[3],
                    EncoderProcessFixture.class.getName(), "sleep", output.toString()).start();
            System.out.println(child.pid());
            System.out.flush();
            child.waitFor();
        } else if (mode.equals("success")) {
            Files.writeString(output, "ok");
            System.out.println("known-success");
        }
    }
}
