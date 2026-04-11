package com.therapyCommunity_Vol1.backend.autocomment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.autocomment.client.GeminiChatClient;
import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.autocomment.domain.AutoCommentJobStatus;
import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;
import com.therapyCommunity_Vol1.backend.autocomment.domain.SourceMode;
import com.therapyCommunity_Vol1.backend.autocomment.repository.PostAiCommentJobRepository;
import com.therapyCommunity_Vol1.backend.knowledge.client.GeminiEmbeddingClient;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeChunkSearchRepository;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeChunkSearchRepository.ChunkSearchResult;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiCommentJobServiceTest {

    private PostAiCommentJobRepository jobRepository;
    private GeminiEmbeddingClient embeddingClient;
    private GeminiChatClient chatClient;
    private KnowledgeChunkSearchRepository chunkSearchRepository;
    private AiCommentProperties properties;
    private AiCommentJobService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(PostAiCommentJobRepository.class);
        embeddingClient = mock(GeminiEmbeddingClient.class);
        chatClient = mock(GeminiChatClient.class);
        chunkSearchRepository = mock(KnowledgeChunkSearchRepository.class);
        properties = new AiCommentProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");

        service = new AiCommentJobService(
                jobRepository, embeddingClient, chatClient,
                chunkSearchRepository, properties, new ObjectMapper()
        );
    }

    @Test
    void RAG_모드_성공() {
        User author = User.builder().id(1L).email("t@t.com").nickname("t").role(UserRole.THERAPIST).build();
        TherapyPost post = TherapyPost.create("<p>언어치료 질문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
        ReflectionTestUtils.setField(post, "id", 10L);

        PostAiCommentJob job = PostAiCommentJob.create(post, author);
        ReflectionTestUtils.setField(job, "id", 1L);

        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(embeddingClient.embed(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new float[768]);
        when(chunkSearchRepository.findSimilarChunks(any(), eq(TherapyArea.SPEECH), anyInt()))
                .thenReturn(List.of(new ChunkSearchResult(1L, 1L, "근거 텍스트", "문서 제목", 0.8)));
        when(chatClient.generate(anyString(), anyString()))
                .thenReturn(new GeminiChatClient.ChatResponse("좋은 질문입니다.", List.of()));

        service.processJob(job);

        assertThat(job.getStatus()).isEqualTo(AutoCommentJobStatus.SUCCEEDED);
        assertThat(job.getSourceMode()).isEqualTo(SourceMode.RAG);
        assertThat(job.getDraftComment()).isEqualTo("좋은 질문입니다.");
    }

    @Test
    void FALLBACK_모드_검색결과_없음() {
        User author = User.builder().id(1L).email("t@t.com").nickname("t").role(UserRole.THERAPIST).build();
        TherapyPost post = TherapyPost.create("<p>질문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
        ReflectionTestUtils.setField(post, "id", 10L);

        PostAiCommentJob job = PostAiCommentJob.create(post, author);
        ReflectionTestUtils.setField(job, "id", 1L);

        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(embeddingClient.embed(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new float[768]);
        when(chunkSearchRepository.findSimilarChunks(any(), any(), anyInt()))
                .thenReturn(List.of());
        when(chatClient.generate(anyString(), anyString()))
                .thenReturn(new GeminiChatClient.ChatResponse("응원합니다.", List.of()));

        service.processJob(job);

        assertThat(job.getStatus()).isEqualTo(AutoCommentJobStatus.SUCCEEDED);
        assertThat(job.getSourceMode()).isEqualTo(SourceMode.FALLBACK);
    }

    @Test
    void 삭제된_게시글은_CANCELLED() {
        User author = User.builder().id(1L).email("t@t.com").nickname("t").role(UserRole.THERAPIST).build();
        TherapyPost post = TherapyPost.create("<p>질문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
        ReflectionTestUtils.setField(post, "id", 10L);
        post.softDelete();

        PostAiCommentJob job = PostAiCommentJob.create(post, author);
        ReflectionTestUtils.setField(job, "id", 1L);

        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        service.processJob(job);

        assertThat(job.getStatus()).isEqualTo(AutoCommentJobStatus.CANCELLED);
    }

    @Test
    void PRIVATE_전환된_게시글은_CANCELLED() {
        User author = User.builder().id(1L).email("t@t.com").nickname("t").role(UserRole.THERAPIST).build();
        TherapyPost post = TherapyPost.create("<p>질문</p>", TherapyArea.SPEECH, Visibility.PRIVATE, author);
        ReflectionTestUtils.setField(post, "id", 10L);

        PostAiCommentJob job = PostAiCommentJob.create(post, author);
        ReflectionTestUtils.setField(job, "id", 1L);

        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        service.processJob(job);

        assertThat(job.getStatus()).isEqualTo(AutoCommentJobStatus.CANCELLED);
    }

    @Test
    void terminal_상태_job은_무시() {
        User author = User.builder().id(1L).email("t@t.com").nickname("t").role(UserRole.THERAPIST).build();
        TherapyPost post = TherapyPost.create("<p>질문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
        ReflectionTestUtils.setField(post, "id", 10L);

        PostAiCommentJob job = PostAiCommentJob.create(post, author);
        ReflectionTestUtils.setField(job, "id", 1L);
        job.markCancelled();

        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        service.processJob(job);

        assertThat(job.getStatus()).isEqualTo(AutoCommentJobStatus.CANCELLED);
        verifyNoInteractions(embeddingClient, chatClient);
    }
}
