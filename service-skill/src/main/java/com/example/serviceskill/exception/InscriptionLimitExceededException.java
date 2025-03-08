package com.example.serviceskill.exception;

public class InscriptionLimitExceededException extends RuntimeException {
    public InscriptionLimitExceededException(String message) {
        super(message);
    }
}