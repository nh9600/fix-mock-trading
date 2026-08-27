package com.example.fixmock.net;

import com.example.fixmock.exception.FixParsingException;
import com.example.fixmock.fix.FixMessage;
import com.example.fixmock.fix.FixMessageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 클라이언트(트레이딩 애플리케이션) 쪽의 TCP 연결.
 *
 * REST Controller가 "주문을 넣어줘"라고 하면, 이 클래스가:
 *   1. Mock 거래소 서버에 TCP로 접속하고,
 *   2. FixMessageBuilder가 만들어준 FIX 원시 문자열을 그대로 바이트로 흘려보내고,
 *   3. 백그라운드 스레드로 서버가 보내주는 응답(Accept/Reject/ExecutionReport)을
 *      계속 읽어서 파싱한 뒤 콜백으로 상위 계층에 알려준다.
 *
 * 즉 "클라이언트가 Socket을 통해 FIX 메시지를 보내고 받는다"는 요구사항이
 * 이 클래스 하나에 그대로 드러난다.
 */
public class FixSocketClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FixSocketClient.class);

    private final Socket socket;
    private final OutputStream out;
    private final Consumer<FixMessage> onMessage;
    private final Thread readerThread;
    private volatile boolean closed = false;

    public FixSocketClient(String host, int port, Consumer<FixMessage> onMessage) throws IOException {
        this.socket = new Socket(host, port);
        this.out = socket.getOutputStream();
        this.onMessage = onMessage;
        this.readerThread = new Thread(this::readLoop, "fix-client-reader-" + socket.getLocalPort());
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readLoop() {
        try (InputStream in = socket.getInputStream()) {
            while (!closed) {
                String raw = FixMessageReader.readOneMessage(in);
                if (raw == null) {
                    log.info("서버가 연결을 종료했습니다.");
                    break;
                }
                try {
                    FixMessage message = FixMessageParser.parse(raw);
                    onMessage.accept(message);
                } catch (FixParsingException e) {
                    // 클라이언트 입장에서도 서버가 보낸 메시지가 손상되었을 가능성을 대비한다.
                    log.error("서버로부터 받은 메시지 파싱 실패: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            if (!closed) {
                log.warn("Socket 읽기 중 연결이 끊어졌습니다: {}", e.getMessage());
            }
        }
    }

    /** FIX 원시 문자열(FixMessageBuilder로 생성한 결과)을 그대로 소켓에 전송한다. */
    public synchronized void send(String rawFixMessage) {
        try {
            out.write(rawFixMessage.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("FIX 메시지 전송 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
