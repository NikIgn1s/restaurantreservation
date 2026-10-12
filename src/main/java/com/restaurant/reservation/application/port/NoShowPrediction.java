package com.restaurant.reservation.application.port;

public record NoShowPrediction(Double probability, String modelVersion) {

    private static final NoShowPrediction UNAVAILABLE = new NoShowPrediction(null, null);

    public static NoShowPrediction of(double probability, String modelVersion) {
        if (Double.isNaN(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Probability must be between 0 and 1");
        }
        return new NoShowPrediction(probability, modelVersion);
    }

    public static NoShowPrediction unavailable() {
        return UNAVAILABLE;
    }

    public boolean isAvailable() {
        return probability != null;
    }
}
