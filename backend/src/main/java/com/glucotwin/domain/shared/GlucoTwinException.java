package com.glucotwin.domain.shared;

/** Base exception for all GlucoTwin domain errors. */
public class GlucoTwinException extends RuntimeException {
    public GlucoTwinException(String message) { super(message); }
    public GlucoTwinException(String message, Throwable cause) { super(message, cause); }
}
