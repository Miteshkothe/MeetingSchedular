package com.meetingbooking.service;

import com.meetingbooking.dto.CreateRoomRequest;
import com.meetingbooking.dto.RoomResponse;
import com.meetingbooking.entity.Room;

import java.util.List;

public interface RoomService {
    RoomResponse createRoom(CreateRoomRequest request);
    List<RoomResponse> getAllRooms();
    RoomResponse getRoomById(Long id);
    Room getRoomEntity(Long id);
}
