package com.restaurant.reservation.presentation.mapper;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.presentation.request.CreateBookingRequest;
import com.restaurant.reservation.presentation.response.BookingResponse;
import com.restaurant.reservation.presentation.response.SlotResponse;
import org.springframework.stereotype.Component;

@Component
public class BookingApiMapper {

    public CreateBookingCommand toCommand(Actor actor, CreateBookingRequest request, String idempotencyKey) {
        return new CreateBookingCommand(actor, request.resourceId(), new TimeSlot(request.startAt(), request.endAt()),
                request.guests(), idempotencyKey);
    }

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getResourceId(), booking.getUserId(),
                booking.getSlot().start(), booking.getSlot().end(), booking.getGuests(), booking.getStatus(),
                booking.isDepositRequired(), booking.getNoShowProbability(), booking.getCreatedAt(), booking.getVersion());
    }

    public SlotResponse toResponse(TimeSlot slot) {
        return new SlotResponse(slot.start(), slot.end());
    }
}
