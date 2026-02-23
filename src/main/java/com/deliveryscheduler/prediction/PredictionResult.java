package com.deliveryscheduler.prediction;

/**
 * Result of a prep time prediction, including uncertainty estimation.
 */
public record PredictionResult(int meanSeconds, double varianceSeconds, String source) {

    public static PredictionResult statistical(int mean, double variance) {
        return new PredictionResult(mean, variance, "STATISTICAL");
    }

    public static PredictionResult defaultPrediction(int mean) {
        return new PredictionResult(mean, mean * 0.15, "DEFAULT");
    }
}
