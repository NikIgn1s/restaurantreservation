package com.restaurant.reservation.presentation;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.model.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor asUser(String userId, String role) {
        return asUser(UUID.fromString(userId), UserRole.valueOf(role.toUpperCase()));
    }

    public static RequestPostProcessor asUser(UUID userId, UserRole role) {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(new Actor(userId, role), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
            return request;
        };
    }
}
