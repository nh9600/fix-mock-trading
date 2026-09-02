package com.example.fixmock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA가 런타임에 구현체를 자동 생성해주는 저장소 인터페이스.
 * FixMockApplication과 같은 패키지(하위 패키지 포함) 안에 있기만 하면
 * 별도 설정 없이 컴포넌트 스캔으로 자동 등록된다.
 */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByClientOrderId(String clientOrderId);
}
