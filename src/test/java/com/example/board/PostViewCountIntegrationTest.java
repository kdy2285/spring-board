package com.example.board;

import com.example.board.domain.Post;
import com.example.board.repository.PostRepository;
import com.example.board.service.PostService;
import com.example.board.service.view.ViewCountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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

    @AfterEach
    void tearDown() {
        postRepository.deleteAll();
        redisTemplate.delete(VIEW_COUNT_DIRTY_SET_KEY);
    }

    @Test
    void 조회하면_Redis_delta가_증가하고_dirty_set에_등록된다() {
        // given
        Post post = postRepository.save(new Post("title", "content"));
        Long postId = post.getId();

        // when
        postService.getById(postId);
        postService.getById(postId);
        postService.getById(postId);

        // then
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
}