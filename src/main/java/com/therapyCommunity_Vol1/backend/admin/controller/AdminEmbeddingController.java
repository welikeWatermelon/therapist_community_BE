package com.therapyCommunity_Vol1.backend.admin.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.post.service.search.EmbeddingBackfillService;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;

@Tag(name = "관리자 — 임베딩", description = "게시글 임베딩 백필 관리")
@Validated
@RestController
@RequestMapping("/api/v1/admin/embeddings")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.embedding.enabled", havingValue = "true")
public class AdminEmbeddingController {

    private final EmbeddingBackfillService embeddingBackfillService;
    private final TherapyPostRepository therapyPostRepository;

    @Operation(summary = "임베딩 백필", description = "content_embedding 이 NULL 인 게시글에 임베딩을 생성한다.")
    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> backfill(
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int batchSize
    ) {
        int processed = embeddingBackfillService.backfillBatch(batchSize);
        return ResponseEntity.ok(ApiResponse.success(Map.of("processed", processed)));
    }

    @Operation(summary = "임베딩 통계", description = "게시글 임베딩 상태별 통계를 반환한다.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        Object[] row = therapyPostRepository.countEmbeddingStats();
        Object[] cols = (Object[]) row;
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "done", ((Number) cols[0]).longValue(),
                "failed", ((Number) cols[1]).longValue(),
                "pending", ((Number) cols[2]).longValue(),
                "total", ((Number) cols[3]).longValue()
        )));
    }
}
