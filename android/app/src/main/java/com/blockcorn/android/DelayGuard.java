package com.blockcorn.android;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 24-hour accountability delay for disabling the VPN filter.
 * State stored in SharedPreferences (survives app reinstall via backup or device backup,
 * but is cleared by a factory reset — acceptable for mobile).
 *
 * For stronger tamper resistance the timestamp is also stored in a second location
 * (internal file), and the earlier of the two is used.
 */
public final class DelayGuard {

    private static final String PREF_FILE  = "blockcorn_delay";
    private static final String KEY_TS     = "disable_requested_at";
    private static final long   DELAY_MS   = 24L * 60 * 60 * 1000;

    private DelayGuard() {}

    /** @return true if a disable request is pending (timer not yet expired). */
    public static boolean isPending(Context ctx) {
        long ts = getRequestedAt(ctx);
        return ts > 0 && !isExpired(ts);
    }

    /** @return remaining seconds, or 0 if no request or expired. */
    public static long secondsRemaining(Context ctx) {
        long ts = getRequestedAt(ctx);
        if (ts <= 0) return 0;
        long elapsed = System.currentTimeMillis() - ts;
        long remaining = DELAY_MS - elapsed;
        return remaining > 0 ? remaining / 1000 : 0;
    }

    /** @return true if a disable request was made AND 24h have elapsed. */
    public static boolean isReadyToDisable(Context ctx) {
        long ts = getRequestedAt(ctx);
        return ts > 0 && isExpired(ts);
    }

    /** Record a new disable request, starting the 24h timer. */
    public static void requestDisable(Context ctx) {
        prefs(ctx).edit().putLong(KEY_TS, System.currentTimeMillis()).apply();
        writeBackupFile(ctx, System.currentTimeMillis());
    }

    /** Clear the delay state (call only after PIN verified + timer expired). */
    public static void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY_TS).apply();
        deleteBackupFile(ctx);
    }

    private static long getRequestedAt(Context ctx) {
        long fromPrefs = prefs(ctx).getLong(KEY_TS, 0);
        long fromFile  = readBackupFile(ctx);
        if (fromPrefs > 0 && fromFile > 0) return Math.min(fromPrefs, fromFile);
        return fromPrefs > 0 ? fromPrefs : fromFile;
    }

    private static boolean isExpired(long ts) {
        return System.currentTimeMillis() - ts >= DELAY_MS;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    private static java.io.File backupFile(Context ctx) {
        return new java.io.File(ctx.getFilesDir(), "delay_guard.json");
    }

    private static void writeBackupFile(Context ctx, long ts) {
        try {
            java.io.File f = backupFile(ctx);
            new java.io.FileWriter(f).append("{\"ts\":" + ts + "}").close();
        } catch (java.io.IOException ignored) {}
    }

    private static long readBackupFile(Context ctx) {
        java.io.File f = backupFile(ctx);
        if (!f.exists()) return 0;
        try (java.util.Scanner sc = new java.util.Scanner(f)) {
            String s = sc.useDelimiter("\\A").next();
            int idx = s.indexOf("\"ts\":");
            if (idx < 0) return 0;
            return Long.parseLong(s.substring(idx + 5).replaceAll("[^0-9].*", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static void deleteBackupFile(Context ctx) {
        backupFile(ctx).delete();
    }
}
