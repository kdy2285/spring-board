package com.example.board;

import com.example.board.domain.Post;
import com.example.board.repository.PostRepository;
import com.example.board.service.PostService;
import com.example.board.service.view.ViewCountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostViewCountIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ViewCountService viewCountService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String VIEW_COUNT_DIRTY_SET_KEY = "post:viewCount:dirty";
    private static final String VIEW_COUNT_DELTA_KEY_PREFIX = "post:viewCount:delta:";

    @BeforeEach
    void setUp() {
        clearRedis();
        postRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        clearRedis();
        postRepository.deleteAll();
    }

    private void clearRedis() {
        redisTemplate.delete(VIEW_COUNT_DIRTY_SET_KEY);

        Set<String> keys = redisTemplate.keys(VIEW_COUNT_DELTA_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void 조회하면_Redis_delta가_증가하고_dirty_set에_등록된다() {
        Post post = postRepository.save(new Post("title", "content"));
        Long postId = post.getId();

        Post savedPost = postRepository.findById(postId).orElseThrow();
        assertThat(savedPost.getViewCount()).isEqualTo(0);

        postService.getById(postId);
        postService.getById(postId);
        postService.getById(postId);

        Long redisCount = viewCountService.get(postId);
        Post foundPost = postRepository.findById(postId).orElseThrow();

        assertThat(redisCount).isEqualTo(3L);
        assertThat(viewCountService.getDirtyPostIds()).contains(String.valueOf(postId));
        assertThat(foundPost.getViewCount()).isEqualTo(0);
    }

    @Test
    void flush하면_Redis_delta가_DB에_반영되고_Redis_key가_정리된다() {
        // given
        Post post = postRepository.save(new Post("title", "content"));
        Long postId = post.getId();

        viewCountService.increase(postId);
        viewCountService.increase(postId);
        viewCountService.increase(postId);
        viewCountService.increase(postId);
        viewCountService.increase(postId);

        // when
        postService.flushViewCountToDb(postId);

        // then
        Post foundPost = postRepository.findById(postId).orElseThrow();
        Long redisCount = viewCountService.get(postId);

        assertThat(foundPost.getViewCount()).isEqualTo(5);
        assertThat(redisCount).isEqualTo(0L);
        assertThat(viewCountService.getDirtyPostIds()).doesNotContain(String.valueOf(postId));
        assertThat(redisTemplate.hasKey(VIEW_COUNT_DELTA_KEY_PREFIX + postId)).isFalse();
    }

    @Test
    void DB_반영이_실패하면_delta가_복구되고_다시_flush_대상이_된다() {
        // given: view_count(INT) 범위를 넘는 delta를 넣어 DB UPDATE가 실패하게 만든다.
        Post post = postRepository.save(new Post("title", "content"));
        Long postId = post.getId();
        long hugeDelta = 3_000_000_000L;

        redisTemplate.opsForValue().set(VIEW_COUNT_DELTA_KEY_PREFIX + postId, String.valueOf(hugeDelta));
        redisTemplate.opsForSet().add(VIEW_COUNT_DIRTY_SET_KEY, String.valueOf(postId));

        // when
        assertThatThrownBy(() -> postService.flushViewCountToDb(postId))
                .isInstanceOf(RuntimeException.class);

        // then: DB는 롤백되고, Redis delta와 dirty 등록은 flush 전 상태로 돌아온다.
        Post foundPost = postRepository.findById(postId).orElseThrow();
        assertThat(foundPost.getViewCount()).isEqualTo(0);
        assertThat(viewCountService.get(postId)).isEqualTo(hugeDelta);
        assertThat(viewCountService.getDirtyPostIds()).contains(String.valueOf(postId));
    }

    @Test
    void 조회와_flush가_동시에_실행돼도_조회수가_유실되지_않는다() throws InterruptedException {
        // given
        Post post = postRepository.save(new Post("title", "content"));
        Long postId = post.getId();

        int viewCount = 2_000;
        ExecutorService viewers = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(viewCount);
        AtomicBoolean viewing = new AtomicBoolean(true);

        // when: 조회 요청이 들어오는 동안 flush를 계속 실행한다.
        for (int i = 0; i < viewCount; i++) {
            viewers.submit(() -> {
                try {
                    startLatch.await();
                    viewCountService.increase(postId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        AtomicReference<Throwable> flushError = new AtomicReference<>();
        AtomicInteger flushesDuringViews = new AtomicInteger();

        Thread flusher = new Thread(() -> {
            try {
                while (viewing.get()) {
                    for (String id : viewCountService.getDirtyPostIds()) {
                        postService.flushViewCountToDb(Long.parseLong(id));
                        if (doneLatch.getCount() > 0) {
                            flushesDuringViews.incrementAndGet();
                        }
                    }
                }
            } catch (Throwable t) {
                flushError.set(t);
            }
        });

        flusher.start();
        startLatch.countDown();
        doneLatch.await();
        viewing.set(false);
        flusher.join();
        viewers.shutdown();

        // flush 스레드의 예외는 테스트 스레드로 전달되지 않으므로 직접 확인한다.
        assertThat(flushError.get()).isNull();
        // 조회가 진행되는 동안 flush가 실제로 실행되지 않았다면 이 테스트는 경합을 검증하지 못한 것이다.
        assertThat(flushesDuringViews.get())
                .as("조회 진행 중 실행된 flush 횟수")
                .isPositive();

        // 스케줄러의 다음 실행과 같다: dirty set에 남은 것만 flush한다.
        for (String id : viewCountService.getDirtyPostIds()) {
            postService.flushViewCountToDb(Long.parseLong(id));
        }

        // then: dirty set 기준 flush만으로 모든 조회수가 DB에 반영돼야 한다.
        Post foundPost = postRepository.findById(postId).orElseThrow();
        assertThat(foundPost.getViewCount()).isEqualTo(viewCount);
        assertThat(redisTemplate.hasKey(VIEW_COUNT_DELTA_KEY_PREFIX + postId)).isFalse();
    }
}