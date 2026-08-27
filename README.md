# 주린이 주식 매매 연습장 (FIX Mock Trading System)

실제 돈 없이 주식 매매를 연습해볼 수 있는 모의 거래 시스템입니다. QuickFIX/J 없이 실제 증권가에서 쓰는 **FIX 프로토콜**과 **TCP 소켓 통신**을 직접 구현해서, 주문이 어떻게 접수되고 체결되는지 화면으로 직접 볼 수 있게 만들었습니다.

주식이 처음이어도 괜찮아요 — 화면 곳곳에 마우스를 올리면 설명이 뜨고, 처음 들어가면 사용법 안내가 나오고, 오른쪽 위 "용어 사전"에서 헷갈리는 말들을 바로 찾아볼 수 있어요.

## 기술 스택
Java 17, Spring Boot 3.3.4, Gradle

## 실행
```bash
gradlew.bat bootRun   # Windows
./gradlew bootRun     # macOS/Linux
```
실행 후 `http://localhost:8080` 접속

## 이렇게 연습해보세요
1. 종목(AAPL/GOOG/MSFT/AMZN/TSLA)과 매수/매도, 지정가/시장가를 선택해 주문 넣기
2. 오른쪽 주문 목록에서 상태가 NEW → ACCEPTED → FILLED로 바뀌는 걸 실시간으로 확인
3. 아래 FIX 메시지 로그에서 실제로 오간 통신 내용 구경하기
4. 모르는 용어는 마우스를 올리거나 "용어 사전" 버튼으로 확인

## 구조
```
주문 입력 → FIX 메시지 생성 → TCP 소켓 전송 → 서버 검증/매칭 → 체결 결과(Execution Report) → 화면 반영
```
- FIX 메시지: New Order Single(D), Order Cancel Request(F), Execution Report(8), Order Cancel Reject(9)
- 매칭 엔진: 가격-시간 우선순위(Price-Time Priority) 기반 메모리 거래소

## 테스트
```bash
gradlew.bat test
```

## 단순화한 부분
세션 관리(Logon/Heartbeat) 미구현, 인메모리 저장(재시작 시 초기화). 실제 투자와 무관한 학습/포트폴리오 목적 프로젝트입니다.
