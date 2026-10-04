package com.glucotwin.api;

import com.glucotwin.api.dto.ErrorResponse;
import com.glucotwin.domain.prediction.GlucoseReadingRequiredException;
import com.glucotwin.domain.prediction.PredictionServiceUnavailableException;
import com.glucotwin.domain.prediction.PredictionTimeoutException;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.shared.ValidationException;
import com.glucotwin.domain.twin.TwinStateArchivedError;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(GlucoseReadingRequiredException.class)
    public ResponseEntity<ErrorResponse> handleGlucoseRequired(GlucoseReadingRequiredException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "GLUCOSE_READING_REQUIRED", ex.getMessage());
    }

    @ExceptionHandler(TwinStateArchivedError.class)
    public ResponseEntity<ErrorResponse> handleArchived(TwinStateArchivedError ex) {
        return build(HttpStatus.CONFLICT, "TWIN_STATE_ARCHIVED", ex.getMessage());
    }

    @ExceptionHandler(PredictionServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMlUnavailable(PredictionServiceUnavailableException ex) {
        log.error("ML service unavailable: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "ML_SERVICE_UNAVAILABLE",
                "The prediction service is temporarily unavailable");
    }

    @ExceptionHandler(PredictionTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleMlTimeout(PredictionTimeoutException ex) {
        log.error("ML service timeout: {}", ex.getMessage());
        return build(HttpStatus.GATEWAY_TIMEOUT, "ML_SERVICE_TIMEOUT",
                "The prediction service timed out");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(errorCode, message, Instant.now(), MDC.get("traceId")));
    }
}
