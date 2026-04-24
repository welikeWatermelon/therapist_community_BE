package com.therapyCommunity_Vol1.backend.global.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchAccessLoggerTest {

    private SearchAccessLogger searchAccessLogger;
    private ObjectMapper objectMapper;
    private ListAppender<ILoggingEvent> listAppender;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        searchAccessLogger = new SearchAccessLogger(objectMapper);
        joinPoint = mock(ProceedingJoinPoint.class);

        // Logback ListAppender로 로그 캡처
        Logger logger = (Logger) LoggerFactory.getLogger("SEARCH_ACCESS");
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    void 정상_검색_시_JSON_로그가_기록된다() throws Throwable {
        // given
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(1L);

        List<TherapyPostSummaryResponse> items = List.of(
                mock(TherapyPostSummaryResponse.class),
                mock(TherapyPostSummaryResponse.class)
        );
        SearchCursorResponse searchResponse = new SearchCursorResponse(
                items, new SearchCursorResponse.SearchCursorMeta(false, null, null));
        ApiResponse<SearchCursorResponse> apiResponse = ApiResponse.success(searchResponse);
        ResponseEntity<ApiResponse<SearchCursorResponse>> responseEntity = ResponseEntity.ok(apiResponse);
        when(joinPoint.proceed()).thenReturn(responseEntity);

        // when
        Object result = searchAccessLogger.logSearchAccess(
                joinPoint, userDetails, "감각통합",
                TherapyArea.SENSORY_INTEGRATION, PostType.COMMUNITY, null, null);

        // then
        assertThat(result).isEqualTo(responseEntity);
        assertThat(listAppender.list).hasSize(1);

        String logMessage = listAppender.list.get(0).getFormattedMessage();
        JsonNode json = objectMapper.readTree(logMessage);

        assertThat(json.get("keyword").asText()).isEqualTo("감각통합");
        assertThat(json.get("keywordLength").asInt()).isEqualTo(4);
        assertThat(json.get("therapyArea").asText()).isEqualTo("SENSORY_INTEGRATION");
        assertThat(json.get("postType").asText()).isEqualTo("COMMUNITY");
        assertThat(json.get("userId").asLong()).isEqualTo(1L);
        assertThat(json.get("resultCount").asInt()).isEqualTo(2);
        assertThat(json.get("isZeroResult").asBoolean()).isFalse();
        assertThat(json.get("responseTimeMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(json.get("requestId").asText()).isNotBlank();
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void 예외_발생_시_error_필드가_포함된_로그가_기록된다() throws Throwable {
        // given
        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB connection failed"));

        // when & then
        assertThatThrownBy(() -> searchAccessLogger.logSearchAccess(
                joinPoint, null, "테스트", null, null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB connection failed");

        assertThat(listAppender.list).hasSize(1);

        String logMessage = listAppender.list.get(0).getFormattedMessage();
        JsonNode json = objectMapper.readTree(logMessage);

        assertThat(json.get("keyword").asText()).isEqualTo("테스트");
        assertThat(json.get("resultCount").asInt()).isEqualTo(-1);
        assertThat(json.get("error").asText()).contains("RuntimeException");
        assertThat(json.get("error").asText()).contains("DB connection failed");
        assertThat(json.has("userId")).isFalse(); // null이면 NON_NULL에 의해 생략
    }

    @Test
    void null_파라미터_처리_시_정상_로그가_기록된다() throws Throwable {
        // given: therapyArea, postType, userDetails 모두 null
        SearchCursorResponse searchResponse = new SearchCursorResponse(
                List.of(), new SearchCursorResponse.SearchCursorMeta(false, null, null));
        ApiResponse<SearchCursorResponse> apiResponse = ApiResponse.success(searchResponse);
        ResponseEntity<ApiResponse<SearchCursorResponse>> responseEntity = ResponseEntity.ok(apiResponse);
        when(joinPoint.proceed()).thenReturn(responseEntity);

        // when
        searchAccessLogger.logSearchAccess(
                joinPoint, null, "검색어", null, null, null, null);

        // then
        assertThat(listAppender.list).hasSize(1);

        String logMessage = listAppender.list.get(0).getFormattedMessage();
        JsonNode json = objectMapper.readTree(logMessage);

        assertThat(json.get("keyword").asText()).isEqualTo("검색어");
        assertThat(json.has("therapyArea")).isFalse(); // @JsonInclude NON_NULL
        assertThat(json.has("postType")).isFalse();
        assertThat(json.has("userId")).isFalse(); // null이면 NON_NULL에 의해 생략
        assertThat(json.get("resultCount").asInt()).isEqualTo(0);
        assertThat(json.get("isZeroResult").asBoolean()).isTrue();
    }

    @Test
    void resultCount_추출이_정확하다() throws Throwable {
        // given: 5개 결과
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(99L);

        List<TherapyPostSummaryResponse> items = List.of(
                mock(TherapyPostSummaryResponse.class),
                mock(TherapyPostSummaryResponse.class),
                mock(TherapyPostSummaryResponse.class),
                mock(TherapyPostSummaryResponse.class),
                mock(TherapyPostSummaryResponse.class)
        );
        SearchCursorResponse searchResponse = new SearchCursorResponse(
                items, new SearchCursorResponse.SearchCursorMeta(true, new BigDecimal("0.35000000"), 38L));
        ApiResponse<SearchCursorResponse> apiResponse = ApiResponse.success(searchResponse);
        ResponseEntity<ApiResponse<SearchCursorResponse>> responseEntity = ResponseEntity.ok(apiResponse);
        when(joinPoint.proceed()).thenReturn(responseEntity);

        // when
        searchAccessLogger.logSearchAccess(
                joinPoint, userDetails, "물리치료",
                TherapyArea.PHYSICAL, null, new BigDecimal("0.45000000"), 42L);

        // then
        String logMessage = listAppender.list.get(0).getFormattedMessage();
        JsonNode json = objectMapper.readTree(logMessage);

        assertThat(json.get("resultCount").asInt()).isEqualTo(5);
        assertThat(json.get("isZeroResult").asBoolean()).isFalse();
        assertThat(json.get("lastScore").decimalValue()).isEqualByComparingTo("0.45000000");
        assertThat(json.get("lastId").asLong()).isEqualTo(42L);
        assertThat(json.get("userId").asLong()).isEqualTo(99L);
    }
}
