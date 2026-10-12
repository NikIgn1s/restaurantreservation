package com.restaurant.reservation.infrastructure.ml;

import com.restaurant.reservation.application.port.NoShowFeatures;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ml.enabled", havingValue = "false")
public class DisabledNoShowPredictor implements NoShowPredictor {

    @Override
    public NoShowPrediction predict(NoShowFeatures features) {
        return NoShowPrediction.unavailable();
    }
}
