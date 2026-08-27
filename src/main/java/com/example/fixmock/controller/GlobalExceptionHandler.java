package com.example.fixmock.controller;

import com.example.fixmock.exception.CancelRejectedException;
import com.example.fixmock.exception.OrderAlreadyClosedException;
import com.example.fixmock.exception.OrderNotFoundException;
import com.example.fixmock.exception.OrderValidationException;
import com.example.fixmock.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 서비스 계층에서 던진 도메인 예외를 적절한 HTTP 상태 코드/응답 바디로 변환한다.
 * (이 예제의 "예외 처리" 요구사항 중 REST API 경계에서의 처리를 담당하는 부분.
 *  Socket/FIX 레벨의 예외 처리는 ServerOrderService에서 이미 FIX 응답으로 처리된다.)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("ORDER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(OrderAlreadyClosedException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyClosed(OrderAlreadyClosedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("ORDER_ALREADY_CLOSED", e.getMessage()));
    }

    @ExceptionHandler(CancelRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handleCancelRejected(CancelRejectedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("CANCEL_REJECTED", e.getMessage()));
    }

    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(OrderValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("ORDER_VALIDATION_FAILED", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("BAD_REQUEST", e.getMessage()));
    }
}
