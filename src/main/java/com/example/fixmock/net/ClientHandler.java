package com.example.fixmock.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 서버 쪽에서 클라이언트 한 명(TCP 연결 하나)을 담당하는 스레드.
 *
 * 여기서 다루는 것은 순수하게 "바이트를 읽고 쓰는 일"뿐이다. 읽은 바이트가
 * 무엇을 의미하는지(파싱/검증/주문 처리)는 전혀 알지 못하며, 전부
 * {@link FixMessageListener}에게 위임한다. 이것이 "Socket 계층"과 "FIX/비즈니스 계층"의
 * 역할 분리를 코드로 보여주는 핵심 지점이다.
 *
 * 서버는 클라이언트 1개당 스레드 1개(thread-per-connection) 모델을 사용한다.
 * 학습용으로는 이해하기 쉽지만, 실제 대규모 거래소는 NIO/이벤트 루프 기반의
 * 비동기 I/O를 사용해 훨씬 더 많은 연결을 적은 스레드로 처리한다.
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final FixMessageListener listener;
    private final OutputStream out;
    private volatile String senderCompId; // 이 연결의 클라이언트를 식별하는 값 (Tag 49에서 채워짐)
    private volatile boolean closed = false;

    public ClientHandler(Socket socket, FixMessageListener listener) throws IOException {
        this.socket = socket;
        this.listener = listener;
        this.out = socket.getOutputStream();
    }

    @Override
    public void run() {
        try (InputStream in = socket.getInputStream()) {
            while (!closed) {
                String rawMessage = FixMessageReader.readOneMessage(in);
                if (rawMessage == null) {
                    log.info("클라이언트가 연결을 정상 종료했습니다: {}", socket.getRemoteSocketAddress());
                    break;
                }
                // 파싱/검증/비즈니스 처리는 전부 listener(상위 계층)에게 위임한다.
                listener.onMessageReceived(this, rawMessage);
            }
        } catch (IOException e) {
            log.warn("Socket 연결이 종료되었습니다({}): {}", socket.getRemoteSocketAddress(), e.getMessage());
        } finally {
            close();
            listener.onConnectionClosed(this);
        }
    }

    /** 이 클라이언트에게 FIX 메시지(원시 문자열)를 전송한다. */
    public synchronized void send(String rawFixMessage) {
        if (closed) {
            log.warn("이미 닫힌 연결로는 전송할 수 없습니다: senderCompId={}", senderCompId);
            return;
        }
        try {
            out.write(rawFixMessage.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            log.warn("메시지 전송 실패, 연결을 닫습니다: {}", e.getMessage());
            close();
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // 종료 과정의 예외는 무시해도 안전하다.
        }
    }

    public String getSenderCompId() {
        return senderCompId;
    }

    public void setSenderCompId(String senderCompId) {
        this.senderCompId = senderCompId;
    }

    public String getRemoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    public boolean isClosed() {
        return closed;
    }
}
