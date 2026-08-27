package com.example.fixmock.dto;

import com.example.fixmock.domain.Order;

import java.math.BigDecimal;

/** REST API 응답으로 내려주는 주문 상태 스냅샷. */
public class OrderResponse {

    private final String clientOrderId;
    private final String exchangeOrderId;
    private final String symbol;
    private final String side;
    private final String orderType;
    private final BigDecimal price;
    private final int originalQuantity;
    private final int leavesQuantity;
    private final int cumulativeQuantity;
    private final String status;
    private final String rejectReason;

    public OrderResponse(Order order) {
        this.clientOrderId = order.getClientOrderId();
        this.exchangeOrderId = order.getExchangeOrderId();
        this.symbol = order.getSymbol();
        this.side = order.getSide().name();
        this.orderType = order.getOrderType().name();
        this.price = order.getPrice();
        this.originalQuantity = order.getOriginalQuantity();
        this.leavesQuantity = order.getLeavesQuantity();
        this.cumulativeQuantity = order.getCumulativeQuantity();
        this.status = order.getStatus().name();
        this.rejectReason = order.getRejectReason();
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public String getOrderType() {
        return orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getOriginalQuantity() {
        return originalQuantity;
    }

    public int getLeavesQuantity() {
        return leavesQuantity;
    }

    public int getCumulativeQuantity() {
        return cumulativeQuantity;
    }

    public String getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
