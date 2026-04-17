package com.blockcorn.core;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * PIN hashing and verification using bcrypt.
 * Storage is platform-specific — see desktop/android modules.
 */
public final class PinManager {

    private static final int BCRYPT_COST = 12;

    private PinManager() {}

    public static String hash(String pin) {
        if (pin == null || pin.length() < 4) {
            throw new IllegalArgumentException("PIN must be at least 4 characters");
        }
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, pin.toCharArray());
    }

    public static boolean verify(String pin, String storedHash) {
        if (pin == null || storedHash == null) return false;
        return BCrypt.verifyer().verify(pin.toCharArray(), storedHash).verified;
    }
}
