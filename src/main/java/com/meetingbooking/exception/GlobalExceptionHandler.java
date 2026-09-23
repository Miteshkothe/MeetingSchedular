package com.meetingbooking.exception;

import com.meetingbooking.conflict.ConflictDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Autowired
    private MeterRegistry meterRegistry;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, Map.of("fieldErrors", fieldErrors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(RoomConflictException.class)
    public ResponseEntity<Map<String, Object>> handleRoomConflict(RoomConflictException ex, WebRequest request) {
        log.warn("Room conflict: {}", ex.getMessage());

        List<Map<String, Object>> conflictDetails = ex.getConflicts().stream()
                .map(this::mapConflictDetail)
                .collect(Collectors.toList());

        Map<String, Object> extra = conflictDetails.isEmpty() ? null : Map.of("conflicts", conflictDetails);
        return buildResponse(HttpStatus.CONFLICT, "ROOM_CONFLICT", ex.getMessage(), request, extra);
    }

    @ExceptionHandler(NonexistentLocalTimeException.class)
    public ResponseEntity<Map<String, Object>> handleNonexistentLocalTime(NonexistentLocalTimeException ex, WebRequest request) {
        log.warn("Nonexistent local time: {}", ex.getMessage());
        Map<String, Object> extra = Map.of(
                "timezone", ex.getTimezone(),
                "localDate", ex.getLocalDate().toString(),
                "localTime", ex.getLocalTime().toString()
        );
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "NONEXISTENT_LOCAL_TIME", ex.getMessage(), request, extra);
    }

    @ExceptionHandler(InvalidRecurrenceRuleException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRecurrence(InvalidRecurrenceRuleException ex, WebRequest request) {
        log.warn("Invalid recurrence rule: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_RECURRENCE_RULE", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        meterRegistry.counter("database.errors", "category", "integrity").increment();
        String message = ex.getMessage();
        if (message != null && (message.contains("no_overlapping_room_bookings") || message.contains("exclusion"))) {
            log.warn("PostgreSQL exclusion constraint violation (concurrent booking): {}", ex.getMessage());
            return buildResponse(HttpStatus.CONFLICT, "ROOM_CONFLICT",
                    "Room reservation failed due to concurrent booking conflict. Please try again.", request, null);
        }
        if (message != null && message.contains("uq_series_occurrence_start")) {
            log.warn("Duplicate occurrence insertion (idempotency): {}", ex.getMessage());
            return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_OCCURRENCE",
                    "Occurrence already exists for this series and start time.", request, null);
        }
        log.error("Data integrity violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "Data integrity constraint violated.", request, null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseFailure(DataAccessException ex, WebRequest request) {
        meterRegistry.counter("database.errors", "category", "access").increment();
        log.error("Database operation failed", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "The database could not complete the request. Please retry.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please contact support.", request, null);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String error, String message, WebRequest request, Map<String, Object> extra
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));

        if (extra != null) {
            body.putAll(extra);
        }

        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> mapConflictDetail(ConflictDetail detail) {
        Map<String, Object> map = new HashMap<>();
        if (detail.conflictingOccurrenceId() != null) map.put("occurrenceId", detail.conflictingOccurrenceId());
        if (detail.conflictingTitle() != null) map.put("title", detail.conflictingTitle());
        if (detail.conflictingStart() != null) map.put("conflictingStart", detail.conflictingStart().toString());
        if (detail.conflictingEnd() != null) map.put("conflictingEnd", detail.conflictingEnd().toString());
        if (detail.requestedStart() != null) map.put("requestedStart", detail.requestedStart().toString());
        if (detail.requestedEnd() != null) map.put("requestedEnd", detail.requestedEnd().toString());
        if (detail.requestedLocalDate() != null) map.put("requestedLocalDate", detail.requestedLocalDate().toString());
        return map;
    }
}
