package com.example.fixmock.dto;

import java.math.BigDecimal;

/**
 * REST API로 들어오는 주문 생성 요청 바디.
 * 예) { "symbol": "AAPL", "side": "BUY", "orderType": "LIMIT", "price": 150.00, "quantity": 100 }
 */
public class PlaceOrderRequest {

    private String symbol;
    private String side;       // "BUY" | "SELL"
    private String orderType;  // "MARKET" | "LIMIT"
    private BigDecimal price;  // LIMIT일 때만 필요
    private int quantity;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
