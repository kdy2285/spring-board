package com.example.board.service;

import com.example.board.domain.Post;
import com.example.board.dto.PostCreateRequest;
import com.example.board.dto.PostResponse;
import com.example.board.dto.PostUpdateRequest;
import com.example.board.exception.PostNotFoundException;
import com.example.board.repository.PostRepository;
import com.example.board.service.view.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final ViewCountService viewCountService;

    @Transactional
    public Long create(PostCreateRequest request) {
        Post post = new Post(request.getTitle(), request.getContent());
        return postRepository.save(post).getId();
    }

    @Transactional(readOnly = true)
    public PostResponse getById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));

        viewCountService.increase(id);

        Long redisCount = viewCountService.get(id);
        long totalViewCount = post.getViewCount() + redisCount;


        return new PostResponse(post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                totalViewCount);
    }

    @Transactional
    public void update(Long id, PostUpdateRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));

        post.update(request.getTitle(), request.getContent());
    }

    @Transactional
    public void delete(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
        postRepository.delete(post);
    }

    public List<PostResponse> getAll() {
        return postRepository.findAll().stream()
                .map(post -> new PostResponse(
                        post.getId(),
                        post.getTitle(),
                        post.getContent(),
                        post.getCreatedAt(),
                        post.getUpdatedAt(),
                        post.getViewCount()
                ))
                .toList();
    }

    public Page<PostResponse> getPosts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        return postRepository.findAll(pageRequest)
                .map(post -> new PostResponse(post.getId(), post.getTitle(), post.getContent(),
                        post.getCreatedAt(), post.getUpdatedAt(), post.getViewCount()));
    }

    /**
     * Redis에서 delta를 원자적으로 꺼낸 뒤(SREM + GET + DEL, Lua) DB에 더한다.
     *
     * 보장하는 것: Redis 명령 사이의 경합으로 증가분이 dirty set 밖에 방치되거나 두 번 반영되지 않는다.
     * 보장하지 않는 것: Redis와 DB는 하나의 트랜잭션이 아니다.
     *  - DB UPDATE가 예외를 던지면 delta를 되돌린다(이 메서드의 catch).
     *  - 꺼낸 뒤 DB 커밋 전에 프로세스가 종료되거나, 커밋 단계에서 실패하면 그 delta는 유실된다.
     *  조회수는 약한 정합성을 허용하는 데이터라 이 범위를 받아들인다.
     */
    @Transactional
    public void flushViewCountToDb(Long postId) {
        long redisCount = viewCountService.takeDeltaForFlush(postId);

        if (redisCount <= 0L) {
            return;
        }

        try {
            postRepository.increaseViewCountBy(postId, redisCount);
        } catch (RuntimeException e) {
            viewCountService.restoreDelta(postId, redisCount);
            throw e;
        }
    }

    @Transactional
    public void directUpdate(Long id) {
        postRepository.directIncreaseViewCount(id);
    }
}