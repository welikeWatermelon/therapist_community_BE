package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;

import java.math.BigDecimal;

public interface PostSearchStrategy {

    SearchCursorResponse search(
            PostSearchCondition condition,
            BigDecimal lastScore,
            Long lastId,
            int size,
            boolean publicOnly
    );
}
