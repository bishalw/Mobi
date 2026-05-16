package com.scalablerest.inventory.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;

public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("""
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s","validationErrors":null}
                """.formatted(LocalDateTime.now(), status, error, message, request.getRequestURI()));
    }
}
