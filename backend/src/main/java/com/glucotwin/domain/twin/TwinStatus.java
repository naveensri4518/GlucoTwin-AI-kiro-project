package com.glucotwin.domain.twin;

/** Lifecycle status of a Digital Twin. Transitions are unidirectional. */
public enum TwinStatus {
    /** EHR loaded; no wearable event received yet. */
    INITIALISED,
    /** Wearable events flowing normally. */
    ACTIVE,
    /** No wearable event received for > configured threshold. */
    STALE,
    /** Patient discharged or study ended; state is frozen and read-only. */
    ARCHIVED
}
