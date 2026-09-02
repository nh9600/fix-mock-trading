package com.example.fixmock.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** FIX 메시지 로그 이력을 저장하는 Spring Data JPA 저장소. */
public interface FixMessageLogJpaRepository extends JpaRepository<FixMessageLogEntity, Long> {
}
