package com.meetingbooking.service.impl;

import com.meetingbooking.dto.CreateRoomRequest;
import com.meetingbooking.dto.RoomResponse;
import com.meetingbooking.entity.Room;
import com.meetingbooking.exception.ResourceNotFoundException;
import com.meetingbooking.repository.RoomRepository;
import com.meetingbooking.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;

    @Override
    @Transactional
    @CacheEvict(value = {"rooms", "availability"}, allEntries = true)
    public RoomResponse createRoom(CreateRoomRequest request) {
        if (roomRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("A room with name '" + request.getName() + "' already exists.");
        }

        Room room = Room.builder()
                .name(request.getName().trim())
                .capacity(request.getCapacity())
                .location(request.getLocation() != null ? request.getLocation().trim() : null)
                .build();

        Room saved = roomRepository.save(room);
        log.info("Created room with id: {} and name: {}", saved.getId(), saved.getName());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "rooms", unless = "#result == null || #result.isEmpty()")
    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "room", key = "#id")
    public RoomResponse getRoomById(Long id) {
        return mapToResponse(getRoomEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Room getRoomEntity(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));
    }

    private RoomResponse mapToResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .location(room.getLocation())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
