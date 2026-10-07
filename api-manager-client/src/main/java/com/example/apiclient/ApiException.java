package com.example.apiclient;

public final class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}
