package com.restaurant.reservation.infrastructure.security;

import com.restaurant.reservation.application.dto.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityActorProvider {

    public Optional<Actor> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Actor actor) {
            return Optional.of(actor);
        }
        return Optional.empty();
    }
}
