package com.example.fixmock.session;

import com.example.fixmock.net.ClientHandler;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버(Mock 거래소) 쪽에서 "SenderCompID(Tag 49) -> 현재 연결(ClientHandler)"를
 * 매핑해두는 레지스트리.
 *
 * 왜 필요한가? Mock 거래소의 매칭 엔진은 서로 다른 두 클라이언트가 낸 주문끼리도
 * 체결시킬 수 있다(예: 클라이언트 A의 매수 주문과 클라이언트 B의 매도 주문).
 * 이때 체결 결과(Execution Report)는 "그 주문을 낸 클라이언트의 소켓"으로
 * 정확히 되돌려 보내야 하므로, 주문에 찍혀 있는 SenderCompID로 현재 연결을
 * 다시 찾아낼 수 있어야 한다.
 */
public class SessionRegistry {

    private final ConcurrentHashMap<String, ClientHandler> connections = new ConcurrentHashMap<>();

    public void register(String senderCompId, ClientHandler handler) {
        handler.setSenderCompId(senderCompId);
        connections.put(senderCompId, handler);
    }

    public void unregister(ClientHandler handler) {
        if (handler.getSenderCompId() != null) {
            connections.remove(handler.getSenderCompId(), handler);
        }
    }

    public ClientHandler find(String senderCompId) {
        return connections.get(senderCompId);
    }

    public boolean isConnected(String senderCompId) {
        ClientHandler handler = connections.get(senderCompId);
        return handler != null && !handler.isClosed();
    }
}
