package com.blockcorn.android;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads, caches and serves the domain blocklist on Android.
 * Cache lives in internal storage; updated daily.
 */
public final class BlocklistManager {

    private static final String STEVENBLACK_URL =
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts";
    private static final long   TTL_MS      = TimeUnit.HOURS.toMillis(24);
    private static final String PREF_FILE   = "blockcorn_prefs";
    private static final String KEY_TS      = "blocklist_ts";
    private static final String CACHE_FILE  = "blocklist.hosts";
    private static final int    MAX_DOMAINS = 29_000;

    private static volatile BlocklistManager instance;

    private final Context context;
    private final OkHttpClient http;
    private volatile Set<String> blocked = Collections.emptySet();

    private BlocklistManager(Context context) {
        this.context = context.getApplicationContext();
        this.http     = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    public static BlocklistManager getInstance(Context ctx) {
        if (instance == null) {
            synchronized (BlocklistManager.class) {
                if (instance == null) instance = new BlocklistManager(ctx);
            }
        }
        return instance;
    }

    public static BlocklistManager getInstance() {
        if (instance == null) throw new IllegalStateException("Not initialized");
        return instance;
    }

    /** Call on a background thread during VPN start. */
    public void loadOrFetch() {
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE);
            SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            long ts = prefs.getLong(KEY_TS, 0);

            String raw;
            if (cacheFile.exists() && System.currentTimeMillis() - ts < TTL_MS) {
                raw = new String(Files.readAllBytes(cacheFile.toPath()));
            } else {
                raw = fetchRaw();
                Files.write(cacheFile.toPath(), raw.getBytes());
                prefs.edit().putLong(KEY_TS, System.currentTimeMillis()).apply();
            }

            blocked = new HashSet<>(parseDomains(raw));
            android.util.Log.i("BlocklistManager", "Loaded " + blocked.size() + " domains");

        } catch (Exception e) {
            android.util.Log.e("BlocklistManager", "loadOrFetch failed", e);
        }
    }

    public boolean isBlocked(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        if (d.startsWith("www.")) d = d.substring(4);
        return blocked.contains(d);
    }

    private String fetchRaw() throws IOException {
        Request req = new Request.Builder().url(STEVENBLACK_URL).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("HTTP " + resp.code());
            }
            return resp.body().string();
        }
    }

    private List<String> parseDomains(String raw) {
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] parts = t.split("\\s+");
            if (parts.length < 2) continue;
            if (!parts[0].equals("0.0.0.0") && !parts[0].equals("127.0.0.1")) continue;
            String d = parts[1].toLowerCase();
            if (d.startsWith("www.")) d = d.substring(4);
            if (!d.isEmpty() && !d.equals("localhost")) {
                seen.add(d);
                if (seen.size() >= MAX_DOMAINS) break;
            }
        }
        return new java.util.ArrayList<>(seen);
    }
}
