package com.example.board.service.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class ViewCountService {
    private final StringRedisTemplate redisTemplate;

    private static final String VIEW_COUNT_DELTA_KEY = "post:viewCount:delta:";
    private static final String VIEW_COUNT_DIRTY_SET_KEY = "post:viewCount:dirty";

    /**
     * flush 대상에서 제거(SREM)하고 delta를 가져오며 삭제(GET + DEL)한다.
     * 세 명령이 Lua 안에서 원자적으로 실행되므로, 그 사이에 조회 요청이나 다른 flush가 끼어들 수 없다.
     * KEYS[1] = dirty set, KEYS[2] = delta key, ARGV[1] = postId
     */
    private static final RedisScript<String> TAKE_DELTA_SCRIPT = RedisScript.of("""
            redis.call('SREM', KEYS[1], ARGV[1])
            local delta = redis.call('GET', KEYS[2])
            if delta then
                redis.call('DEL', KEYS[2])
            end
            return delta
            """, String.class);

    /**
     * DB 반영에 실패한 delta를 되돌리고 dirty set에 다시 등록한다(INCRBY + SADD).
     * KEYS[1] = dirty set, KEYS[2] = delta key, ARGV[1] = postId, ARGV[2] = 되돌릴 값
     */
    private static final RedisScript<Long> RESTORE_DELTA_SCRIPT = RedisScript.of("""
            redis.call('INCRBY', KEYS[2], ARGV[2])
            redis.call('SADD', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    /**
     * 조회 시 INCR 후 SADD 순서로 기록한다.
     * flush가 두 명령 사이에 실행되면 이번 증가분은 flush에 포함되고, 이어지는 SADD는 delta 0인 id를 남긴다.
     * 이 id는 다음 flush에서 그대로 정리되므로 유실이 생기지 않는다.
     */
    public void increase(Long postId) {
        redisTemplate.opsForValue().increment(deltaKey(postId));
        redisTemplate.opsForSet().add(VIEW_COUNT_DIRTY_SET_KEY, String.valueOf(postId));
    }

    public Long get(Long postId) {
        String value = redisTemplate.opsForValue().get(deltaKey(postId));
        return value != null ? Long.parseLong(value) : 0L;
    }

    public Set<String> getDirtyPostIds() {
        return redisTemplate.opsForSet().members(VIEW_COUNT_DIRTY_SET_KEY);
    }

    public long takeDeltaForFlush(Long postId) {
        String value = redisTemplate.execute(
                TAKE_DELTA_SCRIPT,
                List.of(VIEW_COUNT_DIRTY_SET_KEY, deltaKey(postId)),
                String.valueOf(postId)
        );
        return value != null ? Long.parseLong(value) : 0L;
    }

    public void restoreDelta(Long postId, long count) {
        redisTemplate.execute(
                RESTORE_DELTA_SCRIPT,
                List.of(VIEW_COUNT_DIRTY_SET_KEY, deltaKey(postId)),
                String.valueOf(postId),
                String.valueOf(count)
        );
    }

    private String deltaKey(Long postId) {
        return VIEW_COUNT_DELTA_KEY + postId;
    }
}
