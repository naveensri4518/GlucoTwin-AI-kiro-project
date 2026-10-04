package com.glucotwin.test.mock;

import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.twin.TwinStateSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double for PredictionModelPort.
 * Configurable to return a fixed probability or throw a specified exception.
 * Records all calls for assertion in tests.
 */
public class MockPredictionModel implements PredictionModelPort {

    private double fixedProbability;
    private RuntimeException exceptionToThrow;
    private final List<TwinStateSnapshot> recordedCalls = new ArrayList<>();
    private final String modelVersion;

    public MockPredictionModel() {
        this(0.5);
    }

    public MockPredictionModel(double fixedProbability) {
        this.fixedProbability = fixedProbability;
        this.modelVersion = "mock-v1.0.0";
    }

    /** Configure the model to return a specific probability on the next call. */
    public MockPredictionModel withProbability(double probability) {
        this.fixedProbability = probability;
        this.exceptionToThrow = null;
        return this;
    }

    /** Configure the model to throw the given exception on predict(). */
    public MockPredictionModel willThrow(RuntimeException exception) {
        this.exceptionToThrow = exception;
        return this;
    }

    @Override
    public PredictionResult predict(TwinStateSnapshot snapshot) {
        recordedCalls.add(snapshot);
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        double low = Math.max(0.0, fixedProbability - 0.1);
        double high = Math.min(1.0, fixedProbability + 0.1);
        // Ensure non-degenerate interval
        if (Double.compare(low, high) == 0) {
            low = Math.max(0.0, low - 0.01);
            high = Math.min(1.0, high + 0.01);
        }
        return new PredictionResult(
                fixedProbability,
                new ConfidenceInterval(low, high),
                List.of(new ContributingFactor("cgm_current", 0.3, RiskDirection.INCREASES_RISK)),
                modelVersion,
                List.of());
    }

    @Override
    public String getModelVersion() {
        return modelVersion;
    }

    public List<TwinStateSnapshot> getRecordedCalls() {
        return List.copyOf(recordedCalls);
    }

    public int getCallCount() {
        return recordedCalls.size();
    }

    public void reset() {
        recordedCalls.clear();
        exceptionToThrow = null;
        fixedProbability = 0.5;
    }
}
