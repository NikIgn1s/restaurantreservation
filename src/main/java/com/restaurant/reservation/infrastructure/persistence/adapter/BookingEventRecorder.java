package com.restaurant.reservation.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingEventType;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.PaymentStatus;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEntity;
import com.restaurant.reservation.infrastructure.persistence.entity.BookingEventEntity;
import com.restaurant.reservation.infrastructure.persistence.repository.BookingEventJpaRepository;
import com.restaurant.reservation.infrastructure.security.SecurityActorProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BookingEventRecorder {

    private static final long TIE_BREAK_NANOS = 1000L;

    private final BookingEventJpaRepository events;
    private final ObjectMapper objectMapper;
    private final SecurityActorProvider actors;
    private final Clock clock;

    public record BookingSnapshot(BookingStatus status, boolean overbooked, Instant reminderSentAt) {

        public static BookingSnapshot of(BookingEntity entity) {
            return new BookingSnapshot(entity.getStatus(), entity.isOverbooked(), entity.getReminderSentAt());
        }
    }

    private record Pending(BookingEventType type, Map<String, Object> details) {
    }

    public void recordBookingSaved(BookingSnapshot previous, Booking current) {
        List<Pending> pending = new ArrayList<>();
        if (previous == null) {
            pending.add(new Pending(BookingEventType.CREATED, created(current)));
            if (current.isOverbooked()) {
                pending.add(new Pending(BookingEventType.STANDBY_PLACED, Map.of()));
            }
        } else {
            if (previous.status() != current.getStatus()) {
                pending.add(new Pending(typeOf(current.getStatus()), transition(previous.status(), current.getStatus())));
            }
            if (previous.overbooked() && !current.isOverbooked() && current.getStatus().occupiesSlot()) {
                pending.add(new Pending(BookingEventType.STANDBY_PROMOTED, Map.of()));
            }
            if (previous.reminderSentAt() == null && current.getReminderSentAt() != null) {
                pending.add(new Pending(BookingEventType.REMINDER_SENT, Map.of()));
            }
        }
        append(current.getId(), pending);
    }

    public void recordPaymentSaved(PaymentStatus previous, Payment current) {
        if (previous == current.getStatus()) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentId", current.getId());
        details.put("amount", current.getAmount().toPlainString());
        details.put("currency", current.getCurrency());
        if (current.getExternalReference() != null) {
            details.put("reference", current.getExternalReference());
        }
        append(current.getBookingId(), List.of(new Pending(typeOf(current.getStatus()), details)));
    }

    private void append(UUID bookingId, List<Pending> pending) {
        if (pending.isEmpty()) {
            return;
        }
        Instant base = clock.instant();
        List<BookingEventEntity> entities = new ArrayList<>();
        for (int index = 0; index < pending.size(); index++) {
            Pending item = pending.get(index);
            entities.add(new BookingEventEntity(UUID.randomUUID(), bookingId, item.type().name(),
                    json(withActor(item.details())), base.plusNanos(index * TIE_BREAK_NANOS)));
        }
        events.saveAll(entities);
    }

    private Map<String, Object> withActor(Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor", actors.current().map(BookingEventRecorder::describe).orElse(Map.of("type", "system")));
        payload.putAll(details);
        return payload;
    }

    private static Map<String, Object> describe(Actor actor) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "user");
        description.put("id", actor.id());
        description.put("role", actor.role().name());
        return description;
    }

    private static Map<String, Object> created(Booking booking) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("resourceId", booking.getResourceId());
        details.put("userId", booking.getUserId());
        details.put("startAt", booking.getSlot().start());
        details.put("endAt", booking.getSlot().end());
        details.put("guests", booking.getGuests());
        details.put("depositRequired", booking.isDepositRequired());
        details.put("noShowProbability", booking.getNoShowProbability());
        details.put("overbooked", booking.isOverbooked());
        return details;
    }

    private static Map<String, Object> transition(BookingStatus from, BookingStatus to) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from.name());
        details.put("to", to.name());
        return details;
    }

    private static BookingEventType typeOf(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> BookingEventType.CONFIRMED;
            case CANCELLED -> BookingEventType.CANCELLED;
            case EXPIRED -> BookingEventType.EXPIRED;
            case COMPLETED -> BookingEventType.COMPLETED;
            case NO_SHOW -> BookingEventType.NO_SHOW;
            case PENDING -> BookingEventType.CREATED;
        };
    }

    private static BookingEventType typeOf(PaymentStatus status) {
        return switch (status) {
            case PENDING -> BookingEventType.PAYMENT_STARTED;
            case PAID -> BookingEventType.PAYMENT_PAID;
            case FAILED -> BookingEventType.PAYMENT_FAILED;
            case REFUNDED -> BookingEventType.PAYMENT_REFUNDED;
        };
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Booking event payload could not be serialized", e);
        }
    }
}
