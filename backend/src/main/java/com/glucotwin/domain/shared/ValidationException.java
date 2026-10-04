package com.glucotwin.domain.shared;

public class ValidationException extends GlucoTwinException {
    private final String field;
    private final String errorCode;

    public ValidationException(String field, String errorCode, String message) {
        super(message);
        this.field = field;
        this.errorCode = errorCode;
    }

    public String getField() { return field; }
    public String getErrorCode() { return errorCode; }
}
