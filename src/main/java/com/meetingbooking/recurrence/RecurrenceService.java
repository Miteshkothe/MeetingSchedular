package com.meetingbooking.recurrence;

import com.meetingbooking.entity.MeetingSeries;
import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;
import com.meetingbooking.exception.InvalidRecurrenceRuleException;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecurrenceService {

    private final Map<RecurrenceFrequency, RecurrenceGenerator> generators;
    private final TimezoneService timezoneService;
    private final MeterRegistry meterRegistry;

    public RecurrenceService(List<RecurrenceGenerator> generatorList, TimezoneService timezoneService, MeterRegistry meterRegistry) {
        this.generators = generatorList.stream()
                .collect(Collectors.toMap(RecurrenceGenerator::getFrequency, Function.identity()));
        this.timezoneService = timezoneService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Generates all concrete occurrence proposals for a series within the specified window.
     */
    public List<OccurrenceProposal> generateOccurrences(
            MeetingSeries series,
            LocalDate windowStart,
            LocalDate windowEnd
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
        RecurrenceRule rule = series.getRecurrenceRule();
        if (rule == null) {
            // One-time meeting: exactly one occurrence on startDate if within window
            if (!series.getStartDate().isBefore(windowStart) && !series.getStartDate().isAfter(windowEnd)) {
                return List.of(createProposal(series.getStartDate(), series.getStartTime(), series.getEndTime(), series.getTimezone()));
            }
            return List.of();
        }

        RecurrenceGenerator generator = generators.get(rule.getFrequency());
        if (generator == null) {
            throw new InvalidRecurrenceRuleException("Unsupported recurrence frequency: " + rule.getFrequency());
        }

        List<LocalDate> localDates = generator.generateDates(series.getStartDate(), windowStart, windowEnd, rule);

        return localDates.stream()
                .map(date -> createProposal(date, series.getStartTime(), series.getEndTime(), series.getTimezone()))
                .toList();
        } finally {
            sample.stop(meterRegistry.timer("recurrence.generation.duration"));
        }
    }

    public OccurrenceProposal createProposal(LocalDate date, LocalTime startTime, LocalTime endTime, String timezone) {
        ZoneId zoneId = timezoneService.parseZoneId(timezone);
        Instant startUtc = timezoneService.toUtcInstant(date, startTime, zoneId);
        Instant endUtc = timezoneService.toUtcInstant(date, endTime, zoneId);

        if (!endUtc.isAfter(startUtc)) {
            throw new IllegalArgumentException("Occurrence end time must be strictly after start time.");
        }

        return new OccurrenceProposal(date, startUtc, endUtc);
    }
}
