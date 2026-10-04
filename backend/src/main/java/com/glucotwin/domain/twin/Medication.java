package com.glucotwin.domain.twin;

import java.util.Objects;

public record Medication(String name, String dose, String frequency) {
    public Medication {
        Objects.requireNonNull(name, "medication name must not be null");
        Objects.requireNonNull(dose, "dose must not be null");
        Objects.requireNonNull(frequency, "frequency must not be null");
    }
}
