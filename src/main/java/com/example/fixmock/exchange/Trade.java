package com.example.fixmock.exchange;

import com.example.fixmock.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 매칭 엔진에서 매수 주문과 매도 주문이 맞아떨어져 발생한 "체결 1건"을 표현한다.
 * 하나의 신규 주문이 여러 상대 주문과 나눠 체결되면 Trade가 여러 건 생성될 수 있다.
 */
public class Trade {

    private static final AtomicLong TRADE_ID_SEQ = new AtomicLong(1);

    private final String tradeId;
    private final Order buyOrder;
    private final Order sellOrder;
    private final BigDecimal price;
    private final int quantity;
    private final Instant executedAt;

    public Trade(Order buyOrder, Order sellOrder, BigDecimal price, int quantity) {
        this.tradeId = "TRD-" + TRADE_ID_SEQ.getAndIncrement();
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = Instant.now();
    }

    public String getTradeId() {
        return tradeId;
    }

    public Order getBuyOrder() {
        return buyOrder;
    }

    public Order getSellOrder() {
        return sellOrder;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    @Override
    public String toString() {
        return "Trade{" + tradeId + ", " + buyOrder.getSymbol() + ", qty=" + quantity + ", price=" + price
                + ", buyClOrdId=" + buyOrder.getClientOrderId() + ", sellClOrdId=" + sellOrder.getClientOrderId() + '}';
    }
}
