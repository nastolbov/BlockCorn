package com.blockcorn.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes/reads the main process PID so the watchdog can verify it is alive.
 * File location: %ProgramData%\BlockCorn\blockcorn.pid (Windows)
 *                /Library/Application Support/BlockCorn/blockcorn.pid (macOS)
 */
public final class PidFile {

    private PidFile() {}

    private static Path pidPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Path.of(
                System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"),
                "BlockCorn", "blockcorn.pid"
            );
        } else if (os.contains("mac")) {
            return Path.of("/Library/Application Support/BlockCorn/blockcorn.pid");
        } else {
            return Path.of(System.getProperty("user.home"), ".blockcorn", "blockcorn.pid");
        }
    }

    /** Write the current JVM's PID to the PID file. */
    public static void write() {
        long pid = ProcessHandle.current().pid();
        try {
            Path p = pidPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, String.valueOf(pid));
        } catch (IOException e) {
            System.err.println("[PidFile] write failed: " + e.getMessage());
        }
    }

    /** Read the stored PID, or -1 if absent/unreadable. */
    public static long read() {
        try {
            Path p = pidPath();
            if (!Files.exists(p)) return -1;
            return Long.parseLong(Files.readString(p).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isMainProcessAlive() {
        long pid = read();
        if (pid <= 0) return false;
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    public static void delete() {
        try { Files.deleteIfExists(pidPath()); } catch (IOException ignored) {}
    }
}
