package com.blockcorn.desktop;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks that the hosts file still has the BlockCorn section.
 * If someone removed it externally and the filter should be active, re-injects it.
 */
public final class Watchdog {

    private static final long CHECK_INTERVAL_SEC = 60;

    private final AppState state;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "blockcorn-watchdog");
            t.setDaemon(true);
            return t;
        });

    public Watchdog(AppState state) {
        this.state = state;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::check, CHECK_INTERVAL_SEC, CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
        System.out.println("[Watchdog] started, interval=" + CHECK_INTERVAL_SEC + "s");
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void check() {
        if (!state.isEnabled()) return;
        try {
            Path hostsPath = HostsManager.hostsPath();
            List<String> domains = state.getDomains();
            boolean healed = HostsManager.ensureActive(hostsPath, domains);
            if (healed) {
                System.out.println("[Watchdog] hosts file was tampered with — restored");
            }
        } catch (Exception e) {
            System.err.println("[Watchdog] check failed: " + e.getMessage());
        }
    }
}
