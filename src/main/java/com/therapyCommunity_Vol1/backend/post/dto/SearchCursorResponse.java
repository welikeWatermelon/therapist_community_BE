package com.therapyCommunity_Vol1.backend.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
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
 *
 * nextScore 가 BigDecimal 인 이유: pg_trgm similarity 결과는 PG 측에서 real(float4) 인데
 * 클라이언트 왕복 시 부동소수 round-trip 정밀도 손실로 동등 비교(=)가 빗나갈 수 있다.
 * Repository 쿼리에서 numeric(10,8) 로 캐스트한 값을 그대로 받아 BigDecimal 로 노출한다.
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
        private final BigDecimal nextScore;
        private final Long nextId;
    }
}
