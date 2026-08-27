package com.example.fixmock.server;

import com.example.fixmock.domain.OrderType;
import com.example.fixmock.exception.OrderValidationException;
import com.example.fixmock.exchange.SymbolMaster;

import java.math.BigDecimal;

/**
 * 서버가 신규 주문을 접수하기 전 수행하는 비즈니스 유효성 검증.
 * (FIX 메시지 "문법"이 올바른지는 이미 FixMessageParser 단계에서 걸러졌다고 가정하고,
 *  여기서는 "주문 내용"이 타당한지만 검사한다.)
 */
public final class OrderValidator {

    private OrderValidator() {
    }

    public static void validate(String symbol, int quantity, BigDecimal price, OrderType orderType) {
        if (!SymbolMaster.isTradable(symbol)) {
            throw new OrderValidationException("존재하지 않는 종목입니다: " + symbol);
        }
        if (quantity <= 0) {
            throw new OrderValidationException("수량은 0보다 커야 합니다: " + quantity);
        }
        if (orderType == OrderType.LIMIT) {
            if (price == null) {
                throw new OrderValidationException("지정가(LIMIT) 주문에는 가격이 필요합니다.");
            }
            if (price.signum() <= 0) {
                throw new OrderValidationException("가격은 0보다 커야 합니다(음수/0 불가): " + price);
            }
        }
    }
}
