package com.example.board.service;

import com.example.board.domain.Post;
import com.example.board.dto.PostCreateRequest;
import com.example.board.dto.PostResponse;
import com.example.board.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    public Long create(PostCreateRequest request) {
        Post post = new Post(request.getTitle(), request.getContent());
        return postRepository.save(post).getId();
    }

    public PostResponse getById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 없습니다. id=" +id));
        return new PostResponse(post.getId(), post.getTitle(), post.getContent());
    }
}