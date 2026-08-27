package com.example.fixmock.domain;

/**
 * 주문 유형.
 *
 * FIX 태그 40(OrdType)에 대응된다.
 *  - 1 = Market (시장가)
 *  - 2 = Limit  (지정가)
 *
 * 실제 FIX 표준에는 Stop(3), Stop Limit(4) 등도 있으나 학습용 예제이므로 생략한다.
 */
public enum OrderType {
    MARKET("1"),
    LIMIT("2");

    private final String fixValue;

    OrderType(String fixValue) {
        this.fixValue = fixValue;
    }

    public String toFixValue() {
        return fixValue;
    }

    public static OrderType fromFixValue(String value) {
        for (OrderType type : values()) {
            if (type.fixValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("알 수 없는 OrdType(Tag 40) 값입니다: " + value);
    }
}
