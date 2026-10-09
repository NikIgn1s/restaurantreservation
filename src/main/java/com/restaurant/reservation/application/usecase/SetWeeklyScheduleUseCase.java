package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetWeeklyScheduleUseCase {

    private final VenueAccessPolicy accessPolicy;
    private final ScheduleRepository schedules;

    @Transactional
    public Schedule execute(SetWeeklyScheduleCommand command) {
        accessPolicy.loadManageableResource(command.resourceId(), command.actor());
        Schedule schedule = schedules.findByResourceId(command.resourceId()).stream()
                .filter(existing -> existing.getDayOfWeek() == command.dayOfWeek())
                .findFirst()
                .map(existing -> {
                    existing.update(command.openTime(), command.closeTime(), command.slotMinutes());
                    return existing;
                })
                .orElseGet(() -> Schedule.create(command.resourceId(), command.dayOfWeek(),
                        command.openTime(), command.closeTime(), command.slotMinutes()));
        return schedules.save(schedule);
    }
}
