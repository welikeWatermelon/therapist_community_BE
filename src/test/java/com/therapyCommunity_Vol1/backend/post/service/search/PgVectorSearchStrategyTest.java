package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        TherapyPostReactionRepository reactionRepo = mock(TherapyPostReactionRepository.class);
        TherapyPostCommentRepository commentRepo = mock(TherapyPostCommentRepository.class);
        when(reactionRepo.countByPostIdInAndReactionType(anyList(), any(PostReactionType.class)))
                .thenReturn(List.of());
        when(commentRepo.countActiveByPostIdIn(anyList()))
                .thenReturn(List.of());
        assembler = new SearchResultAssembler(therapyPostRepository, reactionRepo, commentRepo);

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

    /**
     * PRIVATE UX 개편 후 — canViewPrivate 값과 무관하게 visibility 필터 없이 통합 쿼리 호출.
     * 회귀 보호: 누가 publicOnly 분기를 다시 추가하면 verify(never)에서 실패.
     */
    @Test
    void canViewPrivate_false여도_visibility_필터_없이_통합_쿼리_사용() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        when(therapyPostRepository.vectorSearchFirstPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class), eq(11)))
                .thenReturn(List.of());

        strategy.search(condition, null, null, 10, false);

        // fallback 발동 시 같은 메서드를 다시 호출하므로 atLeastOnce.
        verify(therapyPostRepository, atLeastOnce()).vectorSearchFirstPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class), eq(11));
        verify(therapyPostRepository, never()).vectorSearchFirstPageAndVisibility(
                anyString(), any(), any(), anyString(), any(BigDecimal.class), anyInt());
    }

    @Test
    void canViewPrivate_true도_동일하게_통합_쿼리_사용() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        when(therapyPostRepository.vectorSearchFirstPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class), eq(11)))
                .thenReturn(List.of());

        strategy.search(condition, null, null, 10, true);

        // fallback 발동 시 같은 메서드를 다시 호출하므로 atLeastOnce.
        verify(therapyPostRepository, atLeastOnce()).vectorSearchFirstPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class), eq(11));
        verify(therapyPostRepository, never()).vectorSearchFirstPageAndVisibility(
                anyString(), any(), any(), anyString(), any(BigDecimal.class), anyInt());
    }

    @Test
    void 다음_페이지에서도_visibility_필터_없이_통합_쿼리_사용() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        when(therapyPostRepository.vectorSearchNextPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class),
                any(BigDecimal.class), eq(5L), eq(11)))
                .thenReturn(List.of());

        strategy.search(condition, new BigDecimal("0.50000000"), 5L, 10, false);

        verify(therapyPostRepository).vectorSearchNextPage(
                anyString(), isNull(), isNull(), any(BigDecimal.class),
                any(BigDecimal.class), eq(5L), eq(11));
        verify(therapyPostRepository, never()).vectorSearchNextPageAndVisibility(
                anyString(), any(), any(), anyString(), any(BigDecimal.class),
                any(BigDecimal.class), anyLong(), anyInt());
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
