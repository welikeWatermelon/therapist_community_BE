package com.therapyCommunity_Vol1.backend.scrap.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ScrapListResponse {

    private List<ScrappedPostResponse> scraps;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
}
