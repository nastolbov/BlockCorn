package com.blockcorn.desktop;

import javax.swing.*;
import java.awt.SystemTray;
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
        // These must be set before AWT initializes
        System.setProperty("apple.awt.UIElement", "false");
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        // Force AWT toolkit init on the main thread (required on macOS)
        java.awt.Toolkit.getDefaultToolkit();

        // Swing look-and-feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Write PID so external watchdog can monitor this process
        PidFile.write();
        Runtime.getRuntime().addShutdownHook(new Thread(PidFile::delete, "blockcorn-pid-cleanup"));

        AppState state = new AppState();
        Path dataDir = dataDir();
        BlocklistFetcher fetcher = new BlocklistFetcher(dataDir);

        // Open window — use invokeAndWait so it's guaranteed to appear before background work starts
        System.out.println("[Main] Opening window…");
        try {
            SwingUtilities.invokeAndWait(() -> {
                TrayApp tray = null;
                try {
                    if (SystemTray.isSupported()) {
                        tray = new TrayApp(state);
                        tray.init();
                    }
                } catch (Exception e) {
                    System.err.println("[Main] Tray init failed: " + e.getMessage());
                }
                SettingsUI.show(state, tray);
                System.out.println("[Main] Window created");
            });
        } catch (Exception e) {
            System.err.println("[Main] Window error: " + e.getMessage());
            e.printStackTrace();
        }

        // Load blocklist in background — apply hosts once ready
        new Thread(() -> {
            System.out.println("[Main] Loading blocklist…");
            List<String> domains = fetcher.getOrFetch();
            state.setDomains(domains);
            System.out.println("[Main] Blocklist: " + domains.size() + " domains");
            try {
                Path hostsPath = HostsManager.hostsPath();
                boolean healed = HostsManager.ensureActive(hostsPath, domains);
                if (healed) System.out.println("[Main] Hosts restored");
            } catch (Exception e) {
                System.err.println("[Main] Could not apply hosts: " + e.getMessage());
            }
        }, "blockcorn-init").start();

        // Start watchdog
        new Watchdog(state).start();

        // Daily refresh
        new Thread(() -> dailyRefreshLoop(fetcher, state), "blockcorn-refresh").start();
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
