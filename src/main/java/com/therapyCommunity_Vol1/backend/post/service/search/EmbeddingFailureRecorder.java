package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임베딩 생성 실패 시 embedding_failed_at 컬럼을 마킹하는 전용 빈.
 *
 * EmbeddingEventListener에서 self-invocation으로 호출하면
 * Spring AOP 프록시가 @Transactional을 인터셉트하지 못하므로,
 * 별도 빈으로 추출하여 프록시 경유를 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.embedding.enabled", havingValue = "true")
public class EmbeddingFailureRecorder {

    private final TherapyPostRepository therapyPostRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long postId) {
        therapyPostRepository.markEmbeddingFailed(postId);
        log.warn("임베딩 실패 마킹 완료: postId={}", postId);
    }
}
