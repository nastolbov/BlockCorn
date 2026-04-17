package com.blockcorn.desktop;

import com.blockcorn.core.DelayState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Dual-storage for the 24-hour accountability delay.
 *
 * Primary:   OS system preferences (Windows registry / macOS plist).
 * Secondary: JSON file in the OS data directory (%ProgramData% / /Library/Application Support).
 *
 * Both locations are checked on read; the earliest timestamp wins (most conservative).
 * Reinstalling the app does NOT clear these because they live outside the install directory.
 */
public final class DelayStore {

    private static final String NODE      = "com/blockcorn";
    private static final String KEY_DELAY = "disable_requested_at";

    private static Preferences prefs() {
        return Preferences.systemRoot().node(NODE);
    }

    private static Path stateFile() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;
        if (os.contains("win")) {
            base = Path.of(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"));
        } else if (os.contains("mac")) {
            base = Path.of(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            base = Path.of(System.getProperty("user.home"), ".local", "share");
        }
        return base.resolve("BlockCorn").resolve("delay_state.json");
    }

    public static DelayState read() {
        Long fromPrefs = readFromPrefs();
        Long fromFile  = readFromFile();

        Long epoch = null;
        if (fromPrefs != null && fromFile != null) {
            epoch = Math.min(fromPrefs, fromFile); // use earliest (conservative)
        } else if (fromPrefs != null) {
            epoch = fromPrefs;
        } else if (fromFile != null) {
            epoch = fromFile;
        }

        return epoch != null ? new DelayState(epoch) : null;
    }

    public static void write(DelayState state) {
        // Prefs
        try {
            Preferences p = prefs();
            p.putLong(KEY_DELAY, state.getRequestedAt().getEpochSecond());
            p.flush();
        } catch (BackingStoreException e) {
            System.err.println("[DelayStore] prefs write failed: " + e.getMessage());
        }

        // File
        try {
            Path f = stateFile();
            Files.createDirectories(f.getParent());
            Files.writeString(f, state.toJson());
        } catch (IOException e) {
            System.err.println("[DelayStore] file write failed: " + e.getMessage());
        }
    }

    public static void clear() {
        try {
            Preferences p = prefs();
            p.remove(KEY_DELAY);
            p.flush();
        } catch (BackingStoreException e) {
            System.err.println("[DelayStore] prefs clear failed: " + e.getMessage());
        }
        try {
            Files.deleteIfExists(stateFile());
        } catch (IOException e) {
            System.err.println("[DelayStore] file clear failed: " + e.getMessage());
        }
    }

    private static Long readFromPrefs() {
        long val = prefs().getLong(KEY_DELAY, -1L);
        return val > 0 ? val : null;
    }

    private static Long readFromFile() {
        Path f = stateFile();
        if (!Files.exists(f)) return null;
        try {
            DelayState s = DelayState.fromJson(Files.readString(f));
            return s != null ? s.getRequestedAt().getEpochSecond() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
