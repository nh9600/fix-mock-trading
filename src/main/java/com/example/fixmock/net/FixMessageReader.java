package com.example.fixmock.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * TCP Socket의 {@link InputStream}에서 "FIX 메시지 한 건"의 경계를 찾아 원시 문자열로
 * 읽어들이는 유틸리티. 이 클래스가 바로 "Socket은 바이트 스트림일 뿐, 메시지의 경계를
 * 아는 것은 프로토콜(FIX)의 몫이다"라는 사실을 가장 잘 보여주는 코드다.
 *
 * TCP는 스트림(stream) 기반이라 "메시지 단위"라는 개념이 없다. 한 번의 write()가
 * 여러 번의 read()로 쪼개져 도착할 수도 있고, 반대로 여러 번의 write()가 한 번의
 * read()로 뭉쳐서 도착할 수도 있다. 따라서 애플리케이션이 직접 "여기까지가 메시지 1건"
 * 이라는 경계를 판단해야 하며, FIX는 이를 위해 CheckSum(태그 10) 필드로 메시지를 끝맺는다.
 *
 * 실제 FIX 엔진은 BodyLength(9)를 먼저 읽어 정확히 그 바이트 수만큼 스트림에서
 * 읽어낸 뒤 CheckSum(10)까지 소비하는 방식으로 프레이밍한다. 이 예제는 학습 목적상
 * "SOH로 구분된 필드를 하나씩 읽다가 태그 10(CheckSum) 필드를 만나면 메시지 종료"
 * 라는 더 단순한 규칙으로 프레이밍을 구현했다 (BodyLength 값 자체는 파서 단계에서
 * 별도로 검증한다).
 *
 * 인코딩 관련 주의: 필드 값은 바이트 단위로 모았다가 SOH를 만난 시점에 "한 번에" UTF-8로
 * 디코딩한다(바이트를 하나씩 char로 캐스팅하지 않는다). Reject 사유 등 TEXT(58) 태그에
 * 한글처럼 멀티바이트 문자가 들어갈 수 있기 때문이다. SOH(0x01)는 UTF-8에서 항상 완전한
 * 1바이트 문자로만 나타나고 멀티바이트 시퀀스의 일부로 등장할 수 없으므로, 바이트 단위로
 * SOH를 찾아 경계를 나누는 방식 자체는 UTF-8 페이로드에도 안전하다.
 */
public final class FixMessageReader {

    /** FIX 필드 구분자 SOH(0x01). */
    private static final int SOH = 0x01;

    private FixMessageReader() {
    }

    /**
     * 스트림에서 한 건의 FIX 메시지를 읽는다.
     * 스트림이 끝(EOF)에 도달해 더 이상 읽을 데이터가 없으면 {@code null}을 반환한다
     * (클라이언트가 연결을 정상적으로 종료한 경우).
     */
    public static String readOneMessage(InputStream in) throws IOException {
        StringBuilder buffer = new StringBuilder();
        ByteArrayOutputStream currentField = new ByteArrayOutputStream();
        boolean sawAnyByte = false;

        int b;
        while ((b = in.read()) != -1) {
            sawAnyByte = true;
            if (b == SOH) {
                currentField.write(SOH);
                String field = currentField.toString(StandardCharsets.UTF_8);
                buffer.append(field);
                currentField.reset();

                // "10=" 로 시작하는 필드(CheckSum)를 만나면 메시지 한 건이 끝난 것으로 간주한다.
                if (field.startsWith("10=")) {
                    return buffer.toString();
                }
            } else {
                currentField.write(b);
            }
        }

        if (!sawAnyByte) {
            // 아예 아무것도 못 읽고 스트림이 끝났다 -> 클라이언트가 연결을 종료함.
            return null;
        }
        // 데이터는 있었지만 CheckSum 필드로 끝맺지 못하고 스트림이 끊긴 경우
        // (예: 잘못된 형식의 메시지, 혹은 전송 도중 연결 종료).
        throw new IOException("메시지가 CheckSum(Tag 10) 필드 없이 스트림이 종료되었습니다. 남은 데이터: '"
                + buffer + currentField.toString(StandardCharsets.UTF_8) + "'");
    }
}
