package com.example.fixmock.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@link com.example.fixmock.domain.Order}를 MySQL {@code orders} 테이블에 그대로 옮긴
 * JPA 엔티티. db/schema.sql로 만든 테이블 구조와 1:1로 대응한다.
 *
 * 이 엔티티는 순수 저장용 스냅샷이며, 매칭/체결 같은 비즈니스 로직은 전혀 갖지 않는다.
 * 실제 도메인 로직은 여전히 {@link com.example.fixmock.domain.Order}가 담당하고,
 * {@link TradePersistenceService}가 주문 상태가 바뀔 때마다 이 엔티티에 스냅샷을 반영한다.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_order_id", nullable = false, unique = true, length = 64)
    private String clientOrderId;

    @Column(name = "exchange_order_id", length = 64)
    private String exchangeOrderId;

    @Column(name = "sender_comp_id", nullable = false, length = 64)
    private String senderCompId;

    @Column(name = "symbol", nullable = false, length = 16)
    private String symbol;

    @Column(name = "side", nullable = false, length = 8)
    private String side;

    @Column(name = "order_type", nullable = false, length = 8)
    private String orderType;

    @Column(name = "price", precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "original_quantity", nullable = false)
    private int originalQuantity;

    @Column(name = "leaves_quantity", nullable = false)
    private int leavesQuantity;

    @Column(name = "cumulative_quantity", nullable = false)
    private int cumulativeQuantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {
        // JPA 기본 생성자
    }

    public Long getId() {
        return id;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }

    public String getSenderCompId() {
        return senderCompId;
    }

    public void setSenderCompId(String senderCompId) {
        this.senderCompId = senderCompId;
    }

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

    public int getOriginalQuantity() {
        return originalQuantity;
    }

    public void setOriginalQuantity(int originalQuantity) {
        this.originalQuantity = originalQuantity;
    }

    public int getLeavesQuantity() {
        return leavesQuantity;
    }

    public void setLeavesQuantity(int leavesQuantity) {
        this.leavesQuantity = leavesQuantity;
    }

    public int getCumulativeQuantity() {
        return cumulativeQuantity;
    }

    public void setCumulativeQuantity(int cumulativeQuantity) {
        this.cumulativeQuantity = cumulativeQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
