package com.blockcorn.desktop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Standalone watchdog process. Launched by Task Scheduler / LaunchDaemon at boot
 * (30 s after main app). Checks every 30 s whether the main BlockCorn process is
 * alive; if not, re-launches it.
 *
 * Usage: java -jar blockcorn-watchdog.jar [/path/to/blockcorn-desktop.jar]
 */
public final class WatchdogMain {

    private static final long CHECK_INTERVAL_SEC = 30;

    public static void main(String[] args) throws InterruptedException {
        String jarPath = args.length > 0 ? args[0] : defaultMainJar();
        System.out.println("[WatchdogMain] monitoring main jar: " + jarPath);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "blockcorn-ext-watchdog");
            t.setDaemon(false);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            () -> checkAndRestart(jarPath),
            CHECK_INTERVAL_SEC, CHECK_INTERVAL_SEC, TimeUnit.SECONDS
        );

        Thread.currentThread().join(); // keep process alive
    }

    private static void checkAndRestart(String jarPath) {
        try {
            if (!PidFile.isMainProcessAlive()) {
                System.out.println("[WatchdogMain] main process gone — restarting");
                launch(jarPath);
            }
        } catch (Exception e) {
            System.err.println("[WatchdogMain] error: " + e.getMessage());
        }
    }

    private static void launch(String jarPath) throws IOException {
        String javaExe = ProcessHandle.current().info().command().orElse("java");
        new ProcessBuilder(javaExe, "-jar", jarPath)
            .inheritIO()
            .start();
    }

    private static String defaultMainJar() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Path.of(
                System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"),
                "BlockCorn", "blockcorn-desktop.jar"
            ).toString();
        } else if (os.contains("mac")) {
            return "/Applications/BlockCorn.app/Contents/MacOS/blockcorn-desktop.jar";
        }
        return Path.of(System.getProperty("user.home"), ".blockcorn", "blockcorn-desktop.jar").toString();
    }
}
