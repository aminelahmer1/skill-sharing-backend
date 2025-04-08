package com.example.serviceexchange.exception;


public class InvalidStatusException extends RuntimeException {
    public InvalidStatusException(String message) {
        super(message);
    }
}