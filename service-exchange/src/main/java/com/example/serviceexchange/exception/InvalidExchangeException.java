package com.example.serviceexchange.exception;

public class InvalidExchangeException extends RuntimeException {
    public InvalidExchangeException(String message) {
        super(message);
    }
}