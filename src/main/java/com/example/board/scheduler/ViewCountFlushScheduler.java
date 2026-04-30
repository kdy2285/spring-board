package com.example.board.scheduler;

import com.example.board.service.PostService;
import com.example.board.service.view.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushScheduler {

    private final ViewCountService viewCountService;
    private final PostService postService;

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
                log.info("[ViewCountFlushScheduler] flush 성공 - postId={}", postId);
            } catch (Exception e) {
                log.error("[ViewCountFlushScheduler] flush 실패 - postId={}", postId, e);
            }
        }

        log.info("[ViewCountFlushScheduler] flush 종료");
    }
}
