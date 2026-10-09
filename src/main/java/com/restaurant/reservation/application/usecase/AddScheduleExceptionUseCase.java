package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AddScheduleExceptionCommand;
import com.restaurant.reservation.domain.model.ScheduleException;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddScheduleExceptionUseCase {

    private final VenueAccessPolicy accessPolicy;
    private final ScheduleRepository schedules;

    @Transactional
    public ScheduleException execute(AddScheduleExceptionCommand command) {
        accessPolicy.loadManageableResource(command.resourceId(), command.actor());
        UUID id = schedules.findException(command.resourceId(), command.date())
                .map(ScheduleException::getId)
                .orElseGet(UUID::randomUUID);
        ScheduleException exception = new ScheduleException(id, command.resourceId(), command.date(),
                command.closed(), command.openTime(), command.closeTime());
        return schedules.saveException(exception);
    }
}
