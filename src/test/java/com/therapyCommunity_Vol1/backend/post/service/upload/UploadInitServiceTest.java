package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.dto.UploadInitResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostImageRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostVideoRepository;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadInitServiceTest {

    private ActivePostFinder activePostFinder;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private ResourceAccessValidator resourceAccessValidator;
    private MediaKindPolicy mediaKindPolicy;
    private FileStorageService fileStorageService;
    private UploadRateLimiter uploadRateLimiter;
    private TherapyPostImageRepository imageRepository;
    private TherapyPostAttachmentRepository attachmentRepository;
    private TherapyPostVideoRepository videoRepository;

    private UploadInitService service;

    private static final long MB = 1024L * 1024L;

    @BeforeEach
    void setUp() {
        activePostFinder = mock(ActivePostFinder.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);
        resourceAccessValidator = mock(ResourceAccessValidator.class);
        mediaKindPolicy = new MediaKindPolicy();
        fileStorageService = mock(FileStorageService.class);
        uploadRateLimiter = mock(UploadRateLimiter.class);
        imageRepository = mock(TherapyPostImageRepository.class);
        attachmentRepository = mock(TherapyPostAttachmentRepository.class);
        videoRepository = mock(TherapyPostVideoRepository.class);

        service = new UploadInitService(
                activePostFinder,
                visibilityPolicy,
                resourceAccessValidator,
                mediaKindPolicy,
                fileStorageService,
                uploadRateLimiter,
                imageRepository,
                attachmentRepository,
                videoRepository
        );
    }

    @Test
    void init_returnsUploadUrlForAuthor() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(imageRepository.countByPostId(7L)).thenReturn(2);
        when(fileStorageService.presignPut(anyString(), eq("image/jpeg"), eq(UploadInitService.UPLOAD_PRESIGN_TTL)))
                .thenReturn("https://s3.example.com/signed");

        UploadInitResponse response = service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "photo.jpg", "image/jpeg", 5 * MB, null);

        assertThat(response.getUploadUrl()).isEqualTo("https://s3.example.com/signed");
        assertThat(response.getStoredKey()).startsWith("uploads-pending/images/7/");
        assertThat(response.getStoredKey()).endsWith(".jpg");
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    void init_throwsWhenNotAuthorOrAdmin() {
        TherapyPost post = post(7L, user(99L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        doThrow(new CustomException(ErrorCode.POST_ACCESS_DENIED))
                .when(resourceAccessValidator)
                .validateAuthorOrAdmin(anyLong(), anyLong(), eq(UserRole.THERAPIST), eq(ErrorCode.POST_ACCESS_DENIED));

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "p.jpg", "image/jpeg", 1 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_ACCESS_DENIED);

        verify(fileStorageService, never()).presignPut(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void init_throwsWhenPrivatePostAndUserRole() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        doThrow(new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED))
                .when(visibilityPolicy).checkAccess(post, UserRole.USER);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.USER, 7L, MediaKind.IMAGE, "p.jpg", "image/jpeg", 1 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
    }

    @Test
    void init_throwsWhenSizeOverLimit() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "p.jpg", "image/jpeg", 11 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_IMAGE);
    }

    @Test
    void init_throwsWhenExtensionNotAllowed() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "p.gif", "image/gif", 1 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_IMAGE);
    }

    @Test
    void init_throwsWhenPerPostLimitExceeded() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(imageRepository.countByPostId(7L)).thenReturn(10);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "p.jpg", "image/jpeg", 1 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_MEDIA_LIMIT_EXCEEDED);
    }

    @Test
    void init_throwsWhenVideoAlreadyExists() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(videoRepository.countByPostId(7L)).thenReturn(1);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.VIDEO, "v.mp4", "video/mp4", 100 * MB, 120))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_MEDIA_LIMIT_EXCEEDED);
    }

    @Test
    void init_video_rejectsWhenDurationNull() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.VIDEO, "v.mp4", "video/mp4", 100 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_VIDEO_DURATION_INVALID);

        verify(fileStorageService, never()).presignPut(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void init_video_rejectsWhenDurationOver300() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.VIDEO, "v.mp4", "video/mp4", 100 * MB, 301))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_VIDEO_DURATION_EXCEEDED);
    }

    @Test
    void init_video_acceptsValidDuration() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(videoRepository.countByPostId(7L)).thenReturn(0);
        when(fileStorageService.presignPut(anyString(), eq("video/mp4"), eq(UploadInitService.UPLOAD_PRESIGN_TTL)))
                .thenReturn("https://signed");

        UploadInitResponse response = service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.VIDEO, "v.mp4", "video/mp4", 100 * MB, 300);

        assertThat(response.getUploadUrl()).isEqualTo("https://signed");
        assertThat(response.getStoredKey()).startsWith("uploads-pending/videos/7/");
    }

    @Test
    void init_throwsWhenPresignPutReturnsNull() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(imageRepository.countByPostId(7L)).thenReturn(0);
        when(fileStorageService.presignPut(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "p.jpg", "image/jpeg", 1 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_STORAGE_ERROR);
    }

    @Test
    void init_storedKeyContainsPostIdAndKindAndExtension() {
        TherapyPost post = post(99L, user(1L));
        when(activePostFinder.findOrThrow(99L)).thenReturn(post);
        when(attachmentRepository.countByPostId(99L)).thenReturn(0);
        when(fileStorageService.presignPut(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn("https://signed");

        UploadInitResponse response = service.init(
                1L, UserRole.THERAPIST, 99L, MediaKind.ATTACHMENT, "doc.pdf", "application/pdf", 1 * MB, null);

        assertThat(response.getStoredKey()).matches("uploads-pending/attachments/99/[A-Fa-f0-9-]+\\.pdf");
    }

    @Test
    void init_throwsWhenRateLimitExceeded() {
        doThrow(new CustomException(ErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED))
                .when(uploadRateLimiter).checkAndIncrement(1L);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "p.jpg", "image/jpeg", 1 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED);

        verify(activePostFinder, never()).findOrThrow(anyLong());
    }

    @Test
    void init_throwsWhenDailyLimitExceeded() {
        doThrow(new CustomException(ErrorCode.UPLOAD_DAILY_LIMIT_EXCEEDED))
                .when(uploadRateLimiter).checkAndIncrement(1L);

        assertThatThrownBy(() -> service.init(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, "p.jpg", "image/jpeg", 1 * MB, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_DAILY_LIMIT_EXCEEDED);
    }

    private User user(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").nickname("u" + id).role(UserRole.THERAPIST).build();
    }

    private TherapyPost post(Long id, User author) {
        TherapyPost p = TherapyPost.create("<p>c</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
