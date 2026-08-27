package com.example.fixmock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 학습용 Mock 주식 매매 시스템 진입점.
 *
 * 이 애플리케이션을 실행하면 한 프로세스 안에서:
 *   1) Mock 거래소 TCP FIX 서버 (기본 포트 9878) 가 뜨고,
 *   2) 클라이언트 역할을 하는 REST API (기본 포트 8080) 가 함께 뜬다.
 *
 * REST API로 주문을 넣으면, 그 요청은 내부적으로 FIX 메시지로 변환되어
 * "진짜 TCP 소켓"을 통해 9878 포트의 Mock 거래소로 전송된다.
 */
@SpringBootApplication
public class FixMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(FixMockApplication.class, args);
    }
}
