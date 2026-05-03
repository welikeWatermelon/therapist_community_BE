package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostVideo;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.dto.PostVideoResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostVideoRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostVideoServiceTest {

    private ActivePostFinder activePostFinder;
    private TherapyPostVideoRepository videoRepository;
    private FileStorageService fileStorageService;
    private ResourceAccessValidator resourceAccessValidator;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private PostVideoService service;

    @BeforeEach
    void setUp() {
        activePostFinder = mock(ActivePostFinder.class);
        videoRepository = mock(TherapyPostVideoRepository.class);
        fileStorageService = mock(FileStorageService.class);
        resourceAccessValidator = mock(ResourceAccessValidator.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);

        service = new PostVideoService(
                activePostFinder,
                videoRepository,
                fileStorageService,
                resourceAccessValidator,
                visibilityPolicy
        );
    }

    @Test
    void getVideosForPostUnchecked_returnsPresignedUrls() {
        TherapyPost post = post(7L, user(1L));
        TherapyPostVideo video = video(100L, post);
        when(videoRepository.findByPostIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(video));
        when(fileStorageService.presignGet(eq(video.getStoredPath()), any(Duration.class)))
                .thenReturn("https://signed");

        List<PostVideoResponse> responses = service.getVideosForPostUnchecked(7L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getVideoUrl()).isEqualTo("https://signed");
        assertThat(responses.get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void loadVideo_throwsWhenWrongPost() {
        TherapyPost post = post(7L, user(1L));
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(videoRepository.findByIdAndPostId(100L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadVideo(7L, 100L, UserRole.THERAPIST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_VIDEO_NOT_FOUND);
    }

    @Test
    void confirmUpload_persistsVideoEntity() {
        TherapyPost post = post(7L, user(1L));
        when(videoRepository.save(any())).thenAnswer(inv -> {
            TherapyPostVideo v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 999L);
            return v;
        });

        PostVideoResponse response = service.confirmUpload(post, "post-videos/x.mp4", "v.mp4", "video/mp4", 100L);

        assertThat(response.getId()).isEqualTo(999L);
        assertThat(response.getOriginalFilename()).isEqualTo("v.mp4");
        verify(videoRepository).save(any(TherapyPostVideo.class));
    }

    @Test
    void deleteVideo_schedulesS3DeleteAfterCommit() {
        TherapyPost post = post(7L, user(1L));
        TherapyPostVideo video = video(100L, post);
        when(activePostFinder.findOrThrow(7L)).thenReturn(post);
        when(videoRepository.findByIdAndPostId(100L, 7L)).thenReturn(Optional.of(video));

        try {
            TransactionSynchronizationManager.initSynchronization();
            service.deleteVideo(1L, UserRole.THERAPIST, 7L, 100L);
            // delete 는 아직 호출되지 않아야 함 (afterCommit 까지 지연)
            verify(fileStorageService, never()).delete(anyString());

            // afterCommit 트리거
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(fileStorageService).delete(video.getStoredPath());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private User user(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").nickname("u" + id).role(UserRole.THERAPIST).build();
    }

    private TherapyPost post(Long id, User author) {
        TherapyPost p = TherapyPost.create("<p>c</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private TherapyPostVideo video(Long id, TherapyPost post) {
        TherapyPostVideo v = TherapyPostVideo.create(post, "post-videos/v.mp4", "v.mp4", "video/mp4", 100L);
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }
}
