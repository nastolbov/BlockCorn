package com.blockcorn.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Registers/deregisters the app for OS startup.
 * Windows  → HKCU\Software\Microsoft\Windows\CurrentVersion\Run (via reg.exe, no JNA needed)
 * macOS    → ~/Library/LaunchAgents/com.blockcorn.plist
 */
public final class AutoStart {

    private static final String APP_NAME   = "BlockCorn";
    private static final String PLIST_NAME = "com.blockcorn.plist";

    private AutoStart() {}

    public static void enable(String jarPath) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                enableWindows(jarPath);
            } else if (os.contains("mac")) {
                enableMac(jarPath);
            }
        } catch (Exception e) {
            System.err.println("[AutoStart] enable failed: " + e.getMessage());
        }
    }

    public static void disable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("reg", "delete",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", APP_NAME, "/f").start().waitFor();
            } else if (os.contains("mac")) {
                Files.deleteIfExists(launchAgentPath());
            }
        } catch (Exception e) {
            System.err.println("[AutoStart] disable failed: " + e.getMessage());
        }
    }

    private static void enableWindows(String jarPath) throws IOException, InterruptedException {
        String cmd = "\"" + ProcessHandle.current().info().command().orElse("java") + "\" -jar \"" + jarPath + "\"";
        new ProcessBuilder("reg", "add",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", APP_NAME, "/t", "REG_SZ", "/d", cmd, "/f"
        ).start().waitFor();
    }

    private static void enableMac(String jarPath) throws IOException {
        String java = ProcessHandle.current().info().command().orElse("java");
        String plist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
              "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>Label</key>       <string>com.blockcorn</string>
              <key>ProgramArguments</key>
              <array>
                <string>%s</string>
                <string>-jar</string>
                <string>%s</string>
              </array>
              <key>RunAtLoad</key>  <true/>
              <key>KeepAlive</key>  <true/>
            </dict>
            </plist>
            """.formatted(java, jarPath);

        Path p = launchAgentPath();
        Files.createDirectories(p.getParent());
        Files.writeString(p, plist);
        new ProcessBuilder("launchctl", "load", p.toString()).start();
    }

    private static Path launchAgentPath() {
        return Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", PLIST_NAME);
    }
}
