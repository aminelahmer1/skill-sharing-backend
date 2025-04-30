package com.example.serviceuser.exception;

public class RoleAssignmentException extends RuntimeException {

    // Constructeur avec message
    public RoleAssignmentException(String message) {
        super(message);
    }

    // Constructeur avec message et cause
    public RoleAssignmentException(String message, Throwable cause) {
        super(message, cause);
    }
}