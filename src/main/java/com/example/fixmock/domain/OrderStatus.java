package com.example.fixmock.domain;

/**
 * 주문 상태(Order State Machine).
 *
 * FIX 태그 39(OrdStatus)에 대응된다. 실제 FIX 4.2/4.4 표준에는 더 많은 상태
 * (Pending New, Suspended, Calculated, DoneForDay 등)가 있지만, 학습 목적상
 * 실무에서 가장 핵심적으로 쓰이는 상태만 남겼다.
 *
 * 상태 전이:
 *   NEW --(검증 통과)--> ACCEPTED --(부분체결)--> PARTIALLY_FILLED --(완전체결)--> FILLED
 *   NEW --(검증 실패)--> REJECTED
 *   ACCEPTED / PARTIALLY_FILLED --(취소 요청 승인)--> CANCELLED
 */
public enum OrderStatus {

    /** 클라이언트가 주문을 생성했지만 아직 거래소(서버)에 전송/접수되지 않은 상태. */
    NEW("A"),

    /** 서버가 유효성 검증을 통과시키고 주문을 접수한 상태(FIX OrdStatus=0/New 에 해당). */
    ACCEPTED("0"),

    /** 서버가 유효성 검증에 실패하여 주문을 거부한 상태(FIX OrdStatus=8/Rejected). */
    REJECTED("8"),

    /** 주문 수량 중 일부만 체결된 상태(FIX OrdStatus=1/Partially filled). */
    PARTIALLY_FILLED("1"),

    /** 주문 수량 전체가 체결된 상태(FIX OrdStatus=2/Filled). */
    FILLED("2"),

    /** 주문이 취소된 상태(FIX OrdStatus=4/Canceled). */
    CANCELLED("4");

    private final String fixValue;

    OrderStatus(String fixValue) {
        this.fixValue = fixValue;
    }

    /** FIX Tag 39(OrdStatus)에 실릴 값. */
    public String toFixValue() {
        return fixValue;
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }

    /** 클라이언트 측에서 서버가 보낸 OrdStatus(Tag 39) 값을 다시 enum으로 되돌릴 때 사용한다. */
    public static OrderStatus fromFixValue(String value) {
        for (OrderStatus status : values()) {
            if (status.fixValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("알 수 없는 OrdStatus(Tag 39) 값입니다: " + value);
    }
}
