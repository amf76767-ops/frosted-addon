package com.manu.frosted.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Swaps the jar after the game has exited. A running jar cannot be replaced safely (on Windows
 * it is locked, on Linux classes may still be read from it), so a tiny helper process waits for
 * the game to end, moves old -> .bak and new -> final, and can start the game again.
 *
 * The new file stays named *.jar.update until then, so Fabric never sees two Frosted jars.
 */
public final class UpdateInstaller {
    public record Plan(Path oldJar, Path pending, Path finalJar, Path backup, String version) {}

    private static final String UNIX_SCRIPT = String.join("\n",
        "trap '' HUP",
        "while kill -0 \"$1\" 2>/dev/null; do sleep 1; done",
        "OLD=\"$2\"; NEW=\"$3\"; FINAL=\"$4\"; BAK=\"$5\"",
        "shift 5",
        "if [ -f \"$NEW\" ]; then",
        "  if [ -f \"$OLD\" ]; then mv -f \"$OLD\" \"$BAK\"; fi",
        "  mv -f \"$NEW\" \"$FINAL\" || { [ -f \"$BAK\" ] && mv -f \"$BAK\" \"$OLD\"; }",
        "fi",
        "if [ \"$#\" -gt 0 ]; then exec \"$@\"; fi");

    private static final String WINDOWS_SCRIPT = String.join("\r\n",
        "@echo off",
        ":wait",
        "tasklist /FI \"PID eq %~1\" 2>nul | find \"%~1\" >nul",
        "if not errorlevel 1 (",
        "  timeout /t 1 /nobreak >nul",
        "  goto wait",
        ")",
        "if exist \"%~3\" (",
        "  if exist \"%~2\" move /Y \"%~2\" \"%~5\" >nul",
        "  move /Y \"%~3\" \"%~4\" >nul",
        ")",
        "del \"%~f0\"");

    private UpdateInstaller() {}

    public static Plan plan(Path currentJar, Path pending, String version) {
        Path dir = currentJar.getParent();
        String safe = version.replaceAll("[^A-Za-z0-9._-]", "_");
        Path finalJar = dir.resolve("frosted-" + safe + ".jar");
        Path backup = dir.resolve(currentJar.getFileName() + ".bak");
        return new Plan(currentJar, pending, finalJar, backup, version);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** The command line this JVM was started with, or empty when the OS does not tell us. */
    public static List<String> currentLaunchCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        Optional<String> cmd = info.command();
        Optional<String[]> args = info.arguments();
        if (cmd.isEmpty() || args.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        out.add(cmd.get());
        out.addAll(List.of(args.get()));
        return out;
    }

    /**
     * Starts the helper. relaunch is the command to run once the swap is done (empty = just swap).
     * On Windows the swap is done but the game is not restarted automatically.
     */
    public static void schedule(Plan plan, List<String> relaunch) throws IOException {
        deleteOldBackups(plan.backup().getParent(), plan.backup());

        long pid = ProcessHandle.current().pid();
        List<String> cmd = new ArrayList<>();

        if (isWindows()) {
            Path bat = Files.createTempFile("frosted-update", ".bat");
            Files.writeString(bat, WINDOWS_SCRIPT);
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add(bat.toString());
        } else {
            cmd.add("/bin/sh");
            cmd.add("-c");
            cmd.add(UNIX_SCRIPT);
            cmd.add("frosted-update");
        }

        cmd.add(Long.toString(pid));
        cmd.add(plan.oldJar().toString());
        cmd.add(plan.pending().toString());
        cmd.add(plan.finalJar().toString());
        cmd.add(plan.backup().toString());
        if (!isWindows()) cmd.addAll(relaunch);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        if (!isWindows()) pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        pb.start();
    }

    /** Keeps only one backup around. */
    static void deleteOldBackups(Path dir, Path keep) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "frosted-*.jar.bak")) {
            for (Path p : stream) {
                if (!p.equals(keep)) Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // best effort
        }
    }
}
