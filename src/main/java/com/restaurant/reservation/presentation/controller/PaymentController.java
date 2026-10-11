package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.DepositOutcome;
import com.restaurant.reservation.application.usecase.GetBookingPaymentsUseCase;
import com.restaurant.reservation.application.usecase.PayDepositUseCase;
import com.restaurant.reservation.domain.model.PaymentStatus;
import com.restaurant.reservation.presentation.mapper.PaymentApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActor;
import com.restaurant.reservation.presentation.response.DepositResponse;
import com.restaurant.reservation.presentation.response.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings/{bookingId}")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Deposit for bookings with a high no-show risk")
public class PaymentController {

    private final PayDepositUseCase payDeposit;
    private final GetBookingPaymentsUseCase getPayments;
    private final PaymentApiMapper mapper;

    @PostMapping("/deposit")
    @Operation(summary = "Pay the deposit of a booking",
            description = "Only the person who made the booking can pay. On success the booking becomes CONFIRMED. "
                    + "With the fake gateway, emails starting with 'decline' are declined")
    @ApiResponse(responseCode = "200", description = "Deposit paid, booking confirmed")
    @ApiResponse(responseCode = "402", description = "Payment was declined, the booking stays PENDING and can be paid again")
    @ApiResponse(responseCode = "409", description = "Booking cannot be paid now (payment in progress, already paid, expired or cancelled)")
    @ApiResponse(responseCode = "422", description = "The booking does not require a deposit")
    public ResponseEntity<DepositResponse> payDeposit(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        DepositOutcome outcome = payDeposit.execute(new BookingActionCommand(actor, bookingId));
        return ResponseEntity.status(statusOf(outcome.payment().getStatus())).body(mapper.toResponse(outcome));
    }

    @GetMapping("/payments")
    @Operation(summary = "Payments of a booking, newest first",
            description = "Visible to the booking owner, the venue owner and ADMIN")
    public List<PaymentResponse> payments(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        return getPayments.execute(new BookingActionCommand(actor, bookingId)).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private static HttpStatus statusOf(PaymentStatus status) {
        return switch (status) {
            case PAID -> HttpStatus.OK;
            case FAILED -> HttpStatus.PAYMENT_REQUIRED;
            case REFUNDED, PENDING -> HttpStatus.CONFLICT;
        };
    }
}
