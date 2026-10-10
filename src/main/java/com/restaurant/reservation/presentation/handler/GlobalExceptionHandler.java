package com.restaurant.reservation.presentation.handler;

import com.restaurant.reservation.domain.exception.ConcurrentBookingModificationException;
import com.restaurant.reservation.domain.exception.EmailAlreadyUsedException;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.IdempotencyKeyConflictException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidCredentialsException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.InvalidTimeSlotException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.presentation.resolver.AuthenticationRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ProblemDetail handleAuthenticationRequired(AuthenticationRequiredException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException e) {
        return problem(HttpStatus.CONFLICT, "Email already registered", e.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail handleForbidden(ForbiddenOperationException e) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(SlotNotAvailableException.class)
    public ProblemDetail handleSlotNotAvailable(SlotNotAvailableException e) {
        return problem(HttpStatus.CONFLICT, "Slot not available", e.getMessage());
    }

    @ExceptionHandler(ConcurrentBookingModificationException.class)
    public ProblemDetail handleConcurrentModification(ConcurrentBookingModificationException e) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", e.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ProblemDetail handleDataConflict(NestedRuntimeException e) {
        log.warn("Data conflict: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with the current state, try again");
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ProblemDetail handleInvalidState(InvalidBookingStateException e) {
        return problem(HttpStatus.CONFLICT, "Invalid booking state", e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyKeyConflictException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key conflict", e.getMessage());
    }

    @ExceptionHandler(InvalidBookingException.class)
    public ProblemDetail handleInvalidBooking(InvalidBookingException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid booking", e.getMessage());
    }

    @ExceptionHandler({InvalidTimeSlotException.class, IllegalArgumentException.class, DateTimeException.class})
    public ProblemDetail handleBadRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", "Invalid value for parameter " + e.getName());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request validation failed");
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
