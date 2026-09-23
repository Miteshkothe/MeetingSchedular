package com.meetingbooking.service;

import com.meetingbooking.dto.AuthResponse;
import com.meetingbooking.dto.LoginRequest;
import com.meetingbooking.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
