package com.example.fixmock.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * {@link com.example.fixmock.client.FixMessageLogStore}에 쌓이는 SENT/RECEIVED FIX 메시지를
 * MySQL {@code fix_message_log} 테이블에 영구 저장하기 위한 JPA 엔티티.
 *
 * 대시보드의 실시간 로그 패널은 여전히 메모리 저장소(FixMessageLogStore)를 읽어서 빠르게
 * 갱신되고, 이 테이블은 재시작 후에도 남아야 하는 이력(감사 로그) 목적으로 별도 저장된다.
 */
@Entity
@Table(name = "fix_message_log")
public class FixMessageLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "msg_type", nullable = false, length = 4)
    private String msgType;

    @Lob
    @Column(name = "raw_message", nullable = false)
    private String rawMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FixMessageLogEntity() {
        // JPA 기본 생성자
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
