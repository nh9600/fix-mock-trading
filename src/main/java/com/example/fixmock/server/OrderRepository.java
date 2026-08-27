package com.example.fixmock.server;

import com.example.fixmock.domain.Order;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버(거래소) 측에서 관리하는 주문 저장소.
 * 학습용 예제이므로 DB 대신 메모리(ConcurrentHashMap)를 사용한다.
 * ClientOrderID(클라이언트가 부여)와 거래소 OrderID 두 가지 키로 모두 조회 가능하게 한다.
 */
public class OrderRepository {

    private final ConcurrentHashMap<String, Order> byClientOrderId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Order> byExchangeOrderId = new ConcurrentHashMap<>();

    public void save(Order order) {
        byClientOrderId.put(order.getClientOrderId(), order);
        if (order.getExchangeOrderId() != null) {
            byExchangeOrderId.put(order.getExchangeOrderId(), order);
        }
    }

    public Order findByClientOrderId(String clientOrderId) {
        return byClientOrderId.get(clientOrderId);
    }

    public Order findByExchangeOrderId(String exchangeOrderId) {
        return byExchangeOrderId.get(exchangeOrderId);
    }

    public Collection<Order> findAll() {
        return byClientOrderId.values();
    }
}
