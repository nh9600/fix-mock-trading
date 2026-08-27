package com.example.fixmock.integration;

import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderType;
import com.example.fixmock.exchange.MatchingEngine;
import com.example.fixmock.fix.FixMessage;
import com.example.fixmock.fix.FixMessageBuilder;
import com.example.fixmock.fix.FixMsgType;
import com.example.fixmock.fix.FixTags;
import com.example.fixmock.net.FixSocketClient;
import com.example.fixmock.net.FixSocketServer;
import com.example.fixmock.server.OrderRepository;
import com.example.fixmock.server.ServerOrderService;
import com.example.fixmock.session.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "실제 TCP Socket + FIX 메시지"를 사용하는 종단간(end-to-end) 통합 테스트.
 *
 * Spring 컨텍스트 없이, {@link FixSocketServer}/{@link FixSocketClient}/{@link ServerOrderService}를
 * 직접 조립해서 사용한다. 즉 이 테스트가 통과한다는 것은 "Socket 위에서 FIX 메시지를 주고받는
 * 전체 파이프라인"이 실제로 동작한다는 뜻이다.
 */
class OrderFlowIntegrationTest {

    private FixSocketServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private FixSocketServer startServer() throws IOException {
        int port = freePort();
        ServerOrderService service = new ServerOrderService(new OrderRepository(), new MatchingEngine(), new SessionRegistry());
        FixSocketServer s = new FixSocketServer(port, service);
        s.start();
        return s;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void 정상_주문_접수_테스트() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> inbox = new LinkedBlockingQueue<>();

        try (FixSocketClient client = new FixSocketClient("localhost", server.getPort(), inbox::add)) {
            client.send(FixMessageBuilder.newOrderSingle("CLIENT1", "MOCK-EXCHANGE", 1, "CL-1",
                    "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100));

            FixMessage response = inbox.poll(3, TimeUnit.SECONDS);

            assertThat(response).isNotNull();
            assertThat(response.getMsgType()).isEqualTo(FixMsgType.EXECUTION_REPORT);
            assertThat(response.getString(FixTags.EXEC_TYPE)).isEqualTo(FixMsgType.EXEC_TYPE_NEW);
            assertThat(response.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_NEW);
            assertThat(response.getString(FixTags.CL_ORD_ID)).isEqualTo("CL-1");
            assertThat(response.getString(FixTags.ORDER_ID)).isNotBlank(); // 거래소가 채번한 OrderID
        }
    }

    @Test
    void 잘못된_주문은_거부된다_존재하지_않는_종목() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> inbox = new LinkedBlockingQueue<>();

        try (FixSocketClient client = new FixSocketClient("localhost", server.getPort(), inbox::add)) {
            client.send(FixMessageBuilder.newOrderSingle("CLIENT1", "MOCK-EXCHANGE", 1, "CL-2",
                    "NOSUCHSTOCK", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10.00"), 10));

            FixMessage response = inbox.poll(3, TimeUnit.SECONDS);

            assertThat(response.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_REJECTED);
            assertThat(response.getString(FixTags.TEXT)).contains("존재하지 않는 종목");
        }
    }

    @Test
    void 잘못된_주문은_거부된다_수량_0_이하() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> inbox = new LinkedBlockingQueue<>();

        try (FixSocketClient client = new FixSocketClient("localhost", server.getPort(), inbox::add)) {
            client.send(FixMessageBuilder.newOrderSingle("CLIENT1", "MOCK-EXCHANGE", 1, "CL-3",
                    "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10.00"), 0));

            FixMessage response = inbox.poll(3, TimeUnit.SECONDS);
            assertThat(response.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_REJECTED);
        }
    }

    @Test
    void 잘못된_주문은_거부된다_가격_음수() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> inbox = new LinkedBlockingQueue<>();

        try (FixSocketClient client = new FixSocketClient("localhost", server.getPort(), inbox::add)) {
            client.send(FixMessageBuilder.newOrderSingle("CLIENT1", "MOCK-EXCHANGE", 1, "CL-4",
                    "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("-5.00"), 10));

            FixMessage response = inbox.poll(3, TimeUnit.SECONDS);
            assertThat(response.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_REJECTED);
        }
    }

    @Test
    void 매수와_매도가_체결되고_양측_모두에게_체결결과가_전달된다() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> buyerInbox = new LinkedBlockingQueue<>();
        BlockingQueue<FixMessage> sellerInbox = new LinkedBlockingQueue<>();

        try (FixSocketClient buyer = new FixSocketClient("localhost", server.getPort(), buyerInbox::add);
             FixSocketClient seller = new FixSocketClient("localhost", server.getPort(), sellerInbox::add)) {

            buyer.send(FixMessageBuilder.newOrderSingle("BUYER1", "MOCK-EXCHANGE", 1, "BUY-1",
                    "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100));
            assertThat(buyerInbox.poll(3, TimeUnit.SECONDS)).isNotNull(); // Accept

            seller.send(FixMessageBuilder.newOrderSingle("SELLER1", "MOCK-EXCHANGE", 1, "SELL-1",
                    "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("150.00"), 100));
            assertThat(sellerInbox.poll(3, TimeUnit.SECONDS)).isNotNull(); // Accept

            FixMessage buyerFill = buyerInbox.poll(3, TimeUnit.SECONDS);
            FixMessage sellerFill = sellerInbox.poll(3, TimeUnit.SECONDS);

            assertThat(buyerFill.getString(FixTags.EXEC_TYPE)).isEqualTo(FixMsgType.EXEC_TYPE_TRADE);
            assertThat(buyerFill.getString(FixTags.LAST_QTY)).isEqualTo("100");
            assertThat(buyerFill.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_FILLED);

            assertThat(sellerFill.getString(FixTags.EXEC_TYPE)).isEqualTo(FixMsgType.EXEC_TYPE_TRADE);
            assertThat(sellerFill.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_FILLED);
        }
    }

    @Test
    void 주문_취소가_정상적으로_처리된다() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> inbox = new LinkedBlockingQueue<>();

        try (FixSocketClient client = new FixSocketClient("localhost", server.getPort(), inbox::add)) {
            client.send(FixMessageBuilder.newOrderSingle("CLIENT1", "MOCK-EXCHANGE", 1, "CL-CXL",
                    "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("100.00"), 50));
            inbox.poll(3, TimeUnit.SECONDS); // Accept

            client.send(FixMessageBuilder.orderCancelRequest("CLIENT1", "MOCK-EXCHANGE", 2,
                    "CL-CXL-C1", "CL-CXL", "AAPL", OrderSide.BUY));
            FixMessage cancelAck = inbox.poll(3, TimeUnit.SECONDS);

            assertThat(cancelAck.getMsgType()).isEqualTo(FixMsgType.EXECUTION_REPORT);
            assertThat(cancelAck.getString(FixTags.EXEC_TYPE)).isEqualTo(FixMsgType.EXEC_TYPE_CANCELED);
            assertThat(cancelAck.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_CANCELED);
        }
    }

    @Test
    void 존재하지_않는_주문ID_취소는_거부된다() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> inbox = new LinkedBlockingQueue<>();

        try (FixSocketClient client = new FixSocketClient("localhost", server.getPort(), inbox::add)) {
            client.send(FixMessageBuilder.orderCancelRequest("CLIENT1", "MOCK-EXCHANGE", 1,
                    "CXL-1", "NO-SUCH-ORDER-ID", "AAPL", OrderSide.BUY));

            FixMessage response = inbox.poll(3, TimeUnit.SECONDS);
            assertThat(response.getMsgType()).isEqualTo(FixMsgType.ORDER_CANCEL_REJECT);
            assertThat(response.getString(FixTags.TEXT)).contains("찾을 수 없습니다");
        }
    }

    @Test
    void 이미_체결완료된_주문의_취소는_거부된다() throws Exception {
        server = startServer();
        BlockingQueue<FixMessage> buyerInbox = new LinkedBlockingQueue<>();
        BlockingQueue<FixMessage> sellerInbox = new LinkedBlockingQueue<>();

        try (FixSocketClient buyer = new FixSocketClient("localhost", server.getPort(), buyerInbox::add);
             FixSocketClient seller = new FixSocketClient("localhost", server.getPort(), sellerInbox::add)) {

            buyer.send(FixMessageBuilder.newOrderSingle("BUYER-F", "MOCK-EXCHANGE", 1, "CL-F1",
                    "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("120.00"), 10));
            buyerInbox.poll(3, TimeUnit.SECONDS);

            seller.send(FixMessageBuilder.newOrderSingle("SELLER-F", "MOCK-EXCHANGE", 1, "CL-F2",
                    "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("120.00"), 10));
            sellerInbox.poll(3, TimeUnit.SECONDS);
            FixMessage fill = buyerInbox.poll(3, TimeUnit.SECONDS);
            assertThat(fill.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_FILLED);

            buyer.send(FixMessageBuilder.orderCancelRequest("BUYER-F", "MOCK-EXCHANGE", 2,
                    "CL-F1-C1", "CL-F1", "AAPL", OrderSide.BUY));
            FixMessage cancelReject = buyerInbox.poll(3, TimeUnit.SECONDS);

            assertThat(cancelReject.getMsgType()).isEqualTo(FixMsgType.ORDER_CANCEL_REJECT);
            assertThat(cancelReject.getString(FixTags.TEXT)).contains("이미 종료된 주문");
        }
    }

    @Test
    void 잘못된_형식의_바이트를_보내도_서버는_죽지_않고_이후_연결을_계속_처리한다() throws Exception {
        server = startServer();

        // 체크섬 필드(10=) 없이 완전히 깨진 바이트를 보내고 연결을 끊는다.
        try (Socket rawSocket = new Socket("localhost", server.getPort())) {
            rawSocket.getOutputStream().write("not-a-fix-message-at-all".getBytes(StandardCharsets.US_ASCII));
            rawSocket.getOutputStream().flush();
        }

        // 서버가 죽지 않았다면, 새 연결로 정상 주문을 보냈을 때 여전히 응답이 와야 한다.
        BlockingQueue<FixMessage> inbox = new LinkedBlockingQueue<>();
        try (FixSocketClient client = new FixSocketClient("localhost", server.getPort(), inbox::add)) {
            client.send(FixMessageBuilder.newOrderSingle("CLIENT-AFTER", "MOCK-EXCHANGE", 1, "CL-AFTER",
                    "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10.00"), 5));

            FixMessage response = inbox.poll(3, TimeUnit.SECONDS);
            assertThat(response).isNotNull();
            assertThat(response.getString(FixTags.EXEC_TYPE)).isEqualTo(FixMsgType.EXEC_TYPE_NEW);
        }
    }
}
