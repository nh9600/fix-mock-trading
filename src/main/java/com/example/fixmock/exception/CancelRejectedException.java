package com.example.fixmock.exception;

/** 클라이언트 측에서, 서버가 보낸 Order Cancel Reject(35=9)를 받았을 때 표현하는 예외. */
public class CancelRejectedException extends RuntimeException {
    public CancelRejectedException(String message) {
        super(message);
    }
}
