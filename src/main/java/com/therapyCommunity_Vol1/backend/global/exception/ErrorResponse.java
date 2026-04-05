package com.therapyCommunity_Vol1.backend.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private final String code;
    private final String message;
    private final int status;
    private final List<FieldError> fieldErrors;

    public ErrorResponse(ErrorCode errorCode) {
        this.code = errorCode.name();
        this.message = errorCode.getMessage();
        this.status = errorCode.getStatus().value();
        this.fieldErrors = null;
    }

    public ErrorResponse(ErrorCode errorCode, List<FieldError> fieldErrors) {
        this.code = errorCode.name();
        this.message = errorCode.getMessage();
        this.status = errorCode.getStatus().value();
        this.fieldErrors = fieldErrors;
    }

    @Getter
    @AllArgsConstructor
    public static class FieldError {
        private final String field;
        private final String message;
    }
}
