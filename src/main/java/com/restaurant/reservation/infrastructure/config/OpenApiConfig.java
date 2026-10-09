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

    private static final String USER_ID = "userId";
    private static final String USER_ROLE = "userRole";

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

                                Temporary authentication: press Authorize and fill X-User-Id and X-User-Role.
                                Seeded users in the docker profile:
                                owner 11111111-1111-1111-1111-111111111111 (VENUE_OWNER),
                                customer 22222222-2222-2222-2222-222222222222 (CUSTOMER),
                                customer 33333333-3333-3333-3333-333333333333 (CUSTOMER),
                                admin 44444444-4444-4444-4444-444444444444 (ADMIN).
                                """))
                .components(new Components()
                        .addSecuritySchemes(USER_ID, headerScheme("X-User-Id"))
                        .addSecuritySchemes(USER_ROLE, headerScheme("X-User-Role")))
                .addSecurityItem(new SecurityRequirement().addList(USER_ID).addList(USER_ROLE));
    }

    private static SecurityScheme headerScheme(String headerName) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName);
    }
}
