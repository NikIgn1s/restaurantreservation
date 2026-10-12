package com.restaurant.reservation.application.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskPolicyTest {

    @Test
    void keepsConfiguredValues() {
        RiskPolicy policy = new RiskPolicy(0.4, true, 0.5);

        assertThat(policy.depositThreshold()).isEqualTo(0.4);
        assertThat(policy.overbookingEnabled()).isTrue();
        assertThat(policy.overbookingMinProbability()).isEqualTo(0.5);
    }

    @Test
    void acceptsTheBoundaries() {
        assertThat(new RiskPolicy(0.0, false, 1.0).depositThreshold()).isZero();
    }

    @Test
    void rejectsThresholdsOutsideTheUnitInterval() {
        assertThatThrownBy(() -> new RiskPolicy(-0.1, true, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RiskPolicy(1.1, true, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RiskPolicy(0.4, true, 2.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RiskPolicy(Double.NaN, true, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
