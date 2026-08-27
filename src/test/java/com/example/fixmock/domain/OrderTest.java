package com.example.fixmock.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** [정상 주문 생성] 요구사항에 대응하는 도메인 단위 테스트. */
class OrderTest {

    @Test
    void 정상적으로_주문을_생성하면_초기값이_올바르게_설정된다() {
        Order order = new Order("CL-1", "CLIENT1", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("150.00"), 100);

        assertThat(order.getClientOrderId()).isEqualTo("CL-1");
        assertThat(order.getSymbol()).isEqualTo("AAPL");
        assertThat(order.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(order.getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(order.getPrice()).isEqualByComparingTo("150.00");
        assertThat(order.getOriginalQuantity()).isEqualTo(100);
        assertThat(order.getLeavesQuantity()).isEqualTo(100);
        assertThat(order.getCumulativeQuantity()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getExchangeOrderId()).isNull();
    }

    @Test
    void accept_호출시_ACCEPTED_상태와_거래소주문번호가_설정된다() {
        Order order = new Order("CL-2", "CLIENT1", "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("100.00"), 10);

        order.accept("EX-999");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getExchangeOrderId()).isEqualTo("EX-999");
    }

    @Test
    void 부분체결_후_완전체결까지_상태전이가_올바르다() {
        Order order = new Order("CL-3", "CLIENT1", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100.00"), 100);
        order.accept("EX-1");

        order.applyFill(40);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getLeavesQuantity()).isEqualTo(60);
        assertThat(order.getCumulativeQuantity()).isEqualTo(40);

        order.applyFill(60);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getLeavesQuantity()).isZero();
        assertThat(order.getCumulativeQuantity()).isEqualTo(100);
    }

    @Test
    void reject_호출시_REJECTED_상태와_사유가_설정된다() {
        Order order = new Order("CL-4", "CLIENT1", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100.00"), 100);

        order.reject("존재하지 않는 종목입니다");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectReason()).isEqualTo("존재하지 않는 종목입니다");
        assertThat(order.getLeavesQuantity()).isZero();
    }

    @Test
    void ACCEPTED_상태의_주문만_취소_가능하다() {
        Order accepted = new Order("CL-5", "CLIENT1", "AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN, 10);
        accepted.accept("EX-2");
        assertThat(accepted.isCancellable()).isTrue();

        Order filled = new Order("CL-6", "CLIENT1", "AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN, 10);
        filled.accept("EX-3");
        filled.applyFill(10);
        assertThat(filled.isCancellable()).isFalse();
    }
}
