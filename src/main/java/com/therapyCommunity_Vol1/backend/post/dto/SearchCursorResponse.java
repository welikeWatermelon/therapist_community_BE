package com.therapyCommunity_Vol1.backend.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * RELEVANCE 검색 (무한스크롤) 전용 응답 DTO.
 *
 * pg_trgm similarity 점수 + id 기반 커서를 사용하므로 단일 문자열 nextCursor 를
 * 쓰는 {@code CursorPagedResponse} 와 분리해서 가져간다.
 *
 * - data : 현재 페이지 게시글 요약 목록
 * - meta : hasNextData / nextScore / nextId
 *   nextScore, nextId 는 마지막 페이지일 경우 모두 null
 */
@Getter
@AllArgsConstructor
public class SearchCursorResponse {

    private final List<TherapyPostSummaryResponse> data;
    private final SearchCursorMeta meta;

    @Getter
    @AllArgsConstructor
    public static class SearchCursorMeta {
        private final boolean hasNextData;
        private final Double nextScore;
        private final Long nextId;
    }
}
