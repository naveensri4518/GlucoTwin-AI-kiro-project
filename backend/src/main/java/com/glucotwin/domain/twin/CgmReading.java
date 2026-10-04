package com.glucotwin.domain.twin;

import java.time.Instant;
import java.util.Objects;

/** A single CGM reading entry in the rolling history buffer. */
public record CgmReading(double value, Instant timestamp) {
    public CgmReading {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
