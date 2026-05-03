package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.file.dto.S3ObjectMeta;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.dto.PostAttachmentResponse;
import com.therapyCommunity_Vol1.backend.post.dto.PostImageResponse;
import com.therapyCommunity_Vol1.backend.post.dto.PostVideoResponse;
import com.therapyCommunity_Vol1.backend.post.dto.UploadConfirmResponse;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import com.therapyCommunity_Vol1.backend.post.service.PostImageService;
import com.therapyCommunity_Vol1.backend.post.service.PostVideoService;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

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

class UploadConfirmServiceTest {

    private ActivePostFinder activePostFinder;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private ResourceAccessValidator resourceAccessValidator;
    private MediaKindPolicy mediaKindPolicy;
    private FileStorageService fileStorageService;
    private UploadInitService uploadInitService;
    private PostImageService postImageService;
    private PostAttachmentService postAttachmentService;
    private PostVideoService postVideoService;

    private UploadConfirmService service;

    private static final long MB = 1024L * 1024L;

    @BeforeEach
    void setUp() {
        activePostFinder = mock(ActivePostFinder.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);
        resourceAccessValidator = mock(ResourceAccessValidator.class);
        mediaKindPolicy = new MediaKindPolicy();
        fileStorageService = mock(FileStorageService.class);
        uploadInitService = mock(UploadInitService.class);
        postImageService = mock(PostImageService.class);
        postAttachmentService = mock(PostAttachmentService.class);
        postVideoService = mock(PostVideoService.class);

        service = new UploadConfirmService(
                activePostFinder,
                visibilityPolicy,
                resourceAccessValidator,
                mediaKindPolicy,
                fileStorageService,
                uploadInitService,
                postImageService,
                postAttachmentService,
                postVideoService
        );
    }

    @Test
    void confirm_image_persistsAndReturnsResponseWithPresignedGet() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 5 * MB));
        when(uploadInitService.currentCount(MediaKind.IMAGE, 7L)).thenReturn(2);
        when(postImageService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new PostImageResponse(100L, "https://signed", "abc.jpg", 0, LocalDateTime.now()));

        UploadConfirmResponse response = service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg");

        assertThat(response.getKind()).isEqualTo(MediaKind.IMAGE);
        assertThat(response.getImage()).isNotNull();
        assertThat(response.getAttachment()).isNull();
        assertThat(response.getVideo()).isNull();
        verify(fileStorageService).copy(eq(storedKey), anyString());
        verify(fileStorageService).delete(storedKey);
    }

    @Test
    void confirm_attachment_delegatesToAttachmentService() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/attachments/7/file.pdf";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("application/pdf", 1 * MB));
        when(uploadInitService.currentCount(MediaKind.ATTACHMENT, 7L)).thenReturn(0);
        when(postAttachmentService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new PostAttachmentResponse(200L, "doc.pdf", "application/pdf", 1024L, "pdf", "url", LocalDateTime.now()));

        UploadConfirmResponse response = service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.ATTACHMENT, storedKey, "doc.pdf");

        assertThat(response.getKind()).isEqualTo(MediaKind.ATTACHMENT);
        assertThat(response.getAttachment()).isNotNull();
    }

    @Test
    void confirm_video_persistsVideoEntity() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/videos/7/v.mp4";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("video/mp4", 100 * MB));
        when(uploadInitService.currentCount(MediaKind.VIDEO, 7L)).thenReturn(0);
        when(postVideoService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new PostVideoResponse(300L, "url", null, "v.mp4", "video/mp4", 100L, null, LocalDateTime.now()));

        UploadConfirmResponse response = service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.VIDEO, storedKey, "v.mp4");

        assertThat(response.getKind()).isEqualTo(MediaKind.VIDEO);
        assertThat(response.getVideo()).isNotNull();
    }

    @Test
    void confirm_throwsWhenStoredKeyDoesNotMatchPostId() {
        // storedKey 의 postId 가 path postId 와 다름
        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE,
                "uploads-pending/images/999/abc.jpg", "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verify(fileStorageService, never()).headObject(anyString());
        verify(fileStorageService, never()).copy(anyString(), anyString());
    }

    @Test
    void confirm_throwsWhenStoredKeyKindMismatch() {
        // confirm 요청은 IMAGE 인데 storedKey 는 videos/...
        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE,
                "uploads-pending/videos/7/v.mp4", "v.mp4"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void confirm_throwsWhenS3HeadReturnsNull() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(null);

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_NOT_FOUND_IN_S3);

        verify(fileStorageService, never()).copy(anyString(), anyString());
    }

    @Test
    void confirm_throwsWhenActualSizeExceedsLimit() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        // 클라가 init 시 1MB 신고했지만 실제 PUT 한 객체는 11MB
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 11 * MB));

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_IMAGE);

        verify(fileStorageService, never()).copy(anyString(), anyString());
    }

    @Test
    void confirm_throwsWhenPerPostLimitExceededAtConfirmTime() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 1 * MB));
        when(uploadInitService.currentCount(MediaKind.IMAGE, 7L)).thenReturn(10);

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_MEDIA_LIMIT_EXCEEDED);
    }

    @Test
    void confirm_safeDeletesFinalKeyWhenInsertFails() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 1 * MB));
        when(uploadInitService.currentCount(MediaKind.IMAGE, 7L)).thenReturn(0);
        when(postImageService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(RuntimeException.class);

        // copy 는 호출, finalKey delete 도 호출 (롤백), pending key delete 는 호출 안 됨
        verify(fileStorageService).copy(eq(storedKey), anyString());
        verify(fileStorageService, never()).delete(storedKey);
    }

    @Test
    void confirm_throwsWhenAuthorMismatch() {
        TherapyPost post = post(7L, user(99L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        doThrow(new CustomException(ErrorCode.POST_ACCESS_DENIED))
                .when(resourceAccessValidator)
                .validateAuthorOrAdmin(anyLong(), anyLong(), eq(UserRole.THERAPIST), eq(ErrorCode.POST_ACCESS_DENIED));

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_ACCESS_DENIED);
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
