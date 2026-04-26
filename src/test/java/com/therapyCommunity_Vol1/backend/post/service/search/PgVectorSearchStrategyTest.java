package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgVectorSearchStrategyTest {

    private TherapyPostRepository therapyPostRepository;
    private EmbeddingService embeddingService;
    private SearchResultAssembler assembler;
    private PgVectorSearchStrategy strategy;

    @BeforeEach
    void setUp() {
        therapyPostRepository = mock(TherapyPostRepository.class);
        embeddingService = mock(EmbeddingService.class);
        assembler = new SearchResultAssembler(therapyPostRepository);

        strategy = new PgVectorSearchStrategy(
                therapyPostRepository, embeddingService, assembler, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(strategy, "minScore", new BigDecimal("0.3"));

        // 임베딩 서비스 mock
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
    }

    @Test
    void 첫_페이지_검색_결과를_반환한다() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, new BigDecimal("0.75000000")});

        when(therapyPostRepository.vectorSearchFirstPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class), eq(11)))
                .thenReturn(rows);

        User author = User.builder().email("test@test.com").nickname("nick").build();
        ReflectionTestUtils.setField(author, "id", 1L);
        TherapyPost post = TherapyPost.create("언어치료 내용", null, null, author);
        ReflectionTestUtils.setField(post, "id", 1L);
        when(therapyPostRepository.findAllByIdInWithAuthor(anyList()))
                .thenReturn(List.of(post));

        SearchCursorResponse response = strategy.search(condition, null, null, 10, false);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getMeta().isHasNextData()).isFalse();
    }

    @Test
    void 결과가_없으면_빈_응답을_반환한다() {
        PostSearchCondition condition = new PostSearchCondition("존재하지않는검색어", null, null);

        when(therapyPostRepository.vectorSearchFirstPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class), eq(11)))
                .thenReturn(List.of());

        SearchCursorResponse response = strategy.search(condition, null, null, 10, false);

        assertThat(response.getData()).isEmpty();
        assertThat(response.getMeta().isHasNextData()).isFalse();
    }

    @Test
    void publicOnly_true면_가시성_쿼리를_사용한다() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        when(therapyPostRepository.vectorSearchFirstPageAndVisibility(
                anyString(), isNull(), isNull(), anyString(), any(BigDecimal.class), eq(11)))
                .thenReturn(List.of());

        SearchCursorResponse response = strategy.search(condition, null, null, 10, true);

        assertThat(response.getData()).isEmpty();
    }

    @Test
    void 커서_페이지네이션이_동작한다() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        when(therapyPostRepository.vectorSearchNextPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class),
                any(BigDecimal.class), eq(5L), eq(11)))
                .thenReturn(List.of());

        SearchCursorResponse response = strategy.search(
                condition, new BigDecimal("0.50000000"), 5L, 10, false);

        assertThat(response.getData()).isEmpty();
    }
}
