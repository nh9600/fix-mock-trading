package com.example.fixmock.fix;

/**
 * 이 예제에서 사용하는 FIX 태그(Tag) 번호 상수 모음.
 *
 * FIX 메시지는 "TagNum=Value" 쌍을 구분자(SOH, 0x01)로 이어붙인 텍스트 프로토콜이다.
 * 실제로는 수백 개의 태그가 정의되어 있지만(FIX 4.2/4.4 Dictionary 기준),
 * 여기서는 학습에 필요한 최소 집합만 정의한다.
 *
 * ── Header (표준 헤더) ──────────────────────────────
 * BEGIN_STRING(8)   : FIX 버전. 예) FIX.4.4
 * BODY_LENGTH(9)    : BeginString/BodyLength/CheckSum을 제외한 본문 바이트 길이
 * MSG_TYPE(35)      : 메시지 종류 (D=NewOrderSingle, 8=ExecutionReport, F=OrderCancelRequest, 9=OrderCancelReject ...)
 * SENDER_COMP_ID(49): 보내는 쪽 식별자 (여기서는 클라이언트 ID로 사용)
 * TARGET_COMP_ID(56): 받는 쪽 식별자 (여기서는 Mock 거래소 ID로 사용)
 * MSG_SEQ_NUM(34)   : 메시지 일련번호(세션 내 순서 보장을 위함, 본 예제에서는 검증하지 않고 기록만 함)
 * SENDING_TIME(52)  : 송신 시각(UTC)
 *
 * ── Body (New Order Single, Execution Report 공통) ─────
 * CL_ORD_ID(11)   : 클라이언트가 채번한 주문 ID
 * ORDER_ID(37)    : 거래소(서버)가 채번한 주문 ID
 * ORIG_CL_ORD_ID(41): 정정/취소 요청 시, 대상이 되는 원본 주문의 ClOrdID
 * EXEC_ID(17)     : 체결/이벤트 단위로 부여되는 고유 ID
 * EXEC_TYPE(150)  : 이번 Execution Report가 어떤 이벤트를 나타내는지 (0=New,4=Canceled,8=Rejected,F=Trade)
 * ORD_STATUS(39)  : 주문의 "현재" 누적 상태 (0=New,1=PartiallyFilled,2=Filled,4=Canceled,8=Rejected)
 * SYMBOL(55)      : 종목 코드
 * SIDE(54)        : 매수/매도 (1=Buy, 2=Sell)
 * ORDER_QTY(38)   : 주문 수량
 * ORD_TYPE(40)    : 주문 유형 (1=Market, 2=Limit)
 * PRICE(44)       : 지정가
 * LAST_QTY(32)    : 이번 체결에서 체결된 수량
 * LAST_PX(31)     : 이번 체결에서 체결된 가격
 * LEAVES_QTY(151) : 미체결 잔량
 * CUM_QTY(14)     : 누적 체결 수량
 * TEXT(58)        : 자유 텍스트(거부 사유 등 설명)
 *
 * ── Trailer ─────────────────────────────────────
 * CHECK_SUM(10)   : 메시지 전체 바이트 합을 256으로 나눈 나머지(3자리 zero-padded)
 */
public final class FixTags {

    private FixTags() {
    }

    // Header
    public static final int BEGIN_STRING = 8;
    public static final int BODY_LENGTH = 9;
    public static final int MSG_TYPE = 35;
    public static final int SENDER_COMP_ID = 49;
    public static final int TARGET_COMP_ID = 56;
    public static final int MSG_SEQ_NUM = 34;
    public static final int SENDING_TIME = 52;

    // Body
    public static final int CL_ORD_ID = 11;
    public static final int ORDER_ID = 37;
    public static final int ORIG_CL_ORD_ID = 41;
    public static final int EXEC_ID = 17;
    public static final int EXEC_TYPE = 150;
    public static final int ORD_STATUS = 39;
    public static final int SYMBOL = 55;
    public static final int SIDE = 54;
    public static final int ORDER_QTY = 38;
    public static final int ORD_TYPE = 40;
    public static final int PRICE = 44;
    public static final int LAST_QTY = 32;
    public static final int LAST_PX = 31;
    public static final int LEAVES_QTY = 151;
    public static final int CUM_QTY = 14;
    public static final int TEXT = 58;

    // Trailer
    public static final int CHECK_SUM = 10;
}
