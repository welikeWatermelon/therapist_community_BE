package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * GIN trigram (pg_trgm) 기반 검색 전략.
 *
 * word_similarity(<%) 연산자 + ILIKE fallback 으로 후보를 모으고
 * similarity 점수로 정렬한다.
 *
 * 두 단계 fetch: 1) native 로 (id, score) + 정렬, 2) ID 로 author 까지 EntityGraph fetch.
 * 페이지네이션은 (lastScore, lastId) 커서 기반. take+1 조회로 hasNextData 를 판단한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.strategy", havingValue = "gin", matchIfMissing = true)
public class GinTrigramSearchStrategy implements PostSearchStrategy {

    private final TherapyPostRepository therapyPostRepository;
    private final SearchResultAssembler assembler;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public SearchCursorResponse search(
            PostSearchCondition condition,
            BigDecimal lastScore,
            Long lastId,
            int size,
            boolean canViewPrivate
    ) {
        // pg_trgm <% 연산자(word_similarity)의 임계값을 트랜잭션 스코프로 0.1로 설정.
        // 트랜잭션 종료 시 자동으로 원복된다.
        entityManager.createNativeQuery("SET LOCAL pg_trgm.word_similarity_threshold = 0.1")
                .executeUpdate();

        // searchText가 소문자로 저장되므로 keyword도 소문자 변환
        String rawKeyword = condition.getKeyword().trim().toLowerCase();
        String escapedKeyword = condition.getEscapedKeyword().trim().toLowerCase();
        String area = condition.getTherapyArea() != null ? condition.getTherapyArea().name() : null;
        String type = condition.getPostType() != null ? condition.getPostType().name() : null;

        int limit = size + 1;
        boolean firstPage = (lastScore == null && lastId == null);

        // PRIVATE UX 개편: 모든 role이 PUBLIC + PRIVATE 결과를 조회. 마스킹은 assembler에서.
        List<Object[]> rows = firstPage
                ? therapyPostRepository.searchIdsByRelevanceFirstPage(
                        rawKeyword, escapedKeyword, area, type, limit)
                : therapyPostRepository.searchIdsByRelevanceNextPage(
                        rawKeyword, escapedKeyword, area, type, lastScore, lastId, limit);

        return assembler.assemble(rows, size, canViewPrivate);
    }
}
