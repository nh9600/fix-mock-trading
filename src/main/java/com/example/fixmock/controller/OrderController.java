package com.example.fixmock.controller;

import com.example.fixmock.client.ClientOrderService;
import com.example.fixmock.domain.Order;
import com.example.fixmock.domain.OrderSide;
import com.example.fixmock.domain.OrderType;
import com.example.fixmock.dto.OrderResponse;
import com.example.fixmock.dto.PlaceOrderRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "클라이언트(트레이딩 애플리케이션)" 역할을 하는 REST 진입점.
 *
 * 이 컨트롤러 자체는 FIX나 Socket에 대해 전혀 모른다. 오직 HTTP 요청을 받아서
 * {@link ClientOrderService}에게 위임할 뿐이다. 실제 FIX 메시지 생성/전송/수신은
 * 전부 client/net/fix 패키지에서 일어난다 — 이 계층 분리가 "Controller는 얇게"
 * 라는 일반적인 스프링 부트 설계 원칙과도 일치한다.
 *
 * clientId는 이 예제에서 FIX SenderCompID(Tag 49) 역할을 겸한다. 서로 다른
 * clientId로 호출하면 서로 다른 트레이더가 매매하는 상황을 재현할 수 있다.
 */
@RestController
@RequestMapping("/api/clients/{clientId}/orders")
public class OrderController {

    private final ClientOrderService clientOrderService;

    public OrderController(ClientOrderService clientOrderService) {
        this.clientOrderService = clientOrderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@PathVariable("clientId") String clientId,
                                                      @RequestBody PlaceOrderRequest request) {
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());

        Order order = clientOrderService.placeOrder(
                clientId, request.getSymbol(), side, orderType, request.getPrice(), request.getQuantity(), 2000);

        return ResponseEntity.ok(new OrderResponse(order));
    }

    /** 해당 clientId(트레이더)가 낸 모든 주문을 최신 상태로 조회한다. 대시보드의 주문 목록 폴링에 사용된다. */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> listOrders(@PathVariable("clientId") String clientId) {
        List<OrderResponse> body = clientOrderService.listOrders(clientId).stream()
                .map(OrderResponse::new)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{clientOrderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable("clientId") String clientId,
                                                   @PathVariable("clientOrderId") String clientOrderId) {
        Order order = clientOrderService.findOrder(clientOrderId);
        return ResponseEntity.ok(new OrderResponse(order));
    }

    @DeleteMapping("/{clientOrderId}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable("clientId") String clientId,
                                                      @PathVariable("clientOrderId") String clientOrderId) {
        Order order = clientOrderService.cancelOrder(clientId, clientOrderId, 2000);
        return ResponseEntity.ok(new OrderResponse(order));
    }
}
