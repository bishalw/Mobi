package com.mobi.payment.exception;

import com.mobi.payment.gateway.PaymentProviderException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exp, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        exp.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "ValidationFailed", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadable(HttpMessageNotReadableException exp, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", request, null);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(PaymentNotFoundException exp, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exp.getMessage(), request, null);
    }

    @ExceptionHandler(PaymentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(PaymentConflictException exp, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exp.getMessage(), request, null);
    }

    @ExceptionHandler(PaymentProviderException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleProvider(PaymentProviderException exp, HttpServletRequest request) {
        log.warn("Payment provider unavailable: {}", exp.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "The payment provider is unavailable; retry shortly", request, null);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleCircuitOpen(CallNotPermittedException exp, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Payments are temporarily unavailable; retry shortly", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException exp, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, exp.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception exp, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), exp);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request, null);
    }

    private static ErrorResponse error(HttpStatus status, String message, HttpServletRequest request, Map<String, String> errors) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), errors);
    }
}
