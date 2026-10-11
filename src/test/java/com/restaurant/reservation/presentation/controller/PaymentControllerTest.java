package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.DepositOutcome;
import com.restaurant.reservation.application.usecase.GetBookingPaymentsUseCase;
import com.restaurant.reservation.application.usecase.PayDepositUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.PaymentInProgressException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.presentation.mapper.BookingApiMapper;
import com.restaurant.reservation.presentation.mapper.PaymentApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActorArgumentResolver;
import com.restaurant.reservation.presentation.resolver.WebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.restaurant.reservation.presentation.TestAuth.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, CurrentActorArgumentResolver.class, PaymentApiMapper.class, BookingApiMapper.class})
class PaymentControllerTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private PayDepositUseCase payDeposit;
    @MockBean
    private GetBookingPaymentsUseCase getPayments;

    private Booking booking;
    private Payment payment;

    @BeforeEach
    void setUp() {
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-07T10:00:00Z"), Instant.parse("2030-01-07T11:00:00Z"));
        booking = Booking.create(table, USER_ID, slot, 2, NOW);
        booking.assessNoShowRisk(0.9, 0.4);
        payment = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
    }

    @Test
    void successfulDepositReturnsOkWithPaymentAndConfirmedBooking() throws Exception {
        payment.markPaid("ref-1");
        booking.confirm();
        when(payDeposit.execute(any())).thenReturn(new DepositOutcome(payment, booking));

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.reference").value("ref-1"))
                .andExpect(jsonPath("$.payment.amount").value(20.00))
                .andExpect(jsonPath("$.payment.currency").value("PLN"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.booking.id").value(booking.getId().toString()));

        ArgumentCaptor<BookingActionCommand> captor = ArgumentCaptor.forClass(BookingActionCommand.class);
        verify(payDeposit).execute(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(booking.getId());
        assertThat(captor.getValue().actor().id()).isEqualTo(USER_ID);
    }

    @Test
    void declinedDepositReturnsPaymentRequired() throws Exception {
        payment.markFailed();
        when(payDeposit.execute(any())).thenReturn(new DepositOutcome(payment, booking));

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.booking.status").value("PENDING"));
    }

    @Test
    void refundedDepositReturnsConflict() throws Exception {
        payment.markPaid("ref-1");
        payment.markRefunded();
        booking.cancel();
        when(payDeposit.execute(any())).thenReturn(new DepositOutcome(payment, booking));

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.booking.status").value("CANCELLED"));
    }

    @Test
    void depositRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void foreignBookingIsForbidden() throws Exception {
        when(payDeposit.execute(any())).thenThrow(new ForbiddenOperationException("Only the booker can pay"));

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void depositNotRequiredIsUnprocessable() throws Exception {
        when(payDeposit.execute(any())).thenThrow(new InvalidBookingException("Deposit is not required for this booking"));

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Deposit is not required for this booking"));
    }

    @Test
    void paymentAlreadyInProgressIsConflict() throws Exception {
        when(payDeposit.execute(any())).thenThrow(new PaymentInProgressException(booking.getId()));

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Payment in progress"));
    }

    @Test
    void malformedBookingIdIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/deposit", "abc").with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsPaymentsOfBooking() throws Exception {
        when(getPayments.execute(any())).thenReturn(List.of(payment));

        mockMvc.perform(get("/api/bookings/{id}/payments", booking.getId()).with(asUser(USER_ID, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(payment.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void paymentListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}/payments", booking.getId())).andExpect(status().isUnauthorized());
    }
}
