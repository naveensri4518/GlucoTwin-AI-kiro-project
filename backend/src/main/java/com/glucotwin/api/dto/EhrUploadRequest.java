package com.glucotwin.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record EhrUploadRequest(
        @NotNull(message = "dateOfBirth is required")
        @Past(message = "dateOfBirth must be a past date")
        LocalDate dateOfBirth,

        @NotBlank(message = "sex is required")
        String sex,

        @DecimalMin(value = "10.0", message = "bmi must be >= 10.0")
        @DecimalMax(value = "80.0", message = "bmi must be <= 80.0")
        Double bmi,

        @NotNull(message = "diabetesOnsetDate is required")
        @Past(message = "diabetesOnsetDate must be a past date")
        LocalDate diabetesOnsetDate,

        @DecimalMin(value = "3.0", message = "hba1c must be >= 3.0%")
        @DecimalMax(value = "20.0", message = "hba1c must be <= 20.0%")
        Double hba1c,

        @DecimalMin(value = "1.0", message = "fastingGlucose must be >= 1.0 mmol/L")
        @DecimalMax(value = "35.0", message = "fastingGlucose must be <= 35.0 mmol/L")
        Double fastingGlucose) {}
