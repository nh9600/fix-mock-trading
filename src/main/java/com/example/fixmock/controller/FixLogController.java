package com.example.fixmock.controller;

import com.example.fixmock.client.FixMessageLogStore;
import com.example.fixmock.dto.FixLogEntryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 특정 클라이언트(트레이더)가 주고받은 FIX 메시지 로그를 조회하는 REST 진입점.
 *
 * 대시보드의 "FIX 메시지 로그" 패널이 이 엔드포인트를 주기적으로(polling) 호출해서
 * 화면을 갱신한다. Socket 통신 자체는 여기서 전혀 일어나지 않으며, 이미
 * {@link com.example.fixmock.client.ClientOrderService}가 송수신 시점에 기록해 둔
 * {@link FixMessageLogStore}의 스냅샷을 읽어 내려줄 뿐이다.
 */
@RestController
@RequestMapping("/api/clients/{clientId}/messages")
public class FixLogController {

    private final FixMessageLogStore messageLogStore;

    public FixLogController(FixMessageLogStore messageLogStore) {
        this.messageLogStore = messageLogStore;
    }

    @GetMapping
    public ResponseEntity<List<FixLogEntryResponse>> getMessages(@PathVariable String clientId) {
        List<FixLogEntryResponse> body = messageLogStore.get(clientId).stream()
                .map(FixLogEntryResponse::new)
                .toList();
        return ResponseEntity.ok(body);
    }
}
