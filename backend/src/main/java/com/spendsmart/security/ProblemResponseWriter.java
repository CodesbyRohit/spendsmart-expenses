package com.spendsmart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsmart.common.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** Writes JSON problem responses for failures raised inside the security filter chain (before controllers run). */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Please sign in to continue.");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                write(response, HttpStatus.FORBIDDEN, "CSRF_INVALID",
                        "Your session security token is missing or expired. Reload the page and try again.");
            } else {
                write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.");
            }
        };
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        ProblemDetail body = GlobalExceptionHandler.problem(status, code, detail).getBody();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
