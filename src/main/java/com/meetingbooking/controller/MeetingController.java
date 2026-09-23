package com.meetingbooking.controller;

import com.meetingbooking.dto.*;
import com.meetingbooking.entity.Role;
import com.meetingbooking.security.CustomUserDetails;
import com.meetingbooking.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Tag(name = "Meetings", description = "Meeting creation, recurring expansion, editing, and cancellation endpoints")
@SecurityRequirement(name = "BearerAuth")
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    @Operation(summary = "Create a meeting (one-time or recurring)", description = "Creates a meeting. If recurrenceRule is provided, expands to rolling 12-month horizon.")
    public ResponseEntity<MeetingSeriesResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MeetingSeriesResponse response = meetingService.createMeeting(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/recurring")
    @Operation(summary = "Create a recurring meeting", description = "Explicit endpoint to book a recurring meeting with atomic conflict detection.")
    public ResponseEntity<MeetingSeriesResponse> createRecurringMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (request.getRecurrenceRule() == null) {
            throw new IllegalArgumentException("recurrenceRule must be provided for recurring meetings.");
        }
        MeetingSeriesResponse response = meetingService.createMeeting(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Query calendar occurrences with date-range filtering and pagination",
            description = "Calendar view retrieving occurrences within [from, to) window with pagination.")
    public ResponseEntity<Page<MeetingOccurrenceResponse>> getCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Instant effectiveFrom = from != null ? from : Instant.now().minusSeconds(86400 * 7); // Default -7 days
        Instant effectiveTo = to != null ? to : Instant.now().plusSeconds(86400 * 30);      // Default +30 days
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));

        boolean isAdmin = userDetails.getRole() == Role.ROLE_ADMIN;
        Page<MeetingOccurrenceResponse> results = meetingService.getCalendarOccurrences(effectiveFrom, effectiveTo, roomId,
                userDetails.getId(), isAdmin, pageRequest);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get meeting series details by series ID")
    public ResponseEntity<MeetingSeriesResponse> getMeetingById(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(meetingService.getMeetingById(id, userDetails.getId(), userDetails.getRole() == Role.ROLE_ADMIN));
    }

    @GetMapping("/occurrences/{occurrenceId}")
    @Operation(summary = "Get concrete meeting occurrence by occurrence ID")
    public ResponseEntity<MeetingOccurrenceResponse> getOccurrenceById(@PathVariable Long occurrenceId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(meetingService.getOccurrenceById(occurrenceId, userDetails.getId(), userDetails.getRole() == Role.ROLE_ADMIN));
    }

    @PatchMapping("/{seriesId}/occurrences/{occurrenceId}")
    @Operation(summary = "Edit a recurring meeting", description = "Supports modes: THIS (occurrence only), THIS_AND_FUTURE (splits series), WHOLE_SERIES (modifies entire future series).")
    public ResponseEntity<MeetingOccurrenceResponse> editOccurrence(
            @PathVariable Long seriesId,
            @PathVariable Long occurrenceId,
            @Valid @RequestBody EditOccurrenceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        boolean isAdmin = userDetails.getRole() == Role.ROLE_ADMIN;
        MeetingOccurrenceResponse response = meetingService.editOccurrence(seriesId, occurrenceId, request, userDetails.getId(), isAdmin);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an entire meeting series")
    public ResponseEntity<Void> cancelSeries(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        boolean isAdmin = userDetails.getRole() == Role.ROLE_ADMIN;
        meetingService.cancelMeeting(id, null, CancelMode.WHOLE_SERIES, userDetails.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{seriesId}/occurrences/{occurrenceId}")
    @Operation(summary = "Cancel meeting occurrence(s)", description = "Mode can be ONE (default), THIS_AND_FUTURE, or WHOLE_SERIES.")
    public ResponseEntity<Void> cancelOccurrence(
            @PathVariable Long seriesId,
            @PathVariable Long occurrenceId,
            @RequestParam(defaultValue = "ONE") CancelMode mode,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        boolean isAdmin = userDetails.getRole() == Role.ROLE_ADMIN;
        meetingService.cancelMeeting(seriesId, occurrenceId, mode, userDetails.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }
}
