package com.meetingbooking.exception;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class NonexistentLocalTimeException extends RuntimeException {

    private final String timezone;
    private final LocalDate localDate;
    private final LocalTime localTime;

    public NonexistentLocalTimeException(String message, String timezone, LocalDate localDate, LocalTime localTime) {
        super(message);
        this.timezone = timezone;
        this.localDate = localDate;
        this.localTime = localTime;
    }
}
