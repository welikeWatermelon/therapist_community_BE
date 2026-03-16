package com.therapyCommunity_Vol1.backend.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostListResponse {

    private List<TherapyPostSummaryResponse> posts;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private Boolean hasNext;
}
