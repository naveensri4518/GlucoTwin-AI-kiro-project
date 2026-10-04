package com.glucotwin.domain.prediction;

import com.glucotwin.domain.shared.GlucoTwinException;

public class PredictionServiceUnavailableException extends GlucoTwinException {
    public PredictionServiceUnavailableException(String message) { super(message); }
    public PredictionServiceUnavailableException(String message, Throwable cause) { super(message, cause); }
}
