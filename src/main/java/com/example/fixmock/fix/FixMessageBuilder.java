package com.example.fixmock.fix;

import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 도메인 객체(Order 등)를 실제 "FIX 와이어 포맷(raw wire format)" 문자열로 직렬화하는 클래스.
 *
 * 이 클래스가 만들어내는 문자열이 바로 TCP Socket을 통해 전송되는 바이트의 원본이다.
 * 즉, "Socket 전송"과 "FIX 메시지"의 관계는 다음과 같다:
 *
 *   FixMessageBuilder(포맷 생성) -> byte[] -> OutputStream.write(byte[]) (Socket 전송)
 *
 * Socket은 그저 바이트를 실어 나르는 "운송 수단(파이프)"이고,
 * FIX는 그 파이프 안에 흘려보내는 바이트를 어떤 "형식(문법)"으로 채울지에 대한 약속이다.
 *
 * ── 이 예제가 단순화한 부분 (실제 FIX 표준과의 차이) ─────────────────────
 * 1. 세션 계층(Logon/Logout/Heartbeat/Resend Request/Sequence Reset) 생략.
 *    실제 FIX는 세션 레벨 메시지(A, 0, 5, 2, 4 등)로 연결 상태를 관리하지만
 *    여기서는 TCP 연결 자체를 세션과 동일하게 취급한다.
 * 2. 반복 그룹(Repeating Group), 커스텀 태그, 확장 필드 미지원.
 * 3. MsgSeqNum(34)에 대한 갭 검증/재전송 로직 없음(단순 증가 카운터로만 기록).
 * 4. BeginString은 FIX.4.4로 고정.
 */
public final class FixMessageBuilder {

    /** FIX 필드 구분자(Start Of Heading, 0x01 제어문자). 터미널/에디터에는 보이지 않는다. */
    private static final String SOH = "";
    private static final String BEGIN_STRING = "FIX.4.4";
    private static final DateTimeFormatter SENDING_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(java.time.ZoneOffset.UTC);

    private static final AtomicInteger EXEC_ID_SEQ = new AtomicInteger(5000);

    private FixMessageBuilder() {
    }

    // ───────────────────────── New Order Single (35=D) ─────────────────────────

    /**
     * 신규 주문 생성. 예시 메시지 형태(문제에서 제시된 예시)와 동일한 필드를 담는다.
     *   35=D, 55=AAPL, 54=1, 38=100, 40=2, 44=150.00
     */
    public static String newOrderSingle(String senderCompId,
                                         String targetCompId,
                                         int seqNum,
                                         String clOrdId,
                                         String symbol,
                                         OrderSide side,
                                         OrderType orderType,
                                         BigDecimal price,
                                         int quantity) {
        LinkedHashMap<Integer, Object> body = new LinkedHashMap<>();
        body.put(FixTags.CL_ORD_ID, clOrdId);
        body.put(FixTags.SYMBOL, symbol);
        body.put(FixTags.SIDE, side.toFixValue());
        body.put(FixTags.ORDER_QTY, quantity);
        body.put(FixTags.ORD_TYPE, orderType.toFixValue());
        if (orderType == OrderType.LIMIT && price != null) {
            body.put(FixTags.PRICE, price.toPlainString());
        }
        return render(FixMsgType.NEW_ORDER_SINGLE, senderCompId, targetCompId, seqNum, body);
    }

    // ─────────────────────── Execution Report (35=8) ───────────────────────
    // 실제 FIX 표준에서는 "접수", "거부", "체결"을 별도 MsgType으로 나누지 않고
    // Execution Report(35=8) 하나로 통일하여 ExecType(150)/OrdStatus(39)로 구분한다.

    /** 주문 접수(Accept) 통지. ExecType=New(0), OrdStatus=New(0). */
    public static String executionReportAccepted(String senderCompId, String targetCompId, int seqNum, Order order) {
        LinkedHashMap<Integer, Object> body = new LinkedHashMap<>();
        body.put(FixTags.ORDER_ID, order.getExchangeOrderId());
        body.put(FixTags.CL_ORD_ID, order.getClientOrderId());
        body.put(FixTags.EXEC_ID, nextExecId());
        body.put(FixTags.EXEC_TYPE, FixMsgType.EXEC_TYPE_NEW);
        body.put(FixTags.ORD_STATUS, FixMsgType.ORD_STATUS_NEW);
        body.put(FixTags.SYMBOL, order.getSymbol());
        body.put(FixTags.SIDE, order.getSide().toFixValue());
        body.put(FixTags.ORDER_QTY, order.getOriginalQuantity());
        body.put(FixTags.LEAVES_QTY, order.getLeavesQuantity());
        body.put(FixTags.CUM_QTY, order.getCumulativeQuantity());
        if (order.getPrice() != null) {
            body.put(FixTags.PRICE, order.getPrice().toPlainString());
        }
        return render(FixMsgType.EXECUTION_REPORT, senderCompId, targetCompId, seqNum, body);
    }

    /**
     * 주문 거부(Reject) 통지. ExecType=Rejected(8), OrdStatus=Rejected(8).
     * 검증 실패는 아직 도메인 Order가 정상 생성되지 않았을 수도 있으므로,
     * 원본 New Order Single에 실려온 값들을 그대로 에코(echo)하여 응답한다.
     */
    public static String executionReportRejected(String senderCompId,
                                                  String targetCompId,
                                                  int seqNum,
                                                  String clOrdId,
                                                  String symbol,
                                                  String sideRaw,
                                                  int quantity,
                                                  BigDecimal price,
                                                  String rejectReasonText) {
        LinkedHashMap<Integer, Object> body = new LinkedHashMap<>();
        body.put(FixTags.ORDER_ID, "NONE");
        body.put(FixTags.CL_ORD_ID, clOrdId == null ? "UNKNOWN" : clOrdId);
        body.put(FixTags.EXEC_ID, nextExecId());
        body.put(FixTags.EXEC_TYPE, FixMsgType.EXEC_TYPE_REJECTED);
        body.put(FixTags.ORD_STATUS, FixMsgType.ORD_STATUS_REJECTED);
        if (symbol != null) {
            body.put(FixTags.SYMBOL, symbol);
        }
        if (sideRaw != null) {
            body.put(FixTags.SIDE, sideRaw);
        }
        body.put(FixTags.ORDER_QTY, quantity);
        body.put(FixTags.LEAVES_QTY, 0);
        body.put(FixTags.CUM_QTY, 0);
        if (price != null) {
            body.put(FixTags.PRICE, price.toPlainString());
        }
        body.put(FixTags.TEXT, rejectReasonText);
        return render(FixMsgType.EXECUTION_REPORT, senderCompId, targetCompId, seqNum, body);
    }

    /**
     * 체결(Trade) 통지. ExecType=Trade(F).
     * OrdStatus는 잔량이 남아있으면 PartiallyFilled(1), 전부 소진되면 Filled(2)가 된다
     * (order.getStatus()가 이미 그 상태로 갱신된 뒤 호출된다고 가정).
     */
    public static String executionReportTrade(String senderCompId,
                                               String targetCompId,
                                               int seqNum,
                                               Order order,
                                               int lastQty,
                                               BigDecimal lastPx) {
        LinkedHashMap<Integer, Object> body = new LinkedHashMap<>();
        body.put(FixTags.ORDER_ID, order.getExchangeOrderId());
        body.put(FixTags.CL_ORD_ID, order.getClientOrderId());
        body.put(FixTags.EXEC_ID, nextExecId());
        body.put(FixTags.EXEC_TYPE, FixMsgType.EXEC_TYPE_TRADE);
        body.put(FixTags.ORD_STATUS, order.getStatus().toFixValue());
        body.put(FixTags.SYMBOL, order.getSymbol());
        body.put(FixTags.SIDE, order.getSide().toFixValue());
        body.put(FixTags.ORDER_QTY, order.getOriginalQuantity());
        body.put(FixTags.LAST_QTY, lastQty);
        body.put(FixTags.LAST_PX, lastPx.toPlainString());
        body.put(FixTags.LEAVES_QTY, order.getLeavesQuantity());
        body.put(FixTags.CUM_QTY, order.getCumulativeQuantity());
        if (order.getPrice() != null) {
            body.put(FixTags.PRICE, order.getPrice().toPlainString());
        }
        return render(FixMsgType.EXECUTION_REPORT, senderCompId, targetCompId, seqNum, body);
    }

    /** 취소 완료 통지. ExecType=Canceled(4), OrdStatus=Canceled(4). */
    public static String executionReportCanceled(String senderCompId, String targetCompId, int seqNum, Order order) {
        LinkedHashMap<Integer, Object> body = new LinkedHashMap<>();
        body.put(FixTags.ORDER_ID, order.getExchangeOrderId());
        body.put(FixTags.CL_ORD_ID, order.getClientOrderId());
        body.put(FixTags.EXEC_ID, nextExecId());
        body.put(FixTags.EXEC_TYPE, FixMsgType.EXEC_TYPE_CANCELED);
        body.put(FixTags.ORD_STATUS, FixMsgType.ORD_STATUS_CANCELED);
        body.put(FixTags.SYMBOL, order.getSymbol());
        body.put(FixTags.SIDE, order.getSide().toFixValue());
        body.put(FixTags.ORDER_QTY, order.getOriginalQuantity());
        body.put(FixTags.LEAVES_QTY, 0);
        body.put(FixTags.CUM_QTY, order.getCumulativeQuantity());
        return render(FixMsgType.EXECUTION_REPORT, senderCompId, targetCompId, seqNum, body);
    }

    // ─────────────────── Order Cancel Request / Reject (35=F, 35=9) ───────────────────

    public static String orderCancelRequest(String senderCompId,
                                             String targetCompId,
                                             int seqNum,
                                             String newClOrdId,
                                             String origClOrdId,
                                             String symbol,
                                             OrderSide side) {
        LinkedHashMap<Integer, Object> body = new LinkedHashMap<>();
        body.put(FixTags.CL_ORD_ID, newClOrdId);
        body.put(FixTags.ORIG_CL_ORD_ID, origClOrdId);
        body.put(FixTags.SYMBOL, symbol);
        body.put(FixTags.SIDE, side.toFixValue());
        return render(FixMsgType.ORDER_CANCEL_REQUEST, senderCompId, targetCompId, seqNum, body);
    }

    /** 취소 요청 거부(예: 대상 주문 없음, 이미 체결완료된 주문). */
    public static String orderCancelReject(String senderCompId,
                                            String targetCompId,
                                            int seqNum,
                                            String clOrdId,
                                            String origClOrdId,
                                            String reasonText) {
        LinkedHashMap<Integer, Object> body = new LinkedHashMap<>();
        body.put(FixTags.ORDER_ID, "NONE");
        body.put(FixTags.CL_ORD_ID, clOrdId == null ? "UNKNOWN" : clOrdId);
        body.put(FixTags.ORIG_CL_ORD_ID, origClOrdId == null ? "UNKNOWN" : origClOrdId);
        body.put(FixTags.ORD_STATUS, FixMsgType.ORD_STATUS_REJECTED);
        body.put(FixTags.TEXT, reasonText);
        return render(FixMsgType.ORDER_CANCEL_REJECT, senderCompId, targetCompId, seqNum, body);
    }

    // ───────────────────────── 내부 직렬화 로직 ─────────────────────────

    private static int nextExecId() {
        return EXEC_ID_SEQ.incrementAndGet();
    }

    private static String render(String msgType,
                                  String senderCompId,
                                  String targetCompId,
                                  int seqNum,
                                  Map<Integer, Object> body) {
        // 1) BodyLength(9) 계산 대상 구간: MsgType(35)부터 마지막 body 필드까지.
        StringBuilder afterBodyLength = new StringBuilder();
        appendField(afterBodyLength, FixTags.MSG_TYPE, msgType);
        appendField(afterBodyLength, FixTags.SENDER_COMP_ID, senderCompId);
        appendField(afterBodyLength, FixTags.TARGET_COMP_ID, targetCompId);
        appendField(afterBodyLength, FixTags.MSG_SEQ_NUM, seqNum);
        appendField(afterBodyLength, FixTags.SENDING_TIME, SENDING_TIME_FORMAT.format(Instant.now()));
        for (Map.Entry<Integer, Object> entry : body.entrySet()) {
            appendField(afterBodyLength, entry.getKey(), entry.getValue());
        }

        String bodySection = afterBodyLength.toString();
        int bodyLength = bodySection.getBytes(StandardCharsets.UTF_8).length;

        // 2) 헤더: BeginString(8), BodyLength(9)
        StringBuilder head = new StringBuilder();
        appendField(head, FixTags.BEGIN_STRING, BEGIN_STRING);
        appendField(head, FixTags.BODY_LENGTH, bodyLength);

        String withoutChecksum = head.toString() + bodySection;

        // 3) 체크섬(10): withoutChecksum의 모든 바이트 합을 256으로 나눈 나머지, 3자리 zero-padding
        int checksum = calculateChecksum(withoutChecksum);
        String checksumField = FixTags.CHECK_SUM + "=" + String.format("%03d", checksum) + SOH;

        return withoutChecksum + checksumField;
    }

    private static void appendField(StringBuilder sb, int tag, Object value) {
        sb.append(tag).append('=').append(value).append(SOH);
    }

    private static int calculateChecksum(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        int sum = 0;
        for (byte b : bytes) {
            sum += (b & 0xFF);
        }
        return sum % 256;
    }
}
