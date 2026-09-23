package com.meetingbooking.idempotency;

import com.meetingbooking.entity.IdempotencyRecord;
import com.meetingbooking.repository.IdempotencyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdempotencyInterceptorTest {
    private final IdempotencyRepository repository = mock(IdempotencyRepository.class);
    private final IdempotencyInterceptor filter = new IdempotencyInterceptor(repository);

    IdempotencyInterceptorTest() { ReflectionTestUtils.setField(filter, "ttlMinutes", 60); }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void completedRequestIsReplayedAndChainIsNotInvokedAgain() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice@example.com", "token", List.of()));
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        MockHttpServletRequest first = request("{\"title\":\"sync\"}");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, (req, res) -> {
            ((HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"id\":7}");
        });

        assertEquals(201, firstResponse.getStatus());
        org.mockito.ArgumentCaptor<IdempotencyRecord> captor = org.mockito.ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository, times(2)).save(captor.capture());
        IdempotencyRecord stored = captor.getValue();
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(stored));
        MockHttpServletRequest retry = request("{\"title\":\"sync\"}");
        MockHttpServletResponse retryResponse = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        filter.doFilter(retry, retryResponse, (req, res) -> calls.incrementAndGet());

        assertEquals(0, calls.get());
        assertTrue(retryResponse.getContentAsString().contains("\"id\":7"));
    }

    @Test
    void keyReuseWithDifferentBodyIsRejected() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alice@example.com", "token", List.of()));
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(IdempotencyRecord.builder()
                .requestHash("different").responseStatus(201).responseBody("{}")
                .expiresAt(Instant.now().plusSeconds(3600)).build()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        filter.doFilter(request("{\"title\":\"new\"}"), response, (req, res) -> calls.incrementAndGet());
        assertEquals(409, response.getStatus());
        assertTrue(response.getContentAsString().contains("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(0, calls.get());
    }

    private MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/meetings");
        request.setContentType("application/json");
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.addHeader("Idempotency-Key", "key-1");
        return request;
    }
}
