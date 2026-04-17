package com.blockcorn.desktop;

import com.blockcorn.core.DomainParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fetches StevenBlack/hosts porn-only list and caches it locally.
 * Cache TTL: 24 hours.
 */
public final class BlocklistFetcher {

    private static final String URL =
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts";
    private static final long TTL_MS = 24L * 60 * 60 * 1000;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    private final Path cacheFile;

    public BlocklistFetcher(Path cacheDir) {
        this.cacheFile = cacheDir.resolve("blocklist.hosts");
    }

    public List<String> getOrFetch() {
        try {
            if (cacheIsValid()) {
                return DomainParser.parse(Files.readString(cacheFile));
            }
            return fetch();
        } catch (IOException e) {
            System.err.println("[BlocklistFetcher] error: " + e.getMessage());
            // Fall back to stale cache if available
            if (Files.exists(cacheFile)) {
                try { return DomainParser.parse(Files.readString(cacheFile)); }
                catch (IOException ignored) {}
            }
            return Collections.emptyList();
        }
    }

    private List<String> fetch() throws IOException {
        Request req = new Request.Builder().url(URL).build();
        try (Response resp = HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("HTTP " + resp.code());
            }
            String raw = resp.body().string();
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, raw);
            System.out.println("[BlocklistFetcher] Fetched and cached blocklist");
            return DomainParser.parse(raw);
        }
    }

    private boolean cacheIsValid() throws IOException {
        if (!Files.exists(cacheFile)) return false;
        long age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
        return age < TTL_MS;
    }
}
