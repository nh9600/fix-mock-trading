-- FIX Mock Trading System - MySQL 스키마
-- DBeaver에서 dev 데이터베이스를 선택한 상태로 이 스크립트를 실행하세요.

USE dev;

-- 주문 테이블: Order 도메인 객체를 그대로 옮긴 구조
CREATE TABLE IF NOT EXISTS orders (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_order_id       VARCHAR(64)   NOT NULL COMMENT '클라이언트가 부여한 주문 ID (ClOrdID, Tag 11)',
    exchange_order_id     VARCHAR(64)   NULL     COMMENT '거래소가 접수 시 부여하는 ID (OrderID, Tag 37)',
    sender_comp_id        VARCHAR(64)   NOT NULL COMMENT '주문을 보낸 클라이언트 식별자 (SenderCompID, Tag 49)',
    symbol                VARCHAR(16)   NOT NULL COMMENT '종목 코드 (Symbol, Tag 55)',
    side                  VARCHAR(8)    NOT NULL COMMENT '매수/매도 (BUY/SELL)',
    order_type            VARCHAR(8)    NOT NULL COMMENT '지정가/시장가 (LIMIT/MARKET)',
    price                 DECIMAL(18,4) NULL     COMMENT '지정가 (시장가 주문은 NULL)',
    original_quantity     INT           NOT NULL COMMENT '최초 주문 수량',
    leaves_quantity       INT           NOT NULL COMMENT '아직 체결되지 않고 남은 수량',
    cumulative_quantity   INT           NOT NULL DEFAULT 0 COMMENT '누적 체결 수량',
    status                VARCHAR(20)   NOT NULL COMMENT 'NEW/ACCEPTED/PARTIALLY_FILLED/FILLED/REJECTED/CANCELLED',
    reject_reason         VARCHAR(255)  NULL     COMMENT '거부 사유 (REJECTED 상태일 때만)',
    created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '주문 생성 시각',
    updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '최종 갱신 시각',
    UNIQUE KEY uk_orders_client_order_id (client_order_id),
    KEY idx_orders_sender_comp_id (sender_comp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='클라이언트가 낸 주문 상태';

-- FIX 메시지 로그 테이블: 대시보드의 로그 패널이 보여주는 데이터
CREATE TABLE IF NOT EXISTS fix_message_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id     VARCHAR(64)  NOT NULL COMMENT '메시지를 주고받은 클라이언트 식별자',
    direction     VARCHAR(10)  NOT NULL COMMENT 'SENT(클라이언트→서버) 또는 RECEIVED(서버→클라이언트)',
    msg_type      VARCHAR(4)   NOT NULL COMMENT 'FIX MsgType(Tag 35) 값 (D/F/8/9 등)',
    raw_message   TEXT         NOT NULL COMMENT '사람이 읽기 좋게 변환된 FIX 메시지 (Tag=Value | Tag=Value ...)',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '메시지 기록 시각',
    KEY idx_fix_log_client_id_created_at (client_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='클라이언트와 서버가 주고받은 FIX 메시지 로그';
