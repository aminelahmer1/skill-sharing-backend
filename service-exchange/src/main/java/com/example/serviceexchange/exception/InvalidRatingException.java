package com.example.serviceexchange.exception;

public class InvalidRatingException extends RuntimeException {
    public InvalidRatingException(String message) {
        super(message);
    }
}