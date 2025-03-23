package com.example.serviceexchange.exception;

public class ExchangeNotFoundException extends RuntimeException {
    public ExchangeNotFoundException(String message) {
        super(message);
    }
}