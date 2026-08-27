package com.example.fixmock.exchange;

import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메모리 기반 Mock 매칭 엔진.
 *
 * 실제 거래소의 매칭 엔진(Matching Engine)이 하는 일을 아주 단순화해서 구현한다:
 * 신규 주문이 들어오면, 반대편 호가창에서 "가격이 맞는" 주문을 찾아 체결시키고,
 * 그래도 남는 수량이 있으면 (지정가 주문에 한해) 자신의 호가창에 대기시킨다(resting).
 *
 * 우선순위 규칙:
 *   1. 가격 우선(Price Priority): 매수는 높은 가격, 매도는 낮은 가격이 먼저 체결된다.
 *   2. 시간 우선(Time Priority): 같은 가격이면 먼저 접수된 주문이 먼저 체결된다.
 *   (구현 세부사항은 {@link OrderBook} 참고)
 *
 * 체결 가격은 "이미 호가창에 대기하고 있던 주문(Maker)의 가격"을 사용한다.
 * 이는 실제 거래소에서 흔히 쓰이는 관례(가격을 먼저 제시한 쪽의 가격으로 체결)를 따른 것이다.
 *
 * ── 단순화한 부분 ─────────────────────────────────────────
 * - 시장가(MARKET) 주문은 상대편 호가와 즉시 매칭되지만, 체결 후 남는 잔량이 있어도
 *   호가창에 대기(resting)하지 않는다(즉시체결/즉시취소, IOC와 유사하게 동작).
 *   실제 거래소는 시장가 주문의 잔량 처리에 대해 더 정교한 규칙(IOC/FOK 등)을 가진다.
 * - 자기 자신의 주문끼리 체결되는 자기매칭(self-trade) 방지 로직은 구현하지 않았다.
 * - 종목별로 객체 모니터 락(synchronized)만 사용하는 단순한 동시성 제어이며,
 *   실제 거래소 수준의 고성능/저지연 처리(Lock-free, Disruptor 패턴 등)는 다루지 않는다.
 */
public class MatchingEngine {

    private final Map<String, OrderBook> booksBySymbol = new ConcurrentHashMap<>();

    private OrderBook bookFor(String symbol) {
        return booksBySymbol.computeIfAbsent(symbol, OrderBook::new);
    }

    /**
     * 신규 주문을 매칭 엔진에 제출한다.
     * @return 이번 제출로 즉시 발생한 체결(Trade) 목록. 매칭이 하나도 안 되면 빈 리스트.
     */
    public List<Trade> submit(Order order) {
        OrderBook book = bookFor(order.getSymbol());
        List<Trade> trades = new ArrayList<>();

        synchronized (book) {
            matchAgainstBook(order, book, trades);

            // 체결 후에도 잔량이 남아있고, 지정가 주문이라면 호가창에 대기시킨다.
            if (order.getLeavesQuantity() > 0 && order.getOrderType() == OrderType.LIMIT) {
                book.rest(order);
            }
            // 시장가 주문의 잔여 수량은 대기시키지 않는다 (위 클래스 설명 참고).
        }
        return trades;
    }

    private void matchAgainstBook(Order incoming, OrderBook book, List<Trade> trades) {
        while (incoming.getLeavesQuantity() > 0) {
            Map.Entry<BigDecimal, Deque<Order>> bestLevel = book.bestOppositeLevel(incoming.getSide());
            if (bestLevel == null) {
                break; // 상대편에 대기 중인 주문이 아예 없음
            }

            BigDecimal bestPrice = bestLevel.getKey();
            if (incoming.getOrderType() == OrderType.LIMIT && !priceCrosses(incoming, bestPrice)) {
                break; // 가격 조건이 맞지 않으면 매칭 중단 (시장가 주문은 가격 조건 없이 항상 매칭 시도)
            }

            Order resting = bestLevel.getValue().peekFirst();
            if (resting == null) {
                break;
            }

            int tradeQty = Math.min(incoming.getLeavesQuantity(), resting.getLeavesQuantity());
            BigDecimal tradePrice = bestPrice; // Maker(먼저 대기하던 주문)의 가격으로 체결

            incoming.applyFill(tradeQty);
            resting.applyFill(tradeQty);

            Order buyOrder = incoming.getSide() == OrderSide.BUY ? incoming : resting;
            Order sellOrder = incoming.getSide() == OrderSide.SELL ? incoming : resting;
            trades.add(new Trade(buyOrder, sellOrder, tradePrice, tradeQty));

            if (resting.isFullyFilled()) {
                book.removeFullyFilledHead(incoming.getSide(), bestPrice);
            }
        }
    }

    /** 지정가 주문의 가격이 상대 호가와 "교차(cross)"하는지, 즉 체결 가능한지 판단한다. */
    private boolean priceCrosses(Order incoming, BigDecimal oppositeBestPrice) {
        if (incoming.getSide() == OrderSide.BUY) {
            // 매수 주문: 내가 낼 수 있는 최대가(price) >= 매도 최우선 호가 이어야 체결 가능
            return incoming.getPrice().compareTo(oppositeBestPrice) >= 0;
        } else {
            // 매도 주문: 내가 받아들일 최소가(price) <= 매수 최우선 호가 이어야 체결 가능
            return incoming.getPrice().compareTo(oppositeBestPrice) <= 0;
        }
    }

    /** 취소 요청 처리를 위해 호가창에서 해당 주문을 제거한다. 이미 체결되어 없다면 false. */
    public boolean cancel(Order order) {
        OrderBook book = booksBySymbol.get(order.getSymbol());
        if (book == null) {
            return false;
        }
        synchronized (book) {
            return book.remove(order);
        }
    }
}
