package com.example.board.scheduler;

import com.example.board.service.PostService;
import com.example.board.service.view.ViewCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ViewCountScheduler {
    private final PostService postService;
    private final ViewCountService viewCountService;

    @Scheduled(fixedDelay = 60000)
    public void flushViewCounts() {
        Set<String> postIds = viewCountService.getDirtyPostIds();

        if (postIds == null || postIds.isEmpty()) {
            return;
        }

        for (String postIdValue : postIds) {
            Long postId = Long.parseLong(postIdValue);
            postService.flushViewCountToDb(postId);
        }
    }
}
