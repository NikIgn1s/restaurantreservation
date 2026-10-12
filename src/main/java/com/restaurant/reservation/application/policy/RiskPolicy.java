package com.restaurant.reservation.application.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.risk")
public record RiskPolicy(
        @DefaultValue("0.4") double depositThreshold,
        @DefaultValue("true") boolean overbookingEnabled,
        @DefaultValue("0.5") double overbookingMinProbability) {

    public RiskPolicy {
        requireProbability(depositThreshold, "depositThreshold");
        requireProbability(overbookingMinProbability, "overbookingMinProbability");
    }

    private static void requireProbability(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
