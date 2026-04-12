package com.example.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostResponse {
    private Long id;
    private String title;
    private String content;
}