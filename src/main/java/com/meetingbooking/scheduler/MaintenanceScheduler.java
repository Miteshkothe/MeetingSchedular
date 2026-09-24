package com.meetingbooking.scheduler;

import com.meetingbooking.entity.MeetingOccurrence;
import com.meetingbooking.entity.SeriesStatus;
import com.meetingbooking.repository.IdempotencyRepository;
import com.meetingbooking.repository.MeetingOccurrenceRepository;
import com.meetingbooking.repository.MeetingSeriesRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class MaintenanceScheduler {
    private final MeetingSeriesRepository seriesRepository;
    private final MeetingOccurrenceRepository occurrenceRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final HorizonExpansionService horizonExpansionService;
    private final int thresholdDays;
    private final int retentionMonths;
    private final int rollingHorizonMonths;
    private final Counter expansionCounter;
    private final Counter expansionFailureCounter;
    private final Counter retentionCleanupCounter;
    private final Timer expansionTimer;

    public MaintenanceScheduler(MeetingSeriesRepository seriesRepository,
            MeetingOccurrenceRepository occurrenceRepository,
            IdempotencyRepository idempotencyRepository,
            HorizonExpansionService horizonExpansionService,
            MeterRegistry registry,
            @Value("${app.meeting.horizon-check-threshold-days:30}") int thresholdDays,
            @Value("${app.meeting.retention-period-months:24}") int retentionMonths,
            @Value("${app.meeting.rolling-horizon-months:12}") int rollingHorizonMonths) {
        this.seriesRepository = seriesRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.horizonExpansionService = horizonExpansionService;
        this.thresholdDays = thresholdDays;
        this.retentionMonths = retentionMonths;
        this.rollingHorizonMonths = rollingHorizonMonths;
        this.expansionCounter = Counter.builder("scheduler.horizon.expansions").register(registry);
        this.expansionFailureCounter = Counter.builder("scheduler.horizon.failures").register(registry);
        this.retentionCleanupCounter = Counter.builder("scheduler.retention.cleanups").register(registry);
        this.expansionTimer = Timer.builder("scheduler.horizon.duration").register(registry);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void expandRollingHorizons() {
        expansionTimer.record(() -> {
            List<Long> ids = seriesRepository.findSeriesApproachingHorizon(SeriesStatus.ACTIVE,
                            LocalDate.now().plusDays(thresholdDays),
                            LocalDate.now().plusMonths(rollingHorizonMonths)).stream()
                    .map(s -> s.getId()).toList();
            for (Long id : ids) {
                try {
                    if (horizonExpansionService.expandSeries(id)) expansionCounter.increment();
                } catch (Exception e) {
                    expansionFailureCounter.increment();
                    log.error("Horizon expansion failed for series {}", id, e);
                }
            }
        });
    }

    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void cleanupOldOccurrences() {
        Instant cutoff = LocalDate.now().minusMonths(retentionMonths).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        List<MeetingOccurrence> old = occurrenceRepository.findOccurrencesForRetention(cutoff);
        if (!old.isEmpty()) {
            occurrenceRepository.deleteAll(old);
            retentionCleanupCounter.increment(old.size());
            log.info("Retention removed {} old occurrences before {}", old.size(), cutoff);
        }
        int expired = idempotencyRepository.deleteExpiredRecords(Instant.now());
        if (expired > 0) log.info("Removed {} expired idempotency records", expired);
    }
}
