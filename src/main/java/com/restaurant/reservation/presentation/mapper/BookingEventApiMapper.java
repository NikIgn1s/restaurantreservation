package com.restaurant.reservation.presentation.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.restaurant.reservation.domain.model.BookingEvent;
import com.restaurant.reservation.presentation.response.BookingEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingEventApiMapper {

    private final ObjectMapper objectMapper;

    public BookingEventResponse toResponse(BookingEvent event) {
        return new BookingEventResponse(event.id(), event.type(), parse(event.payload()), event.createdAt());
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            return NullNode.getInstance();
        }
    }
}
