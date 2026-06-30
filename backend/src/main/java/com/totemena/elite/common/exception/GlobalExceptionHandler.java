package com.totemena.elite.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<?> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            Map.of("error", ex.getMessage(), "code", "INSUFFICIENT_BALANCE")
        );
    }

    @ExceptionHandler(BettingClosedException.class)
    public ResponseEntity<?> handleBettingClosed(BettingClosedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            Map.of("error", ex.getMessage(), "code", "BETTING_CLOSED")
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
        // Only expose reason for client errors (4xx), never for 5xx
        String reason = ex.getStatusCode().is4xxClientError()
            ? ex.getReason()
            : "An unexpected error occurred.";
        return ResponseEntity.status(ex.getStatusCode()).body(
            Map.of("error", reason != null ? reason : "Request failed")
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            Map.of("error", "Forbidden")
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            Map.of("error", "Validation failed", "fields", errors)
        );
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            Map.of("error", "Invalid request parameter")
        );
    }

    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<?> handleDatabaseExceptions(Exception ex) {
        log.error("Database error occurred: [{}]", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            Map.of("error", "An internal error occurred. Please try again later.")
        );
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> handleInvalidFormatException(Exception ex) {
        log.warn("Invalid input format received: [{}]", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            Map.of("error", "Invalid input format. When placing bets, only numbers are allowed (no letters or special symbols).")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAllExceptions(Exception ex) {
        // Log exception class internally — NEVER expose confidential user data or stack trace to client
        log.error("Unhandled exception: [{}]", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            Map.of("error", "An unexpected error occurred. Please try again.")
        );
    }
}
