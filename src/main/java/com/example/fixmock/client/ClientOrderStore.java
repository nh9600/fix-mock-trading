package com.example.fixmock.client;

import com.example.fixmock.domain.Order;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 클라이언트(트레이딩 애플리케이션) 쪽에서 "내가 낸 주문들의 최신 상태"를 보관하는 저장소.
 * 서버로부터 Execution Report를 받을 때마다 이 저장소의 Order가 갱신된다.
 */
public class ClientOrderStore {

    private final ConcurrentHashMap<String, Order> ordersByClOrdId = new ConcurrentHashMap<>();

    public void save(Order order) {
        ordersByClOrdId.put(order.getClientOrderId(), order);
    }

    public Order find(String clientOrderId) {
        return ordersByClOrdId.get(clientOrderId);
    }

    public Collection<Order> findByClientId(String senderCompId) {
        return ordersByClOrdId.values().stream()
                .filter(o -> senderCompId.equals(o.getSenderCompId()))
                .toList();
    }
}
