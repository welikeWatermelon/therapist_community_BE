package com.therapyCommunity_Vol1.backend.global.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Aspect
@Component
public class SearchAccessLogger {

    private static final Logger log = LoggerFactory.getLogger("SEARCH_ACCESS");
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"));

    private final ObjectMapper objectMapper;

    public SearchAccessLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("execution(* com.therapyCommunity_Vol1.backend.post.controller.PostController.searchPosts(..))")
    public Object logSearchAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        CustomUserDetails userDetails = (CustomUserDetails) args[0];
        String keyword = (String) args[1];
        TherapyArea therapyArea = (TherapyArea) args[2];
        PostType postType = (PostType) args[3];
        BigDecimal lastScore = (BigDecimal) args[4];
        Long lastId = (Long) args[5];

        String requestId = UUID.randomUUID().toString();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            Object result = joinPoint.proceed();
            stopWatch.stop();

            int resultCount = extractResultCount(result);

            SearchAccessLog accessLog = SearchAccessLog.builder()
                    .timestamp(ISO_FORMATTER.format(Instant.now()))
                    .requestId(requestId)
                    .keyword(keyword)
                    .keywordLength(keyword != null ? keyword.length() : 0)
                    .therapyArea(therapyArea != null ? therapyArea.name() : null)
                    .postType(postType != null ? postType.name() : null)
                    .userId(userDetails != null ? userDetails.getUserId() : null)
                    .lastScore(lastScore)
                    .lastId(lastId)
                    .resultCount(resultCount)
                    .responseTimeMs(stopWatch.getTotalTimeMillis())
                    .isZeroResult(resultCount == 0)
                    .build();

            log.info(objectMapper.writeValueAsString(accessLog));
            return result;

        } catch (Throwable ex) {
            stopWatch.stop();

            SearchAccessLog accessLog = SearchAccessLog.builder()
                    .timestamp(ISO_FORMATTER.format(Instant.now()))
                    .requestId(requestId)
                    .keyword(keyword)
                    .keywordLength(keyword != null ? keyword.length() : 0)
                    .therapyArea(therapyArea != null ? therapyArea.name() : null)
                    .postType(postType != null ? postType.name() : null)
                    .userId(userDetails != null ? userDetails.getUserId() : null)
                    .lastScore(lastScore)
                    .lastId(lastId)
                    .resultCount(-1)
                    .responseTimeMs(stopWatch.getTotalTimeMillis())
                    .isZeroResult(false)
                    .error(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();

            log.info(objectMapper.writeValueAsString(accessLog));
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private int extractResultCount(Object result) {
        try {
            ResponseEntity<ApiResponse<SearchCursorResponse>> response =
                    (ResponseEntity<ApiResponse<SearchCursorResponse>>) result;
            ApiResponse<SearchCursorResponse> body = response.getBody();
            if (body != null && body.getData() != null) {
                return body.getData().getData().size();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
