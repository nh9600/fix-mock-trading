# FIX Mock Trading System

QuickFIX/J 없이 FIX 프로토콜 메시지와 TCP 소켓 통신을 직접 구현한 학습용 Mock 주식 거래 시스템입니다.

## 기술 스택
Java 17, Spring Boot 3.3.4, Gradle

## 구조
```
Controller → ClientOrderService → FIX Builder → TCP Socket → FIX Parser → ServerOrderService → MatchingEngine
                                                                                 ↓
                                                        Execution Report → TCP Socket → ClientOrderService
```

- FIX 메시지: New Order Single(D), Order Cancel Request(F), Execution Report(8, 접수/거부/체결 겸용), Order Cancel Reject(9)
- 매칭 엔진: 가격-시간 우선순위(Price-Time Priority) 기반 메모리 거래소

## 실행
```bash
gradlew.bat bootRun   # Windows
./gradlew bootRun     # macOS/Linux
```
`http://localhost:8080` 접속 시 웹 대시보드에서 주문 생성/취소, 실시간 FIX 메시지 로그 확인 가능

## 테스트
```bash
gradlew.bat test
```

## 주요 API
| Method | URL | 설명 |
|---|---|---|
| POST | `/api/clients/{clientId}/orders` | 주문 생성 |
| GET | `/api/clients/{clientId}/orders` | 주문 목록 조회 |
| DELETE | `/api/clients/{clientId}/orders/{clientOrderId}` | 주문 취소 |
| GET | `/api/clients/{clientId}/messages` | FIX 메시지 로그 조회 |

## 단순화한 부분
세션 관리(Logon/Heartbeat) 미구현, BodyLength 대신 CheckSum 필드 기준 메시지 경계 탐지, thread-per-connection 모델, 인메모리 저장(재시작 시 초기화).

학습/포트폴리오 목적 프로젝트입니다.
