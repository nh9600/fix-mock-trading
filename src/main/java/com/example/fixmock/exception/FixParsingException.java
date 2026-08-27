package com.example.fixmock.exception;

/**
 * 원시(raw) FIX 메시지 문자열을 파싱하는 도중 규격을 위반한 내용을 발견했을 때 던진다.
 * 예) 체크섬 불일치, 필수 태그 누락, 태그=값 형식 위반, 알 수 없는 MsgType 등.
 *
 * 실제 FIX 엔진에서는 이런 상황을 세션 레벨 Reject(35=3) 또는 연결 종료로 처리하지만,
 * 본 예제에서는 학습 목적상 예외 -> 서버측 Business Reject 메시지로 단순화해서 응답한다.
 */
public class FixParsingException extends RuntimeException {

    public FixParsingException(String message) {
        super(message);
    }

    public FixParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
