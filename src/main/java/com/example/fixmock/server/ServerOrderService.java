package com.example.fixmock.server;

import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderType;
import com.example.fixmock.exception.FixParsingException;
import com.example.fixmock.exception.OrderAlreadyClosedException;
import com.example.fixmock.exception.OrderNotFoundException;
import com.example.fixmock.exception.OrderValidationException;
import com.example.fixmock.exchange.MatchingEngine;
import com.example.fixmock.exchange.Trade;
import com.example.fixmock.fix.FixMessage;
import com.example.fixmock.fix.FixMessageBuilder;
import com.example.fixmock.fix.FixMessageParser;
import com.example.fixmock.fix.FixMsgType;
import com.example.fixmock.fix.FixTags;
import com.example.fixmock.net.ClientHandler;
import com.example.fixmock.net.FixMessageListener;
import com.example.fixmock.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock 거래소 서버의 "두뇌"에 해당하는 클래스.
 *
 * {@link FixMessageListener}를 구현함으로써 net 계층(FixSocketServer/ClientHandler)에
 * 꽂혀서 실행된다. 하는 일은 다음 순서 그대로다.
 *
 *   원시 문자열 수신 -> FIX 파싱(FixMessageParser) -> 주문 유효성 검증(OrderValidator)
 *   -> 접수/거부 응답(FixMessageBuilder) -> 매칭 엔진에 제출(MatchingEngine)
 *   -> 체결 발생 시 관련된 모든 클라이언트에게 Execution Report 전송
 *
 * 이 클래스는 "무엇을 해야 하는가(비즈니스 로직)"만 알고 있을 뿐, 소켓을 어떻게
 * 열고 바이트를 어떻게 읽는지는 전혀 몰라도 된다 (그건 net 패키지의 책임).
 */
public class ServerOrderService implements FixMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ServerOrderService.class);

    /** 이 Mock 거래소 자신을 가리키는 식별자. FIX 헤더의 SenderCompID(응답 시)/TargetCompID(수신 시)로 쓰인다. */
    public static final String EXCHANGE_COMP_ID = "MOCK-EXCHANGE";

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final SessionRegistry sessionRegistry;
    private final AtomicInteger outboundSeqNum = new AtomicInteger(0);

    public ServerOrderService(OrderRepository orderRepository, MatchingEngine matchingEngine, SessionRegistry sessionRegistry) {
        this.orderRepository = orderRepository;
        this.matchingEngine = matchingEngine;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void onMessageReceived(ClientHandler connection, String rawMessage) {
        FixMessage message;
        try {
            message = FixMessageParser.parse(rawMessage);
        } catch (FixParsingException e) {
            // 잘못된 FIX 메시지 형식: 세션을 끊지 않고, 알아낼 수 있는 만큰만 담아 거부 응답을 준다.
            log.warn("잘못된 FIX 메시지 수신 (연결: {}): {}", connection.getRemoteAddress(), e.getMessage());
            String reject = FixMessageBuilder.executionReportRejected(
                    EXCHANGE_COMP_ID, "UNKNOWN", nextSeq(), null, null, null, 0, null,
                    "잘못된 FIX 메시지 형식입니다: " + e.getMessage());
            connection.send(reject);
            return;
        }

        // 세션 등록: 이후 다른 클라이언트와의 체결 결과를 이 연결로 되돌려 보낼 수 있도록 기록해둔다.
        String senderCompId = message.getString(FixTags.SENDER_COMP_ID);
        if (senderCompId != null) {
            sessionRegistry.register(senderCompId, connection);
        }

        String msgType = message.getMsgType();
        switch (msgType) {
            case FixMsgType.NEW_ORDER_SINGLE -> handleNewOrderSingle(connection, message, senderCompId);
            case FixMsgType.ORDER_CANCEL_REQUEST -> handleOrderCancelRequest(connection, message, senderCompId);
            default -> {
                log.warn("알 수 없는 MsgType(35={}) 수신. 무시합니다.", msgType);
                connection.send(FixMessageBuilder.executionReportRejected(
                        EXCHANGE_COMP_ID, senderCompId == null ? "UNKNOWN" : senderCompId, nextSeq(),
                        message.getString(FixTags.CL_ORD_ID), null, null, 0, null,
                        "지원하지 않는 MsgType 입니다: " + msgType));
            }
        }
    }

    @Override
    public void onConnectionClosed(ClientHandler connection) {
        sessionRegistry.unregister(connection);
        log.info("연결 종료로 세션 해제: senderCompId={}", connection.getSenderCompId());
    }

    // ───────────────────────── New Order Single 처리 ─────────────────────────

    private void handleNewOrderSingle(ClientHandler connection, FixMessage message, String senderCompId) {
        String clOrdId = message.getString(FixTags.CL_ORD_ID);
        String symbol = message.getString(FixTags.SYMBOL);
        String sideRaw = message.getString(FixTags.SIDE);
        int quantity = safeParseInt(message.getString(FixTags.ORDER_QTY));
        BigDecimal price = message.getBigDecimal(FixTags.PRICE);

        try {
            FixMessageParser.requireNewOrderSingleFields(message);

            OrderSide side = OrderSide.fromFixValue(sideRaw);
            OrderType orderType = OrderType.fromFixValue(message.getRequiredString(FixTags.ORD_TYPE));
            symbol = symbol.toUpperCase();

            OrderValidator.validate(symbol, quantity, price, orderType);

            if (orderRepository.findByClientOrderId(clOrdId) != null) {
                throw new OrderValidationException("이미 존재하는 ClOrdID 입니다: " + clOrdId);
            }

            Order order = new Order(clOrdId, senderCompId, symbol, side, orderType, price, quantity);
            order.accept(Order.nextExchangeOrderId());
            orderRepository.save(order);

            // 1) 접수(Accept) 통지
            connection.send(FixMessageBuilder.executionReportAccepted(EXCHANGE_COMP_ID, senderCompId, nextSeq(), order));
            log.info("주문 접수: {}", order);

            // 2) 매칭 엔진에 제출 -> 즉시 체결 가능한지 시도
            List<Trade> trades = matchingEngine.submit(order);
            for (Trade trade : trades) {
                log.info("체결 발생: {}", trade);
                notifyFill(trade.getBuyOrder(), trade.getQuantity(), trade.getPrice());
                notifyFill(trade.getSellOrder(), trade.getQuantity(), trade.getPrice());
            }
        } catch (FixParsingException | OrderValidationException | IllegalArgumentException e) {
            log.info("주문 거부: clOrdId={}, 사유={}", clOrdId, e.getMessage());
            connection.send(FixMessageBuilder.executionReportRejected(
                    EXCHANGE_COMP_ID, senderCompId, nextSeq(), clOrdId, symbol, sideRaw, quantity, price, e.getMessage()));
        }
    }

    private void notifyFill(Order order, int fillQty, BigDecimal fillPrice) {
        ClientHandler target = sessionRegistry.find(order.getSenderCompId());
        String raw = FixMessageBuilder.executionReportTrade(EXCHANGE_COMP_ID, order.getSenderCompId(), nextSeq(), order, fillQty, fillPrice);
        if (target != null && !target.isClosed()) {
            target.send(raw);
        } else {
            log.warn("체결 통지를 보낼 연결을 찾을 수 없습니다(연결 종료됨?): senderCompId={}, order={}", order.getSenderCompId(), order);
        }
    }

    // ───────────────────────── Order Cancel Request 처리 ─────────────────────────

    private void handleOrderCancelRequest(ClientHandler connection, FixMessage message, String senderCompId) {
        String newClOrdId = message.getString(FixTags.CL_ORD_ID);
        String origClOrdId = message.getString(FixTags.ORIG_CL_ORD_ID);
        try {
            FixMessageParser.requireOrderCancelRequestFields(message);

            Order order = orderRepository.findByClientOrderId(origClOrdId);
            if (order == null) {
                throw new OrderNotFoundException("취소 대상 주문(ClOrdID=" + origClOrdId + ")을 찾을 수 없습니다.");
            }
            if (!order.isCancellable()) {
                throw new OrderAlreadyClosedException(
                        "이미 종료된 주문은 취소할 수 없습니다. 현재 상태=" + order.getStatus());
            }

            matchingEngine.cancel(order); // 호가창에 남아있다면 제거
            order.cancel();

            connection.send(FixMessageBuilder.executionReportCanceled(EXCHANGE_COMP_ID, senderCompId, nextSeq(), order));
            log.info("주문 취소 완료: {}", order);
        } catch (FixParsingException | OrderNotFoundException | OrderAlreadyClosedException e) {
            log.info("주문 취소 거부: origClOrdId={}, 사유={}", origClOrdId, e.getMessage());
            connection.send(FixMessageBuilder.orderCancelReject(
                    EXCHANGE_COMP_ID, senderCompId, nextSeq(), newClOrdId, origClOrdId, e.getMessage()));
        }
    }

    private int nextSeq() {
        return outboundSeqNum.incrementAndGet();
    }

    private static int safeParseInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
