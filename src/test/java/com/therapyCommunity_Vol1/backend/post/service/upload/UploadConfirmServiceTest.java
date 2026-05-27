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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
    private MagicByteValidator magicByteValidator;
    private PostImageService postImageService;
    private PostAttachmentService postAttachmentService;
    private PostVideoService postVideoService;

    private UploadConfirmService service;

    private static final long MB = 1024L * 1024L;

    // JPEG magic bytes: FF D8 FF + padding
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    // PDF magic bytes: %PDF
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    // MP4 magic bytes: ftyp at offset 4
    private static final byte[] MP4_MAGIC = {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x00, 0x00};

    @BeforeEach
    void setUp() {
        activePostFinder = mock(ActivePostFinder.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);
        resourceAccessValidator = mock(ResourceAccessValidator.class);
        mediaKindPolicy = new MediaKindPolicy();
        fileStorageService = mock(FileStorageService.class);
        uploadInitService = mock(UploadInitService.class);
        magicByteValidator = new MagicByteValidator();
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
                magicByteValidator,
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
        when(postImageService.findByStoredPath("post-images/abc.jpg")).thenReturn(Optional.empty());
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 5 * MB));
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(JPEG_MAGIC);
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
    void confirm_image_idempotent_returnsExistingAndSkipsS3WhenAlreadyConfirmed() {
        // 같은 storedKey 로 confirm 재시도 (첫 성공 후 응답 유실 → FE 재시도).
        // finalKey 는 storedKey 의 filename 으로 결정적 생성: post-images/abc.jpg
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        PostImageResponse existing = new PostImageResponse(100L, "https://signed", "abc.jpg", 0, LocalDateTime.now());
        when(postImageService.findByStoredPath("post-images/abc.jpg")).thenReturn(Optional.of(existing));

        UploadConfirmResponse response = service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg");

        // 첫 성공과 동일한 결과를 에러·중복 없이 반환
        assertThat(response.getKind()).isEqualTo(MediaKind.IMAGE);
        assertThat(response.getImage()).isEqualTo(existing);
        // S3 head/copy 미접근 + persist 미호출. pending 잔재가 있으면 best-effort로 정리.
        verify(fileStorageService, never()).headObject(anyString());
        verify(fileStorageService, never()).copy(anyString(), anyString());
        verify(fileStorageService).delete(storedKey);
        verify(postImageService, never()).confirmUpload(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void confirm_image_uniqueConflict_returnsConflictAndPreservesSharedFinalKey() {
        // 거의 불가능한 동시 race: 진입 단락 miss 후 persist 중 다른 요청이 같은 finalKey 로 먼저
        // INSERT → stored_path 유니크 위반. PG는 위반 후 트랜잭션을 abort 하므로 같은 tx 안 재조회는
        // 불가 → 흡수하지 않고 깔끔히 실패시킨다(중복 0). 단 finalKey 는 승자 레코드가 참조하므로
        // 절대 삭제하면 안 된다. 이 클라의 재시도는 진입부 findByStoredPath 단락으로 멱등 복구.
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(postImageService.findByStoredPath("post-images/abc.jpg")).thenReturn(Optional.empty()); // 진입 단락 miss
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 1 * MB));
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(JPEG_MAGIC);
        when(uploadInitService.currentCount(MediaKind.IMAGE, 7L)).thenReturn(0);
        when(postImageService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new DataIntegrityViolationException("duplicate stored_path"));

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_CONFIRM_CONFLICT);

        // 공유 finalKey(승자 레코드가 참조) 는 삭제 금지
        verify(fileStorageService, never()).delete("post-images/abc.jpg");
    }

    @Test
    void confirm_image_usesDeterministicFinalKeyFromStoredKeyFilename() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(postImageService.findByStoredPath("post-images/abc.jpg")).thenReturn(Optional.empty());
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 1 * MB));
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(JPEG_MAGIC);
        when(uploadInitService.currentCount(MediaKind.IMAGE, 7L)).thenReturn(0);
        when(postImageService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new PostImageResponse(100L, "https://signed", "abc.jpg", 0, LocalDateTime.now()));

        service.confirm(1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg");

        // 결정적: post-images/{storedKey filename} (random UUID 아님)
        verify(fileStorageService).copy(storedKey, "post-images/abc.jpg");
        verify(postImageService).confirmUpload(any(), eq("post-images/abc.jpg"), anyString(), anyString(), anyLong());
    }

    @Test
    void confirm_attachment_delegatesToAttachmentService() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/attachments/7/file.pdf";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("application/pdf", 1 * MB));
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(PDF_MAGIC);
        when(uploadInitService.currentCount(MediaKind.ATTACHMENT, 7L)).thenReturn(0);
        when(postAttachmentService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new PostAttachmentResponse(200L, "doc.pdf", "application/pdf", 1024L, "pdf", "url", LocalDateTime.now()));

        UploadConfirmResponse response = service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.ATTACHMENT, storedKey, "doc.pdf");

        assertThat(response.getKind()).isEqualTo(MediaKind.ATTACHMENT);
        assertThat(response.getAttachment()).isNotNull();
        verify(fileStorageService).copy(
                eq(storedKey),
                argThat(key -> key.startsWith("post-attachments/") && key.endsWith(".pdf") && !key.equals("post-attachments/file.pdf"))
        );
    }

    @Test
    void confirm_video_persistsVideoEntity() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/videos/7/v.mp4";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("video/mp4", 100 * MB));
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(MP4_MAGIC);
        when(uploadInitService.currentCount(MediaKind.VIDEO, 7L)).thenReturn(0);
        when(postVideoService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new PostVideoResponse(300L, "url", null, "v.mp4", "video/mp4", 100L, null, LocalDateTime.now()));

        UploadConfirmResponse response = service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.VIDEO, storedKey, "v.mp4");

        assertThat(response.getKind()).isEqualTo(MediaKind.VIDEO);
        assertThat(response.getVideo()).isNotNull();
        verify(fileStorageService).copy(
                eq(storedKey),
                argThat(key -> key.startsWith("post-videos/") && key.endsWith(".mp4") && !key.equals("post-videos/v.mp4"))
        );
    }

    @Test
    void confirm_throwsWhenMagicBytesMismatch() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(postImageService.findByStoredPath("post-images/abc.jpg")).thenReturn(Optional.empty());
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 1 * MB));
        // 실제 파일은 PDF (클라가 image/jpeg 라고 속임)
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(PDF_MAGIC);

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);

        verify(fileStorageService, never()).copy(anyString(), anyString());
    }

    @Test
    void confirm_throwsWhenMagicBytesEmpty() {
        TherapyPost post = post(7L, user(1L));
        String storedKey = "uploads-pending/images/7/abc.jpg";
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(fileStorageService.headObject(storedKey)).thenReturn(new S3ObjectMeta("image/jpeg", 1 * MB));
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(new byte[0]);

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);
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
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(JPEG_MAGIC);

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
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(JPEG_MAGIC);
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
        when(fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES)).thenReturn(JPEG_MAGIC);
        when(uploadInitService.currentCount(MediaKind.IMAGE, 7L)).thenReturn(0);
        when(postImageService.confirmUpload(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> service.confirm(
                1L, UserRole.THERAPIST, 7L, MediaKind.IMAGE, storedKey, "abc.jpg"))
                .isInstanceOf(RuntimeException.class);

        // copy 는 호출, finalKey delete 도 호출 (롤백), pending key delete 는 호출 안 됨
        verify(fileStorageService).copy(eq(storedKey), anyString());
        verify(fileStorageService).delete("post-images/abc.jpg");
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
