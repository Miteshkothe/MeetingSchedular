package com.meetingbooking.controller;

import com.meetingbooking.dto.CreateRoomRequest;
import com.meetingbooking.dto.RoomResponse;
import com.meetingbooking.dto.RoomAvailabilityResponse;
import com.meetingbooking.service.MeetingService;
import com.meetingbooking.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room management endpoints")
@SecurityRequirement(name = "BearerAuth")
public class RoomController {

    private final RoomService roomService;
    private final MeetingService meetingService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create a new meeting room (ADMIN only)", description = "Requires ROLE_ADMIN authority")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all meeting rooms", description = "Returns all available meeting rooms")
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get room by ID", description = "Returns details of a specific room")
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Check room availability for a UTC time interval")
    public ResponseEntity<RoomAvailabilityResponse> getAvailability(@PathVariable Long id,
            @RequestParam Instant from, @RequestParam Instant to) {
        return ResponseEntity.ok(meetingService.getRoomAvailability(id, from, to));
    }
}
