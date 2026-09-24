package com.meetingbooking.recurrence;

import com.meetingbooking.exception.NonexistentLocalTimeException;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.zone.ZoneRules;
import java.util.List;

@Service
public class TimezoneService {
    public Instant toUtcInstant(LocalDate localDate, LocalTime localTime, ZoneId zoneId) {
        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> validOffsets = rules.getValidOffsets(localDateTime);

        if (validOffsets.isEmpty()) {
            throw new NonexistentLocalTimeException(
                    String.format("Requested local time %s on %s does not exist in timezone %s due to daylight saving transition.",
                            localTime, localDate, zoneId.getId()),
                    zoneId.getId(),
                    localDate,
                    localTime
            );
        } else if (validOffsets.size() > 1) {
            return localDateTime.toInstant(validOffsets.get(0));
        } else {
            return localDateTime.toInstant(validOffsets.get(0));
        }
    }

    public LocalDateTime toAttendeeLocal(Instant instant, ZoneId attendeeZone) {
        return LocalDateTime.ofInstant(instant, attendeeZone);
    }

    public ZoneId parseZoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IANA timezone identifier: " + timezone, e);
        }
    }
}
