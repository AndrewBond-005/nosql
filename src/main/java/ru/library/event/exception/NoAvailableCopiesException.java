package ru.library.event.exception;

public class NoAvailableCopiesException extends RuntimeException {
    public NoAvailableCopiesException(String eventId) {
        super("No available copies for event: " + eventId);
    }
}
