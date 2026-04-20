package com.therapyCommunity_Vol1.backend.global.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchAccessLog {
    private final String timestamp;
    private final String requestId;
    private final String keyword;
    private final int keywordLength;
    private final String therapyArea;
    private final String postType;
    private final Long userId;
    private final BigDecimal lastScore;
    private final Long lastId;
    private final int resultCount;
    private final long responseTimeMs;
    @JsonProperty("isZeroResult")
    private final boolean isZeroResult;
    private final String error;
}
