package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * pgvector 기반 시맨틱 검색 전략.
 *
 * 검색 키워드를 OpenAI text-embedding-3-small 모델로 벡터화한 뒤
 * 코사인 유사도 기반으로 게시글을 검색한다.
 * 임베딩이 없는 게시글은 결과에서 제외된다.
 */
@Component
@ConditionalOnProperty(name = "app.search.strategy", havingValue = "pgvector")
public class PgVectorSearchStrategy implements PostSearchStrategy {

    private static final BigDecimal FALLBACK_MIN_SCORE = new BigDecimal("0.2");
    private static final int FALLBACK_THRESHOLD = 3;

    private final TherapyPostRepository therapyPostRepository;
    private final EmbeddingService embeddingService;
    private final SearchResultAssembler assembler;
    private final DistributionSummary scoreDistribution;

    @Value("${app.search.vector.min-score}")
    private BigDecimal minScore;

    public PgVectorSearchStrategy(TherapyPostRepository therapyPostRepository,
                                   EmbeddingService embeddingService,
                                   SearchResultAssembler assembler,
                                   MeterRegistry meterRegistry) {
        this.therapyPostRepository = therapyPostRepository;
        this.embeddingService = embeddingService;
        this.assembler = assembler;
        this.scoreDistribution = DistributionSummary.builder("search.query.score.distribution")
                .description("벡터 검색 결과 유사도 점수 분포")
                .register(meterRegistry);
    }

    @Override
    public SearchCursorResponse search(
            PostSearchCondition condition,
            BigDecimal lastScore,
            Long lastId,
            int size,
            boolean publicOnly
    ) {
        // 검색 키워드를 벡터로 변환
        float[] queryEmbedding = embeddingService.embed(condition.getKeyword().trim());
        String embeddingStr = EmbeddingService.toVectorString(queryEmbedding);

        String area = condition.getTherapyArea() != null ? condition.getTherapyArea().name() : null;
        String type = condition.getPostType() != null ? condition.getPostType().name() : null;

        int limit = size + 1;
        boolean firstPage = (lastScore == null && lastId == null);

        List<Object[]> rows;

        if (firstPage) {
            rows = publicOnly
                    ? therapyPostRepository.vectorSearchFirstPageAndVisibility(
                            embeddingStr, area, type, Visibility.PUBLIC.name(), minScore, limit)
                    : therapyPostRepository.vectorSearchFirstPage(
                            embeddingStr, area, type, minScore, limit);
        } else {
            rows = publicOnly
                    ? therapyPostRepository.vectorSearchNextPageAndVisibility(
                            embeddingStr, area, type, Visibility.PUBLIC.name(), minScore,
                            lastScore, lastId, limit)
                    : therapyPostRepository.vectorSearchNextPage(
                            embeddingStr, area, type, minScore, lastScore, lastId, limit);
        }

        // 첫 페이지에서 결과가 부족하면 min-score를 완화하여 재검색
        boolean fallbackApplied = false;
        if (firstPage && rows.size() < FALLBACK_THRESHOLD && FALLBACK_MIN_SCORE.compareTo(minScore) < 0) {
            rows = publicOnly
                    ? therapyPostRepository.vectorSearchFirstPageAndVisibility(
                            embeddingStr, area, type, Visibility.PUBLIC.name(), FALLBACK_MIN_SCORE, limit)
                    : therapyPostRepository.vectorSearchFirstPage(
                            embeddingStr, area, type, FALLBACK_MIN_SCORE, limit);
            fallbackApplied = true;
        }

        // 검색 결과 점수 분포 기록 (실제 반환 행만, hasNext 여분 행 제외)
        List<Object[]> pageRows = rows.size() > size ? rows.subList(0, size) : rows;
        for (Object[] row : pageRows) {
            BigDecimal score = (BigDecimal) row[1];
            scoreDistribution.record(score.doubleValue());
        }

        // 폴백이 발동된 경우: 다음 페이지 쿼리가 원래 minScore를 사용하면
        // 폴백 범위(minScore 미만) 결과와 커서가 불일치하므로 hasNext를 강제 false 처리
        if (fallbackApplied) {
            return assembler.assembleNoNext(rows, size);
        }

        return assembler.assemble(rows, size);
    }
}
