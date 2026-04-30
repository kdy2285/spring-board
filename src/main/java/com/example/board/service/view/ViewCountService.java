package com.example.board.service.view;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@RequiredArgsConstructor
@Service
public class ViewCountService {
    private final StringRedisTemplate redisTemplate;

    private static final String VIEW_COUNT_DELTA_KEY = "post:viewCount:delta:";
    private static final String VIEW_COUNT_DIRTY_SET_KEY = "post:viewCount:dirty";

    public void increase(Long postId) {
        redisTemplate.opsForValue().increment(deltaKey(postId));
        addDirtyPostId(postId);
    }

    public Long get(Long postId) {
        String value = redisTemplate.opsForValue().get(deltaKey(postId));
        return value != null ? Long.parseLong(value) : 0L;
    }

//    public void delete(Long postId) {
//        redisTemplate.delete(deltaKey(postId));
//    }

    public Set<String> getDirtyPostIds() {
        return redisTemplate.opsForSet().members(VIEW_COUNT_DIRTY_SET_KEY);
    }

    public void removeDirtyPostId(Long postId) {
        redisTemplate.opsForSet().remove(VIEW_COUNT_DIRTY_SET_KEY, String.valueOf(postId));
    }

    private String deltaKey(Long postId) {
        return VIEW_COUNT_DELTA_KEY + postId;
    }

    public Long getAndDeleteDelta(Long postId) {
        String key = VIEW_COUNT_DELTA_KEY + postId;
        String value = redisTemplate.opsForValue().getAndDelete(key);
        if (value == null) {
            return 0L;
        }

        return Long.parseLong(value);
    }

    public void increaseBy(Long postId, Long count) {
        String key = VIEW_COUNT_DELTA_KEY + postId;
        redisTemplate.opsForValue().increment(key, count);
        addDirtyPostId(postId);
    }

    public void addDirtyPostId(Long postId) {
        redisTemplate.opsForSet().add(VIEW_COUNT_DIRTY_SET_KEY, String.valueOf(postId));
    }
}
