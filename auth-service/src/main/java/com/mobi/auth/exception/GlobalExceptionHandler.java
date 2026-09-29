package com.mobi.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException exp, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        exp.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Bad Request", "ValidationFailed", request.getRequestURI(), errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.CONFLICT.value(), "Conflict", exp.getMessage(), request.getRequestURI(), null);
    }

    // Deliberately not folded into the IllegalArgumentException handler above: that one
    // answers 409, which is right for "username already exists" and wrong for both of
    // these.

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(UserNotFoundException exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.NOT_FOUND.value(), "Not Found", exp.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(AccountDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccountDisabled(AccountDisabledException exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.FORBIDDEN.value(), "Forbidden", exp.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidJson(HttpMessageNotReadableException exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Bad Request", "JSON Format Data Mismatch - Input Missing", request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Bad Request", "Invalid value for parameter: " + exp.getName(), request.getRequestURI(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Bad Request", "Database constraint violation", request.getRequestURI(), null);
    }

    // Required because of the catch-all below. An AccessDeniedException raised in a
    // controller or service is handled here, by @RestControllerAdvice, before it can
    // reach Spring Security's ExceptionTranslationFilter further out in the chain —
    // so without this entry a legitimate 403 is reported as a 500.
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.FORBIDDEN.value(), "Forbidden", exp.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGlobalException(Exception exp, HttpServletRequest request) {
        return new ErrorResponse(LocalDateTime.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", exp.getMessage(), request.getRequestURI(), null);
    }
}
