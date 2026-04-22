package com.example.bpmn.exception;

import org.springframework.http.HttpStatus;

public class CamundaIntegrationException extends RuntimeException {

    private final HttpStatus status;

    public CamundaIntegrationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public CamundaIntegrationException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}