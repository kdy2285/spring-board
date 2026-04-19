package com.example.board.service.view;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ViewCountService {
    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "post:viewCount:delta:";

    public void increase(Long postId) {
        String key = PREFIX+postId;
        redisTemplate.opsForValue().increment(key);
    }

    public Long get(Long postId) {
        String key = PREFIX + postId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }
}
