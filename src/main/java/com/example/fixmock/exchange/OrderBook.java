package com.example.fixmock.exchange;

import com.example.fixmock.domain.Order;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

/**
 * 단일 종목에 대한 매수/매도 호가창(Order Book).
 *
 * 가격 우선(price priority) + 시간 우선(time priority) 원칙을 다음과 같이 구현한다.
 *   - 가격 우선: 매수 호가는 "높은 가격"이 우선이므로 내림차순 TreeMap을 사용하고,
 *                매도 호가는 "낮은 가격"이 우선이므로 오름차순 TreeMap을 사용한다.
 *                따라서 firstEntry()가 항상 "가장 유리한 가격"이 된다.
 *   - 시간 우선: 같은 가격대에서는 먼저 들어온 주문이 먼저 체결되어야 하므로,
 *                각 가격 레벨을 FIFO 큐(ArrayDeque)로 관리해 새 주문은 tail에 추가,
 *                체결은 항상 head(가장 오래된 주문)부터 처리한다.
 */
public class OrderBook {

    private final String symbol;

    // 매수 호가: 높은 가격이 먼저 나오도록 내림차순 정렬
    private final TreeMap<BigDecimal, Deque<Order>> buyLevels = new TreeMap<>(Comparator.reverseOrder());
    // 매도 호가: 낮은 가격이 먼저 나오도록 오름차순 정렬(기본 정렬)
    private final TreeMap<BigDecimal, Deque<Order>> sellLevels = new TreeMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    private TreeMap<BigDecimal, Deque<Order>> levelsFor(com.example.fixmock.domain.OrderSide side) {
        return side == com.example.fixmock.domain.OrderSide.BUY ? buyLevels : sellLevels;
    }

    /** 상대편(매칭 대상) 호가창을 가져온다. 매수 주문이 들어오면 매도 호가창을 봐야 매칭할 수 있다. */
    public TreeMap<BigDecimal, Deque<Order>> oppositeLevels(com.example.fixmock.domain.OrderSide incomingSide) {
        return levelsFor(incomingSide.opposite());
    }

    /** 미체결 잔량이 남은 지정가 주문을 호가창에 등록(resting)한다. */
    public void rest(Order order) {
        levelsFor(order.getSide())
                .computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>())
                .addLast(order);
    }

    /** 가장 좋은 호가의 첫 번째(가장 오래된) 주문을 제거 없이 조회한다. */
    public Map.Entry<BigDecimal, Deque<Order>> bestOppositeLevel(com.example.fixmock.domain.OrderSide incomingSide) {
        return oppositeLevels(incomingSide).firstEntry();
    }

    /** 체결로 인해 완전히 소진된 최우선 주문을 큐에서 제거하고, 레벨이 비면 레벨 자체도 제거한다. */
    public void removeFullyFilledHead(com.example.fixmock.domain.OrderSide incomingSide, BigDecimal price) {
        TreeMap<BigDecimal, Deque<Order>> levels = oppositeLevels(incomingSide);
        Deque<Order> queue = levels.get(price);
        if (queue == null) {
            return;
        }
        queue.pollFirst();
        if (queue.isEmpty()) {
            levels.remove(price);
        }
    }

    /** 취소 등을 위해 호가창에서 특정 주문을 직접 제거한다. */
    public boolean remove(Order order) {
        TreeMap<BigDecimal, Deque<Order>> levels = levelsFor(order.getSide());
        Deque<Order> queue = levels.get(order.getPrice());
        if (queue == null) {
            return false;
        }
        boolean removed = queue.remove(order);
        if (queue.isEmpty()) {
            levels.remove(order.getPrice());
        }
        return removed;
    }

    public boolean isEmpty() {
        return buyLevels.isEmpty() && sellLevels.isEmpty();
    }
}
