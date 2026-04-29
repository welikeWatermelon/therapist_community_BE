package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;

import java.math.BigDecimal;

public interface PostSearchStrategy {

    /**
     * @param canViewPrivate THERAPIST/ADMIN이면 true. PRIVATE 검색 결과를 일반 형태로 응답에 노출.
     *                       USER이면 false. PRIVATE도 결과에 포함되지만 SearchResultAssembler가
     *                       contentPreview를 마스킹하고 accessLocked=true 표시.
     */
    SearchCursorResponse search(
            PostSearchCondition condition,
            BigDecimal lastScore,
            Long lastId,
            int size,
            boolean canViewPrivate
    );
}
