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

    @Around("execution(* com.therapyCommunity_Vol1.backend.post.controller.PostController.searchPosts(..)) && args(userDetails, keyword, therapyArea, postType, lastScore, lastId, ..)")
    public Object logSearchAccess(
            ProceedingJoinPoint joinPoint,
            CustomUserDetails userDetails,
            String keyword,
            TherapyArea therapyArea,
            PostType postType,
            BigDecimal lastScore,
            Long lastId
    ) throws Throwable {

        String requestId = UUID.randomUUID().toString();
        StopWatch stopWatch = new StopWatch(); // 시간 측정
        stopWatch.start();

        try {
            Object result = joinPoint.proceed(); // 원본 메서드 실행 (searchPost()실행)
            stopWatch.stop();

            int resultCount = extractResultCount(result); // 결과 개수 추출

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

    private int extractResultCount(Object result) {
        if (!(result instanceof ResponseEntity<?> responseEntity)) {
            log.warn("extractResultCount: 예상하지 못한 반환 타입 — {}", result.getClass().getName());
            return 0;
        }
        Object rawBody = responseEntity.getBody();
        if (!(rawBody instanceof ApiResponse<?> apiResponse)) {
            log.warn("extractResultCount: ResponseEntity body가 ApiResponse가 아님 — {}",
                    rawBody != null ? rawBody.getClass().getName() : "null");
            return 0;
        }
        Object data = apiResponse.getData();
        if (!(data instanceof SearchCursorResponse cursorResponse)) {
            log.warn("extractResultCount: ApiResponse data가 SearchCursorResponse가 아님 — {}",
                    data != null ? data.getClass().getName() : "null");
            return 0;
        }
        return cursorResponse.getData().size();
    }
}
