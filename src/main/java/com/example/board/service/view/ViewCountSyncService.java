package com.example.board.service.view;

import com.example.board.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@RequiredArgsConstructor
@Service
public class ViewCountSyncService {
    private final StringRedisTemplate redisTemplate;
    private final PostRepository postRepository;

    private static final String PREFIX = "post:viewCount:delta:";

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sync() {
//        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        ScanOptions options = ScanOptions.scanOptions()
                .match(PREFIX + "*")
                .count(100)
                .build();

        Cursor<byte[]> cursor =redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(options);

        while (cursor.hasNext()) {
            String key = new String(cursor.next());

            Long postId = Long.parseLong(key.replace(PREFIX, ""));

            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                continue;
            }
            Long delta = Long.parseLong(value);
            postRepository.increaseViewCountBy(postId, delta);

            redisTemplate.delete(key);
//            postRepository.updateViewCount(postId, count);

//            System.out.println("sync: " + postId + " -> " + count);
        }

        cursor.close();

//        if (keys == null) {
//            return;
//        }

//        for (String key : keys) {
//            Long postId = Long.parseLong(key.replace(PREFIX, ""));
//            String value = redisTemplate.opsForValue().get(key);
//
//            if (value == null) {
//                continue;
//            }
//
//            Long count = Long.parseLong(value);
//
//            postRepository.updateViewCount(postId, count);
//        }
    }
}
