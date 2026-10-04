package com.glucotwin.api.dto;

import java.time.Instant;

public record ErrorResponse(String error, String message, Instant timestamp, String traceId) {}
