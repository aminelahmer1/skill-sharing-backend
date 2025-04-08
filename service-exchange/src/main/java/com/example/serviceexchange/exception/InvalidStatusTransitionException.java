package com.example.serviceexchange.exception;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}