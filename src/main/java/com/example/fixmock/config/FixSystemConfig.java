package com.example.fixmock.config;

import com.example.fixmock.client.ClientOrderService;
import com.example.fixmock.client.ClientOrderStore;
import com.example.fixmock.client.ConnectionManager;
import com.example.fixmock.client.FixMessageLogStore;
import com.example.fixmock.exchange.MatchingEngine;
import com.example.fixmock.net.FixSocketServer;
import com.example.fixmock.server.OrderRepository;
import com.example.fixmock.server.ServerOrderService;
import com.example.fixmock.session.SessionRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 스프링 빈 조립(wiring) 설정.
 *
 * 여기서 "서버(Mock 거래소) 쪽 컴포넌트"와 "클라이언트(트레이딩 앱) 쪽 컴포넌트"가
 * 각각 무엇으로 구성되는지 한눈에 볼 수 있다. 서버와 클라이언트는 오직
 * TCP Socket(localhost:{fix.mock-exchange.port})을 통해서만 통신하며,
 * 자바 메서드를 직접 호출하는 지름길은 전혀 없다 — 실제 클라이언트/서버가
 * 서로 다른 프로세스, 다른 서버에 있는 상황과 동일한 통신 경로를 강제로 사용한다.
 */
@Configuration
public class FixSystemConfig {

    @Value("${fix.mock-exchange.port}")
    private int exchangePort;

    @Value("${fix.client.host}")
    private String clientHost;

    private FixSocketServer fixSocketServer;

    // ── 서버(Mock 거래소) 쪽 빈 ──────────────────────────────

    @Bean
    public OrderRepository orderRepository() {
        return new OrderRepository();
    }

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistry();
    }

    @Bean
    public ServerOrderService serverOrderService(OrderRepository orderRepository,
                                                  MatchingEngine matchingEngine,
                                                  SessionRegistry sessionRegistry) {
        return new ServerOrderService(orderRepository, matchingEngine, sessionRegistry);
    }

    /**
     * 애플리케이션이 완전히 기동된 후 TCP FIX 서버를 시작한다.
     * (Bean 생성 시점이 아니라 ApplicationReadyEvent 시점에 여는 이유는,
     *  포트 바인딩 같은 부수효과를 스프링 컨텍스트 초기화가 다 끝난 뒤 안전하게 실행하기 위함이다.)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startFixServer(ApplicationReadyEvent event) {
        ServerOrderService serverOrderService = event.getApplicationContext().getBean(ServerOrderService.class);
        this.fixSocketServer = new FixSocketServer(exchangePort, serverOrderService);
        this.fixSocketServer.start();
    }

    @PreDestroy
    public void stopFixServer() {
        if (fixSocketServer != null) {
            fixSocketServer.stop();
        }
    }

    // ── 클라이언트(트레이딩 앱) 쪽 빈 ──────────────────────────

    @Bean
    public ClientOrderStore clientOrderStore() {
        return new ClientOrderStore();
    }

    @Bean
    public ConnectionManager connectionManager() {
        return new ConnectionManager(clientHost, exchangePort);
    }

    /** 클라이언트가 주고받은 FIX 메시지 로그(대시보드 로그 패널용). */
    @Bean
    public FixMessageLogStore fixMessageLogStore() {
        return new FixMessageLogStore();
    }

    @Bean
    public ClientOrderService clientOrderService(ConnectionManager connectionManager,
                                                  ClientOrderStore clientOrderStore,
                                                  FixMessageLogStore fixMessageLogStore) {
        return new ClientOrderService(connectionManager, clientOrderStore, fixMessageLogStore);
    }
}
