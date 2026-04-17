package com.blockcorn.android;

import android.content.Context;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Stores the bcrypt PIN hash in EncryptedSharedPreferences (Android Keystore).
 * Falls back to regular SharedPreferences if encryption is unavailable.
 */
public final class PinManager {

    private static final int    BCRYPT_COST = 12;
    private static final String PREF_FILE   = "blockcorn_pin";
    private static final String KEY_HASH    = "pin_hash";

    private PinManager() {}

    public static boolean isSet(Context ctx) {
        return loadHash(ctx) != null;
    }

    public static void setPin(Context ctx, String pin) {
        if (pin == null || pin.length() < 4) {
            throw new IllegalArgumentException("PIN must be at least 4 characters");
        }
        String hash = BCrypt.withDefaults().hashToString(BCRYPT_COST, pin.toCharArray());
        saveHash(ctx, hash);
    }

    public static boolean verify(Context ctx, String pin) {
        String hash = loadHash(ctx);
        if (hash == null) return false;
        return BCrypt.verifyer().verify(pin.toCharArray(), hash).verified;
    }

    public static void clearPin(Context ctx) {
        getPrefs(ctx).edit().remove(KEY_HASH).apply();
    }

    private static void saveHash(Context ctx, String hash) {
        getPrefs(ctx).edit().putString(KEY_HASH, hash).apply();
    }

    private static String loadHash(Context ctx) {
        return getPrefs(ctx).getString(KEY_HASH, null);
    }

    private static android.content.SharedPreferences getPrefs(Context ctx) {
        try {
            MasterKey masterKey = new MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
            return EncryptedSharedPreferences.create(
                ctx, PREF_FILE, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            android.util.Log.w("PinManager", "EncryptedSharedPreferences unavailable, using plain", e);
            return ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        }
    }
}
