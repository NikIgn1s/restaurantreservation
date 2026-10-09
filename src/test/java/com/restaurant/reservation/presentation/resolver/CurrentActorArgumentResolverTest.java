package com.restaurant.reservation.presentation.resolver;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentActorArgumentResolverTest {

    private final CurrentActorArgumentResolver resolver = new CurrentActorArgumentResolver();

    private Object resolve(MockHttpServletRequest request) {
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    @Test
    void resolvesActorFromAuthenticatedPrincipal() {
        Actor actor = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(new UsernamePasswordAuthenticationToken(actor, null, List.of()));

        assertThat(resolve(request)).isEqualTo(actor);
    }

    @Test
    void failsWhenRequestIsAnonymous() {
        assertThatThrownBy(() -> resolve(new MockHttpServletRequest()))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void failsWhenPrincipalIsNotAnActor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(new UsernamePasswordAuthenticationToken("some-user", null, List.of()));

        assertThatThrownBy(() -> resolve(request)).isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void ignoresLegacyIdentityHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "ADMIN");

        assertThatThrownBy(() -> resolve(request)).isInstanceOf(AuthenticationRequiredException.class);
    }
}
