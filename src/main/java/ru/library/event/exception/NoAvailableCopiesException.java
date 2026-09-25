package ru.library.event.exception;

public class NoAvailableCopiesException extends RuntimeException {
    public NoAvailableCopiesException(String eventId) {
        super("Копий книги больше нет: " + eventId);
    }
}
