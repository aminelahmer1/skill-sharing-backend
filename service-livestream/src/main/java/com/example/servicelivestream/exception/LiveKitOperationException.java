package com.example.servicelivestream.exception;

public class LiveKitOperationException extends RuntimeException {
    public LiveKitOperationException(String message) {
        super(message);
    }

    public LiveKitOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}