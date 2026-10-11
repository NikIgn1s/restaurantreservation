package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.PendingDeposit;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.PaymentRepository;
import com.restaurant.reservation.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class DepositPaymentSteps {

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final Clock clock;
    private final BigDecimal depositAmount;
    private final String currency;

    public DepositPaymentSteps(BookingRepository bookings, PaymentRepository payments, UserRepository users, Clock clock,
                               @Value("${app.payment.deposit-amount:20.00}") BigDecimal depositAmount,
                               @Value("${app.payment.currency:PLN}") String currency) {
        this.bookings = bookings;
        this.payments = payments;
        this.users = users;
        this.clock = clock;
        this.depositAmount = depositAmount;
        this.currency = currency;
    }

    @Transactional
    public PendingDeposit begin(BookingActionCommand command) {
        Booking booking = bookings.findById(command.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + command.bookingId() + " not found"));
        if (!booking.isOwnedBy(command.actor().id())) {
            throw new ForbiddenOperationException("Only the person who made the booking can pay its deposit");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(booking.getStatus(), BookingStatus.CONFIRMED);
        }
        if (!booking.isDepositRequired()) {
            throw new InvalidBookingException("Deposit is not required for this booking");
        }
        User payer = users.findById(command.actor().id())
                .orElseThrow(() -> new NotFoundException("User " + command.actor().id() + " not found"));
        Payment payment = payments.save(Payment.start(booking.getId(), depositAmount, currency, clock.instant()));
        return new PendingDeposit(payment, booking.getId(), payer.getEmail());
    }

    @Transactional
    public Payment markFailed(UUID paymentId) {
        Payment payment = load(paymentId);
        payment.markFailed();
        return payments.save(payment);
    }

    @Transactional
    public Payment markPaid(UUID paymentId, String reference) {
        Payment payment = load(paymentId);
        payment.markPaid(reference);
        return payments.save(payment);
    }

    @Transactional
    public Booking confirmBooking(UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
        booking.confirm();
        return bookings.save(booking);
    }

    @Transactional
    public Payment markRefunded(UUID paymentId) {
        Payment payment = load(paymentId);
        payment.markRefunded();
        return payments.save(payment);
    }

    @Transactional(readOnly = true)
    public Booking loadBooking(UUID bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
    }

    private Payment load(UUID paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment " + paymentId + " not found"));
    }
}
