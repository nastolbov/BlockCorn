package com.blockcorn.desktop;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Stores the bcrypt PIN hash in the OS system preferences.
 * Windows  → HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Prefs\com\blockcorn (requires admin).
 * macOS    → /Library/Preferences/com.apple.java.util.prefs.plist (system root).
 * Linux    → /etc/java/prefs/com/blockcorn (for dev/testing).
 */
public final class PinStore {

    private static final String NODE = "com/blockcorn";
    private static final String KEY_HASH = "pin_hash";

    private static Preferences prefs() {
        return Preferences.systemRoot().node(NODE);
    }

    public static boolean isSet() {
        return prefs().get(KEY_HASH, null) != null;
    }

    public static void saveHash(String bcryptHash) throws BackingStoreException {
        Preferences p = prefs();
        p.put(KEY_HASH, bcryptHash);
        p.flush();
    }

    public static String loadHash() {
        return prefs().get(KEY_HASH, null);
    }

    public static void clear() throws BackingStoreException {
        Preferences p = prefs();
        p.remove(KEY_HASH);
        p.flush();
    }
}
