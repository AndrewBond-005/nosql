package ru.library.event.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String userId) {
        super("User already exists: " + userId);
    }
}
