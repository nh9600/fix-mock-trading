package com.example.fixmock.client;

import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderStatus;
import com.example.fixmock.domain.OrderType;
import com.example.fixmock.exception.CancelRejectedException;
import com.example.fixmock.exception.OrderNotFoundException;
import com.example.fixmock.fix.FixMessage;
import com.example.fixmock.fix.FixMessageBuilder;
import com.example.fixmock.fix.FixMsgType;
import com.example.fixmock.fix.FixTags;
import com.example.fixmock.net.FixSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 클라이언트(트레이딩 애플리케이션) 쪽 주문 서비스.
 *
 * REST Controller가 호출하는 진입점이며, 아래 흐름을 그대로 담당한다.
 *   1) Order 도메인 객체 생성 (status=NEW)
 *   2) FixMessageBuilder로 New Order Single(35=D) 메시지 생성
 *   3) ConnectionManager를 통해 TCP Socket으로 전송
 *   4) 서버 응답(Execution Report)을 비동기로 수신하여 로컬 Order 상태 갱신
 *
 * FIX 자체는 원래 비동기(요청과 응답이 같은 스레드에서 바로 짝지어지지 않음) 프로토콜이지만,
 * REST API는 보통 동기 응답을 기대하므로, 여기서는 CompletableFuture를 이용해
 * "첫 응답(접수 또는 거부)이 올 때까지 짧게 기다렸다가 결과를 돌려주는" 방식으로
 * 비동기 소켓 통신을 동기 HTTP 응답에 연결(bridge)한다.
 */
public class ClientOrderService {

    private static final Logger log = LoggerFactory.getLogger(ClientOrderService.class);
    private static final String TARGET_COMP_ID = "MOCK-EXCHANGE";

    private final ConnectionManager connectionManager;
    private final ClientOrderStore orderStore;
    private final FixMessageLogStore messageLogStore;
    private final AtomicInteger clOrdIdSeq = new AtomicInteger(0);
    private final AtomicInteger outboundSeqNum = new AtomicInteger(0);

    // 신규 주문에 대한 첫 응답(접수/거부)을 기다리는 future들. key = ClOrdID
    private final ConcurrentHashMap<String, CompletableFuture<Order>> pendingAcks = new ConcurrentHashMap<>();
    // 취소 요청에 대한 응답(취소완료/취소거부)을 기다리는 future들. key = 원본 주문의 ClOrdID
    private final ConcurrentHashMap<String, CompletableFuture<Order>> pendingCancels = new ConcurrentHashMap<>();

    public ClientOrderService(ConnectionManager connectionManager, ClientOrderStore orderStore,
                               FixMessageLogStore messageLogStore) {
        this.connectionManager = connectionManager;
        this.orderStore = orderStore;
        this.messageLogStore = messageLogStore;
    }

    /**
     * 신규 주문을 생성하고 Mock 거래소로 전송한다.
     * @return 서버의 접수(Accept) 또는 거부(Reject) 응답이 반영된 Order (최대 timeoutMs 만큼 대기)
     */
    public Order placeOrder(String clientId, String symbol, OrderSide side, OrderType orderType,
                             BigDecimal price, int quantity, long timeoutMs) {
        String clOrdId = clientId + "-" + clOrdIdSeq.incrementAndGet();
        Order localOrder = new Order(clOrdId, clientId, symbol, side, orderType, price, quantity);
        orderStore.save(localOrder);

        CompletableFuture<Order> ackFuture = new CompletableFuture<>();
        pendingAcks.put(clOrdId, ackFuture);

        String rawMessage = FixMessageBuilder.newOrderSingle(
                clientId, TARGET_COMP_ID, nextSeq(), clOrdId, symbol, side, orderType, price, quantity);

        FixSocketClient connection = connectionManager.getOrCreate(clientId, msg -> handleIncoming(clientId, msg));
        log.info("[{}] New Order Single 전송: {}", clientId, describe(rawMessage));
        messageLogStore.append(clientId, FixMessageLogStore.Direction.SENT, FixMsgType.NEW_ORDER_SINGLE, toDisplay(rawMessage));
        connection.send(rawMessage);

        return awaitOrTimeout(ackFuture, localOrder, timeoutMs);
    }

    /**
     * 기존 주문에 대한 취소를 요청한다.
     * @return 취소가 완료된(CANCELLED) Order, 혹은 실패 시 예외 발생
     */
    public Order cancelOrder(String clientId, String origClOrdId, long timeoutMs) {
        Order local = orderStore.find(origClOrdId);
        if (local == null) {
            // 로컬에도 기록이 없는 주문 ID -> 서버 왕복 없이 즉시 실패 처리한다.
            throw new OrderNotFoundException("클라이언트에 존재하지 않는 주문 ID 입니다: " + origClOrdId);
        }

        String cancelRequestId = clientId + "-CXL-" + clOrdIdSeq.incrementAndGet();
        CompletableFuture<Order> cancelFuture = new CompletableFuture<>();
        pendingCancels.put(origClOrdId, cancelFuture);

        String rawMessage = FixMessageBuilder.orderCancelRequest(
                clientId, TARGET_COMP_ID, nextSeq(), cancelRequestId, origClOrdId, local.getSymbol(), local.getSide());

        FixSocketClient connection = connectionManager.getOrCreate(clientId, msg -> handleIncoming(clientId, msg));
        log.info("[{}] Order Cancel Request 전송: {}", clientId, describe(rawMessage));
        messageLogStore.append(clientId, FixMessageLogStore.Direction.SENT, FixMsgType.ORDER_CANCEL_REQUEST, toDisplay(rawMessage));
        connection.send(rawMessage);

        return awaitCancelOrTimeout(cancelFuture, timeoutMs);
    }

    public Order findOrder(String clOrdId) {
        Order order = orderStore.find(clOrdId);
        if (order == null) {
            throw new OrderNotFoundException("존재하지 않는 주문 ID 입니다: " + clOrdId);
        }
        return order;
    }

    /** 특정 클라이언트(트레이더)가 낸 모든 주문을, 생성된 순서(오래된 순)로 조회한다. 대시보드의 주문 목록 갱신에 사용된다. */
    public Collection<Order> listOrders(String clientId) {
        return orderStore.findByClientId(clientId).stream()
                .sorted(java.util.Comparator.comparing(Order::getCreatedAt))
                .toList();
    }

    // ───────────────────────── 서버 응답(수신 스레드) 처리 ─────────────────────────

    private void handleIncoming(String clientId, FixMessage message) {
        String msgType = message.getMsgType();
        messageLogStore.append(clientId, FixMessageLogStore.Direction.RECEIVED, msgType, message.toDisplayString());
        if (FixMsgType.EXECUTION_REPORT.equals(msgType)) {
            handleExecutionReport(message);
        } else if (FixMsgType.ORDER_CANCEL_REJECT.equals(msgType)) {
            handleCancelReject(message);
        } else {
            log.warn("[{}] 알 수 없는 응답 MsgType 수신: {}", clientId, msgType);
        }
    }

    private void handleExecutionReport(FixMessage message) {
        String clOrdId = message.getString(FixTags.CL_ORD_ID);
        String execType = message.getString(FixTags.EXEC_TYPE);
        String ordStatusRaw = message.getString(FixTags.ORD_STATUS);
        String exchangeOrderId = message.getString(FixTags.ORDER_ID);
        int leaves = parseIntOrZero(message.getString(FixTags.LEAVES_QTY));
        int cum = parseIntOrZero(message.getString(FixTags.CUM_QTY));
        String text = message.getString(FixTags.TEXT);

        Order local = orderStore.find(clOrdId);
        OrderStatus status = OrderStatus.fromFixValue(ordStatusRaw);

        if (local != null) {
            local.syncFromServer(status, exchangeOrderId, leaves, cum, text);
            log.info("[{}] Execution Report 수신 (execType={}): {}", local.getSenderCompId(), execType, local);
        } else {
            log.warn("로컬에 없는 주문에 대한 Execution Report 수신: clOrdId={}", clOrdId);
        }

        if (FixMsgType.EXEC_TYPE_NEW.equals(execType) || FixMsgType.EXEC_TYPE_REJECTED.equals(execType)) {
            completeFuture(pendingAcks, clOrdId, local, null);
        }
        if (FixMsgType.EXEC_TYPE_CANCELED.equals(execType)) {
            completeFuture(pendingCancels, clOrdId, local, null);
        }
    }

    private void handleCancelReject(FixMessage message) {
        String origClOrdId = message.getString(FixTags.ORIG_CL_ORD_ID);
        String reason = message.getString(FixTags.TEXT);
        log.info("Order Cancel Reject 수신: origClOrdId={}, 사유={}", origClOrdId, reason);
        completeFuture(pendingCancels, origClOrdId, null, new CancelRejectedException(reason));
    }

    private void completeFuture(ConcurrentHashMap<String, CompletableFuture<Order>> map,
                                 String key, Order successValue, RuntimeException error) {
        CompletableFuture<Order> future = map.remove(key);
        if (future == null) {
            return;
        }
        if (error != null) {
            future.completeExceptionally(error);
        } else {
            future.complete(successValue);
        }
    }

    private Order awaitOrTimeout(CompletableFuture<Order> future, Order fallback, long timeoutMs) {
        try {
            return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("서버 응답 대기 시간 초과. 현재 로컬 상태(status={})로 반환합니다.", fallback.getStatus());
            return fallback; // 타임아웃 시 마지막으로 알려진 로컬 상태라도 반환 (여전히 NEW일 수 있음)
        }
    }

    private Order awaitCancelOrTimeout(CompletableFuture<Order> future, long timeoutMs) {
        try {
            return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            throw new IllegalStateException("취소 응답 대기 시간이 초과되었습니다.", e);
        }
    }

    private int nextSeq() {
        return outboundSeqNum.incrementAndGet();
    }

    private static int parseIntOrZero(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String describe(String rawFixMessage) {
        return rawFixMessage.replace('', '|');
    }

    /**
     * 대시보드 로그 패널 표시용으로, 보낸 원시 FIX 문자열의 SOH(0x01) 구분자를
     * {@link FixMessage#toDisplayString()}과 동일한 " | " 형태로 바꿔준다.
     * (전송 직전 원시 문자열이라 아직 FixMessage 객체로 파싱되지 않았기 때문에 별도 처리한다.)
     */
    private static String toDisplay(String rawFixMessage) {
        String withoutTrailingSoh = rawFixMessage.endsWith("\u0001")
                ? rawFixMessage.substring(0, rawFixMessage.length() - 1)
                : rawFixMessage;
        return withoutTrailingSoh.replace("\u0001", " | ");
    }
}
