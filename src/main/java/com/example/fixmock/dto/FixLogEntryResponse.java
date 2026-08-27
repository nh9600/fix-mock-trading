package com.example.fixmock.dto;

import com.example.fixmock.client.FixMessageLogStore;

/** REST API로 내려주는 FIX 메시지 로그 한 건. 대시보드의 메시지 로그 패널이 사용한다. */
public class FixLogEntryResponse {

    private final String direction;
    private final String msgType;
    private final String raw;
    private final String timestamp;

    public FixLogEntryResponse(FixMessageLogStore.Entry entry) {
        this.direction = entry.direction().name();
        this.msgType = entry.msgType();
        this.raw = entry.displayText();
        this.timestamp = entry.timestamp().toString();
    }

    public String getDirection() {
        return direction;
    }

    public String getMsgType() {
        return msgType;
    }

    public String getRaw() {
        return raw;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
