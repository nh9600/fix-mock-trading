package com.example.fixmock.exchange;

import java.util.Set;

/**
 * Mock 거래소가 거래를 지원하는 종목 목록.
 * 실제로는 상장 종목 마스터 DB를 조회하겠지만, 학습용 예제이므로 고정된 집합으로 대체한다.
 */
public final class SymbolMaster {

    private static final Set<String> TRADABLE_SYMBOLS = Set.of("AAPL", "GOOG", "MSFT", "AMZN", "TSLA");

    private SymbolMaster() {
    }

    public static boolean isTradable(String symbol) {
        return symbol != null && TRADABLE_SYMBOLS.contains(symbol.toUpperCase());
    }

    public static Set<String> all() {
        return TRADABLE_SYMBOLS;
    }
}
