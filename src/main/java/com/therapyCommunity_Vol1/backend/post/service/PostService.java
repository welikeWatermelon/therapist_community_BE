package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.global.cache.PostViewCountService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.FeedSortType;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.*;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final TherapyPostRepository therapyPostRepository;
    private final TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private final TherapyPostReactionRepository therapyPostReactionRepository;
    private final TherapyPostCommentRepository therapyPostCommentRepository;
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;
    private final ResourceAccessValidator resourceAccessValidator;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final PostViewCountService postViewCountService;

    @Transactional
    public void recalculatePopularityScore(Long postId) {
        therapyPostRepository.recalculatePopularityScore(postId);
    }

    @Transactional
    public TherapyPostDetailResponse createPost(
            Long userId,
            UserRole currentUserRole,
            CreateTherapyPostRequest request
    ) {
        if (request.getVisibility() == Visibility.PRIVATE) {
            visibilityPolicy.checkCanWritePrivate(currentUserRole);
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        TherapyPost post = TherapyPost.create(
                request.getContent(),
                request.getTherapyArea(),
                request.getVisibility(),
                author
        );
        TherapyPost saved = therapyPostRepository.save(post);

        return TherapyPostDetailResponse.from(saved, userId, author.getRole());
    }

    public PagedResponse<TherapyPostSummaryResponse> getPosts(
            int page,
            int size,
            PostSortType sortType,
            PostSearchCondition condition,
            UserRole currentUserRole
    ) {
        Page<TherapyPost> result = findPosts(page, size, sortType, condition, currentUserRole);

        List<TherapyPostSummaryResponse> posts = toSummaries(result.getContent());

        return PagedResponse.from(result, posts);
    }

    private Page<TherapyPost> findPosts(int page, int size, PostSortType sortType,
                                         PostSearchCondition condition, UserRole role) {
        boolean publicOnly = !visibilityPolicy.canViewPrivate(role);

        // RELEVANCE 는 별도 무한스크롤 엔드포인트(/posts/search)에서만 노출된다.
        // 이 경로로 sortType=RELEVANCE 가 들어오면 toSort() 가 LATEST 정렬로 폴백한다.
        Pageable pageable = PageRequest.of(page, size, toSort(sortType));

        if (condition.isEmpty()) {
            return publicOnly
                    ? therapyPostRepository.findByDeletedAtIsNullAndVisibility(Visibility.PUBLIC, pageable)
                    : therapyPostRepository.findByDeletedAtIsNull(pageable);
        } else if (condition.hasKeyword()) {
            return publicOnly
                    ? therapyPostRepository.searchByKeywordAndVisibility(
                            condition.getEscapedKeyword().trim(), condition.getTherapyArea(),
                            condition.getPostType(), Visibility.PUBLIC, pageable)
                    : therapyPostRepository.searchByKeyword(
                            condition.getEscapedKeyword().trim(), condition.getTherapyArea(),
                            condition.getPostType(), pageable);
        } else {
            return publicOnly
                    ? therapyPostRepository.searchByFilterAndVisibility(
                            condition.getTherapyArea(), condition.getPostType(), Visibility.PUBLIC, pageable)
                    : therapyPostRepository.searchByFilter(
                            condition.getTherapyArea(), condition.getPostType(), pageable);
        }
    }

    /**
     * RELEVANCE 검색 (무한스크롤) — 외부 진입 메서드.
     * lastScore/lastId 가 모두 null 이면 첫 페이지, 모두 있으면 다음 페이지.
     * 컨트롤러에서 두 값의 쌍 검증을 통과한 뒤 호출되는 것을 가정한다.
     */
    public SearchCursorResponse searchPostsByRelevance(
            PostSearchCondition condition,
            Double lastScore,
            Long lastId,
            int size,
            UserRole role
    ) {
        boolean publicOnly = !visibilityPolicy.canViewPrivate(role);
        return findPostsByRelevance(condition, publicOnly, lastScore, lastId, size);
    }

    /**
     * RELEVANCE 정렬 — pg_trgm similarity + ILIKE fallback 으로 점수 매겨 정렬.
     * 두 단계 fetch: 1) native 로 (id, score) + 정렬, 2) ID 로 author 까지 EntityGraph fetch.
     * native query 는 @EntityGraph 가 동작하지 않아 N+1 회피 목적.
     *
     * 페이지네이션은 (lastScore, lastId) 커서 기반. take+1 조회로 hasNextData 를 판단한다.
     */
    private SearchCursorResponse findPostsByRelevance(
            PostSearchCondition condition,
            boolean publicOnly,
            Double lastScore,
            Long lastId,
            int size
    ) {
        // similarity 는 raw, ILIKE 는 escaped — 두 함수가 메타문자 의미가 달라 분리 필수
        String rawKeyword = condition.getKeyword().trim();
        String escapedKeyword = condition.getEscapedKeyword().trim();
        String area = condition.getTherapyArea() != null ? condition.getTherapyArea().name() : null;
        String type = condition.getPostType() != null ? condition.getPostType().name() : null;

        int limit = size + 1; // hasNext 판별용 take+1 조회
        boolean firstPage = (lastScore == null && lastId == null);

        List<Object[]> rows;
        if (firstPage) {
            rows = publicOnly
                    ? therapyPostRepository.searchIdsByRelevanceFirstPageAndVisibility(
                            rawKeyword, escapedKeyword, area, type, Visibility.PUBLIC.name(), limit)
                    : therapyPostRepository.searchIdsByRelevanceFirstPage(
                            rawKeyword, escapedKeyword, area, type, limit);
        } else {
            rows = publicOnly
                    ? therapyPostRepository.searchIdsByRelevanceNextPageAndVisibility(
                            rawKeyword, escapedKeyword, area, type, Visibility.PUBLIC.name(),
                            lastScore, lastId, limit)
                    : therapyPostRepository.searchIdsByRelevanceNextPage(
                            rawKeyword, escapedKeyword, area, type, lastScore, lastId, limit);
        }

        // hasNext 판별 + take 개로 트림
        boolean hasNextData = rows.size() > size;
        List<Object[]> pageRows = hasNextData ? rows.subList(0, size) : rows;

        if (pageRows.isEmpty()) {
            return new SearchCursorResponse(
                    List.of(),
                    new SearchCursorResponse.SearchCursorMeta(false, null, null)
            );
        }

        // ID 추출 → author 까지 fetch
        List<Long> ids = pageRows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .toList();
        Map<Long, TherapyPost> byId = therapyPostRepository.findAllByIdInWithAuthor(ids).stream()
                .collect(Collectors.toMap(TherapyPost::getId, Function.identity()));

        // IN 절은 정렬을 보존하지 않으므로 native 결과의 ID 순서대로 재정렬
        List<TherapyPostSummaryResponse> items = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(p -> TherapyPostSummaryResponse.from(p, false))
                .toList();

        // 다음 커서: 트림된 마지막 행의 (score, id). 마지막 페이지면 둘 다 null.
        Double nextScore = null;
        Long nextId = null;
        if (hasNextData) {
            Object[] lastRow = pageRows.get(pageRows.size() - 1);
            nextId = ((Number) lastRow[0]).longValue();
            nextScore = ((Number) lastRow[1]).doubleValue();
        }

        return new SearchCursorResponse(
                items,
                new SearchCursorResponse.SearchCursorMeta(hasNextData, nextScore, nextId)
        );
    }

    public PagedResponse<TherapyPostSummaryResponse> getMyPosts(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<TherapyPost> result = therapyPostRepository.findByAuthorIdAndDeletedAtIsNull(userId, pageable);

        List<TherapyPostSummaryResponse> posts = toSummaries(result.getContent());

        return PagedResponse.from(result, posts);
    }

    private static final int FEED_MAX_SIZE = 50;

    /**
     * 커서 기반 피드 조회 (무한스크롤용)
     *
     * @param size     요청 페이지 크기 (1~50, 컨트롤러 기본값 20)
     * @param cursor   이전 페이지 마지막 항목의 Base64 커서. null이면 첫 페이지
     * @param role     USER는 PUBLIC만, THERAPIST/ADMIN은 전체 조회
     * @param sortType LATEST(최신순) 또는 POPULAR(인기순)
     */
    public CursorPagedResponse<TherapyPostSummaryResponse> getPostsFeed(
            int size, String cursor, UserRole role, FeedSortType sortType) {
        size = Math.min(Math.max(size, 1), FEED_MAX_SIZE);
        boolean publicOnly = !visibilityPolicy.canViewPrivate(role);

        return switch (sortType) {
            case LATEST -> fetchLatestFeed(size, cursor, publicOnly);
            case POPULAR -> fetchPopularFeed(size, cursor, publicOnly);
        };
    }

    private CursorPagedResponse<TherapyPostSummaryResponse> fetchLatestFeed(
            int size, String cursor, boolean publicOnly) {
        PostCursor postCursor = cursor != null ? PostCursor.decode(cursor) : null;
        Pageable limit = PageRequest.of(0, size + 1);

        List<TherapyPost> posts;
        if (postCursor == null) {
            posts = publicOnly
                    ? therapyPostRepository.findFeedLatestByVisibility(Visibility.PUBLIC, limit)
                    : therapyPostRepository.findFeedLatest(limit);
        } else {
            posts = publicOnly
                    ? therapyPostRepository.findFeedLatestByVisibility(
                            Visibility.PUBLIC, postCursor.createdAt(), postCursor.id(), limit)
                    : therapyPostRepository.findFeedLatest(
                            postCursor.createdAt(), postCursor.id(), limit);
        }

        List<TherapyPostSummaryResponse> dtos = toSummaries(posts);

        return CursorPagedResponse.of(dtos, size, item ->
                new PostCursor(item.getCreatedAt(), item.getId()).encode());
    }

    private CursorPagedResponse<TherapyPostSummaryResponse> fetchPopularFeed(
            int size, String cursor, boolean publicOnly) {
        PopularCursor popCursor = cursor != null ? PopularCursor.decode(cursor) : null;
        Pageable limit = PageRequest.of(0, size + 1);

        List<TherapyPost> posts;
        if (popCursor == null) {
            posts = publicOnly
                    ? therapyPostRepository.findFeedPopularByVisibility(Visibility.PUBLIC, limit)
                    : therapyPostRepository.findFeedPopular(limit);
        } else {
            posts = publicOnly
                    ? therapyPostRepository.findFeedPopularByVisibility(
                            Visibility.PUBLIC, popCursor.score(), popCursor.id(), limit)
                    : therapyPostRepository.findFeedPopular(
                            popCursor.score(), popCursor.id(), limit);
        }

        boolean hasNext = posts.size() > size;
        List<TherapyPost> trimmed = hasNext ? posts.subList(0, size) : posts;

        List<TherapyPostSummaryResponse> dtos = toSummaries(trimmed);

        String nextCursor = hasNext
                ? new PopularCursor(
                        trimmed.get(trimmed.size() - 1).getPopularityScore(),
                        trimmed.get(trimmed.size() - 1).getId()
                ).encode()
                : null;

        return new CursorPagedResponse<>(dtos, nextCursor, hasNext, size);
    }

    private Sort toSort(PostSortType sortType) {
        return switch (sortType) {
            case MOST_VIEWED -> Sort.by(
                    Sort.Order.desc("viewCount"),
                    Sort.Order.desc("id")
            );
            // RELEVANCE 는 keyword 가 있을 때만 native 분기로 처리되므로,
            // keyword 없는 RELEVANCE 호출은 LATEST 와 동일하게 폴백
            case LATEST, RELEVANCE -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
        };
    }

    @Transactional
    public TherapyPostDetailResponse getPostDetail(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            boolean isScrapped
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        if (postViewCountService.isFirstView(postId, currentUserId)) {
            post.increaseViewCount();
        }

        List<PostAttachmentResponse> attachments = therapyPostAttachmentRepository
                .findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(PostAttachmentResponse::from)
                .toList();

        long commentCount = therapyPostCommentRepository.countByPostIdAndDeletedAtIsNull(postId);
        Map<PostReactionType, Long> reactionCounts = buildReactionCountMap(postId);
        PostReactionType myReactionType = therapyPostReactionRepository
                .findByPostIdAndUserId(postId, currentUserId)
                .map(TherapyPostReaction::getReactionType)
                .orElse(null);

        return TherapyPostDetailResponse.from(
                post,
                attachments,
                commentCount,
                reactionCounts,
                myReactionType,
                currentUserId,
                currentUserRole,
                isScrapped
        );
    }

    @Transactional
    public TherapyPostDetailResponse updatePost(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            UpdateTherapyPostRequest request
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        if (request.getVisibility() == Visibility.PRIVATE) {
            visibilityPolicy.checkCanWritePrivate(currentUserRole);
        }

        post.update(
                request.getContent(),
                request.getTherapyArea(),
                request.getVisibility()
        );
        return TherapyPostDetailResponse.from(post, currentUserId, currentUserRole);
    }

    @Transactional
    public void deletePost(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        post.softDelete();
    }

    // ── Summary DTO 변환 헬퍼 ──────────────────────────────

    private List<TherapyPostSummaryResponse> toSummaries(List<TherapyPost> posts) {
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
                        false
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

    private Map<PostReactionType, Long> buildReactionCountMap(Long postId) {
        Map<PostReactionType, Long> counts = new EnumMap<>(PostReactionType.class);
        Arrays.stream(PostReactionType.values()).forEach(t -> counts.put(t, 0L));
        therapyPostReactionRepository.countGroupedByPostId(postId).forEach(row -> {
            counts.put((PostReactionType) row[0], (Long) row[1]);
        });
        return counts;
    }
}
