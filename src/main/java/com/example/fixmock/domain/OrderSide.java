package com.example.fixmock.domain;

/**
 * 주문 방향(매수/매도).
 *
 * FIX 태그 54(Side)에 대응된다.
 *  - 1 = Buy  (매수)
 *  - 2 = Sell (매도)
 *
 * 실제 FIX 표준에는 3(Buy minus), 4(Sell short) 등 다양한 값이 더 존재하지만,
 * 학습용 예제이므로 매수/매도 두 가지만 단순화하여 다룬다.
 */
public enum OrderSide {
    BUY("1"),
    SELL("2");

    private final String fixValue;

    OrderSide(String fixValue) {
        this.fixValue = fixValue;
    }

    /** FIX Tag 54(Side)에 실릴 원본 값. */
    public String toFixValue() {
        return fixValue;
    }

    public static OrderSide fromFixValue(String value) {
        for (OrderSide side : values()) {
            if (side.fixValue.equals(value)) {
                return side;
            }
        }
        throw new IllegalArgumentException("알 수 없는 Side(Tag 54) 값입니다: " + value);
    }

    /** 매칭 시 반대편 진영을 구할 때 사용. */
    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
