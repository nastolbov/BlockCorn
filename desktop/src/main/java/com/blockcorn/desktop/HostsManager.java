package com.blockcorn.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Manages the OS hosts file, inserting/removing the BlockCorn section.
 * Works on Windows (C:\Windows\...\hosts) and macOS/Linux (/etc/hosts).
 */
public final class HostsManager {

    static final String MARKER_START = "# === blockcorn-start ===";
    static final String MARKER_END   = "# === blockcorn-end ===";

    private HostsManager() {}

    public static Path hostsPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Paths.get("C:\\Windows\\System32\\drivers\\etc\\hosts");
        }
        return Paths.get("/etc/hosts");
    }

    public static boolean isActive(Path path) throws IOException {
        return Files.readString(path).contains(MARKER_START);
    }

    /** Write the blocklist section into the hosts file. */
    public static void applyBlocklist(Path path, List<String> domains) throws IOException {
        String base = stripSection(Files.readString(path));

        StringBuilder section = new StringBuilder();
        section.append("\n").append(MARKER_START).append("\n");
        for (String domain : domains) {
            section.append("0.0.0.0 ").append(domain).append("\n");
            section.append("0.0.0.0 www.").append(domain).append("\n");
        }
        section.append(MARKER_END).append("\n");

        writePrivileged(path, base + section);
    }

    /** Remove the BlockCorn section from the hosts file. */
    public static void removeBlocklist(Path path) throws IOException {
        writePrivileged(path, stripSection(Files.readString(path)));
    }

    /**
     * Write content to a root-owned file.
     * On macOS: uses osascript to show a system password dialog (no sudo needed).
     * On Windows: direct write (app must be run as Administrator).
     */
    private static void writePrivileged(Path path, String content) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            // Write to a temp file, then move it with osascript elevation
            Path tmp = Files.createTempFile("blockcorn-hosts-", ".txt");
            Files.writeString(tmp, content);
            String script = String.format(
                "do shell script \"cp '%s' '%s'\" with administrator privileges",
                tmp.toString(), path.toString()
            );
            int rc;
            try {
                rc = new ProcessBuilder("osascript", "-e", script)
                    .inheritIO().start().waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано ожидание привилегий");
            }
            Files.deleteIfExists(tmp);
            if (rc != 0) throw new IOException("Привилегии отклонены или ошибка записи hosts");
        } else {
            Files.writeString(path, content);
        }
    }

    /**
     * Self-healing: if the section disappeared externally but should be active, re-inject.
     * @return true if the file was healed.
     */
    public static boolean ensureActive(Path path, List<String> domains) throws IOException {
        if (!isActive(path)) {
            applyBlocklist(path, domains);
            return true;
        }
        return false;
    }

    private static String stripSection(String content) {
        StringBuilder out = new StringBuilder();
        boolean inside = false;
        for (String line : content.split("\n", -1)) {
            if (line.trim().equals(MARKER_START)) { inside = true; continue; }
            if (line.trim().equals(MARKER_END))   { inside = false; continue; }
            if (!inside) { out.append(line).append("\n"); }
        }
        return out.toString();
    }
}
