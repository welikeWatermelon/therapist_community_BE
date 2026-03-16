package com.therapyCommunity_Vol1.backend.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException (
            Exception e,
            HttpServletRequest request
    ) {

        log.error("Unhandled Exception 발생: path={}",
                request.getRequestURI(),
                e);

        ErrorResponse errorResponse = new ErrorResponse(request.getRequestURI(), ErrorCode.INVALID_INPUT);
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(errorResponse);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException (
            CustomException e,
            HttpServletRequest request
    ){
        ErrorCode errorCode = e.getErrorCode();

        log.error("CustomException 발생 : path={}, code={}, message={}",
                request.getRequestURI(),
                errorCode,
                e.getMessage(),
                e);

        ErrorResponse errorResponse = new ErrorResponse(request.getRequestURI(),errorCode);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(errorResponse);
    }
}
