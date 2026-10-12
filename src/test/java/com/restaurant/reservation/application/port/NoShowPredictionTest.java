package com.restaurant.reservation.application.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoShowPredictionTest {

    @Test
    void availablePredictionKeepsProbabilityAndVersion() {
        NoShowPrediction prediction = NoShowPrediction.of(0.42, "v1");

        assertThat(prediction.isAvailable()).isTrue();
        assertThat(prediction.probability()).isEqualTo(0.42);
        assertThat(prediction.modelVersion()).isEqualTo("v1");
    }

    @Test
    void acceptsTheBoundaries() {
        assertThat(NoShowPrediction.of(0.0, "v").isAvailable()).isTrue();
        assertThat(NoShowPrediction.of(1.0, "v").isAvailable()).isTrue();
    }

    @Test
    void rejectsValuesOutsideTheUnitInterval() {
        assertThatThrownBy(() -> NoShowPrediction.of(-0.01, "v")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NoShowPrediction.of(1.01, "v")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NoShowPrediction.of(Double.NaN, "v")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unavailableIsNotAvailable() {
        assertThat(NoShowPrediction.unavailable().isAvailable()).isFalse();
        assertThat(NoShowPrediction.unavailable()).isSameAs(NoShowPrediction.unavailable());
    }
}
