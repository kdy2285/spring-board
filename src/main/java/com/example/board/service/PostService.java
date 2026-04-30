package com.example.board.service;

import com.example.board.domain.Post;
import com.example.board.dto.PostCreateRequest;
import com.example.board.dto.PostResponse;
import com.example.board.dto.PostUpdateRequest;
import com.example.board.exception.PostNotFoundException;
import com.example.board.repository.PostRepository;
import com.example.board.service.view.ViewCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
//        postRepository.increaseViewCount(id);

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

    @Transactional
    public void flushViewCountToDb(Long postId) {
        Long redisCount = viewCountService.getAndDeleteDelta(postId);

        if (redisCount <= 0L) {
            viewCountService.removeDirtyPostId(postId);
            return;
        }

        try {
            postRepository.increaseViewCountBy(postId, redisCount);
            viewCountService.removeDirtyPostId(postId);
        } catch (Exception e) {
            viewCountService.increaseBy(postId, redisCount);
            throw e;
        }
    }
}