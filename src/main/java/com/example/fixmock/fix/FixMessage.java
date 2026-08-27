package com.example.fixmock.fix;

import com.example.fixmock.exception.FixParsingException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 파싱되어 메모리에 올라온 FIX 메시지를 표현하는 객체.
 *
 * 내부적으로는 단순히 {@code Tag(Integer) -> Value(String)} 맵일 뿐이다.
 * FIX 메시지는 원래 "35=D55=AAPL54=1..." 같은 순수 텍스트이므로,
 * 이 클래스가 바로 "텍스트 프로토콜 위의 파싱 결과를 담는 그릇" 역할을 한다.
 *
 * Socket이 주고받는 것은 바이트 스트림이고, FixMessage는 그 바이트를
 * 애플리케이션이 다루기 쉬운 형태로 변환한 결과물이라는 점이 이 클래스의 핵심 의도다.
 */
public class FixMessage {

    // 태그가 입력된 순서를 보존해야 사람이 읽기에도, 재직렬화 시에도 자연스럽다.
    private final Map<Integer, String> fields = new LinkedHashMap<>();

    public FixMessage set(int tag, Object value) {
        fields.put(tag, String.valueOf(value));
        return this;
    }

    public boolean has(int tag) {
        return fields.containsKey(tag);
    }

    public String getString(int tag) {
        return fields.get(tag);
    }

    public String getRequiredString(int tag) {
        String value = fields.get(tag);
        if (value == null || value.isBlank()) {
            throw new FixParsingException("필수 태그(Tag " + tag + ")가 누락되었습니다.");
        }
        return value;
    }

    public int getRequiredInt(int tag) {
        String raw = getRequiredString(tag);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new FixParsingException("Tag " + tag + " 값이 정수 형식이 아닙니다: '" + raw + "'");
        }
    }

    public BigDecimal getBigDecimal(int tag) {
        String raw = fields.get(tag);
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new FixParsingException("Tag " + tag + " 값이 숫자 형식이 아닙니다: '" + raw + "'");
        }
    }

    public String getMsgType() {
        return getRequiredString(FixTags.MSG_TYPE);
    }

    public Map<Integer, String> asMap() {
        return fields;
    }

    /**
     * 화면/로그 표시용으로 태그=값 목록을 사람이 읽기 좋은 형태로 재구성한다.
     * 실제 와이어 포맷의 구분자는 눈에 보이지 않는 SOH(0x01)이므로, 여기서는 " | "로 대체한다.
     * 태그가 입력된 순서(LinkedHashMap)를 그대로 보존하므로 원본 메시지와 필드 순서가 같다.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            if (!first) {
                sb.append(" | ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return fields.toString();
    }
}
