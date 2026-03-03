package com.example.bpmn.exception;

/**
 * Thrown when email or password is invalid.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
