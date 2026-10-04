package com.glucotwin.domain.prediction;

import com.glucotwin.domain.shared.GlucoTwinException;

public class PredictionTimeoutException extends GlucoTwinException {
    public PredictionTimeoutException(String message) { super(message); }
    public PredictionTimeoutException(String message, Throwable cause) { super(message, cause); }
}
