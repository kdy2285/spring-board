package com.example.board.controller;

import com.example.board.dto.PostCreateRequest;
import com.example.board.dto.PostResponse;
import com.example.board.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<Long> create(@RequestBody PostCreateRequest request) {
        return ResponseEntity.ok(postService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getById(id));
    }
}