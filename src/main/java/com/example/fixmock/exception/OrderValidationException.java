package com.example.fixmock.exception;

/**
 * 문법적으로는 올바른 FIX 메시지지만, "주문"으로서 비즈니스 유효성 검증에 실패했을 때 던진다.
 * 예) 존재하지 않는 종목, 수량 0 이하, 가격 음수 등.
 *
 * 이 예외는 세션을 끊지 않고 Execution Report(OrdStatus=8 Rejected)로 정상 응답된다.
 */
public class OrderValidationException extends RuntimeException {

    public OrderValidationException(String message) {
        super(message);
    }
}
