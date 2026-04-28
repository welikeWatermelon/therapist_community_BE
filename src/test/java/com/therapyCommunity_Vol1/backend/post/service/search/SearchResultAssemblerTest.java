package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchResultAssemblerTest {

    private TherapyPostRepository therapyPostRepository;
    private TherapyPostReactionRepository therapyPostReactionRepository;
    private TherapyPostCommentRepository therapyPostCommentRepository;
    private SearchResultAssembler assembler;

    @BeforeEach
    void setUp() {
        therapyPostRepository = mock(TherapyPostRepository.class);
        therapyPostReactionRepository = mock(TherapyPostReactionRepository.class);
        therapyPostCommentRepository = mock(TherapyPostCommentRepository.class);
        assembler = new SearchResultAssembler(therapyPostRepository, therapyPostReactionRepository, therapyPostCommentRepository);

        // 기본 count stub — 개별 테스트에서 오버라이드 가능
        when(therapyPostReactionRepository.countByPostIdInAndReactionType(anyList(), any(PostReactionType.class)))
                .thenReturn(List.of());
        when(therapyPostCommentRepository.countActiveByPostIdIn(anyList()))
                .thenReturn(List.of());
    }

    @Test
    void 빈_결과는_빈_응답을_반환한다() {
        SearchCursorResponse response = assembler.assemble(List.of(), 10);

        assertThat(response.getData()).isEmpty();
        assertThat(response.getMeta().isHasNextData()).isFalse();
        assertThat(response.getMeta().getNextScore()).isNull();
        assertThat(response.getMeta().getNextId()).isNull();
    }

    @Test
    void hasNext가_true일_때_커서가_설정된다() {
        // size=2 인데 rows 3개 → hasNext=true
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{3L, new BigDecimal("0.90000000")});
        rows.add(new Object[]{2L, new BigDecimal("0.80000000")});
        rows.add(new Object[]{1L, new BigDecimal("0.70000000")});

        List<TherapyPost> posts = List.of(
                createPost(3L, "test3"),
                createPost(2L, "test2")
        );
        when(therapyPostRepository.findAllByIdInWithAuthor(anyList())).thenReturn(posts);

        SearchCursorResponse response = assembler.assemble(rows, 2);

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getMeta().isHasNextData()).isTrue();
        assertThat(response.getMeta().getNextScore()).isEqualTo(new BigDecimal("0.80000000"));
        assertThat(response.getMeta().getNextId()).isEqualTo(2L);
    }

    @Test
    void hasNext가_false일_때_커서가_null이다() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, new BigDecimal("0.50000000")});

        List<TherapyPost> posts = List.of(createPost(1L, "test1"));
        when(therapyPostRepository.findAllByIdInWithAuthor(anyList())).thenReturn(posts);

        SearchCursorResponse response = assembler.assemble(rows, 10);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getMeta().isHasNextData()).isFalse();
        assertThat(response.getMeta().getNextScore()).isNull();
        assertThat(response.getMeta().getNextId()).isNull();
    }

    @Test
    void 동점_게시글이_페이지_경계에_걸릴_때_커서가_마지막_항목을_가리킨다() {
        // 동일 score(0.80) 게시글 3개 + 다음 페이지 존재 → size=2
        // 커서는 pageRows 의 마지막(id=2, score=0.80)을 가리켜야 한다
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{3L, new BigDecimal("0.80000000")});
        rows.add(new Object[]{2L, new BigDecimal("0.80000000")});
        rows.add(new Object[]{1L, new BigDecimal("0.80000000")}); // hasNext indicator

        List<TherapyPost> posts = List.of(
                createPost(3L, "test3"),
                createPost(2L, "test2")
        );
        when(therapyPostRepository.findAllByIdInWithAuthor(anyList())).thenReturn(posts);

        SearchCursorResponse response = assembler.assemble(rows, 2);

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getMeta().isHasNextData()).isTrue();
        // 동점이므로 nextScore 가 동일하고 nextId 로 타이브레이킹해야 다음 페이지에서 id < 2 조건 적용
        assertThat(response.getMeta().getNextScore()).isEqualTo(new BigDecimal("0.80000000"));
        assertThat(response.getMeta().getNextId()).isEqualTo(2L);
    }

    @Test
    void DB에서_삭제된_게시글은_결과에서_제외된다() {
        // native query 는 id=1,2 를 반환했지만 findAllByIdInWithAuthor 에서 id=2 가 없는 경우
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{2L, new BigDecimal("0.90000000")});
        rows.add(new Object[]{1L, new BigDecimal("0.80000000")});

        // id=2 게시글은 DB 에서 찾을 수 없음 (삭제됨)
        when(therapyPostRepository.findAllByIdInWithAuthor(anyList()))
                .thenReturn(List.of(createPost(1L, "test1")));

        SearchCursorResponse response = assembler.assemble(rows, 10);

        assertThat(response.getData()).hasSize(1);
    }

    private TherapyPost createPost(Long id, String content) {
        User author = User.builder().email("test@test.com").nickname("nickname").build();
        ReflectionTestUtils.setField(author, "id", 1L);
        TherapyPost post = TherapyPost.create(content, null, null, author);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}
