package com.example.board.controller;

import com.example.board.dto.ApiResponse;
import com.example.board.dto.PostCreateRequest;
import com.example.board.dto.PostResponse;
import com.example.board.dto.PostUpdateRequest;
import com.example.board.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody @Valid PostCreateRequest request) {
        Long id = postService.create(request);
        return ResponseEntity.ok(ApiResponse.success(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(postService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable Long id, @RequestBody @Valid PostUpdateRequest request) {
        postService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

//    @GetMapping
//    public ResponseEntity<List<PostResponse>> getAll() {
//        return ResponseEntity.ok(postService.getAll());
//    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getPosts(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(postService.getPosts(page, size)));
    }

    @PostMapping("/{postId}/view-count/flush")
    public ApiResponse<Void> flushViewCount(@PathVariable Long postId) {
        postService.flushViewCountToDb(postId);
        return ApiResponse.success(null);
    }
}