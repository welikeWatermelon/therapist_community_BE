package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.global.cache.PostViewCountService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.*;
import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.*;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.therapyCommunity_Vol1.backend.post.domain.FeedSortType;

import static org.assertj.core.api.Assertions.*;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import static org.mockito.Mockito.*;

class PostServiceTest {

    private TherapyPostRepository therapyPostRepository;
    private TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private TherapyPostReactionRepository therapyPostReactionRepository;
    private TherapyPostCommentRepository therapyPostCommentRepository;
    private ActivePostFinder activePostFinder;
    private UserRepository userRepository;
    private ResourceAccessValidator resourceAccessValidator;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private PostViewCountService postViewCountService;
    private com.therapyCommunity_Vol1.backend.post.service.search.PostSearchStrategy searchStrategy;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private PostService postService;

    @BeforeEach
    void setUp() {
        therapyPostRepository = mock(TherapyPostRepository.class);
        therapyPostAttachmentRepository = mock(TherapyPostAttachmentRepository.class);
        therapyPostReactionRepository = mock(TherapyPostReactionRepository.class);
        therapyPostCommentRepository = mock(TherapyPostCommentRepository.class);
        activePostFinder = mock(ActivePostFinder.class);
        userRepository = mock(UserRepository.class);
        resourceAccessValidator = mock(ResourceAccessValidator.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);
        postViewCountService = mock(PostViewCountService.class);
        searchStrategy = mock(com.therapyCommunity_Vol1.backend.post.service.search.PostSearchStrategy.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        when(visibilityPolicy.canViewPrivate(UserRole.THERAPIST)).thenReturn(true);
        when(visibilityPolicy.canViewPrivate(UserRole.ADMIN)).thenReturn(true);
        when(visibilityPolicy.canViewPrivate(UserRole.USER)).thenReturn(false);
        when(postViewCountService.isFirstView(anyLong(), anyLong())).thenReturn(true);
        when(therapyPostReactionRepository.countByPostIdInAndReactionType(anyList(), any()))
                .thenReturn(List.of());
        when(therapyPostCommentRepository.countActiveByPostIdIn(anyList()))
                .thenReturn(List.of());
        when(therapyPostReactionRepository.countGroupedByPostId(anyLong()))
                .thenReturn(List.of());
        when(therapyPostReactionRepository.findByPostIdAndUserId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(therapyPostCommentRepository.countByPostIdAndDeletedAtIsNull(anyLong()))
                .thenReturn(0L);
        postService = new PostService(
                therapyPostRepository,
                therapyPostAttachmentRepository,
                therapyPostReactionRepository,
                therapyPostCommentRepository,
                activePostFinder,
                userRepository,
                resourceAccessValidator,
                visibilityPolicy,
                postViewCountService,
                searchStrategy,
                eventPublisher
        );
    }

    @Test
    void 게시글_작성_성공() {

        // given
        Long userId = 1L;

        CreateTherapyPostRequest request = new CreateTherapyPostRequest(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC
        );

        User author = User.builder()
                .id(userId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost savedPost = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );
        ReflectionTestUtils.setField(savedPost, "id", 100L);
        ReflectionTestUtils.setField(savedPost, "viewCount", 0L);
        ReflectionTestUtils.setField(savedPost, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(savedPost, "updatedAt", LocalDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(author));
        when(therapyPostRepository.save(any(TherapyPost.class))).thenReturn(savedPost);

        // when
        TherapyPostDetailResponse response = postService.createPost(userId, UserRole.THERAPIST, request);

        // then
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getAuthorId()).isEqualTo(userId);
        assertThat(response.getAuthorNickname()).isEqualTo("tester");
        assertThat(response.getPostType()).isEqualTo(PostType.COMMUNITY);
        assertThat(response.getTherapyArea()).isEqualTo(TherapyArea.SPEECH);
        assertThat(response.isCanEdit()).isTrue();
        assertThat(response.isCanDelete()).isTrue();
        verify(therapyPostRepository).save(any(TherapyPost.class));
    }

    @Test
    void 게시글_목록_조회_성공() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문입니다</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "viewCount", 10L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());

        Pageable pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TherapyPost> page = new PageImpl<>(List.of(post), pageable, 1);

        when(therapyPostRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(page);
        when(therapyPostReactionRepository.countByPostIdInAndReactionType(
                eq(List.of(1L)), eq(PostReactionType.LIKE)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));
        when(therapyPostCommentRepository.countActiveByPostIdIn(eq(List.of(1L))))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));

        // when
        PostSearchCondition condition = new PostSearchCondition(null, null, null);
        PagedResponse<TherapyPostSummaryResponse> response = postService.getPosts(0, 10, PostSortType.LATEST, condition, UserRole.THERAPIST);

        // then
        assertThat(response.getItems()).hasSize(1);
        TherapyPostSummaryResponse item = response.getItems().get(0);
        assertThat(item.getAuthorNickname()).isEqualTo("tester");
        assertThat(item.getLikeCount()).isEqualTo(5L);
        assertThat(item.getCommentCount()).isEqualTo(3L);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    void 게시글_상세조회_성공_조회수_증가() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "viewCount", 10L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        when(activePostFinder.findOrThrow(1L)).thenReturn(post);
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(therapyPostCommentRepository.countByPostIdAndDeletedAtIsNull(1L))
                .thenReturn(7L);
        when(therapyPostReactionRepository.countGroupedByPostId(1L))
                .thenReturn(List.<Object[]>of(
                        new Object[]{PostReactionType.LIKE, 4L},
                        new Object[]{PostReactionType.CURIOUS, 2L}
                ));
        TherapyPostReaction myReaction = TherapyPostReaction.create(post, author, PostReactionType.LIKE);
        when(therapyPostReactionRepository.findByPostIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(myReaction));

        // when
        TherapyPostDetailResponse response = postService.getPostDetail(
                1L,
                UserRole.THERAPIST,
                1L,
                false
        );

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getAuthorId()).isEqualTo(1L);
        assertThat(response.getViewCount()).isEqualTo(11L);
        assertThat(response.getContent()).isEqualTo("<p>본문</p>");
        assertThat(response.isCanEdit()).isTrue();
        assertThat(response.isCanDelete()).isTrue();
        assertThat(response.getCommentCount()).isEqualTo(7L);
        assertThat(response.getReactionCounts())
                .containsEntry(PostReactionType.LIKE, 4L)
                .containsEntry(PostReactionType.CURIOUS, 2L)
                .containsEntry(PostReactionType.USEFUL, 0L);
        assertThat(response.getMyReactionType()).isEqualTo(PostReactionType.LIKE);
    }

    @Test
    void 게시글_상세조회_중복조회시_viewCount_증가없음() {
        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "viewCount", 10L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        when(activePostFinder.findOrThrow(1L)).thenReturn(post);
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());
        // 30분 내 재조회 시나리오 — isFirstView가 false를 반환
        when(postViewCountService.isFirstView(1L, 1L)).thenReturn(false);

        // when
        TherapyPostDetailResponse response = postService.getPostDetail(
                1L,
                UserRole.THERAPIST,
                1L,
                false
        );

        // then — viewCount가 증가하지 않고 10으로 유지
        assertThat(response.getViewCount()).isEqualTo(10L);
    }

    @Test
    void 게시글_상세조회_실패_게시글없음() {

        // given
        when(activePostFinder.findOrThrow(999L))
                .thenThrow(new CustomException(ErrorCode.POST_NOT_FOUND));
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(999L))
                .thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> postService.getPostDetail(
                1L,
                UserRole.THERAPIST,
                999L,
                false
        ))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 게시글_상세조회_작성자아니면_권한없음() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        when(activePostFinder.findOrThrow(1L)).thenReturn(post);
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        // when
        TherapyPostDetailResponse response = postService.getPostDetail(
                2L,
                UserRole.THERAPIST,
                1L,
                false
        );

        // then
        assertThat(response.isCanEdit()).isFalse();
        assertThat(response.isCanDelete()).isFalse();
    }

    @Test
    void 게시글_상세조회_관리자는_권한있음() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        when(activePostFinder.findOrThrow(1L)).thenReturn(post);
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        // when
        TherapyPostDetailResponse response = postService.getPostDetail(
                99L,
                UserRole.ADMIN,
                1L,
                false
        );

        // then
        assertThat(response.isCanEdit()).isTrue();
        assertThat(response.isCanDelete()).isTrue();
    }

    @Test
    void 게시글_수정_성공_작성자() {

        // given
        Long currentUserId = 1L;

        User author = User.builder()
                .id(currentUserId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>기존 본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        UpdateTherapyPostRequest request = new UpdateTherapyPostRequest(
                "<p>수정 본문</p>",
                TherapyArea.COGNITIVE,
                Visibility.PRIVATE
        );

        when(activePostFinder.findOrThrow(1L)).thenReturn(post);

        // when
        TherapyPostDetailResponse response =
                postService.updatePost(
                        currentUserId,
                        UserRole.THERAPIST,
                        1L,
                        request
                );

        // then
        assertThat(response.getContent()).isEqualTo("<p>수정 본문</p>");
        assertThat(response.getTherapyArea()).isEqualTo(TherapyArea.COGNITIVE);
        assertThat(response.getAuthorId()).isEqualTo(currentUserId);
        assertThat(response.isCanEdit()).isTrue();
        assertThat(response.isCanDelete()).isTrue();
    }

    @Test
    void 게시글_수정_실패_작성자아님() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>기존 본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        UpdateTherapyPostRequest request = new UpdateTherapyPostRequest(
                "<p>수정 본문</p>",
                TherapyArea.COGNITIVE,
                Visibility.PRIVATE
        );

        when(activePostFinder.findOrThrow(1L)).thenReturn(post);
        doThrow(new CustomException(ErrorCode.POST_ACCESS_DENIED))
                .when(resourceAccessValidator).validateAuthorOrAdmin(1L, 2L, UserRole.THERAPIST, ErrorCode.POST_ACCESS_DENIED);

        // when / then
        assertThatThrownBy(() ->
                postService.updatePost(
                        2L,
                        UserRole.THERAPIST,
                        1L,
                        request
                )
        ).isInstanceOf(CustomException.class);
    }

    @Test
    void 게시글_soft_delete_성공() {

        // given
        Long currentUserId = 1L;

        User author = User.builder()
                .id(currentUserId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>기존 본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        when(activePostFinder.findOrThrow(1L)).thenReturn(post);

        // when
        postService.deletePost(currentUserId, UserRole.THERAPIST, 1L);

        // then
        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void 피드_첫페이지_조회_성공() {
        // given
        User author = User.builder()
                .id(1L).email("test@test.com").nickname("tester").role(UserRole.THERAPIST).build();

        List<TherapyPost> posts = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TherapyPost post = TherapyPost.create("<p>본문" + i + "</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
            ReflectionTestUtils.setField(post, "id", (long) i);
            ReflectionTestUtils.setField(post, "viewCount", 0L);
            ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now().minusMinutes(i));
            posts.add(post);
        }

        when(therapyPostRepository.findFeedLatest(any(Pageable.class)))
                .thenReturn(posts);

        // when
        CursorPagedResponse<TherapyPostSummaryResponse> response = postService.getPostsFeed(10, null, UserRole.THERAPIST, FeedSortType.LATEST);

        // then
        assertThat(response.getItems()).hasSize(3);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void 피드_hasNext_true_다음페이지_존재() {
        // given
        User author = User.builder()
                .id(1L).email("test@test.com").nickname("tester").role(UserRole.THERAPIST).build();

        int size = 2;
        List<TherapyPost> posts = new ArrayList<>();
        for (int i = 1; i <= size + 1; i++) {
            TherapyPost post = TherapyPost.create("<p>본문" + i + "</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
            ReflectionTestUtils.setField(post, "id", (long) i);
            ReflectionTestUtils.setField(post, "viewCount", 0L);
            ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now().minusMinutes(i));
            posts.add(post);
        }

        when(therapyPostRepository.findFeedLatest(any(Pageable.class)))
                .thenReturn(posts);

        // when
        CursorPagedResponse<TherapyPostSummaryResponse> response = postService.getPostsFeed(size, null, UserRole.THERAPIST, FeedSortType.LATEST);

        // then
        assertThat(response.getItems()).hasSize(size);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getNextCursor()).isNotNull();
    }

    @Test
    void 피드_빈결과() {
        when(therapyPostRepository.findFeedLatest(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        CursorPagedResponse<TherapyPostSummaryResponse> response = postService.getPostsFeed(10, null, UserRole.THERAPIST, FeedSortType.LATEST);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void 피드_USER는_PUBLIC_ONLY_쿼리_사용() {
        when(therapyPostRepository.findFeedLatestByVisibility(eq(Visibility.PUBLIC), any(Pageable.class)))
                .thenReturn(List.of());

        postService.getPostsFeed(10, null, UserRole.USER, FeedSortType.LATEST);

        verify(therapyPostRepository).findFeedLatestByVisibility(eq(Visibility.PUBLIC), any(Pageable.class));
        verify(therapyPostRepository, never()).findFeedLatest(any(Pageable.class));
    }

    @Test
    void 인기순_피드_첫페이지_조회_성공() {
        // given
        User author = User.builder()
                .id(1L).email("test@test.com").nickname("tester").role(UserRole.THERAPIST).build();

        List<TherapyPost> posts = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TherapyPost post = TherapyPost.create("<p>본문" + i + "</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
            ReflectionTestUtils.setField(post, "id", (long) i);
            ReflectionTestUtils.setField(post, "viewCount", 0L);
            ReflectionTestUtils.setField(post, "popularityScore", (long) (100 - i));
            ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now().minusMinutes(i));
            posts.add(post);
        }

        when(therapyPostRepository.findFeedPopular(any(Pageable.class)))
                .thenReturn(posts);

        // when
        CursorPagedResponse<TherapyPostSummaryResponse> response = postService.getPostsFeed(10, null, UserRole.THERAPIST, FeedSortType.POPULAR);

        // then
        assertThat(response.getItems()).hasSize(3);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void 인기순_피드_USER는_PUBLIC_ONLY_쿼리_사용() {
        when(therapyPostRepository.findFeedPopularByVisibility(eq(Visibility.PUBLIC), any(Pageable.class)))
                .thenReturn(List.of());

        postService.getPostsFeed(10, null, UserRole.USER, FeedSortType.POPULAR);

        verify(therapyPostRepository).findFeedPopularByVisibility(eq(Visibility.PUBLIC), any(Pageable.class));
        verify(therapyPostRepository, never()).findFeedPopular(any(Pageable.class));
    }
}
