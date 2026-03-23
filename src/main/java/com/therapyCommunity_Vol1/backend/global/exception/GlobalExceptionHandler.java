package com.therapyCommunity_Vol1.backend.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException (
            CustomException e,
            HttpServletRequest request
    ){
        ErrorCode errorCode = e.getErrorCode();

        log.error("CustomException 발생 : path={}, code={}, message={}",
                request.getRequestURI(), errorCode, e.getMessage(), e);

        ErrorResponse response = new ErrorResponse(request.getRequestURI(),errorCode);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(response);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception e,
            HttpServletRequest request
    ) {
        log.warn("BadRequest: path={}, message={}", request.getRequestURI(), e.getMessage(), e);

        ErrorResponse response = new ErrorResponse(request.getRequestURI(), ErrorCode.INVALID_INPUT);

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e,
            HttpServletRequest request
    ) {
        log.warn("NoResourceFound: path={}, message={}", request.getRequestURI(), e.getMessage());

        ErrorResponse response = new ErrorResponse(
                request.getRequestURI(),
                ErrorCode.RESOURCE_NOT_FOUND
        );

        return ResponseEntity
                .status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException (
            Exception e,
            HttpServletRequest request
    ) {
        log.error("Unhandled Exception 발생: path={}", request.getRequestURI(), e);

        ErrorResponse response = new ErrorResponse(
                request.getRequestURI(),
                ErrorCode.INTERNAL_SERVER_ERROR
        );
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(response);
    }


}
