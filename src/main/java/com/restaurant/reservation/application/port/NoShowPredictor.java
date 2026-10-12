package com.restaurant.reservation.application.port;

public interface NoShowPredictor {

    NoShowPrediction predict(NoShowFeatures features);
}
