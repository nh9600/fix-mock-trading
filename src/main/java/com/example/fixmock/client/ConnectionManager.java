package com.example.fixmock.client;

import com.example.fixmock.fix.FixMessage;
import com.example.fixmock.net.FixSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * clientId(=FIX SenderCompID) 별로 Mock 거래소 서버와의 TCP 연결(FixSocketClient)을
 * 관리한다. 실제 트레이딩 시스템에서 여러 트레이더/전략이 각자의 세션으로 거래소에
 * 접속하는 모습을 흉내내기 위함이다 (REST 데모에서 clientId를 바꿔가며 호출하면
 * 서로 다른 두 "참가자"가 매매하는 상황을 재현할 수 있다).
 */
public class ConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final String host;
    private final int port;
    private final ConcurrentHashMap<String, FixSocketClient> connections = new ConcurrentHashMap<>();

    public ConnectionManager(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** clientId에 대한 연결이 없으면 새로 만들고, 있으면 재사용한다. */
    public FixSocketClient getOrCreate(String clientId, Consumer<FixMessage> onMessage) {
        return connections.computeIfAbsent(clientId, id -> {
            try {
                log.info("클라이언트[{}] -> Mock 거래소({}:{}) 신규 TCP 연결 생성", id, host, port);
                return new FixSocketClient(host, port, onMessage);
            } catch (IOException e) {
                throw new IllegalStateException("Mock 거래소 서버에 연결할 수 없습니다(host=" + host + ", port=" + port + ")", e);
            }
        });
    }

    public void closeAll() {
        connections.values().forEach(FixSocketClient::close);
        connections.clear();
    }
}
