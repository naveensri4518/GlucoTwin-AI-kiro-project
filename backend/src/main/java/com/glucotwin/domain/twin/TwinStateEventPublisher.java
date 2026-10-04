package com.glucotwin.domain.twin;

/** Domain port for publishing twin state update events (e.g. SSE push to dashboard). */
public interface TwinStateEventPublisher {
    void publishUpdated(TwinStateSnapshot snapshot);
}
