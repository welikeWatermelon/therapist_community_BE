package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.post.domain.*;
import com.therapyCommunity_Vol1.backend.post.dto.*;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostServiceTest {

    private TherapyPostRepository therapyPostRepository;
    private TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private UserRepository userRepository;
    private PostService postService;

    @BeforeEach
    void setUp() {
        therapyPostRepository = mock(TherapyPostRepository.class);
        therapyPostAttachmentRepository = mock(TherapyPostAttachmentRepository.class);
        userRepository = mock(UserRepository.class);
        postService = new PostService(
                therapyPostRepository,
                therapyPostAttachmentRepository,
                userRepository
        );
    }

    @Test
    void 게시글_작성_성공() {

        // given
        Long userId = 1L;

        CreateTherapyPostRequest request = new CreateTherapyPostRequest(
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5
        );

        User author = User.builder()
                .id(userId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost savedPost = TherapyPost.create(
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );
        ReflectionTestUtils.setField(savedPost, "id", 100L);
        ReflectionTestUtils.setField(savedPost, "viewCount", 0L);
        ReflectionTestUtils.setField(savedPost, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(savedPost, "updatedAt", LocalDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(author));
        when(therapyPostRepository.save(any(TherapyPost.class))).thenReturn(savedPost);

        // when
        TherapyPostDetailResponse response = postService.createPost(userId, request);

        // then
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTitle()).isEqualTo("제목");
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
                "제목",
                "<p>본문입니다</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
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

        // when
        PostListResponse response = postService.getPosts(0, 10, PostSortType.LATEST);

        // then
        assertThat(response.getPosts()).hasSize(1);
        assertThat(response.getPosts().get(0).getTitle()).isEqualTo("제목");
        assertThat(response.getPosts().get(0).getAuthorNickname()).isEqualTo("tester");
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getHasNext()).isFalse();
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
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "viewCount", 10L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        when(therapyPostRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(post));
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        // when
        TherapyPostDetailResponse response = postService.getPostDetail(
                1L,
                UserRole.THERAPIST,
                1L
        );

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getAuthorId()).isEqualTo(1L);
        assertThat(response.getViewCount()).isEqualTo(11L);
        assertThat(response.getContent()).isEqualTo("<p>본문</p>");
        assertThat(response.isCanEdit()).isTrue();
        assertThat(response.isCanDelete()).isTrue();
    }

    @Test
    void 게시글_상세조회_실패_게시글없음() {

        // given
        when(therapyPostRepository.findByIdAndDeletedAtIsNull(999L))
                .thenReturn(Optional.empty());
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(999L))
                .thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> postService.getPostDetail(
                1L,
                UserRole.THERAPIST,
                999L
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
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        when(therapyPostRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(post));
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        // when
        TherapyPostDetailResponse response = postService.getPostDetail(
                2L,
                UserRole.THERAPIST,
                1L
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
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        when(therapyPostRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(post));
        when(therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        // when
        TherapyPostDetailResponse response = postService.getPostDetail(
                99L,
                UserRole.ADMIN,
                1L
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
                "기존 제목",
                "<p>기존 본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());

        UpdateTherapyPostRequest request = new UpdateTherapyPostRequest(
                "수정 제목",
                "<p>수정 본문</p>",
                TherapyArea.COGNITIVE,
                AgeGroup.AGE_6_12
        );

        when(therapyPostRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(post));

        // when
        TherapyPostDetailResponse response =
                postService.updatePost(
                        currentUserId,
                        UserRole.THERAPIST,
                        1L,
                        request
                );

        // then
        assertThat(response.getTitle()).isEqualTo("수정 제목");
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
                "기존 제목",
                "<p>기존 본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        UpdateTherapyPostRequest request = new UpdateTherapyPostRequest(
                "수정 제목",
                "<p>수정 본문</p>",
                TherapyArea.COGNITIVE,
                AgeGroup.AGE_6_12
        );

        when(therapyPostRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(post));

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
                "기존 제목",
                "<p>기존 본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        when(therapyPostRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(post));

        // when
        postService.deletePost(currentUserId, UserRole.THERAPIST, 1L);

        // then
        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedAt()).isNotNull();
    }
}
