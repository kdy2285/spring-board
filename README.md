# Redis 기반 게시판 조회수 개선 프로젝트

## 프로젝트 개요

Spring Boot 기반 게시판 CRUD 프로젝트입니다.

게시글 조회 시마다 DB에 조회수를 직접 update하면 트래픽 증가 시 DB write 부하가 커질 수 있습니다.  
이를 개선하기 위해 Redis에 조회수 증가분을 먼저 누적하고, Scheduler를 통해 주기적으로 DB에 반영하는 구조를 적용했습니다.

조회수는 강한 정합성이 필요한 데이터가 아니므로 약간의 지연 반영을 허용하는 eventual consistency 방식으로 설계했습니다.

---

## 기술 스택

- Java 17
- Spring Boot
- Spring Data JPA
- Redis
- Docker
- Gradle

---

## 주요 기능

- 게시글 CRUD
- 게시글 목록 페이징
- Validation / Global Exception Handling
- BaseTimeEntity 기반 생성일/수정일 관리
- Redis 기반 조회수 증가
- Dirty Set 기반 flush 대상 관리
- Scheduler/manual flush를 통한 DB 반영
- DB direct update 방식과 Redis buffering 방식 비교
- flush 실패 시 Redis delta 복구

---

## 조회수 처리 구조

```text
게시글 조회 요청
→ DB에서 게시글 조회
→ Redis delta +1
→ dirty set에 postId 등록
→ 응답 viewCount = DB viewCount + Redis delta
→ Scheduler/manual flush
→ DB viewCount += Redis delta
→ Redis delta key 삭제
→ dirty set 제거
```

---

## Redis Key 설계

### 조회수 delta key

```text
post:viewCount:delta:{postId}
```

게시글별 조회수 증가분을 저장합니다.

### Dirty Set

```text
post:viewCount:dirty
```

조회수가 증가한 게시글 ID를 Set으로 관리합니다.  
전체 Redis key scan 없이 변경된 게시글만 flush 대상으로 처리하기 위해 사용했습니다.

---

## 주요 구현

### Redis 조회수 증가

```java
public void increase(Long postId) {
    redisTemplate.opsForValue().increment(deltaKey(postId));
    addDirtyPostId(postId);
}
```

조회 요청 시 DB를 직접 update하지 않고 Redis delta만 증가시킵니다.

---

### Scheduler Batch Flush

```java
@Scheduled(fixedDelay = 60000)
public void flushViewCounts() {
    Set<String> dirtyPostIds = viewCountService.getDirtyPostIds();

    if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
        log.info("[ViewCountFlushScheduler] flush 대상 없음");
        return;
    }

    log.info("[ViewCountFlushScheduler] flush 시작 - 대상 수={}", dirtyPostIds.size());

    for (String postId : dirtyPostIds) {
        try {
            postService.flushViewCountToDb(Long.parseLong(postId));
            log.debug("[ViewCountFlushScheduler] flush 성공 - postId={}", postId);
        } catch (Exception e) {
            log.error("[ViewCountFlushScheduler] flush 실패 - postId={}", postId, e);
        }
    }

    log.info("[ViewCountFlushScheduler] flush 종료 - 처리 수={}", dirtyPostIds.size());
}
```

`fixedDelay`를 사용하여 이전 flush 작업이 끝난 뒤 60초 후 다음 작업이 실행되도록 했습니다.

---

### DB 반영 및 실패 복구

```java
@Transactional
public void flushViewCountToDb(Long postId) {
    Long redisCount = viewCountService.getAndDeleteDelta(postId);

    if (redisCount == null || redisCount <= 0L) {
        viewCountService.removeDirtyPostId(postId);
        return;
    }

    try {
        postRepository.increaseViewCountBy(postId, redisCount);
        viewCountService.removeDirtyPostId(postId);
    } catch (Exception e) {
        viewCountService.increaseBy(postId, redisCount);
        throw e;
    }
}
```

DB 반영 실패 시 Redis delta를 다시 복구하여 데이터 유실 가능성을 줄였습니다.

---

## Flush 안정성

단순히 `GET → DB UPDATE → DELETE` 순서로 처리하면, GET 이후 새로 증가한 조회수가 DELETE 과정에서 함께 삭제될 수 있습니다.

이를 완화하기 위해 Redis의 `GETDEL` 방식으로 delta를 가져오면서 삭제하고, DB 반영 실패 시 다시 Redis에 복구합니다.

```text
Redis getAndDeleteDelta()
→ DB view_count += delta
→ dirty set 제거
→ 실패 시 Redis delta 복구
```

---

## DB Direct Update 방식과 비교

비교를 위해 DB에 직접 조회수를 증가시키는 API도 구현했습니다.

```java
@Modifying
@Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
int directIncreaseViewCount(@Param("id") Long id);
```

### 처리 방식 비교

| 방식 | 요청 수 | DB Write 횟수 | 특징 |
|---|---:|---:|---|
| DB Direct Update | 1000회 | 1000회 | 요청마다 DB update |
| Redis + Batch Flush | 1000회 | 1회 | Redis 누적 후 batch 반영 |

핵심 차이는 단건 처리 속도가 아니라 DB write 횟수 감소입니다.

---

## 성능 비교 결과

테스트 환경:

- 로컬 개발 환경
- 단일 스레드 반복 호출
- 요청 수: 1000회
- DB Direct Update와 Redis Buffering 방식 비교

| 방식 | 요청 수 | Total Time | DB Write 횟수 | 특징 |
|---|---:|---:|---:|---|
| DB Direct Update | 1000회 | 325 ms | 1000회 | 요청마다 DB update |
| Redis + Batch Flush | 1000회 | 719 ms | 1회 | Redis 누적 후 batch 반영 |

로컬 단일 스레드 환경에서는 Redis 방식의 total time이 DB direct update보다 더 크게 측정되었습니다.

이는 Redis `increment`, dirty set 추가, network round-trip 비용이 포함되기 때문입니다.  
따라서 이 결과는 Redis가 단건 처리에서 항상 빠르다는 의미가 아니라, Redis 기반 구조가 DB write 횟수를 줄이는 데 목적이 있음을 보여줍니다.

---

## 테스트 시나리오

1. 게시글 조회 요청을 여러 번 호출
2. Redis delta 값과 dirty set 확인
3. Scheduler/manual flush 실행
4. DB viewCount 증가 확인
5. Redis delta key 삭제 확인
6. dirty set에서 postId 제거 확인
7. DB 반영 실패 시 Redis delta 복구 확인

Redis CLI 확인:

```bash
docker exec -it board-redis redis-cli
```

```redis
GET post:viewCount:delta:1
SMEMBERS post:viewCount:dirty
```

---

## 설계 Trade-off

### 장점

- 조회 요청마다 DB write를 수행하지 않음
- DB write 횟수 감소
- dirty set을 통해 변경된 게시글만 flush
- batch flush를 통한 write aggregation
- flush 실패 시 Redis delta 복구 가능

### 단점

- DB 조회수 반영이 지연됨
- Redis와 DB 간 일시적인 데이터 차이 발생
- Redis 장애 상황에 대한 추가 대응 필요
- 단건 요청 기준으로는 Redis가 더 느릴 수 있음

---

## 결론

Redis delta, dirty set, Scheduler batch flush를 활용하여 조회수 증가 요청에서 발생하는 DB write 부하를 줄이는 구조를 적용했습니다.

조회수처럼 강한 정합성이 필요하지 않은 데이터에 대해 eventual consistency를 허용하고, batch flush와 실패 복구 로직을 통해 확장성과 안정성을 고려한 설계를 경험했습니다.