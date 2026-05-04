package com.example.board.controller;

import com.example.board.dto.ApiResponse;
import com.example.board.dto.PostCreateRequest;
import com.example.board.dto.PostResponse;
import com.example.board.dto.PostUpdateRequest;
import com.example.board.service.PostService;
import com.example.board.service.view.ViewCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;
    private final ViewCountService viewCountService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody @Valid PostCreateRequest request) {
        Long id = postService.create(request);
        return ResponseEntity.ok(ApiResponse.success(id));
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

//    Redis 조회수 업데이트
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(postService.getById(id)));
    }

//    DB 직접 조회수 업데이트
    @PatchMapping("/{id}/view/db")
    public ResponseEntity<ApiResponse<Void>> directUpdate(@PathVariable Long id) {
        postService.directUpdate(id);

        return  ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/test/db")
    public void dbTest(@RequestParam int count) {

        long start = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            postService.directUpdate(1L);
        }

        long end = System.currentTimeMillis();

        log.info("DB Test Total Time = {}ms", end - start);
    }

    @PostMapping("/test/redis")
    public void redisTest(@RequestParam int count) {

        long start = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            viewCountService.increase(1L);
        }

        long end = System.currentTimeMillis();

        log.info("Redis Test Total Time = {}ms", end - start);
    }
}