package com.restaurant.reservation.presentation.resolver;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.model.UserRole;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

@Component
public class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class) && Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(USER_ID_HEADER);
        String role = webRequest.getHeader(USER_ROLE_HEADER);
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            throw new AuthenticationRequiredException("Headers " + USER_ID_HEADER + " and " + USER_ROLE_HEADER + " are required");
        }
        try {
            return new Actor(UUID.fromString(userId.trim()), UserRole.valueOf(role.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + USER_ID_HEADER + " or " + USER_ROLE_HEADER + " header");
        }
    }
}
