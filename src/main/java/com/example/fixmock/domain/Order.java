package com.example.fixmock.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 주문 도메인 객체.
 *
 * 이 클래스는 "FIX 메시지"가 아니라 "우리 시스템 내부에서 다루는 주문 상태"를 표현한다.
 * FIX 메시지(FixMessage)는 어디까지나 이 Order를 시스템 간에 주고받기 위한
 * 전송 포맷(직렬화 규격)일 뿐이고, Order 자체는 순수 자바 객체다.
 *
 * 필드와 FIX 태그의 대응 관계:
 *   clientOrderId (ClOrdID)  -> Tag 11  : 클라이언트가 부여한 주문 ID (재전송/취소 시 식별자)
 *   exchangeOrderId (OrderID)-> Tag 37  : 거래소(서버)가 접수하면서 부여하는 ID
 *   symbol (Symbol)          -> Tag 55  : 종목 코드
 *   side (Side)              -> Tag 54  : 매수/매도
 *   orderType (OrdType)      -> Tag 40  : 시장가/지정가
 *   price (Price)            -> Tag 44  : 지정가 (시장가 주문은 사용하지 않음)
 *   originalQuantity(OrderQty)-> Tag 38 : 주문 최초 수량
 *   leavesQuantity (LeavesQty)-> Tag 151: 아직 체결되지 않고 남은 수량
 *   cumulativeQuantity(CumQty)-> Tag 14 : 누적 체결 수량
 *   status (OrdStatus)       -> Tag 39  : 주문 상태
 *   senderCompId (SenderCompID)-> Tag 49: 이 주문을 보낸 클라이언트(세션) 식별자
 */
public class Order {

    // 학습용 예제이므로 거래소 주문번호는 프로세스 내 원자적 카운터로 간단히 채번한다.
    // 실제 거래소는 종목/세션별 채번 규칙, 영속 저장소, 분산 환경에서의 유일성 보장 등을 고려해야 한다.
    private static final AtomicInteger EXCHANGE_ORDER_ID_SEQ = new AtomicInteger(1000);

    private final String clientOrderId;
    private String exchangeOrderId;
    private final String senderCompId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType orderType;
    private final BigDecimal price;
    private final int originalQuantity;

    private int leavesQuantity;
    private int cumulativeQuantity;
    private OrderStatus status;
    private String rejectReason;

    private final Instant createdAt;

    public Order(String clientOrderId,
                 String senderCompId,
                 String symbol,
                 OrderSide side,
                 OrderType orderType,
                 BigDecimal price,
                 int originalQuantity) {
        this.clientOrderId = clientOrderId;
        this.senderCompId = senderCompId;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.originalQuantity = originalQuantity;
        this.leavesQuantity = originalQuantity;
        this.cumulativeQuantity = 0;
        this.status = OrderStatus.NEW;
        this.createdAt = Instant.now();
    }

    public static String nextExchangeOrderId() {
        return "EX-" + EXCHANGE_ORDER_ID_SEQ.incrementAndGet();
    }

    public void accept(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
        this.status = OrderStatus.ACCEPTED;
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason;
        this.leavesQuantity = 0;
    }

    /** 체결 발생 시 누적/잔량을 갱신하고 상태를 전이시킨다. */
    public void applyFill(int fillQuantity) {
        if (fillQuantity <= 0 || fillQuantity > leavesQuantity) {
            throw new IllegalStateException("체결 수량이 잔량을 초과합니다. leaves=" + leavesQuantity + ", fill=" + fillQuantity);
        }
        this.cumulativeQuantity += fillQuantity;
        this.leavesQuantity -= fillQuantity;
        this.status = (leavesQuantity == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.leavesQuantity = 0;
    }

    public boolean isFullyFilled() {
        return leavesQuantity == 0;
    }

    public boolean isCancellable() {
        return status == OrderStatus.ACCEPTED || status == OrderStatus.PARTIALLY_FILLED;
    }

    /**
     * 클라이언트 측 전용 메서드. 서버가 보내온 Execution Report 내용을 그대로
     * 로컬 사본에 반영(sync)한다. 서버측 applyFill()과 달리 클라이언트는 이미
     * 서버가 계산을 끝낸 "결과값"을 신뢰하고 그대로 덮어쓰기만 하면 된다.
     */
    public void syncFromServer(OrderStatus status, String exchangeOrderId, int leavesQuantity, int cumulativeQuantity, String text) {
        this.status = status;
        if (exchangeOrderId != null) {
            this.exchangeOrderId = exchangeOrderId;
        }
        this.leavesQuantity = leavesQuantity;
        this.cumulativeQuantity = cumulativeQuantity;
        if (status == OrderStatus.REJECTED) {
            this.rejectReason = text;
        }
    }

    // ---- getters ----

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    public String getSenderCompId() {
        return senderCompId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getOrderType() {
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

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Order{" +
                "clOrdId=" + clientOrderId +
                ", exOrdId=" + exchangeOrderId +
                ", symbol=" + symbol +
                ", side=" + side +
                ", type=" + orderType +
                ", price=" + price +
                ", qty=" + originalQuantity +
                ", leaves=" + leavesQuantity +
                ", cum=" + cumulativeQuantity +
                ", status=" + status +
                '}';
    }
}
