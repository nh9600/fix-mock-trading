package com.example.fixmock.fix;

import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** [FIX 메시지 생성] 요구사항에 대응하는 테스트. 문제에서 제시된 예시 메시지 형태를 그대로 검증한다. */
class FixMessageBuilderTest {

    @Test
    void New_Order_Single_생성시_문제에_제시된_예시_태그를_모두_포함한다() {
        String raw = FixMessageBuilder.newOrderSingle(
                "CLIENT1", "MOCK-EXCHANGE", 1, "CL-1001",
                "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100);

        assertThat(raw).contains("35=D");   // MsgType: New Order Single
        assertThat(raw).contains("55=AAPL"); // Symbol
        assertThat(raw).contains("54=1");    // Side: Buy
        assertThat(raw).contains("38=100");  // OrderQty
        assertThat(raw).contains("40=2");    // OrdType: Limit
        assertThat(raw).contains("44=150.00"); // Price
        assertThat(raw).contains("8=FIX.4.4"); // BeginString
        assertThat(raw).contains("9=");        // BodyLength
        assertThat(raw).contains("10=");       // CheckSum
    }

    @Test
    void 생성된_메시지는_스스로_다시_파싱될_수_있다_라운드트립() {
        String raw = FixMessageBuilder.newOrderSingle(
                "CLIENT1", "MOCK-EXCHANGE", 7, "CL-2002",
                "TSLA", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("245.75"), 30);

        FixMessage parsed = FixMessageParser.parse(raw); // 예외 없이 파싱되면 체크섬/바디렌스가 올바르다는 뜻
        assertThat(parsed.getString(FixTags.SYMBOL)).isEqualTo("TSLA");
        assertThat(parsed.getRequiredInt(FixTags.ORDER_QTY)).isEqualTo(30);
    }

    @Test
    void Execution_Report_Accepted_생성시_ExecType과_OrdStatus가_New이다() {
        Order order = new Order("CL-3003", "CLIENT1", "AAPL", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("150.00"), 100);
        order.accept("EX-1");

        String raw = FixMessageBuilder.executionReportAccepted("MOCK-EXCHANGE", "CLIENT1", 1, order);
        FixMessage parsed = FixMessageParser.parse(raw);

        assertThat(parsed.getMsgType()).isEqualTo(FixMsgType.EXECUTION_REPORT);
        assertThat(parsed.getString(FixTags.EXEC_TYPE)).isEqualTo(FixMsgType.EXEC_TYPE_NEW);
        assertThat(parsed.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_NEW);
        assertThat(parsed.getString(FixTags.ORDER_ID)).isEqualTo("EX-1");
    }

    @Test
    void Execution_Report_Rejected_생성시_거부사유가_Text에_담긴다() {
        String raw = FixMessageBuilder.executionReportRejected(
                "MOCK-EXCHANGE", "CLIENT1", 1, "CL-4004", "NOSUCH", "1", 10, null, "존재하지 않는 종목입니다: NOSUCH");
        FixMessage parsed = FixMessageParser.parse(raw);

        assertThat(parsed.getString(FixTags.EXEC_TYPE)).isEqualTo(FixMsgType.EXEC_TYPE_REJECTED);
        assertThat(parsed.getString(FixTags.ORD_STATUS)).isEqualTo(FixMsgType.ORD_STATUS_REJECTED);
        assertThat(parsed.getString(FixTags.TEXT)).contains("존재하지 않는 종목");
    }
}
