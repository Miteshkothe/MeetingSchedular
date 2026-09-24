package com.meetingbooking.idempotency;

import com.meetingbooking.entity.IdempotencyRecord;
import com.meetingbooking.repository.IdempotencyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;


@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyInterceptor extends OncePerRequestFilter {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private final IdempotencyRepository idempotencyRepository;

    @Value("${app.idempotency.ttl-minutes:60}")
    private int ttlMinutes;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/meetings");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!StringUtils.hasText(key)) {
            chain.doFilter(request, response);
            return;
        }
        if (key.length() > 200) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Idempotency-Key must be 200 characters or fewer");
            return;
        }

        byte[] requestBody = request.getInputStream().readAllBytes();
        if (requestBody.length > 1_048_576) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Meeting request body exceeds 1 MiB");
            return;
        }
        String requestHash = sha256(request.getMethod() + "\n" + request.getRequestURI() + "\n"
                + request.getQueryString() + "\n" + HexFormat.of().formatHex(sha256Bytes(requestBody)));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String owner = authentication == null ? "anonymous" : authentication.getName();
        String scopedKey = sha256(owner + ":" + key);
        var existing = idempotencyRepository.findByIdempotencyKey(scopedKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.getExpiresAt().isAfter(Instant.now())) {
                if (!record.getRequestHash().equals(requestHash)) {
                    writeProblem(response, "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request.");
                    return;
                }
                if (record.getResponseStatus() == 102) {
                    response.setHeader("Retry-After", "1");
                    writeProblem(response, "IDEMPOTENCY_REQUEST_IN_PROGRESS", "A request with this key is still being processed.");
                    return;
                }
                response.setHeader("Idempotency-Replayed", "true");
                response.setStatus(record.getResponseStatus());
                response.setContentType("application/json");
                if (record.getResponseBody() != null) response.getWriter().write(record.getResponseBody());
                return;
            }
            idempotencyRepository.delete(record);
        }

        IdempotencyRecord reservation;
        try {
            reservation = idempotencyRepository.save(IdempotencyRecord.builder()
                    .idempotencyKey(scopedKey).requestHash(requestHash).responseStatus(102)
                    .expiresAt(Instant.now().plusSeconds(ttlMinutes * 60L)).build());
        } catch (DataIntegrityViolationException racedReservation) {
            // Another request claimed the key after our lookup; don't run the booking twice.
            var winner = idempotencyRepository.findByIdempotencyKey(scopedKey);
            if (winner.isEmpty()) throw racedReservation;
            IdempotencyRecord record = winner.get();
            if (!record.getRequestHash().equals(requestHash)) {
                writeProblem(response, "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request.");
            } else {
                response.setHeader("Retry-After", "1");
                writeProblem(response, "IDEMPOTENCY_REQUEST_IN_PROGRESS", "A request with this key is still being processed.");
            }
            return;
        }

        HttpServletRequest repeatableRequest = new RepeatableRequest(request, requestBody);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        chain.doFilter(repeatableRequest, wrappedResponse);
        int status = wrappedResponse.getStatus();
        String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        try {
            reservation.setResponseStatus(status);
            reservation.setResponseBody(body);
            reservation.setExpiresAt(Instant.now().plusSeconds(ttlMinutes * 60L));
            idempotencyRepository.save(reservation);
        } catch (RuntimeException storageFailure) {
            // An IN_PROGRESS reservation prevents a retry from creating a duplicate while storage recovers.
            log.error("Could not finalize idempotency result for key hash {}", scopedKey, storageFailure);
        }
        wrappedResponse.copyBodyToResponse();
    }

    private void writeProblem(HttpServletResponse response, String error, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("application/json");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":409,\"error\":\""
                + error + "\",\"message\":\"" + message + "\"}");
    }

    private static byte[] sha256Bytes(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    private static String sha256(String value) { return HexFormat.of().formatHex(sha256Bytes(value.getBytes(StandardCharsets.UTF_8))); }

    private static final class RepeatableRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private RepeatableRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }
        @Override public BufferedReader getReader() throws IOException { return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncodingOrUtf8())); }
        private String getCharacterEncodingOrUtf8() { return getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding(); }
    }
}
