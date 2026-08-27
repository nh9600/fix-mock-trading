package com.example.fixmock.fix;

import com.example.fixmock.exception.FixParsingException;

import java.nio.charset.StandardCharsets;

/**
 * TCP Socket으로부터 읽어들인 "원시 바이트(raw wire format)"를 다시 {@link FixMessage}
 * 객체로 역직렬화(parse)하는 클래스. {@link FixMessageBuilder}의 반대 방향 작업이다.
 *
 * 파싱 단계에서 검증하는 것:
 *   1) 태그=값 형식 위반 여부 (예: "55AAPL"처럼 '='가 없는 잘못된 필드)
 *   2) 필수 헤더 태그(8,9,35,49,56,34,52) 존재 여부
 *   3) BodyLength(9) 값이 실제 본문 길이와 일치하는지
 *   4) CheckSum(10) 값이 실제 계산값과 일치하는지
 *
 * 이 네 가지 검증에 실패하면 {@link FixParsingException}을 던지며, 서버는 이를
 * 세션을 끊지 않고 "잘못된 메시지"로 처리해 클라이언트에게 거부 응답을 보낸다.
 *
 * 참고: 실제 FIX 엔진은 BodyLength(9)를 먼저 읽어 그만큼의 바이트를 스트림에서
 * 정확히 읽어낸 뒤 CheckSum(10)까지 확인하는 방식으로 "메시지 경계(framing)"를 찾는다.
 * 본 예제에서는 소켓 레벨의 {@code FixMessageReader}가 "10=" 필드가 등장할 때까지
 * SOH 단위로 읽어들이는 방식으로 프레이밍을 단순화했다 (자세한 설명은 FixMessageReader 참고).
 */
public final class FixMessageParser {

    private FixMessageParser() {
    }

    public static FixMessage parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new FixParsingException("빈 메시지는 파싱할 수 없습니다.");
        }

        String[] tokens = rawMessage.split("");
        FixMessage message = new FixMessage();

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq <= 0) {
                throw new FixParsingException("잘못된 FIX 필드 형식입니다(Tag=Value 아님): '" + token + "'");
            }
            String tagStr = token.substring(0, eq);
            String value = token.substring(eq + 1);
            int tag;
            try {
                tag = Integer.parseInt(tagStr);
            } catch (NumberFormatException e) {
                throw new FixParsingException("잘못된 FIX Tag 번호입니다(숫자가 아님): '" + tagStr + "'");
            }
            message.set(tag, value);
        }

        validateRequiredHeader(message);
        validateBodyLength(rawMessage, message);
        validateChecksum(rawMessage, message);

        return message;
    }

    private static void validateRequiredHeader(FixMessage message) {
        int[] requiredHeaderTags = {
                FixTags.BEGIN_STRING, FixTags.BODY_LENGTH, FixTags.MSG_TYPE,
                FixTags.SENDER_COMP_ID, FixTags.TARGET_COMP_ID,
                FixTags.MSG_SEQ_NUM, FixTags.SENDING_TIME, FixTags.CHECK_SUM
        };
        for (int tag : requiredHeaderTags) {
            if (!message.has(tag)) {
                throw new FixParsingException("필수 헤더 태그(Tag " + tag + ")가 누락되었습니다.");
            }
        }
        if (!"FIX.4.4".equals(message.getString(FixTags.BEGIN_STRING))) {
            throw new FixParsingException("지원하지 않는 BeginString(Tag 8) 입니다: " + message.getString(FixTags.BEGIN_STRING));
        }
    }

    private static void validateBodyLength(String rawMessage, FixMessage message) {
        int declaredBodyLength = message.getRequiredInt(FixTags.BODY_LENGTH);

        // BodyLength는 "9=..." 필드 다음부터 "10=" 필드 시작 전까지의 바이트 길이여야 한다.
        String afterBodyLengthMarker = "" + FixTags.BODY_LENGTH + "=" + declaredBodyLength + "";
        int idx = rawMessage.indexOf(afterBodyLengthMarker);
        // BeginString이 맨 앞에 오므로 marker 앞에 SOH가 없을 수도 있어 대체 탐색을 시도한다.
        String bodySection;
        if (idx >= 0) {
            int bodyStart = idx + afterBodyLengthMarker.length();
            int checksumFieldStart = rawMessage.indexOf("" + FixTags.CHECK_SUM + "=");
            if (checksumFieldStart < 0 || checksumFieldStart < bodyStart) {
                throw new FixParsingException("CheckSum(Tag 10) 필드 위치를 찾을 수 없습니다.");
            }
            bodySection = rawMessage.substring(bodyStart, checksumFieldStart + 1);
        } else {
            throw new FixParsingException("BodyLength(Tag 9) 필드를 메시지에서 찾을 수 없습니다.");
        }

        int actualBodyLength = bodySection.getBytes(StandardCharsets.UTF_8).length;
        if (actualBodyLength != declaredBodyLength) {
            throw new FixParsingException(
                    "BodyLength(Tag 9) 불일치: 선언된 값=" + declaredBodyLength + ", 실제 값=" + actualBodyLength);
        }
    }

    private static void validateChecksum(String rawMessage, FixMessage message) {
        String checksumTagPrefix = "" + FixTags.CHECK_SUM + "=";
        int checksumStart = rawMessage.indexOf(checksumTagPrefix);
        if (checksumStart < 0) {
            throw new FixParsingException("CheckSum(Tag 10) 필드를 찾을 수 없습니다.");
        }
        // CheckSum 필드를 제외한 나머지 전체(BeginString ~ 마지막 body 필드 + trailing SOH)에 대해 계산한다.
        String dataForChecksum = rawMessage.substring(0, checksumStart + 1);
        int expected = calculateChecksum(dataForChecksum);

        String declared = message.getRequiredString(FixTags.CHECK_SUM);
        int declaredChecksum;
        try {
            declaredChecksum = Integer.parseInt(declared);
        } catch (NumberFormatException e) {
            throw new FixParsingException("CheckSum(Tag 10) 값이 숫자가 아닙니다: '" + declared + "'");
        }

        if (expected != declaredChecksum) {
            throw new FixParsingException(
                    "CheckSum(Tag 10) 불일치: 선언된 값=" + declaredChecksum + ", 실제 계산값=" + expected
                            + " (메시지가 전송 중 손상되었을 가능성이 있습니다)");
        }
    }

    private static int calculateChecksum(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        int sum = 0;
        for (byte b : bytes) {
            sum += (b & 0xFF);
        }
        return sum % 256;
    }

    /**
     * New Order Single(35=D) 메시지에 필요한 필수 바디 태그를 검증한다.
     * LIMIT 주문(OrdType=2)인 경우 Price(44)도 필수가 된다.
     */
    public static void requireNewOrderSingleFields(FixMessage message) {
        int[] required = {FixTags.CL_ORD_ID, FixTags.SYMBOL, FixTags.SIDE, FixTags.ORDER_QTY, FixTags.ORD_TYPE};
        for (int tag : required) {
            message.getRequiredString(tag);
        }
        if ("2".equals(message.getString(FixTags.ORD_TYPE)) && !message.has(FixTags.PRICE)) {
            throw new FixParsingException("지정가(LIMIT) 주문에는 Price(Tag 44)가 반드시 필요합니다.");
        }
    }

    /** Order Cancel Request(35=F) 메시지에 필요한 필수 바디 태그를 검증한다. */
    public static void requireOrderCancelRequestFields(FixMessage message) {
        int[] required = {FixTags.CL_ORD_ID, FixTags.ORIG_CL_ORD_ID, FixTags.SYMBOL, FixTags.SIDE};
        for (int tag : required) {
            message.getRequiredString(tag);
        }
    }
}
