package com.glucotwin.domain.twin;

public enum ActivityLevel {
    SEDENTARY(0), LIGHT(1), MODERATE(2), VIGOROUS(3);

    private final int encodedValue;

    ActivityLevel(int encodedValue) {
        this.encodedValue = encodedValue;
    }

    public int getEncodedValue() {
        return encodedValue;
    }
}
