package com.blockcorn.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses StevenBlack/hosts format into a deduplicated domain list.
 * Input lines: "0.0.0.0 domain.com"
 * Output: unique base domains (www. prefix stripped so one rule covers both).
 */
public final class DomainParser {

    private static final int DEFAULT_MAX = 29_000;

    private DomainParser() {}

    public static List<String> parse(String raw) {
        return parse(raw, DEFAULT_MAX);
    }

    public static List<String> parse(String raw, int maxDomains) {
        Set<String> seen = new LinkedHashSet<>();

        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            String[] parts = trimmed.split("\\s+");
            if (parts.length < 2) continue;

            String ip = parts[0];
            if (!ip.equals("0.0.0.0") && !ip.equals("127.0.0.1")) continue;

            String domain = parts[1].toLowerCase();
            if (domain.startsWith("www.")) domain = domain.substring(4);
            if (domain.isEmpty() || domain.equals("localhost")) continue;

            seen.add(domain);
            if (seen.size() >= maxDomains) break;
        }

        return new ArrayList<>(seen);
    }
}
