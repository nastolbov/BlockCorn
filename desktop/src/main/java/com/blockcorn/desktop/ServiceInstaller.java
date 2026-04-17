package com.blockcorn.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Registers BlockCorn and its external watchdog as system services:
 *   Windows — Task Scheduler tasks under "BlockCorn\" folder
 *   macOS   — LaunchDaemons plists (requires root / sudo)
 */
public final class ServiceInstaller {

    private ServiceInstaller() {}

    // ------------------------------------------------------------------ Windows

    public static void installWindows(String mainJarPath, String watchdogJarPath)
            throws IOException, InterruptedException {
        String javaExe = javaExecutable();

        runSchtasks("/Create", "/TN", "BlockCorn\\MainApp",
            buildMainTaskXml(javaExe, mainJarPath));

        if (watchdogJarPath != null) {
            runSchtasks("/Create", "/TN", "BlockCorn\\Watchdog",
                buildWatchdogTaskXml(javaExe, watchdogJarPath, mainJarPath));
        }
    }

    public static void uninstallWindows() throws IOException, InterruptedException {
        run("schtasks", "/Delete", "/TN", "BlockCorn\\Watchdog", "/F");
        run("schtasks", "/Delete", "/TN", "BlockCorn\\MainApp",  "/F");
    }

    private static void runSchtasks(String action, String tn, String name, String xml)
            throws IOException, InterruptedException {
        Path tmp = Files.createTempFile("blockcorn-task-", ".xml");
        try {
            // schtasks requires UTF-16 LE for XML files on Windows
            Files.write(tmp, xml.getBytes(StandardCharsets.UTF_16LE));
            run("schtasks", action, "/TN", name, "/XML", tmp.toString(), "/F");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ------------------------------------------------------------------- macOS

    public static void installMac(String mainJarPath, String watchdogJarPath)
            throws IOException, InterruptedException {
        String javaExe = javaExecutable();
        writePlist("/Library/LaunchDaemons/com.blockcorn.app.plist",
            buildMacPlist(javaExe, mainJarPath, "com.blockcorn.app", false));
        launchctl("load", "-w", "/Library/LaunchDaemons/com.blockcorn.app.plist");

        if (watchdogJarPath != null) {
            writePlist("/Library/LaunchDaemons/com.blockcorn.watchdog.plist",
                buildMacPlist(javaExe, watchdogJarPath, "com.blockcorn.watchdog", true));
            launchctl("load", "-w", "/Library/LaunchDaemons/com.blockcorn.watchdog.plist");
        }
    }

    public static void uninstallMac() throws IOException, InterruptedException {
        launchctl("unload", "-w", "/Library/LaunchDaemons/com.blockcorn.watchdog.plist");
        launchctl("unload", "-w", "/Library/LaunchDaemons/com.blockcorn.app.plist");
        Files.deleteIfExists(Path.of("/Library/LaunchDaemons/com.blockcorn.watchdog.plist"));
        Files.deleteIfExists(Path.of("/Library/LaunchDaemons/com.blockcorn.app.plist"));
    }

    private static void writePlist(String path, String content) throws IOException {
        Path p = Path.of(path);
        Files.createDirectories(p.getParent());
        Files.createDirectories(Path.of("/Library/Logs/BlockCorn"));
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private static void launchctl(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "launchctl";
        System.arraycopy(args, 0, cmd, 1, args.length);
        run(cmd);
    }

    // ------------------------------------------------------------- XML / plist builders

    private static String buildMainTaskXml(String javaExe, String jarPath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n"
            + "<Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\n"
            + "  <Triggers><BootTrigger><Enabled>true</Enabled></BootTrigger></Triggers>\n"
            + "  <Principals><Principal id=\"Author\">"
            +      "<RunLevel>HighestAvailable</RunLevel></Principal></Principals>\n"
            + "  <Settings>\n"
            + "    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>\n"
            + "    <RestartOnFailure><Interval>PT1M</Interval><Count>10</Count></RestartOnFailure>\n"
            + "    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>\n"
            + "    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>\n"
            + "    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>\n"
            + "  </Settings>\n"
            + "  <Actions Context=\"Author\"><Exec>\n"
            + "    <Command>" + esc(javaExe) + "</Command>\n"
            + "    <Arguments>-jar &quot;" + esc(jarPath) + "&quot;</Arguments>\n"
            + "  </Exec></Actions>\n"
            + "</Task>";
    }

    private static String buildWatchdogTaskXml(String javaExe, String wdJar, String mainJar) {
        return "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n"
            + "<Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\n"
            + "  <Triggers><BootTrigger><Delay>PT30S</Delay><Enabled>true</Enabled></BootTrigger></Triggers>\n"
            + "  <Principals><Principal id=\"Author\">"
            +      "<RunLevel>HighestAvailable</RunLevel></Principal></Principals>\n"
            + "  <Settings>\n"
            + "    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>\n"
            + "    <RestartOnFailure><Interval>PT30S</Interval><Count>999</Count></RestartOnFailure>\n"
            + "    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>\n"
            + "    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>\n"
            + "    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>\n"
            + "  </Settings>\n"
            + "  <Actions Context=\"Author\"><Exec>\n"
            + "    <Command>" + esc(javaExe) + "</Command>\n"
            + "    <Arguments>-jar &quot;" + esc(wdJar) + "&quot; &quot;" + esc(mainJar) + "&quot;</Arguments>\n"
            + "  </Exec></Actions>\n"
            + "</Task>";
    }

    private static String buildMacPlist(String javaExe, String jarPath,
                                        String label, boolean withMainJarArg) {
        String extraArgs = withMainJarArg ? "" : ""; // watchdog passes mainJar at caller level; keep clean
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\""
            +   " \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
            + "<plist version=\"1.0\"><dict>\n"
            + "  <key>Label</key><string>" + label + "</string>\n"
            + "  <key>ProgramArguments</key><array>\n"
            + "    <string>" + javaExe + "</string>\n"
            + "    <string>-jar</string>\n"
            + "    <string>" + jarPath + "</string>\n"
            + "  </array>\n"
            + "  <key>RunAtLoad</key><true/>\n"
            + "  <key>KeepAlive</key><true/>\n"
            + "  <key>StandardOutPath</key>"
            +    "<string>/Library/Logs/BlockCorn/" + label + ".log</string>\n"
            + "  <key>StandardErrorPath</key>"
            +    "<string>/Library/Logs/BlockCorn/" + label + "-err.log</string>\n"
            + "</dict></plist>";
    }

    // ----------------------------------------------------------------------- helpers

    private static String javaExecutable() {
        String home = System.getProperty("java.home", "");
        String os = System.getProperty("os.name", "").toLowerCase();
        String bin = os.contains("win") ? "javaw.exe" : "java";
        Path candidate = Path.of(home, "bin", bin);
        return candidate.toFile().exists() ? candidate.toString() : bin;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void run(String... cmd) throws IOException, InterruptedException {
        new ProcessBuilder(cmd).inheritIO().start().waitFor();
    }
}
