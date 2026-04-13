package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.*;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;
    private final ResourceAccessValidator resourceAccessValidator;
    private final PostVisibilityAccessPolicy visibilityPolicy;

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

        List<TherapyPostSummaryResponse> posts = result.getContent()
                .stream()
                .map(post -> TherapyPostSummaryResponse.from(post, false))
                .toList();

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
     * RELEVANCE 검색 (무한스크롤) — pg_trgm % 연산자 + ILIKE fallback 으로 후보를 모으고
     * similarity 점수로 정렬한다.
     *
     * 두 단계 fetch: 1) native 로 (id, score) + 정렬, 2) ID 로 author 까지 EntityGraph fetch.
     * native query 는 @EntityGraph 가 동작하지 않아 N+1 회피 목적.
     *
     * 페이지네이션은 (lastScore, lastId) 커서 기반. take+1 조회로 hasNextData 를 판단한다.
     * score 는 numeric(10,8) 로 캐스트해 BigDecimal 로 왕복시켜 동등 비교 안전성을 확보한다.
     *
     * 클래스 레벨 readOnly=true 를 명시적으로 오버라이드해 readOnly=false 트랜잭션을 연다.
     * SET LOCAL pg_trgm.similarity_threshold 를 안전하게 실행하기 위함이다
     * (JDBC/Hibernate 일부 버전에서 READ ONLY 트랜잭션의 SET LOCAL 을 거부한 사례가 있어
     *  안전 차원에서 명시적으로 풀어둔다).
     * SET LOCAL 은 트랜잭션 종료 시 자동 해제되므로 RESET 호출은 불필요하다.
     */
    @Transactional
    public SearchCursorResponse searchPostsByRelevance(
            PostSearchCondition condition,
            BigDecimal lastScore,
            Long lastId,
            int size,
            UserRole role
    ) {
        boolean publicOnly = !visibilityPolicy.canViewPrivate(role);
        // pg_trgm % 연산자의 임계값을 트랜잭션 스코프로 0.03 으로 낮춘다.
        // 트랜잭션 종료 시 자동으로 원복된다.
        entityManager.createNativeQuery("SET LOCAL pg_trgm.similarity_threshold = 0.03")
                .executeUpdate();

        // similarity/% 는 raw, ILIKE 는 escaped — 두 함수가 메타문자 의미가 달라 분리 필수
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
        // score 는 SQL 에서 numeric(10,8) 로 캐스트되어 BigDecimal 로 그대로 전달된다.
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

    public PagedResponse<TherapyPostSummaryResponse> getMyPosts(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<TherapyPost> result = therapyPostRepository.findByAuthorIdAndDeletedAtIsNull(userId, pageable);

        List<TherapyPostSummaryResponse> posts = result.getContent().stream()
                .map(post -> TherapyPostSummaryResponse.from(post, false))
                .toList();

        return PagedResponse.from(result, posts);
    }

    private static final int FEED_MAX_SIZE = 50;

    /**
     * 커서 기반 피드 조회 (LATEST 고정, 무한스크롤용)
     *
     * @param size   요청 페이지 크기 (1~50, 컨트롤러 기본값 20)
     * @param cursor 이전 페이지 마지막 항목의 Base64 커서. null이면 첫 페이지
     * @param role   USER는 PUBLIC만, THERAPIST/ADMIN은 전체 조회
     */
    public CursorPagedResponse<TherapyPostSummaryResponse> getPostsFeed(int size, String cursor, UserRole role) {
        // size 범위 보정: 최소 1, 최대 50
        size = Math.min(Math.max(size, 1), FEED_MAX_SIZE);

        // 커서 디코딩: null이면 첫 페이지, 값이 있으면 해당 위치부터
        PostCursor postCursor = cursor != null ? PostCursor.decode(cursor) : null;

        // role에 따라 PUBLIC_ONLY / 전체 쿼리 분기
        boolean publicOnly = !visibilityPolicy.canViewPrivate(role);

        // size+1개 조회: 초과분이 있으면 다음 페이지 존재
        List<TherapyPost> posts = publicOnly
                ? therapyPostRepository.findFeedLatestByVisibility(
                        Visibility.PUBLIC,
                        postCursor != null ? postCursor.createdAt() : null,
                        postCursor != null ? postCursor.id() : null,
                        PageRequest.of(0, size + 1))
                : therapyPostRepository.findFeedLatest(
                        postCursor != null ? postCursor.createdAt() : null,
                        postCursor != null ? postCursor.id() : null,
                        PageRequest.of(0, size + 1));

        List<TherapyPostSummaryResponse> dtos = posts.stream()
                .map(post -> TherapyPostSummaryResponse.from(post, false))
                .toList();

        // CursorPagedResponse.of()가 size+1 → trim + hasNext/nextCursor 계산
        return CursorPagedResponse.of(dtos, size, item ->
                new PostCursor(item.getCreatedAt(), item.getId()).encode());
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

        post.increaseViewCount();

        List<PostAttachmentResponse> attachments = therapyPostAttachmentRepository
                .findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(PostAttachmentResponse::from)
                .toList();

        return TherapyPostDetailResponse.from(post, attachments, currentUserId, currentUserRole, isScrapped);
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

}
