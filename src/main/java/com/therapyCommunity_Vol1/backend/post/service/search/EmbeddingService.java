package com.therapyCommunity_Vol1.backend.post.service.search;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.search.embedding.enabled", havingValue = "true")
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final TherapyPostRepository therapyPostRepository;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer generationTimer;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            TherapyPostRepository therapyPostRepository,
                            MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.therapyPostRepository = therapyPostRepository;
        this.successCounter = Counter.builder("embedding.generation.success")
                .description("임베딩 생성 성공 횟수")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("embedding.generation.failure")
                .description("임베딩 생성 실패 횟수")
                .register(meterRegistry);
        this.generationTimer = Timer.builder("embedding.generation.duration")
                .description("임베딩 생성 소요 시간")
                .register(meterRegistry);
    }

    /** 검색 쿼리 임베딩 캐시 — Caffeine LRU + TTL (동일 검색어 반복 호출 시 OpenAI API 절약) */
    private final Cache<String, float[]> queryEmbeddingCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    /**
     * 검색 쿼리를 임베딩 벡터로 변환한다. 동일 텍스트는 캐시에서 반환.
     * 검색 쿼리 전용 — 게시글 본문 임베딩에는 embedWithoutCache()를 사용할 것.
     */
    public float[] embed(String text) {
        return queryEmbeddingCache.get(text, key -> embeddingModel.embed(key));
    }

    /**
     * 게시글 본문 임베딩용 — 캐시하지 않는다.
     * 게시글 본문은 각각 고유하므로 캐시에 넣어도 히트가 발생하지 않고,
     * 검색 쿼리 캐시 슬롯만 낭비한다.
     */
    public float[] embedWithoutCache(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 게시글의 search_text를 임베딩하여 content_embedding 컬럼에 저장한다.
     * 실패 시 최대 3회 재시도 (1초 → 2초 → 4초 간격).
     * 카운터는 작업 단위로만 기록: 성공은 여기서, 실패는 @Recover에서.
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void generateAndSave(Long postId, String text) {
        generationTimer.record(() -> {
            float[] embedding = embedWithoutCache(text);
            String vectorString = toVectorString(embedding);
            therapyPostRepository.updateContentEmbedding(postId, vectorString);
            log.debug("임베딩 저장 완료: postId={}, dimension={}", postId, embedding.length);
        });
        successCounter.increment();
    }

    /**
     * 모든 재시도 소진 후 호출되는 최종 실패 핸들러.
     * failureCounter는 여기서만 증가하여 작업 단위 메트릭을 보장한다.
     */
    @Recover
    public void generateAndSaveRecover(Exception e, Long postId, String text) {
        failureCounter.increment();
        log.error("임베딩 생성 최종 실패 (재시도 소진): postId={}", postId, e);
        throw new RuntimeException("임베딩 생성 최종 실패: postId=" + postId, e);
    }

    /**
     * float[] → pgvector 텍스트 포맷 "[0.1,0.2,...]" 변환.
     */
    static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
