package com.meetingbooking.exception;

public class InvalidRecurrenceRuleException extends RuntimeException {
    public InvalidRecurrenceRuleException(String message) {
        super(message);
    }
}
