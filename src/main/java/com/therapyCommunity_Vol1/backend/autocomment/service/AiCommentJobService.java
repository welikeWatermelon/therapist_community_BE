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
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCommentJobService {

    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_POLL = 10;
    private static final int[] BACKOFF_MINUTES = {1, 5, 30};

    private final PostAiCommentJobRepository jobRepository;
    private final GeminiEmbeddingClient embeddingClient;
    private final GeminiChatClient chatClient;
    private final KnowledgeChunkSearchRepository chunkSearchRepository;
    private final AiCommentProperties properties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void pollDueJobs() {
        if (!properties.isEnabled()) return;

        List<PostAiCommentJob> dueJobs = jobRepository.findDueJobs(LocalDateTime.now(), MAX_POLL);
        for (PostAiCommentJob job : dueJobs) {
            try {
                processJob(job);
            } catch (Exception e) {
                log.error("AI comment job processing failed: jobId={}", job.getId(), e);
            }
        }
    }

    @Transactional
    public void processJob(PostAiCommentJob job) {
        // pessimistic lock으로 재조회 — 스케줄러/이벤트 동시 접근 방지
        PostAiCommentJob lockedJob = jobRepository.findByIdForUpdate(job.getId())
                .orElse(null);
        if (lockedJob == null || lockedJob.isTerminal()) return;
        job = lockedJob;

        job.markProcessing();

        try {
            TherapyPost post = job.getPost();

            // 삭제/PRIVATE 체크
            if (post.isDeleted() || post.getVisibility() == Visibility.PRIVATE) {
                job.markCancelled();
                return;
            }

            // content 정제
            String cleanContent = cleanHtml(post.getContent());
            if (cleanContent.length() > MAX_CONTENT_LENGTH) {
                cleanContent = cleanContent.substring(0, MAX_CONTENT_LENGTH);
            }

            // embedding + retrieval
            float[] queryEmbedding = embeddingClient.embed(cleanContent);
            List<ChunkSearchResult> chunks = chunkSearchRepository.findSimilarChunks(
                    queryEmbedding, post.getTherapyArea(), properties.getRetrieval().getTopK()
            );

            // RAG vs FALLBACK 판정
            boolean hasGoodResults = !chunks.isEmpty()
                    && chunks.get(0).score() >= properties.getRetrieval().getMinScore();

            SourceMode sourceMode = hasGoodResults ? SourceMode.RAG : SourceMode.FALLBACK;
            double confidenceScore = hasGoodResults ? chunks.get(0).score() : 0.1;

            // 프롬프트 구성
            String systemPrompt = buildSystemPrompt(sourceMode);
            String userPrompt = buildUserPrompt(cleanContent, post.getTherapyArea(), chunks, sourceMode);

            // Gemini 호출
            GeminiChatClient.ChatResponse chatResponse = chatClient.generate(systemPrompt, userPrompt);

            if (chatResponse.comment() == null || chatResponse.comment().isBlank()) {
                job.markFailed("EMPTY_COMMENT", "LLM returned empty comment", null);
                return;
            }

            // retrieval context JSON
            String contextJson = null;
            if (sourceMode == SourceMode.RAG && chatResponse.grounds() != null) {
                contextJson = objectMapper.writeValueAsString(chatResponse.grounds());
            }

            job.markSucceeded(chatResponse.comment(), contextJson, sourceMode, confidenceScore);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                handleRetryable(job, "RATE_LIMITED", e.getMessage());
            } else {
                job.markFailed("CLIENT_ERROR_" + e.getStatusCode().value(), truncate(e.getMessage()), null);
            }
        } catch (HttpServerErrorException e) {
            handleRetryable(job, "SERVER_ERROR_" + e.getStatusCode().value(), e.getMessage());
        } catch (ResourceAccessException e) {
            handleRetryable(job, "TIMEOUT", e.getMessage());
        } catch (Exception e) {
            job.markFailed(e.getClass().getSimpleName(), truncate(e.getMessage()), null);
        }
    }

    private void handleRetryable(PostAiCommentJob job, String errorCode, String errorMessage) {
        if (job.isRetryable()) {
            int backoffIndex = Math.min(job.getAttemptCount() - 1, BACKOFF_MINUTES.length - 1);
            LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(BACKOFF_MINUTES[backoffIndex]);
            job.markFailed(errorCode, truncate(errorMessage), nextAttempt);
        } else {
            job.markFailed(errorCode, truncate(errorMessage), null);
        }
    }

    private String buildSystemPrompt(SourceMode sourceMode) {
        String base = """
                당신은 치료사 커뮤니티의 AI 어시스턴트입니다.
                게시글에 대한 댓글 초안을 작성해주세요.

                규칙:
                - 한국어로 작성
                - 2~4문장
                - 진단, 처방, 확정적 의료 조언 금지
                - 개인정보 요청 금지
                - 인간 치료사처럼 가장하지 않기
                - 근거 없는 단정 금지

                반드시 JSON 형식으로 응답: {"comment":"...", "grounds":[]}
                """;

        if (sourceMode == SourceMode.RAG) {
            return base + "\n공감 1문장 + 근거 기반 실질 조언 1~2문장 + 필요 시 자료 추천.";
        } else {
            return base + "\n공감 1문장 + 일반적 조언 1~2문장. grounds는 빈 배열로 반환.";
        }
    }

    private String buildUserPrompt(String content, com.therapyCommunity_Vol1.backend.post.domain.TherapyArea therapyArea,
                                    List<ChunkSearchResult> chunks, SourceMode sourceMode) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 게시글\n");
        if (therapyArea != null) {
            sb.append("치료 영역: ").append(therapyArea.getDescription()).append("\n");
        }
        sb.append("내용:\n").append(content).append("\n");

        if (sourceMode == SourceMode.RAG && !chunks.isEmpty()) {
            sb.append("\n## 참고 자료\n");
            for (ChunkSearchResult chunk : chunks) {
                sb.append("- [문서 ").append(chunk.documentId()).append(": ").append(chunk.title()).append("]\n");
                sb.append(chunk.content()).append("\n\n");
            }
        }

        return sb.toString();
    }

    private String cleanHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private String truncate(String message) {
        if (message == null) return "Unknown";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
