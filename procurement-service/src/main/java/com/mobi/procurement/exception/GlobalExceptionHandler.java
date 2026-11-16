package com.mobi.procurement.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadable(Exception exp, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request", request, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingHeader(MissingRequestHeaderException exp, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exp.getHeaderName() + " header is required", request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException exp, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exp.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoRoute(NoResourceFoundException exp, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Not found", request, null);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException exp, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exp.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException exp, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exp.getMessage(), request, null);
    }

    // Two people acting on the same purchase order at once: the second write loses its
    // @Version check. The caller reloads and sees the new status.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConcurrentChange(Exception exp, HttpServletRequest request) {
        log.info("Concurrent change on {}: {}", request.getRequestURI(), exp.getMessage());
        return error(HttpStatus.CONFLICT, "This record was changed at the same time; reload and try again", request, null);
    }

    /**
     * A unique key (duplicate supplier name, a racing insert) is a conflict the caller can
     * act on. Anything else (value too long, missing column) is a server bug and must not
     * masquerade as one.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException exp,
                                                                                  HttpServletRequest request) {
        String detail = String.valueOf(exp.getMostSpecificCause().getMessage());
        if (detail.contains("Duplicate entry")) {
            log.info("Unique key conflict on {}: {}", request.getRequestURI(), detail);
            return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error(HttpStatus.CONFLICT, "That already exists or was just changed; reload and try again", request, null));
        }
        log.error("Data integrity error on {}", request.getRequestURI(), exp);
        return org.springframework.http.ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request, null));
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
