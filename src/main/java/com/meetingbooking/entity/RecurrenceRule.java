package com.meetingbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "recurrence_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurrenceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceFrequency frequency;

    @Column(name = "interval_value", nullable = false)
    @Builder.Default
    private Integer intervalValue = 1;

    /**
     * Comma-separated DayOfWeek names, e.g. "MONDAY,WEDNESDAY,FRIDAY"
     */
    @Column(length = 100)
    private String weekdays;

    /**
     * For monthly recurrence by fixed date (e.g. 15th)
     */
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /**
     * For monthly recurrence by nth weekday: 1 (1st), 2 (2nd), 3 (3rd), 4 (4th), 5 (5th), or -1 (last)
     */
    @Column(name = "week_number")
    private Integer weekNumber;

    /**
     * For monthly recurrence by nth weekday, e.g. "WEDNESDAY"
     */
    @Column(length = 20)
    private String weekday;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Helper methods for weekdays
    public Set<DayOfWeek> getWeekdaysSet() {
        if (weekdays == null || weekdays.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(weekdays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    public void setWeekdaysSet(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            this.weekdays = null;
        } else {
            this.weekdays = days.stream()
                    .map(DayOfWeek::name)
                    .sorted()
                    .collect(Collectors.joining(","));
        }
    }
}
