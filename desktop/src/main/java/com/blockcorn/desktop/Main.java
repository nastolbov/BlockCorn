package com.blockcorn.desktop;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point for the BlockCorn desktop app (Windows + macOS).
 *
 * On startup:
 *  1. Load/fetch the domain blocklist.
 *  2. Apply to the OS hosts file if filter is enabled.
 *  3. Show system tray icon.
 *  4. Start watchdog for self-healing.
 *  5. Schedule daily blocklist refresh.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        // Swing look-and-feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // macOS dock icon hide (tray-only app)
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            System.setProperty("apple.awt.UIElement", "true");
        }

        // Write PID so external watchdog can monitor this process
        PidFile.write();
        Runtime.getRuntime().addShutdownHook(new Thread(PidFile::delete, "blockcorn-pid-cleanup"));

        AppState state = new AppState();

        // Data/cache directory
        Path dataDir = dataDir();

        // Load blocklist (cached or fetch)
        System.out.println("[Main] Loading blocklist…");
        BlocklistFetcher fetcher = new BlocklistFetcher(dataDir);
        List<String> domains = fetcher.getOrFetch();
        state.setDomains(domains);
        System.out.println("[Main] Blocklist: " + domains.size() + " domains");

        // Apply hosts file on startup (self-heal)
        try {
            Path hostsPath = HostsManager.hostsPath();
            boolean healed = HostsManager.ensureActive(hostsPath, domains);
            if (healed) System.out.println("[Main] Hosts file was missing BlockCorn section — restored");
        } catch (Exception e) {
            System.err.println("[Main] Could not apply hosts: " + e.getMessage());
            // Continue — may need admin privileges
        }

        // Start watchdog
        new Watchdog(state).start();

        // Schedule daily blocklist refresh
        new Thread(() -> dailyRefreshLoop(fetcher, state), "blockcorn-refresh").start();

        // Start tray on AWT thread
        SwingUtilities.invokeLater(() -> {
            try {
                new TrayApp(state).init();
                System.out.println("[Main] System tray ready");
            } catch (Exception e) {
                System.err.println("[Main] Tray init failed: " + e.getMessage());
                System.exit(1);
            }
        });
    }

    private static void dailyRefreshLoop(BlocklistFetcher fetcher, AppState state) {
        long intervalMs = 24L * 60 * 60 * 1000;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalMs);
                System.out.println("[Main] Daily blocklist refresh…");
                List<String> domains = fetcher.getOrFetch();
                state.setDomains(domains);
                if (state.isEnabled()) {
                    HostsManager.applyBlocklist(HostsManager.hostsPath(), domains);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("[Main] Refresh error: " + e.getMessage());
            }
        }
    }

    private static Path dataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "BlockCorn");
        } else if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "BlockCorn");
        } else {
            return Path.of(System.getProperty("user.home"), ".blockcorn");
        }
    }
}
