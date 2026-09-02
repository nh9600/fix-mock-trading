package com.example.fixmock.integration;

import com.example.fixmock.client.ClientOrderService;
import com.example.fixmock.client.ClientOrderStore;
import com.example.fixmock.client.ConnectionManager;
import com.example.fixmock.client.FixMessageLogStore;
import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderStatus;
import com.example.fixmock.domain.OrderType;
import com.example.fixmock.exception.CancelRejectedException;
import com.example.fixmock.exception.OrderNotFoundException;
import com.example.fixmock.exchange.MatchingEngine;
import com.example.fixmock.net.FixSocketServer;
import com.example.fixmock.persistence.FixMessageLogJpaRepository;
import com.example.fixmock.persistence.OrderJpaRepository;
import com.example.fixmock.persistence.TradePersistenceService;
import com.example.fixmock.server.OrderRepository;
import com.example.fixmock.server.ServerOrderService;
import com.example.fixmock.session.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "REST Controller가 실제로 호출하는" {@link ClientOrderService} 계층을 검증하는 통합 테스트.
 * 클라이언트가 주문을 넣고, 서버 응답(Accept/Reject/Execution Report)을 받아
 * 로컬 Order 상태를 갱신하는 전체 흐름(요구사항 11번)을 그대로 재현한다.
 */
class ClientOrderServiceIntegrationTest {

    private FixSocketServer server;
    private ClientOrderService clientOrderService;

    @BeforeEach
    void setUp() throws IOException {
        int port = freePort();
        ServerOrderService serverOrderService =
                new ServerOrderService(new OrderRepository(), new MatchingEngine(), new SessionRegistry());
        server = new FixSocketServer(port, serverOrderService);
        server.start();

        ConnectionManager connectionManager = new ConnectionManager("localhost", port);

        // 이 테스트는 DB 없이 순수 자바 객체만으로 도는 통합 테스트이므로,
        // JPA 저장소는 Mockito로 대체하고 "항상 신규 삽입"으로만 동작하도록 최소한만 스텁한다.
        // TradePersistenceService는 저장 실패를 내부적으로 흡수하므로, 이렇게만 해도 충분하다.
        OrderJpaRepository orderJpaRepository = mock(OrderJpaRepository.class);
        when(orderJpaRepository.findByClientOrderId(anyString())).thenReturn(Optional.empty());
        FixMessageLogJpaRepository fixMessageLogJpaRepository = mock(FixMessageLogJpaRepository.class);
        TradePersistenceService persistenceService =
                new TradePersistenceService(orderJpaRepository, fixMessageLogJpaRepository);

        clientOrderService = new ClientOrderService(
                connectionManager, new ClientOrderStore(), new FixMessageLogStore(), persistenceService);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void 주문을_넣으면_서버_접수_결과가_동기적으로_반영된다() {
        Order order = clientOrderService.placeOrder("TRADER-A", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("150.00"), 100, 2000);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getExchangeOrderId()).isNotBlank();
    }

    @Test
    void 잘못된_주문은_클라이언트측에도_거부로_반영된다() {
        Order order = clientOrderService.placeOrder("TRADER-A", "NOSUCH", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("10.00"), 10, 2000);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectReason()).contains("존재하지 않는 종목");
    }

    @Test
    void 매수와_매도가_체결되면_양쪽_클라이언트_로컬_상태가_모두_FILLED로_갱신된다() {
        Order buyOrder = clientOrderService.placeOrder("TRADER-BUY", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("150.00"), 100, 2000);
        assertThat(buyOrder.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

        Order sellOrder = clientOrderService.placeOrder("TRADER-SELL", "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("150.00"), 100, 2000);
        assertThat(sellOrder.getStatus()).isIn(OrderStatus.ACCEPTED, OrderStatus.FILLED);

        // placeOrder()는 "접수(Accept)" 응답까지만 동기적으로 기다린다. 실제 체결(Trade) 통지는
        // 매칭 엔진이 처리를 끝낸 뒤 별도의 Execution Report로, 소켓 리더 스레드를 통해 비동기로
        // 도착하므로 두 주문 모두 Awaitility로 최종 상태(FILLED)가 될 때까지 기다려야 한다.
        // (이 비동기성이 바로 "FIX는 요청-응답이 한 쌍으로 즉시 끝나지 않는 프로토콜"이라는 특징이다.)
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(clientOrderService.findOrder(buyOrder.getClientOrderId()).getStatus())
                        .isEqualTo(OrderStatus.FILLED));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(clientOrderService.findOrder(sellOrder.getClientOrderId()).getStatus())
                        .isEqualTo(OrderStatus.FILLED));
    }

    @Test
    void 주문_취소가_성공하면_로컬_상태가_CANCELLED로_바뀐다() {
        Order order = clientOrderService.placeOrder("TRADER-C", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100.00"), 30, 2000);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

        Order cancelled = clientOrderService.cancelOrder("TRADER-C", order.getClientOrderId(), 2000);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void 존재하지_않는_주문ID를_취소하면_클라이언트측에서_즉시_예외가_발생한다() {
        assertThatThrownBy(() -> clientOrderService.cancelOrder("TRADER-C", "NO-SUCH-ID", 2000))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void 이미_체결된_주문을_취소하면_CancelRejectedException이_발생한다() {
        Order buyOrder = clientOrderService.placeOrder("TRADER-BUY2", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("140.00"), 20, 2000);
        clientOrderService.placeOrder("TRADER-SELL2", "AAPL", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("140.00"), 20, 2000);

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(clientOrderService.findOrder(buyOrder.getClientOrderId()).getStatus())
                        .isEqualTo(OrderStatus.FILLED));

        assertThatThrownBy(() -> clientOrderService.cancelOrder("TRADER-BUY2", buyOrder.getClientOrderId(), 2000))
                .isInstanceOf(CancelRejectedException.class)
                .hasMessageContaining("이미 종료된 주문");
    }
}
