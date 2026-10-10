package com.restaurant.reservation.infrastructure.scheduler;

import com.restaurant.reservation.application.usecase.SendBookingRemindersUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BookingReminderJob {

    static final String LOCK_NAME = "sendBookingReminders";

    private final SendBookingRemindersUseCase sendBookingReminders;

    @Scheduled(fixedDelayString = "${app.scheduler.reminder-interval:PT1M}",
            initialDelayString = "${app.scheduler.initial-delay:PT30S}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "${app.scheduler.min-lock:PT10S}")
    public void run() {
        int sent = sendBookingReminders.execute();
        if (sent > 0) {
            log.info("Sent {} booking reminders", sent);
        }
    }
}
