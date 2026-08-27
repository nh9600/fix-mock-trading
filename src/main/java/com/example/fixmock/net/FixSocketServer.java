package com.example.fixmock.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mock 거래소 서버의 TCP 진입점.
 *
 * 이 클래스는 딱 두 가지 일만 한다.
 *   1. 지정된 포트에서 TCP 연결을 받아들인다 (accept loop).
 *   2. 연결마다 {@link ClientHandler} 스레드를 만들어 맡긴다.
 *
 * "FIX 메시지를 이해하는 로직"은 이 클래스에 전혀 없다 — 그것은 ClientHandler가
 * 전달받는 {@link FixMessageListener} 구현체(비즈니스 계층)의 책임이다.
 * 이렇게 분리해두면, 나중에 이 Socket 계층을 NIO나 Netty로 바꾸더라도
 * 비즈니스 로직(주문 검증/체결)은 전혀 손댈 필요가 없다.
 */
public class FixSocketServer {

    private static final Logger log = LoggerFactory.getLogger(FixSocketServer.class);

    private final int port;
    private final FixMessageListener listener;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;

    public FixSocketServer(int port, FixMessageListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new IllegalStateException("Mock 거래소 서버 소켓을 열 수 없습니다(port=" + port + ")", e);
        }
        running = true;
        acceptThread = new Thread(this::acceptLoop, "fix-server-accept-loop");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.info("Mock 거래소 FIX 서버 시작. port={}", port);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                log.info("새 클라이언트 연결: {}", socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket, listener);
                connectionPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    log.warn("연결 수락 중 오류: {}", e.getMessage());
                }
                // running == false 이면 stop()에 의해 서버 소켓이 닫힌 정상적인 상황이다.
            }
        }
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        connectionPool.shutdownNow();
        log.info("Mock 거래소 FIX 서버 종료. port={}", port);
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }
}
