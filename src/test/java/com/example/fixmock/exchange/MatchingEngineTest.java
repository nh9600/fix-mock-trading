package com.example.fixmock.exchange;

import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderStatus;
import com.example.fixmock.domain.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** [매수/매도 체결] 요구사항에 대응하는 매칭 엔진 단위 테스트. 가격/시간 우선 원칙을 검증한다. */
class MatchingEngineTest {

    @Test
    void 가격이_같은_매수_매도_주문은_전량_체결된다() {
        MatchingEngine engine = new MatchingEngine();

        Order buy = order("B1", "C1", OrderSide.BUY, "150.00", 100);
        engine.submit(buy);

        Order sell = order("S1", "C2", OrderSide.SELL, "150.00", 100);
        List<Trade> trades = engine.submit(sell);

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getQuantity()).isEqualTo(100);
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("150.00");
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void 수량이_다르면_부분체결_후_잔량이_호가창에_남는다() {
        MatchingEngine engine = new MatchingEngine();

        Order buy = order("B2", "C1", OrderSide.BUY, "150.00", 100);
        engine.submit(buy);

        Order sell = order("S2", "C2", OrderSide.SELL, "150.00", 60);
        List<Trade> trades = engine.submit(sell);

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getQuantity()).isEqualTo(60);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(buy.getLeavesQuantity()).isEqualTo(40);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.FILLED);

        // 남은 매수 잔량 40에 대해 추가 매도 주문이 들어오면 이어서 체결되어야 한다 (호가창에 잘 남아있는지 확인)
        Order sell2 = order("S3", "C2", OrderSide.SELL, "150.00", 40);
        List<Trade> trades2 = engine.submit(sell2);
        assertThat(trades2).hasSize(1);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void 가격이_교차하지_않으면_체결되지_않고_각자_호가창에_대기한다() {
        MatchingEngine engine = new MatchingEngine();

        Order buy = order("B3", "C1", OrderSide.BUY, "149.00", 100); // 149에 사고 싶다
        engine.submit(buy);

        Order sell = order("S4", "C2", OrderSide.SELL, "150.00", 100); // 150에 팔고 싶다 -> 교차 안 함
        List<Trade> trades = engine.submit(sell);

        assertThat(trades).isEmpty();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void 같은_가격이면_먼저_들어온_주문이_시간우선으로_체결된다() {
        MatchingEngine engine = new MatchingEngine();

        Order earlyBuy = order("B4", "C1", OrderSide.BUY, "150.00", 50);
        engine.submit(earlyBuy);
        Order laterBuy = order("B5", "C1", OrderSide.BUY, "150.00", 50);
        engine.submit(laterBuy);

        Order sell = order("S5", "C2", OrderSide.SELL, "150.00", 50);
        List<Trade> trades = engine.submit(sell);

        assertThat(trades.get(0).getBuyOrder()).isSameAs(earlyBuy);
        assertThat(earlyBuy.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(laterBuy.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void 더_높은_매수호가가_더_낮은_매수호가보다_먼저_체결된다_가격우선() {
        MatchingEngine engine = new MatchingEngine();

        Order lowBuy = order("B6", "C1", OrderSide.BUY, "150.00", 50);
        engine.submit(lowBuy);
        Order highBuy = order("B7", "C1", OrderSide.BUY, "151.00", 50); // 더 늦게 들어왔지만 가격이 더 좋다
        engine.submit(highBuy);

        Order sell = order("S6", "C2", OrderSide.SELL, "150.00", 50);
        List<Trade> trades = engine.submit(sell);

        assertThat(trades.get(0).getBuyOrder()).isSameAs(highBuy);
        assertThat(trades.get(0).getPrice()).isEqualByComparingTo("151.00");
    }

    private Order order(String clOrdId, String clientId, OrderSide side, String price, int qty) {
        Order o = new Order(clOrdId, clientId, "AAPL", side, OrderType.LIMIT, new BigDecimal(price), qty);
        o.accept(Order.nextExchangeOrderId());
        return o;
    }
}
