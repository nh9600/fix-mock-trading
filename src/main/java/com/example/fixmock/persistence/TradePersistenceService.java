package com.example.fixmock.persistence;

import com.example.fixmock.client.FixMessageLogStore;
import com.example.fixmock.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 도메인 객체({@link Order})와 FIX 메시지 로그를 MySQL에 영구 저장하는 서비스.
 *
 * 실시간 화면 갱신은 여전히 기존 인메모리 저장소({@code ClientOrderStore},
 * {@link FixMessageLogStore})를 그대로 사용한다. 이 서비스는 그 위에 "이력을 남기는"
 * 별도의 영속화 계층으로 추가된 것이라, DB 연결이 잠깐 끊기거나 느려져도 주문 처리 같은
 * 실시간 흐름 자체는 영향을 받지 않도록 저장 실패를 조용히 흡수(로그만 남기고 무시)한다.
 */
public class TradePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(TradePersistenceService.class);

    private final OrderJpaRepository orderJpaRepository;
    private final FixMessageLogJpaRepository fixMessageLogJpaRepository;

    public TradePersistenceService(OrderJpaRepository orderJpaRepository,
                                    FixMessageLogJpaRepository fixMessageLogJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.fixMessageLogJpaRepository = fixMessageLogJpaRepository;
    }

    /** 주문의 현재 상태를 orders 테이블에 upsert(있으면 갱신, 없으면 삽입) 한다. */
    @Transactional
    public void saveOrderSnapshot(Order order) {
        try {
            Instant now = Instant.now();
            OrderEntity entity = orderJpaRepository.findByClientOrderId(order.getClientOrderId())
                    .orElseGet(() -> {
                        OrderEntity created = new OrderEntity();
                        created.setClientOrderId(order.getClientOrderId());
                        created.setCreatedAt(now);
                        return created;
                    });
            entity.setExchangeOrderId(order.getExchangeOrderId());
            entity.setSenderCompId(order.getSenderCompId());
            entity.setSymbol(order.getSymbol());
            entity.setSide(order.getSide().name());
            entity.setOrderType(order.getOrderType().name());
            entity.setPrice(order.getPrice());
            entity.setOriginalQuantity(order.getOriginalQuantity());
            entity.setLeavesQuantity(order.getLeavesQuantity());
            entity.setCumulativeQuantity(order.getCumulativeQuantity());
            entity.setStatus(order.getStatus().name());
            entity.setRejectReason(order.getRejectReason());
            entity.setUpdatedAt(now);
            orderJpaRepository.save(entity);
        } catch (Exception e) {
            log.warn("주문 영속화 실패(실시간 처리에는 영향 없음): clOrdId={}, 사유={}",
                    order.getClientOrderId(), e.getMessage());
        }
    }

    /** SENT/RECEIVED FIX 메시지 한 건을 fix_message_log 테이블에 기록한다. */
    @Transactional
    public void saveMessageLog(String clientId, FixMessageLogStore.Direction direction, String msgType, String rawDisplay) {
        try {
            FixMessageLogEntity entity = new FixMessageLogEntity();
            entity.setClientId(clientId);
            entity.setDirection(direction.name());
            entity.setMsgType(msgType);
            entity.setRawMessage(rawDisplay);
            entity.setCreatedAt(Instant.now());
            fixMessageLogJpaRepository.save(entity);
        } catch (Exception e) {
            log.warn("FIX 메시지 로그 영속화 실패(실시간 처리에는 영향 없음): clientId={}, 사유={}", clientId, e.getMessage());
        }
    }
}
