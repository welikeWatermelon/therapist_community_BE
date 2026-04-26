package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 기존 게시글의 임베딩을 백필하는 서비스.
 *
 * content_embedding IS NULL 인 게시글을 배치 단위로 조회하여
 * 임베딩을 생성하고 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.embedding.enabled", havingValue = "true")
public class EmbeddingBackfillService {

    private final TherapyPostRepository therapyPostRepository;
    private final EmbeddingService embeddingService;
    private final EmbeddingFailureRecorder failureRecorder;

    /**
     * 임베딩이 없는 게시글을 배치 단위로 백필한다.
     *
     * @param batchSize 한 번에 처리할 게시글 수
     * @return 처리된 게시글 수
     */
    public int backfillBatch(int batchSize) {
        List<Object[]> rows = therapyPostRepository.findIdsWithoutEmbedding(batchSize);

        if (rows.isEmpty()) {
            log.info("백필 대상 게시글 없음");
            return 0;
        }

        int processed = 0;
        for (Object[] row : rows) {
            Long postId = ((Number) row[0]).longValue();
            String searchText = (String) row[1];
            try {
                embeddingService.generateAndSave(postId, searchText);
                processed++;
                Thread.sleep(100); // OpenAI rate limit 여유분 확보 (최대 ~10 req/s)
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("백필 중단됨: 처리={}/{}", processed, rows.size());
                break;
            } catch (Exception e) {
                log.error("백필 실패: postId={}", postId, e);
                try {
                    failureRecorder.markFailed(postId);
                } catch (Exception ex) {
                    log.error("백필 실패 마킹 오류: postId={}", postId, ex);
                }
            }
        }

        log.info("백필 완료: 처리={}/{}", processed, rows.size());
        return processed;
    }
}
