package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.PostImageResponse;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.post.service.PostImageService;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.support.ProfileImageUrlAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 검색 쿼리 결과(id, score 행 목록)를 SearchCursorResponse 로 조립하는 공통 컴포넌트.
 *
 * GinTrigramSearchStrategy 와 PgVectorSearchStrategy 모두 이 컴포넌트에 위임한다.
 * - hasNext 판별 + take 개로 트림
 * - ID 추출 → findAllByIdInWithAuthor 로 2단계 fetch
 * - native 결과 순서대로 DTO 매핑
 * - 커서(nextScore, nextId) 추출
 */
@Component
@RequiredArgsConstructor
public class SearchResultAssembler {

    private final TherapyPostRepository therapyPostRepository;
    private final TherapyPostReactionRepository therapyPostReactionRepository;
    private final TherapyPostCommentRepository therapyPostCommentRepository;
    private final ProfileImageUrlAssembler profileImageUrlAssembler;
    private final PostImageService postImageService;

    /**
     * @param rows native query 결과. 각 행은 [postId(Number), score(BigDecimal)].
     *             score DESC, id DESC 로 정렬되어 있어야 한다.
     * @param size 요청 페이지 크기 (rows 는 size+1 개까지 포함 가능)
     */
    public SearchCursorResponse assemble(List<Object[]> rows, int size, boolean canViewPrivate) {
        boolean hasNextData = rows.size() > size;
        List<Object[]> pageRows = hasNextData ? rows.subList(0, size) : rows;

        if (pageRows.isEmpty()) {
            return new SearchCursorResponse(
                    List.of(),
                    new SearchCursorResponse.SearchCursorMeta(false, null, null)
            );
        }

        List<Long> ids = pageRows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .toList();

        Map<Long, TherapyPost> byId = therapyPostRepository.findAllByIdInWithAuthor(ids).stream()
                .collect(Collectors.toMap(TherapyPost::getId, Function.identity()));

        List<TherapyPost> orderedPosts = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        List<TherapyPostSummaryResponse> items = toSummaries(orderedPosts, canViewPrivate);

        BigDecimal nextScore = null;
        Long nextId = null;
        if (hasNextData) {
            Object[] lastRow = pageRows.get(pageRows.size() - 1);
            nextId = ((Number) lastRow[0]).longValue();
            nextScore = (BigDecimal) lastRow[1];
        }

        return new SearchCursorResponse(
                items,
                new SearchCursorResponse.SearchCursorMeta(hasNextData, nextScore, nextId)
        );
    }

    /**
     * 폴백 검색용 — hasNext를 항상 false로 반환한다.
     * 폴백 min-score로 조회한 결과의 커서가 원래 minScore 기준 다음 페이지와
     * 불일치하는 문제를 방지한다.
     */
    public SearchCursorResponse assembleNoNext(List<Object[]> rows, int size, boolean canViewPrivate) {
        List<Object[]> pageRows = rows.size() > size ? rows.subList(0, size) : rows;

        if (pageRows.isEmpty()) {
            return new SearchCursorResponse(
                    List.of(),
                    new SearchCursorResponse.SearchCursorMeta(false, null, null)
            );
        }

        List<Long> ids = pageRows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .toList();

        Map<Long, TherapyPost> byId = therapyPostRepository.findAllByIdInWithAuthor(ids).stream()
                .collect(Collectors.toMap(TherapyPost::getId, Function.identity()));

        List<TherapyPost> orderedPosts = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        List<TherapyPostSummaryResponse> items = toSummaries(orderedPosts, canViewPrivate);

        return new SearchCursorResponse(
                items,
                new SearchCursorResponse.SearchCursorMeta(false, null, null)
        );
    }

    private List<TherapyPostSummaryResponse> toSummaries(List<TherapyPost> posts, boolean canViewPrivate) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(TherapyPost::getId).toList();

        Map<Long, Long> likeCounts = toCountMap(
                therapyPostReactionRepository.countByPostIdInAndReactionType(postIds, PostReactionType.LIKE)
        );
        Map<Long, Long> commentCounts = toCountMap(
                therapyPostCommentRepository.countActiveByPostIdIn(postIds)
        );

        return posts.stream()
                .map(post -> TherapyPostSummaryResponse.from(
                        post,
                        likeCounts.getOrDefault(post.getId(), 0L),
                        commentCounts.getOrDefault(post.getId(), 0L),
                        false,
                        canViewPrivate,
                        profileImageUrlAssembler.toFullUrl(post.getAuthor().getProfileImageUrl()),
                        postImageService.getImagesForPostUnchecked(post.getId()).stream()
                                .map(PostImageResponse::getImageUrl)
                                .toList()
                ))
                .toList();
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
