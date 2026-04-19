package com.example.board;

import com.example.board.domain.Post;
import com.example.board.repository.PostRepository;
import com.example.board.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class PostServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Test
    void viewCount_동시성_테스트() throws InterruptedException {
        Post savedPost = postRepository.save(
                new Post("제목", "내용")
        );

        Long postId = savedPost.getId();

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    postService.getById(postId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Post post = postRepository.findById(postId).orElseThrow();
        System.out.println("viewCount = " + post.getViewCount());
        assertThat(post.getViewCount()).isEqualTo(100);
    }

    @Transactional
    @Test
    void 영속성컨텍스트_불일치_확인() {
        Post saved = postRepository.save(
                new Post("title", "content")
        );

        Long id = saved.getId();

        Post post = postRepository.findById(id).orElseThrow();
        System.out.println("before = " + post.getViewCount());

        postRepository.increaseViewCount(id);

        System.out.println("after = " + post.getViewCount());
    }
}