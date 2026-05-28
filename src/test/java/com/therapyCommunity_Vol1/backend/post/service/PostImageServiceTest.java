package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostImage;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostImageRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostImageServiceTest {

    private ActivePostFinder activePostFinder;
    private TherapyPostImageRepository therapyPostImageRepository;
    private FileStorageService fileStorageService;
    private ResourceAccessValidator resourceAccessValidator;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private PostImageService postImageService;

    @BeforeEach
    void setUp() {
        activePostFinder = mock(ActivePostFinder.class);
        therapyPostImageRepository = mock(TherapyPostImageRepository.class);
        fileStorageService = mock(FileStorageService.class);
        resourceAccessValidator = mock(ResourceAccessValidator.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);

        postImageService = new PostImageService(
                activePostFinder,
                therapyPostImageRepository,
                fileStorageService,
                resourceAccessValidator,
                visibilityPolicy
        );
    }

    @Test
    void 작성자가_이미지를_삭제하면_파일과_DB가_모두_삭제되고_displayOrder가_재정렬된다() {
        Long userId = 1L;
        User author = user(userId, UserRole.THERAPIST);
        TherapyPost post = post(10L, author);

        TherapyPostImage target = image(100L, post, "images/b.jpg", 1);
        TherapyPostImage remaining0 = image(99L, post, "images/a.jpg", 0);
        TherapyPostImage remaining2 = image(101L, post, "images/c.jpg", 2);

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(therapyPostImageRepository.findById(100L)).thenReturn(Optional.of(target));
        when(therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of(remaining0, remaining2));

        postImageService.deleteImage(userId, UserRole.THERAPIST, 10L, 100L);

        verify(resourceAccessValidator).validateAuthorOrAdmin(
                userId, userId, UserRole.THERAPIST, ErrorCode.POST_ACCESS_DENIED
        );
        verify(therapyPostImageRepository).delete(target);
        verify(fileStorageService).delete("images/b.jpg");
        assertThat(remaining0.getDisplayOrder()).isEqualTo(0);
        assertThat(remaining2.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void 관리자는_타인의_이미지도_삭제할_수_있다() {
        Long authorId = 1L;
        Long adminId = 99L;
        User author = user(authorId, UserRole.THERAPIST);
        TherapyPost post = post(10L, author);
        TherapyPostImage target = image(100L, post, "images/b.jpg", 0);

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(therapyPostImageRepository.findById(100L)).thenReturn(Optional.of(target));
        when(therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of());

        postImageService.deleteImage(adminId, UserRole.ADMIN, 10L, 100L);

        verify(resourceAccessValidator).validateAuthorOrAdmin(
                authorId, adminId, UserRole.ADMIN, ErrorCode.POST_ACCESS_DENIED
        );
        verify(therapyPostImageRepository).delete(target);
    }

    @Test
    void 권한_없는_사용자는_이미지를_삭제할_수_없다() {
        Long authorId = 1L;
        Long otherUserId = 2L;
        User author = user(authorId, UserRole.THERAPIST);
        TherapyPost post = post(10L, author);

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        doThrow(new CustomException(ErrorCode.POST_ACCESS_DENIED))
                .when(resourceAccessValidator)
                .validateAuthorOrAdmin(authorId, otherUserId, UserRole.THERAPIST, ErrorCode.POST_ACCESS_DENIED);

        assertThatThrownBy(() ->
                postImageService.deleteImage(otherUserId, UserRole.THERAPIST, 10L, 100L)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_ACCESS_DENIED);

        verify(therapyPostImageRepository, never()).delete(any(TherapyPostImage.class));
        verify(fileStorageService, never()).delete(anyString());
    }

    @Test
    void imageId가_postId에_속하지_않으면_RESOURCE_NOT_FOUND를_던진다() {
        Long userId = 1L;
        User author = user(userId, UserRole.THERAPIST);
        TherapyPost postA = post(10L, author);
        TherapyPost postB = post(20L, author);
        TherapyPostImage imageOfB = image(100L, postB, "images/b.jpg", 0);

        when(activePostFinder.findOrThrow(10L)).thenReturn(postA);
        when(therapyPostImageRepository.findById(100L)).thenReturn(Optional.of(imageOfB));

        assertThatThrownBy(() ->
                postImageService.deleteImage(userId, UserRole.THERAPIST, 10L, 100L)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(therapyPostImageRepository, never()).delete(any(TherapyPostImage.class));
        verify(fileStorageService, never()).delete(anyString());
    }

    @Test
    void 이미지가_존재하지_않으면_RESOURCE_NOT_FOUND를_던진다() {
        Long userId = 1L;
        User author = user(userId, UserRole.THERAPIST);
        TherapyPost post = post(10L, author);

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(therapyPostImageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                postImageService.deleteImage(userId, UserRole.THERAPIST, 10L, 999L)
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void 파일_삭제_실패해도_DB_삭제는_롤백되지_않는다() {
        Long userId = 1L;
        User author = user(userId, UserRole.THERAPIST);
        TherapyPost post = post(10L, author);
        TherapyPostImage target = image(100L, post, "images/b.jpg", 0);

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(therapyPostImageRepository.findById(100L)).thenReturn(Optional.of(target));
        when(therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of());
        doThrow(new RuntimeException("S3 down"))
                .when(fileStorageService).delete("images/b.jpg");

        postImageService.deleteImage(userId, UserRole.THERAPIST, 10L, 100L);

        verify(therapyPostImageRepository).delete(target);
        verify(fileStorageService).delete("images/b.jpg");
    }

    @Test
    void 트랜잭션_커밋_전에는_파일_삭제가_실행되지_않는다() {
        Long userId = 1L;
        User author = user(userId, UserRole.THERAPIST);
        TherapyPost post = post(10L, author);
        TherapyPostImage target = image(100L, post, "images/b.jpg", 0);

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(therapyPostImageRepository.findById(100L)).thenReturn(Optional.of(target));
        when(therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            postImageService.deleteImage(userId, UserRole.THERAPIST, 10L, 100L);

            // 커밋 전 — DB 삭제는 실행됐지만 스토리지 삭제는 아직 예약만 된 상태
            verify(therapyPostImageRepository).delete(target);
            verify(fileStorageService, never()).delete(anyString());

            // 커밋 트리거 → 이제서야 파일 삭제
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(fileStorageService).delete("images/b.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션_롤백_시_파일_삭제가_실행되지_않는다() {
        Long userId = 1L;
        User author = user(userId, UserRole.THERAPIST);
        TherapyPost post = post(10L, author);
        TherapyPostImage target = image(100L, post, "images/b.jpg", 0);

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(therapyPostImageRepository.findById(100L)).thenReturn(Optional.of(target));
        when(therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            postImageService.deleteImage(userId, UserRole.THERAPIST, 10L, 100L);

            // 롤백 시뮬레이션 — afterCommit은 호출되지 않고 afterCompletion(ROLLED_BACK)만 호출
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(fileStorageService, never()).delete(anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void USER는_PRIVATE_게시글의_이미지_목록을_조회할_수_없다() {
        User author = user(1L, UserRole.THERAPIST);
        TherapyPost privatePost = privatePost(10L, author);

        when(activePostFinder.findOrThrow(10L)).thenReturn(privatePost);
        doThrow(new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED))
                .when(visibilityPolicy).checkAccess(eq(privatePost), eq(UserRole.USER), any());

        assertThatThrownBy(() -> postImageService.getImages(10L, UserRole.USER, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
    }

    @Test
    void USER는_PRIVATE_게시글의_이미지를_다운로드할_수_없다() {
        User author = user(1L, UserRole.THERAPIST);
        TherapyPost privatePost = privatePost(10L, author);

        when(activePostFinder.findOrThrow(10L)).thenReturn(privatePost);
        doThrow(new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED))
                .when(visibilityPolicy).checkAccess(eq(privatePost), eq(UserRole.USER), any());

        assertThatThrownBy(() -> postImageService.loadImage(10L, 100L, UserRole.USER, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);

        verify(therapyPostImageRepository, never()).findById(anyLong());
    }

    @Test
    void USER는_PRIVATE_게시글의_이미지_업로드_삭제를_할_수_없다() {
        User author = user(1L, UserRole.THERAPIST);
        TherapyPost privatePost = privatePost(10L, author);

        when(activePostFinder.findOrThrow(10L)).thenReturn(privatePost);
        doThrow(new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED))
                .when(visibilityPolicy).checkAccess(eq(privatePost), eq(UserRole.USER), any());

        // upload
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "a.jpg", "image/jpeg", "bytes".getBytes());
        assertThatThrownBy(() -> postImageService.uploadImage(99L, UserRole.USER, 10L, file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);

        // delete
        assertThatThrownBy(() -> postImageService.deleteImage(99L, UserRole.USER, 10L, 100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);

        verify(fileStorageService, never()).storeProfileImage(any());
        verify(fileStorageService, never()).delete(anyString());
    }

    private TherapyPost privatePost(Long id, User author) {
        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PRIVATE,
                author
        );
        ReflectionTestUtils.setField(post, "id", id);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.of(2026, 4, 22, 9, 0));
        return post;
    }

    private User user(Long id, UserRole role) {
        return User.builder()
                .id(id)
                .email("u" + id + "@test.com")
                .nickname("user" + id)
                .role(role)
                .build();
    }

    private TherapyPost post(Long id, User author) {
        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );
        ReflectionTestUtils.setField(post, "id", id);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.of(2026, 4, 21, 9, 0));
        return post;
    }

    private TherapyPostImage image(Long id, TherapyPost post, String storedPath, int order) {
        TherapyPostImage image = TherapyPostImage.create(
                post,
                storedPath,
                "orig.jpg",
                "image/jpeg",
                1024L,
                order
        );
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }
}
