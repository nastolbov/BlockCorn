package com.blockcorn.core;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Records when a disable request was made and checks if the 24-hour wait is over.
 * Serialised to/from JSON for dual-location storage (registry + file).
 */
public final class DelayState {

    public static final long DELAY_HOURS = 24;
    private static final Gson GSON = new Gson();

    @SerializedName("requested_at")
    private final long requestedAtEpoch; // unix seconds

    public DelayState() {
        this.requestedAtEpoch = Instant.now().getEpochSecond();
    }

    /** For deserialization. */
    public DelayState(long requestedAtEpoch) {
        this.requestedAtEpoch = requestedAtEpoch;
    }

    public Instant getRequestedAt() {
        return Instant.ofEpochSecond(requestedAtEpoch);
    }

    public long secondsRemaining() {
        Instant deadline = getRequestedAt().plus(DELAY_HOURS, ChronoUnit.HOURS);
        return Math.max(0, deadline.getEpochSecond() - Instant.now().getEpochSecond());
    }

    public boolean isExpired() {
        return secondsRemaining() == 0;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static DelayState fromJson(String json) {
        return GSON.fromJson(json, DelayState.class);
    }
}
