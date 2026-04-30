# Redis 기반 게시판 조회수 개선 프로젝트

## 프로젝트 개요

Spring Boot 기반 게시판 CRUD 프로젝트입니다.

게시글 생성, 조회, 수정, 삭제 기능을 구현하고, 조회수 증가 처리에서 발생할 수 있는 DB write 부하를 줄이기 위해 Redis 기반 조회수 버퍼링 구조를 적용했습니다.

## 기술 스택

- Java 17
- Spring Boot
- Spring Data JPA
- Redis
- Docker
- Gradle

## 주요 기능

- 게시글 CRUD
- 게시글 목록 페이징
- Validation
- Global Exception Handling
- BaseTimeEntity 기반 생성일/수정일 관리
- Redis 기반 조회수 증가
- Scheduler/manual flush를 통한 Redis 조회수 DB 반영

## 조회수 처리 구조

조회수 증가분을 Redis에 delta 값으로 누적하고, 일정 주기마다 DB에 반영합니다.

```text
게시글 조회 요청
→ DB에서 게시글 조회
→ Redis delta +1
→ 응답 viewCount = DB viewCount + Redis delta
→ Scheduler/manual flush
→ DB viewCount += Redis delta
Redis Key 설계
post:viewCount:delta:{postId}

게시글별 조회수 증가분을 저장합니다.

post:viewCount:dirty

조회수가 증가한 게시글 ID를 Set으로 관리합니다.

Flush 안정성 개선

Redis에 누적된 조회수 delta를 DB에 반영할 때 단순히 GET → DB UPDATE → DELETE 순서로 처리하면 race condition이 발생할 수 있습니다.

예를 들어 Scheduler가 Redis delta 값을 읽은 뒤 DB에 반영하기 전에 새로운 조회 요청이 들어오면, 이후 Redis key 삭제 과정에서 새로 증가한 조회수가 함께 삭제될 수 있습니다.

이를 완화하기 위해 Redis의 GETDEL 기반 처리 방식인 getAndDeleteDelta()를 적용했습니다.

Scheduler/manual flush 실행
→ Redis getAndDeleteDelta()
→ DB viewCount += delta
→ dirty set 제거
→ DB 반영 실패 시 Redis delta 복구
테스트 시나리오
게시글을 생성합니다.
게시글 상세 조회 API를 여러 번 호출합니다.
Redis CLI에서 delta 값과 dirty set을 확인합니다.
manual flush 또는 Scheduler를 실행합니다.
DB viewCount 증가 여부를 확인합니다.
Redis delta key 삭제 여부를 확인합니다.
dirty set에서 postId 제거 여부를 확인합니다.
Redis CLI 확인 명령어
docker exec -it board-redis redis-cli
GET post:viewCount:delta:1
SMEMBERS post:viewCount:dirty
성능 비교

DB 직접 update 방식과 Redis buffering 방식의 성능 비교는 추후 진행 예정입니다.