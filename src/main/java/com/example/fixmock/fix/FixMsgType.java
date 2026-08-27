package com.example.fixmock.fix;

/**
 * FIX 태그 35(MsgType) 값 및 관련 하위 코드(ExecType, OrdStatus에 쓰이는 문자) 상수.
 *
 * 실제 FIX 표준에서는 "주문 접수", "주문 거부", "체결"을 서로 다른 MsgType으로
 * 나누지 않고, 전부 Execution Report(35=8) 하나로 표현하며 그 안의
 * ExecType(150), OrdStatus(39) 값으로 구분한다. 이 예제도 실제 FIX 관례를 그대로 따른다.
 *
 * 즉:
 *   신규 주문       -> 35=D (New Order Single)
 *   주문 접수/거부/체결 -> 35=8 (Execution Report), ExecType/OrdStatus로 구분
 *   주문 취소 요청    -> 35=F (Order Cancel Request)
 *   주문 취소 거부    -> 35=9 (Order Cancel Reject)
 */
public final class FixMsgType {

    private FixMsgType() {
    }

    /** New Order Single: 신규 주문 접수 요청 (클라이언트 -> 서버) */
    public static final String NEW_ORDER_SINGLE = "D";

    /** Execution Report: 접수/거부/체결 통지 (서버 -> 클라이언트) */
    public static final String EXECUTION_REPORT = "8";

    /** Order Cancel Request: 주문 취소 요청 (클라이언트 -> 서버) */
    public static final String ORDER_CANCEL_REQUEST = "F";

    /** Order Cancel Reject: 취소 요청 거부 (서버 -> 클라이언트) */
    public static final String ORDER_CANCEL_REJECT = "9";

    // ── ExecType(150) 값 ─────────────────────
    public static final String EXEC_TYPE_NEW = "0";
    public static final String EXEC_TYPE_CANCELED = "4";
    public static final String EXEC_TYPE_REJECTED = "8";
    public static final String EXEC_TYPE_TRADE = "F";

    // ── OrdStatus(39) 값 ─────────────────────
    public static final String ORD_STATUS_NEW = "0";
    public static final String ORD_STATUS_PARTIALLY_FILLED = "1";
    public static final String ORD_STATUS_FILLED = "2";
    public static final String ORD_STATUS_CANCELED = "4";
    public static final String ORD_STATUS_REJECTED = "8";
}
