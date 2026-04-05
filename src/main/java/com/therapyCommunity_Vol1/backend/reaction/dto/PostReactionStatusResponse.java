package com.therapyCommunity_Vol1.backend.reaction.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 게시글 반응 상태 응답.
 *
 * Legacy 필드 (empathyCount, appreciateCount, helpfulCount):
 *   프론트 하위 호환을 위해 유지. 새 반응 타입 추가 시에도 이 필드는 그대로 두고,
 *   프론트는 reactionCounts map을 사용하도록 점진적으로 마이그레이션.
 *
 * 확장 필드:
 *   - reactionCounts: 모든 반응 타입의 count map (타입 추가 시 자동 포함)
 *   - topReactionType/Count/ColorToken: 대표 반응 (count 최대, 동률 시 displayOrder 우선)
 */
@Getter
@AllArgsConstructor
public class PostReactionStatusResponse {

    private Long postId;

    // --- Legacy 필드 (하위 호환) ---
    private Long empathyCount;
    private Long appreciateCount;
    private Long helpfulCount;
    private PostReactionType myReactionType;

    // --- 확장 필드 ---
    /** 모든 반응 타입별 count. 새 타입 추가 시 자동 포함. */
    private Map<PostReactionType, Long> reactionCounts;

    /** 대표 반응: count가 가장 큰 타입. 모두 0이면 null. */
    private PostReactionType topReactionType;

    /** 대표 반응의 count. topReactionType이 null이면 null. */
    private Long topReactionCount;

    /** 대표 반응의 색상 토큰. topReactionType이 null이면 null. */
    private String topReactionColorToken;
}
