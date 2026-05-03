package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.autocomment.service.AiCommentStatusProvider;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.global.cache.PostViewCountService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.FeedSortType;
import com.therapyCommunity_Vol1.backend.post.event.PostCreatedEvent;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.*;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.post.event.EmbeddingEvent;
import com.therapyCommunity_Vol1.backend.post.service.search.PostSearchStrategy;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import com.therapyCommunity_Vol1.backend.user.support.ProfileImageUrlAssembler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    private final PostSearchStrategy searchStrategy;
    private final UserEventPublisher userEventPublisher;
    private final AiCommentStatusProvider aiCommentStatusProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final ProfileImageUrlAssembler profileImageUrlAssembler;
    private final PostImageService postImageService;
    private final PostAttachmentService postAttachmentService;

    @Transactional
    public void recalculatePopularityScore(Long postId) {
        therapyPostRepository.recalculatePopularityScore(postId);
    }

    // RELEVANCE 검색에서 SET LOCAL pg_trgm.similarity_threshold 를 실행하기 위한 EntityManager.
    // @RequiredArgsConstructor 가 생성자에 포함하지 않도록 final 을 붙이지 않는다.
    @PersistenceContext
    private EntityManager entityManager;

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

        eventPublisher.publishEvent(EmbeddingEvent.builder()
                .postId(saved.getId())
                .text(saved.getSearchText())
                .build());

        // 자동 댓글 요청: 검증만 하고, job 생성은 autocomment 패키지의 리스너에서 처리
        boolean requestAutoComment = Boolean.TRUE.equals(request.getRequestAutoComment());
        if (requestAutoComment && request.getVisibility() == Visibility.PRIVATE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // PostCreatedEvent 발행 — autocomment 리스너가 job 생성/이벤트 처리
        eventPublisher.publishEvent(new PostCreatedEvent(saved.getId(), userId, requestAutoComment));

        TherapyPostDetailResponse response = TherapyPostDetailResponse.from(saved, userId, author.getRole());
        AiCommentStatusProvider.AutoCommentStatus acStatus = aiCommentStatusProvider.getStatus(saved.getId());
        response.setAutoComment(acStatus.status(), acStatus.sourceMode());
        return response;
    }

    public PagedResponse<TherapyPostSummaryResponse> getPosts(
            int page,
            int size,
            PostSortType sortType,
            PostSearchCondition condition,
            UserRole currentUserRole
    ) {
        if (sortType == PostSortType.RELEVANCE) {
            throw new CustomException(ErrorCode.INVALID_SORT_TYPE);
        }
        Page<TherapyPost> result = findPosts(page, size, sortType, condition);

        boolean canViewPrivate = visibilityPolicy.canViewPrivate(currentUserRole);
        List<TherapyPostSummaryResponse> posts = toSummaries(result.getContent(), canViewPrivate);

        return PagedResponse.from(result, posts);
    }

    /**
     * PRIVATE UX 개편: 모든 role이 PUBLIC + PRIVATE 게시글을 함께 조회.
     * USER role의 경우 응답 변환 시 contentPreview/이미지가 마스킹되고 accessLocked=true가 표시됨.
     * 상세 페이지 진입은 PostVisibilityAccessPolicy.checkAccess가 여전히 차단.
     */
    private Page<TherapyPost> findPosts(int page, int size, PostSortType sortType,
                                         PostSearchCondition condition) {
        // RELEVANCE 는 별도 무한스크롤 엔드포인트(/posts/search)에서만 노출된다.
        // 이 경로로 sortType=RELEVANCE 가 들어오면 toSort() 가 LATEST 정렬로 폴백한다.
        Pageable pageable = PageRequest.of(page, size, toSort(sortType));

        if (condition.isEmpty()) {
            return therapyPostRepository.findByDeletedAtIsNull(pageable);
        } else if (condition.hasKeyword()) {
            String lowerKeyword = condition.getEscapedKeyword().trim().toLowerCase();
            return therapyPostRepository.searchByKeyword(
                    lowerKeyword, condition.getTherapyArea(),
                    condition.getPostType(), pageable);
        } else {
            return therapyPostRepository.searchByFilter(
                    condition.getTherapyArea(), condition.getPostType(), pageable);
        }
    }

    /**
     * RELEVANCE 검색 (무한스크롤) — PostSearchStrategy 에 위임.
     *
     * 클래스 레벨 readOnly=true 를 명시적으로 오버라이드해 readOnly=false 트랜잭션을 연다.
     * GIN 전략의 SET LOCAL pg_trgm.word_similarity_threshold 를 안전하게 실행하기 위함이다.
     */
    @Transactional
    public SearchCursorResponse searchPostsByRelevance(
            PostSearchCondition condition,
            BigDecimal lastScore,
            Long lastId,
            int size,
            UserRole role
    ) {
        // PRIVATE UX 개편: 모든 role이 PUBLIC + PRIVATE 검색 결과를 받고, USER는 마스킹된 형태로 노출.
        boolean canViewPrivate = visibilityPolicy.canViewPrivate(role);
        return searchStrategy.search(condition, lastScore, lastId, size, canViewPrivate);
    }

    public PagedResponse<TherapyPostSummaryResponse> getMyPosts(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<TherapyPost> result = therapyPostRepository.findByAuthorIdAndDeletedAtIsNull(userId, pageable);

        // 본인 글이므로 PRIVATE도 자유롭게 볼 수 있음.
        List<TherapyPostSummaryResponse> posts = toSummaries(result.getContent(), true);

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
        boolean canViewPrivate = visibilityPolicy.canViewPrivate(role);

        return switch (sortType) {
            case LATEST -> fetchLatestFeed(size, cursor, canViewPrivate);
            case POPULAR -> fetchPopularFeed(size, cursor, canViewPrivate);
        };
    }

    private CursorPagedResponse<TherapyPostSummaryResponse> fetchLatestFeed(
            int size, String cursor, boolean canViewPrivate) {
        PostCursor postCursor = cursor != null ? PostCursor.decode(cursor) : null;
        Pageable limit = PageRequest.of(0, size + 1);

        List<TherapyPost> posts;
        if (postCursor == null) {
            posts = therapyPostRepository.findFeedLatest(limit);
        } else {
            posts = therapyPostRepository.findFeedLatest(
                    postCursor.createdAt(), postCursor.id(), limit);
        }

        List<TherapyPostSummaryResponse> dtos = toSummaries(posts, canViewPrivate);

        return CursorPagedResponse.of(dtos, size, item ->
                new PostCursor(item.getCreatedAt(), item.getId()).encode());
    }

    private CursorPagedResponse<TherapyPostSummaryResponse> fetchPopularFeed(
            int size, String cursor, boolean canViewPrivate) {
        PopularCursor popCursor = cursor != null ? PopularCursor.decode(cursor) : null;
        Pageable limit = PageRequest.of(0, size + 1);

        List<TherapyPost> posts;
        if (popCursor == null) {
            posts = therapyPostRepository.findFeedPopular(limit);
        } else {
            posts = therapyPostRepository.findFeedPopular(
                    popCursor.score(), popCursor.id(), limit);
        }

        boolean hasNext = posts.size() > size;
        List<TherapyPost> trimmed = hasNext ? posts.subList(0, size) : posts;

        List<TherapyPostSummaryResponse> dtos = toSummaries(trimmed, canViewPrivate);

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
            // RELEVANCE 는 getPosts() 진입 시 이미 차단되므로 여기 도달 불가
            case LATEST -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case RELEVANCE -> throw new CustomException(ErrorCode.INVALID_SORT_TYPE);
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

        boolean firstView = postViewCountService.isFirstView(postId, currentUserId);
        if (firstView) {
            post.increaseViewCount();
        }

        // 집계 시점에 dedup/window 처리할 수 있도록 매 조회마다 raw 발행.
        // isFirstView 플래그는 view_count와의 정합성 재구성을 위해 보존.
        userEventPublisher.publish(
                currentUserId,
                UserEventType.POST_VIEW,
                EventTargetType.POST,
                postId,
                Map.of(
                        "isFirstView", firstView,
                        "postType", post.getPostType().name(),
                        "therapyArea", post.getTherapyArea().name(),
                        "visibility", post.getVisibility().name()
                )
        );

        // 첨부파일은 PostAttachmentService에 위임 — presigned S3 URL 발급 + audit log INSERT(idempotent).
        // 발급 == 다운로드 의도로 간주(보기 != 다운로드 정확도는 trade-off).
        List<PostAttachmentResponse> attachments = postAttachmentService.getAttachmentsForPostUnchecked(post, currentUserId);

        long commentCount = therapyPostCommentRepository.countByPostIdAndDeletedAtIsNull(postId);
        Map<PostReactionType, Long> reactionCounts = buildReactionCountMap(postId);
        PostReactionType myReactionType = therapyPostReactionRepository
                .findByPostIdAndUserId(postId, currentUserId)
                .map(TherapyPostReaction::getReactionType)
                .orElse(null);

        TherapyPostDetailResponse response = TherapyPostDetailResponse.from(
                post,
                attachments,
                commentCount,
                reactionCounts,
                myReactionType,
                currentUserId,
                currentUserRole,
                isScrapped,
                profileImageUrlAssembler.toFullUrl(post.getAuthor().getProfileImageUrl()),
                postImageService.getImagesForPostUnchecked(postId)
        );
        AiCommentStatusProvider.AutoCommentStatus acStatus = aiCommentStatusProvider.getStatus(postId);
        response.setAutoComment(acStatus.status(), acStatus.sourceMode());
        return response;
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

        String oldSearchText = post.getSearchText();

        post.update(
                request.getContent(),
                request.getTherapyArea(),
                request.getVisibility()
        );

        if (!oldSearchText.equals(post.getSearchText())) {
            eventPublisher.publishEvent(EmbeddingEvent.builder()
                    .postId(post.getId())
                    .text(post.getSearchText())
                    .build());
        }

        TherapyPostDetailResponse response = TherapyPostDetailResponse.from(post, currentUserId, currentUserRole);
        AiCommentStatusProvider.AutoCommentStatus acStatus = aiCommentStatusProvider.getStatus(postId);
        response.setAutoComment(acStatus.status(), acStatus.sourceMode());
        return response;
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

    private List<TherapyPostSummaryResponse> toSummaries(List<TherapyPost> posts, boolean canViewPrivate) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(TherapyPost::getId).toList();

        // 3종 reaction 카운트를 한 번의 GROUP BY로 batch 조회 (postId, type, count) → Map<postId, Map<type, count>>
        Map<Long, Map<PostReactionType, Long>> reactionByPostId = new HashMap<>();
        for (Object[] row : therapyPostReactionRepository.countByPostIdInGroupedByType(postIds)) {
            Long postId = (Long) row[0];
            PostReactionType type = (PostReactionType) row[1];
            Long count = (Long) row[2];
            reactionByPostId.computeIfAbsent(postId, k -> new HashMap<>()).put(type, count);
        }
        Map<Long, Long> commentCounts = toCountMap(
                therapyPostCommentRepository.countActiveByPostIdIn(postIds)
        );

        // 권한 없는 사용자에겐 PRIVATE 게시글의 이미지 URL을 DB 조회 자체에서 제외 (효율 + 보안 이중 방어).
        // DTO 단계의 accessLocked 마스킹은 그대로 유지.
        List<Long> visiblePostIds = canViewPrivate
                ? postIds
                : posts.stream()
                        .filter(p -> p.getVisibility() == Visibility.PUBLIC)
                        .map(TherapyPost::getId)
                        .toList();
        Map<Long, List<PostImageResponse>> imagesByPostId =
                postImageService.getImagesByPostIds(visiblePostIds);

        return posts.stream()
                .map(post -> {
                    Map<PostReactionType, Long> counts = reactionByPostId.getOrDefault(post.getId(), Map.of());
                    return TherapyPostSummaryResponse.from(
                            post,
                            counts.getOrDefault(PostReactionType.LIKE, 0L),
                            counts.getOrDefault(PostReactionType.CURIOUS, 0L),
                            counts.getOrDefault(PostReactionType.USEFUL, 0L),
                            commentCounts.getOrDefault(post.getId(), 0L),
                            false,
                            canViewPrivate,
                            profileImageUrlAssembler.toFullUrl(post.getAuthor().getProfileImageUrl()),
                            imagesByPostId.getOrDefault(post.getId(), List.of()).stream()
                                    .map(PostImageResponse::getImageUrl)
                                    .toList(),
                            null
                    );
                })
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
