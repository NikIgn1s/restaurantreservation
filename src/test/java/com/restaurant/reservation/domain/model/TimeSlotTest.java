package com.restaurant.reservation.domain.model;

import com.restaurant.reservation.domain.exception.InvalidTimeSlotException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    private static final Instant BASE = Instant.parse("2030-01-01T10:00:00Z");

    private static TimeSlot slot(int fromMinutes, int toMinutes) {
        return new TimeSlot(BASE.plus(Duration.ofMinutes(fromMinutes)), BASE.plus(Duration.ofMinutes(toMinutes)));
    }

    @Test
    void rejectsStartAfterEnd() {
        assertThatThrownBy(() -> slot(60, 30)).isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void rejectsEmptySlot() {
        assertThatThrownBy(() -> slot(30, 30)).isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void rejectsNullBoundaries() {
        assertThatThrownBy(() -> new TimeSlot(null, BASE)).isInstanceOf(InvalidTimeSlotException.class);
        assertThatThrownBy(() -> new TimeSlot(BASE, null)).isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void calculatesDuration() {
        assertThat(slot(0, 90).duration()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void detectsPartialOverlap() {
        assertThat(slot(0, 60).overlaps(slot(30, 90))).isTrue();
        assertThat(slot(30, 90).overlaps(slot(0, 60))).isTrue();
    }

    @Test
    void detectsContainedSlotAsOverlap() {
        assertThat(slot(0, 120).overlaps(slot(30, 60))).isTrue();
        assertThat(slot(30, 60).overlaps(slot(0, 120))).isTrue();
    }

    @Test
    void detectsIdenticalSlotsAsOverlap() {
        assertThat(slot(0, 60).overlaps(slot(0, 60))).isTrue();
    }

    @Test
    void adjacentSlotsDoNotOverlap() {
        assertThat(slot(0, 60).overlaps(slot(60, 120))).isFalse();
        assertThat(slot(60, 120).overlaps(slot(0, 60))).isFalse();
    }

    @Test
    void distantSlotsDoNotOverlap() {
        assertThat(slot(0, 60).overlaps(slot(120, 180))).isFalse();
    }

    @Test
    void containsIsInclusiveOfStartAndExclusiveOfEnd() {
        TimeSlot slot = slot(0, 60);

        assertThat(slot.contains(slot.start())).isTrue();
        assertThat(slot.contains(slot.end())).isFalse();
        assertThat(slot.contains(slot.start().plusSeconds(1))).isTrue();
    }
}
