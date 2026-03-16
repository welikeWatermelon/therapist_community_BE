package com.therapyCommunity_Vol1.backend.global.exception;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ErrorResponse {
    private final LocalDateTime timestamp;
    private final String path;
    private final String code;
    private final String message;

    public ErrorResponse(String path, ErrorCode errorCode) {
        this.timestamp = LocalDateTime.now();
        this.path = path;
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
}
