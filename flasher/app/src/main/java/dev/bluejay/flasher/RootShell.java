package dev.bluejay.flasher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around a single {@code su -c} command. Everything this app does
 * to a partition goes through here, so a device without root simply gets
 * exitCode -1 and an error message instead of a silent failure.
 */
final class RootShell {

    static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static final long TIMEOUT_SECONDS = 180;

    private RootShell() {
    }

    static Result run(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, output + "\nTimed out after " + TIMEOUT_SECONDS + "s.");
            }

            return new Result(process.exitValue(), output.toString());
        } catch (Exception e) {
            return new Result(-1, e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
