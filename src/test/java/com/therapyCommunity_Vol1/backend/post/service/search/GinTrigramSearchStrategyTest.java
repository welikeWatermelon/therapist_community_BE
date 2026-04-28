package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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

class GinTrigramSearchStrategyTest {

    private TherapyPostRepository therapyPostRepository;
    private SearchResultAssembler assembler;
    private EntityManager entityManager;
    private GinTrigramSearchStrategy strategy;

    @BeforeEach
    void setUp() {
        therapyPostRepository = mock(TherapyPostRepository.class);
        TherapyPostReactionRepository reactionRepo = mock(TherapyPostReactionRepository.class);
        TherapyPostCommentRepository commentRepo = mock(TherapyPostCommentRepository.class);
        when(reactionRepo.countByPostIdInAndReactionType(anyList(), any(PostReactionType.class)))
                .thenReturn(List.of());
        when(commentRepo.countActiveByPostIdIn(anyList()))
                .thenReturn(List.of());
        assembler = new SearchResultAssembler(therapyPostRepository, reactionRepo, commentRepo);
        entityManager = mock(EntityManager.class);

        strategy = new GinTrigramSearchStrategy(therapyPostRepository, assembler);
        ReflectionTestUtils.setField(strategy, "entityManager", entityManager);

        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.executeUpdate()).thenReturn(0);
    }

    @Test
    void 첫_페이지_검색_결과를_반환한다() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, new BigDecimal("0.80000000")});

        when(therapyPostRepository.searchIdsByRelevanceFirstPage(
                anyString(), anyString(), isNull(), isNull(), eq(11)))
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
    void publicOnly_true면_가시성_쿼리를_사용한다() {
        PostSearchCondition condition = new PostSearchCondition("언어치료", null, null);

        when(therapyPostRepository.searchIdsByRelevanceFirstPageAndVisibility(
                anyString(), anyString(), isNull(), isNull(), anyString(), eq(11)))
                .thenReturn(List.of());

        SearchCursorResponse response = strategy.search(condition, null, null, 10, true);

        assertThat(response.getData()).isEmpty();
        assertThat(response.getMeta().isHasNextData()).isFalse();
    }
}
