package com.restaurant.reservation.infrastructure.config;

import com.restaurant.reservation.application.dto.Actor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
    }

    @Bean
    public OpenAPI reservationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reservation API")
                        .version("0.1.0")
                        .description("""
                                Booking platform for restaurants, coworkings and sport venues.

                                How to authorize:
                                1. Call POST /api/auth/login (or POST /api/auth/register first).
                                2. Copy accessToken from the response.
                                3. Press Authorize and paste the token without the Bearer prefix.

                                Dev users in the docker profile, password password123:
                                owner@dev.local (VENUE_OWNER), alice@dev.local and bob@dev.local (CUSTOMER), admin@dev.local (ADMIN).
                                """))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
