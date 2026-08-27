package com.example.fixmock.exception;

/** 이미 체결완료(FILLED) 되었거나 취소(CANCELLED)/거부(REJECTED)된 주문에 취소 요청이 들어왔을 때 던진다. */
public class OrderAlreadyClosedException extends RuntimeException {
    public OrderAlreadyClosedException(String message) {
        super(message);
    }
}
