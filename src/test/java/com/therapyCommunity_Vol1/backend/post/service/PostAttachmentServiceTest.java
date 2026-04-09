package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.post.domain.*;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.DownloadedPostResponse;
import com.therapyCommunity_Vol1.backend.post.dto.PostAttachmentResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostDownloadRepository;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostAttachmentServiceTest {

    private ActivePostFinder activePostFinder;
    private TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private TherapyPostDownloadRepository therapyPostDownloadRepository;
    private UserRepository userRepository;
    private FileStorageService fileStorageService;
    private ResourceAccessValidator resourceAccessValidator;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private PostAttachmentService postAttachmentService;

    @BeforeEach
    void setUp() {
        activePostFinder = mock(ActivePostFinder.class);
        therapyPostAttachmentRepository = mock(TherapyPostAttachmentRepository.class);
        therapyPostDownloadRepository = mock(TherapyPostDownloadRepository.class);
        userRepository = mock(UserRepository.class);
        fileStorageService = mock(FileStorageService.class);
        resourceAccessValidator = mock(ResourceAccessValidator.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);

        postAttachmentService = new PostAttachmentService(
                activePostFinder,
                therapyPostAttachmentRepository,
                therapyPostDownloadRepository,
                userRepository,
                fileStorageService,
                resourceAccessValidator,
                visibilityPolicy
        );
    }

    @Test
    void 자료형_게시글에_pdf_첨부를_업로드할_수_있다() {
        Long userId = 1L;
        User author = therapist(userId, "author@test.com", "author");
        TherapyPost post = resourcePost(10L, author);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.pdf",
                "application/pdf",
                "%PDF-sample".getBytes()
        );

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(fileStorageService.storePostAttachment(file)).thenReturn(
                new StoredFileInfo("post-attachments/guide.pdf", "guide.pdf", "application/pdf")
        );
        when(therapyPostAttachmentRepository.save(any(TherapyPostAttachment.class))).thenAnswer(invocation -> {
            TherapyPostAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 100L);
            ReflectionTestUtils.setField(attachment, "createdAt", LocalDateTime.of(2026, 3, 22, 10, 0));
            return attachment;
        });

        PostAttachmentResponse response = postAttachmentService.uploadAttachment(
                userId,
                UserRole.THERAPIST,
                10L,
                file
        );

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getOriginalFilename()).isEqualTo("guide.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getExtension()).isEqualTo("pdf");
        assertThat(response.getDownloadUrl()).isEqualTo("/api/v1/posts/10/attachments/100/download");
        verify(fileStorageService).storePostAttachment(file);
        verify(therapyPostAttachmentRepository).save(any(TherapyPostAttachment.class));
    }

    @Test
    void 첨부파일_업로드시_PostType이_RESOURCE로_변경된다() {
        Long userId = 1L;
        User author = therapist(userId, "author@test.com", "author");
        TherapyPost post = communityPost(10L, author);

        assertThat(post.getPostType()).isEqualTo(PostType.COMMUNITY);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.pdf",
                "application/pdf",
                "%PDF-sample".getBytes()
        );

        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(fileStorageService.storePostAttachment(file)).thenReturn(
                new StoredFileInfo("post-attachments/guide.pdf", "guide.pdf", "application/pdf")
        );
        when(therapyPostAttachmentRepository.save(any(TherapyPostAttachment.class))).thenAnswer(invocation -> {
            TherapyPostAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 100L);
            ReflectionTestUtils.setField(attachment, "createdAt", LocalDateTime.of(2026, 3, 22, 10, 0));
            return attachment;
        });

        postAttachmentService.uploadAttachment(userId, UserRole.THERAPIST, 10L, file);

        assertThat(post.getPostType()).isEqualTo(PostType.RESOURCE);
    }

    @Test
    void 첨부파일_다운로드시_다운로드_이력을_신규_저장한다() {
        Long userId = 2L;
        User author = therapist(1L, "author@test.com", "author");
        User downloader = therapist(userId, "reader@test.com", "reader");
        TherapyPost post = resourcePost(10L, author);
        TherapyPostAttachment attachment = attachment(99L, post);
        StoredFileResource storedFile = new StoredFileResource(
                new ByteArrayResource("pdf".getBytes()),
                "application/pdf",
                "guide.pdf"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(downloader));
        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
        when(therapyPostAttachmentRepository.findByIdAndPostId(99L, 10L)).thenReturn(Optional.of(attachment));
        when(fileStorageService.loadAsResource("post-attachments/guide.pdf", "application/pdf", "guide.pdf"))
                .thenReturn(storedFile);
        when(therapyPostDownloadRepository.findByPostIdAndUserId(10L, userId)).thenReturn(Optional.empty());

        StoredFileResource response = postAttachmentService.downloadAttachment(userId, UserRole.THERAPIST, 10L, 99L);

        assertThat(response.getOriginalFilename()).isEqualTo("guide.pdf");
        verify(therapyPostDownloadRepository).save(any(TherapyPostDownload.class));
    }

    @Test
    void 내_다운로드_목록을_최신순으로_조회한다() {
        Long userId = 2L;
        User author = therapist(1L, "author@test.com", "author");
        User downloader = therapist(userId, "reader@test.com", "reader");
        TherapyPost post = resourcePost(10L, author);

        TherapyPostDownload download = TherapyPostDownload.create(post, downloader);
        ReflectionTestUtils.setField(download, "id", 30L);
        ReflectionTestUtils.setField(download, "firstDownloadedAt", LocalDateTime.of(2026, 3, 20, 10, 0));
        ReflectionTestUtils.setField(download, "lastDownloadedAt", LocalDateTime.of(2026, 3, 22, 12, 0));
        ReflectionTestUtils.setField(download, "downloadCount", 3L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(downloader));
        when(therapyPostDownloadRepository.findByUserIdAndPost_DeletedAtIsNull(eq(userId), any()))
                .thenReturn(new PageImpl<>(
                        List.of(download),
                        PageRequest.of(0, 10),
                        1
                ));

        PagedResponse<DownloadedPostResponse> response = postAttachmentService.getMyDownloads(userId, 0, 10);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getPostId()).isEqualTo(10L);
        assertThat(response.getItems().get(0).getPostType()).isEqualTo(PostType.RESOURCE);
        assertThat(response.getItems().get(0).getDownloadCount()).isEqualTo(3L);
        assertThat(response.isHasNext()).isFalse();
    }

    private User therapist(Long id, String email, String nickname) {
        return User.builder()
                .id(id)
                .email(email)
                .nickname(nickname)
                .role(UserRole.THERAPIST)
                .build();
    }

    private TherapyPost resourcePost(Long id, User author) {
        TherapyPost post = TherapyPost.create(
                "<p>자료 본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );
        post.updatePostType(PostType.RESOURCE);
        ReflectionTestUtils.setField(post, "id", id);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.of(2026, 3, 20, 9, 0));
        return post;
    }

    private TherapyPost communityPost(Long id, User author) {
        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private TherapyPostAttachment attachment(Long id, TherapyPost post) {
        TherapyPostAttachment attachment = TherapyPostAttachment.create(
                post,
                "post-attachments/guide.pdf",
                "guide.pdf",
                "application/pdf",
                1234L,
                "pdf"
        );
        ReflectionTestUtils.setField(attachment, "id", id);
        ReflectionTestUtils.setField(attachment, "createdAt", LocalDateTime.of(2026, 3, 22, 9, 0));
        return attachment;
    }
}
