package com.example.fixmock.client;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 클라이언트별로 "오고 간 FIX 메시지"를 최근 N건까지 메모리에 보관하는 저장소.
 *
 * 화면(대시보드)의 FIX 메시지 로그 패널이 이 저장소를 폴링해서 보여준다.
 * 실제 거래 시스템이라면 이런 로그는 감사(audit) 목적상 영구 저장소(DB, 파일)에
 * 남겨야 하지만, 이 예제에서는 학습/시연 목적이므로 프로세스 메모리에만 보관하고
 * 클라이언트당 최대 개수를 제한해 무한정 커지지 않도록 한다.
 */
public class FixMessageLogStore {

    /** 클라이언트 하나당 보관할 최대 로그 개수. 넘으면 오래된 것부터 버린다. */
    private static final int MAX_ENTRIES_PER_CLIENT = 200;

    private final ConcurrentHashMap<String, Deque<Entry>> logsByClientId = new ConcurrentHashMap<>();

    public void append(String clientId, Direction direction, String msgType, String displayText) {
        Deque<Entry> deque = logsByClientId.computeIfAbsent(clientId, id -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Entry(direction, msgType, displayText, Instant.now()));
            while (deque.size() > MAX_ENTRIES_PER_CLIENT) {
                deque.removeFirst();
            }
        }
    }

    /** 오래된 순서(도착한 순서) 그대로 반환한다. */
    public List<Entry> get(String clientId) {
        Deque<Entry> deque = logsByClientId.get(clientId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    public enum Direction {
        /** 클라이언트 -> 서버로 보낸 메시지. */
        SENT,
        /** 서버 -> 클라이언트로 받은 메시지. */
        RECEIVED
    }

    /** 로그 한 건. */
    public record Entry(Direction direction, String msgType, String displayText, Instant timestamp) {
    }
}
