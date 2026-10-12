package com.restaurant.reservation.infrastructure.config;

import com.restaurant.reservation.application.policy.RiskPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RiskPolicy.class)
public class RiskPolicyConfig {
}
