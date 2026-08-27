package com.example.fixmock.net;

import com.example.fixmock.fix.FixMessage;

/**
 * 서버가 클라이언트로부터 FIX 메시지를 수신했을 때 호출되는 콜백.
 *
 * net 패키지(Socket/전송 계층)는 이 인터페이스를 통해서만 상위(비즈니스) 계층과
 * 소통한다. 즉, "Socket으로 바이트를 주고받는 방법"과 "받은 주문을 어떻게 처리할지"를
 * 완전히 분리하기 위한 경계선(boundary)이 바로 이 인터페이스다.
 */
public interface FixMessageListener {

    /**
     * @param connection 메시지를 보낸 클라이언트와의 연결(응답을 보낼 때 사용)
     * @param rawMessage 파싱 이전의 원시 FIX 문자열 (파싱 실패 시 로깅/디버깅 용도로도 필요)
     */
    void onMessageReceived(ClientHandler connection, String rawMessage);

    /** 클라이언트 연결이 종료되었을 때 호출된다 (정상 종료/비정상 종료 모두). */
    default void onConnectionClosed(ClientHandler connection) {
    }
}
