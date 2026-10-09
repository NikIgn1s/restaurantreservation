package com.restaurant.reservation.infrastructure.config;

import com.restaurant.reservation.infrastructure.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityPropertiesConfig {
}
