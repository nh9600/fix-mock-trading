package com.example.fixmock.fix;

import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderType;
import com.example.fixmock.exception.FixParsingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** [FIX 메시지 파싱] 및 [잘못된 FIX 메시지 처리] 요구사항에 대응하는 테스트. */
class FixMessageParserTest {

    private String validNewOrderSingle() {
        return FixMessageBuilder.newOrderSingle("CLIENT1", "MOCK-EXCHANGE", 1, "CL-1",
                "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100);
    }

    @Test
    void 정상_메시지는_모든_필드가_올바르게_파싱된다() {
        FixMessage parsed = FixMessageParser.parse(validNewOrderSingle());

        assertThat(parsed.getMsgType()).isEqualTo(FixMsgType.NEW_ORDER_SINGLE);
        assertThat(parsed.getString(FixTags.CL_ORD_ID)).isEqualTo("CL-1");
        assertThat(parsed.getString(FixTags.SYMBOL)).isEqualTo("AAPL");
        assertThat(parsed.getString(FixTags.SIDE)).isEqualTo("1");
        assertThat(parsed.getRequiredInt(FixTags.ORDER_QTY)).isEqualTo(100);
        assertThat(parsed.getString(FixTags.ORD_TYPE)).isEqualTo("2");
        assertThat(parsed.getBigDecimal(FixTags.PRICE)).isEqualByComparingTo("150.00");
    }

    @Test
    void 체크섬이_조작된_메시지는_예외가_발생한다() {
        String tampered = validNewOrderSingle().replace("38=100", "38=999");

        assertThatThrownBy(() -> FixMessageParser.parse(tampered))
                .isInstanceOf(FixParsingException.class)
                .hasMessageContaining("CheckSum");
    }

    @Test
    void Tag_Value_형식이_아닌_필드는_예외가_발생한다() {
        assertThatThrownBy(() -> FixMessageParser.parse("this-is-not-a-fix-message"))
                .isInstanceOf(FixParsingException.class);
    }

    @Test
    void 빈_메시지는_예외가_발생한다() {
        assertThatThrownBy(() -> FixMessageParser.parse(""))
                .isInstanceOf(FixParsingException.class);
        assertThatThrownBy(() -> FixMessageParser.parse(null))
                .isInstanceOf(FixParsingException.class);
    }

    @Test
    void LIMIT_주문인데_Price_태그가_없으면_필수필드_검증에서_예외가_발생한다() {
        // 원시 문자열을 조작하면 체크섬이 깨지므로, 이미 파싱이 끝난 FixMessage 객체를
        // 직접 조립해서 "문법은 맞지만 LIMIT인데 Price가 없는" 상황만 순수하게 재현한다.
        FixMessage messageMissingPrice = new FixMessage()
                .set(FixTags.CL_ORD_ID, "CL-1")
                .set(FixTags.SYMBOL, "AAPL")
                .set(FixTags.SIDE, "1")
                .set(FixTags.ORDER_QTY, 100)
                .set(FixTags.ORD_TYPE, "2"); // LIMIT, Price(44) 없음

        assertThatThrownBy(() -> FixMessageParser.requireNewOrderSingleFields(messageMissingPrice))
                .isInstanceOf(FixParsingException.class)
                .hasMessageContaining("Price");
    }
}
