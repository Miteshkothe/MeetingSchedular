package com.meetingbooking.security;

import com.meetingbooking.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String testSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(testSecret, 3600000);
    }

    @Test
    @DisplayName("Should generate valid JWT token and extract subject and claims")
    void testGenerateAndValidateToken() {
        String token = tokenProvider.generateToken(1L, "user@example.com", Role.ROLE_USER);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals("user@example.com", tokenProvider.getUsernameFromToken(token));
        assertEquals(1L, tokenProvider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("Should return false for tampered token")
    void testTamperedToken() {
        String token = tokenProvider.generateToken(1L, "user@example.com", Role.ROLE_USER);
        String tampered = token + "xyz";

        assertFalse(tokenProvider.validateToken(tampered));
    }
}
