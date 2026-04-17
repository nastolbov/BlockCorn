package com.blockcorn.core;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/** Shared application configuration, serialisable to/from JSON. */
public final class Config {

    public enum Strictness { STRICT, MEDIUM, LIGHT }

    public Strictness strictness = Strictness.STRICT;
    public List<String> whitelist = new ArrayList<>();
    public boolean imageClassification = false;
    public long blocklistUpdatedAt = 0; // unix epoch seconds

    private static final Gson GSON = new Gson();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static Config fromJson(String json) {
        return GSON.fromJson(json, Config.class);
    }

    public static Config defaults() {
        return new Config();
    }
}
