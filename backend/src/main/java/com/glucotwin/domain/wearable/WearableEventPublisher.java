package com.glucotwin.domain.wearable;

/** Domain port for publishing wearable events to the stream. */
public interface WearableEventPublisher {
    void publish(WearableEvent event);
}
