package com.restaurant.reservation.infrastructure.persistence.mapper;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEntity;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public Booking toDomain(BookingEntity entity) {
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return new Booking(entity.getId(), entity.getResourceId(), entity.getUserId(),
                new TimeSlot(entity.getStartAt(), entity.getEndAt()), entity.getGuests(), entity.getStatus(),
                entity.isDepositRequired(), entity.getNoShowProbability(), entity.getCreatedAt(),
                entity.getReminderSentAt(), version);
    }

    public BookingEntity toEntity(Booking booking) {
        return new BookingEntity(booking.getId(), booking.getResourceId(), booking.getUserId(),
                booking.getSlot().start(), booking.getSlot().end(), booking.getGuests(), booking.getStatus(),
                booking.isDepositRequired(), booking.getNoShowProbability(), booking.getCreatedAt(),
                booking.getReminderSentAt(), booking.getVersion());
    }
}
