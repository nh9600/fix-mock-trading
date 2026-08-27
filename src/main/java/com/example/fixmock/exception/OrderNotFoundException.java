package com.example.fixmock.exception;

/** 취소/조회 대상 주문(ClOrdID 또는 OrderID)을 찾을 수 없을 때 던진다. */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
