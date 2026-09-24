package com.meetingbooking.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter bookingRequestCounter(MeterRegistry registry) {
        return Counter.builder("booking.requests.total")
                .description("Total number of meeting booking requests")
                .register(registry);
    }

    @Bean
    public Counter bookingConflictCounter(MeterRegistry registry) {
        return Counter.builder("booking.conflicts.total")
                .description("Total number of room conflict rejections")
                .register(registry);
    }

    @Bean
    public Counter bookingFailureCounter(MeterRegistry registry) {
        return Counter.builder("booking.failures.total")
                .description("Total number of booking failures")
                .register(registry);
    }

    @Bean
    public Timer bookingLatencyTimer(MeterRegistry registry) {
        return Timer.builder("booking.latency")
                .description("Latency of meeting creation requests")
                .register(registry);
    }

    @Bean
    public Timer recurrenceGenerationTimer(MeterRegistry registry) {
        return Timer.builder("recurrence.generation.duration")
                .description("Duration of recurrence expansion generation")
                .register(registry);
    }
}
