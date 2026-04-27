package com.therapyCommunity_Vol1.backend.reaction.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 게시글 반응 상태 응답.
 *
 * 타입별 개별 필드 (likeCount, curiousCount, usefulCount):
 *   프론트에서 3종을 개별 표시할 때 사용. 반응 타입 추가 시 함께 확장.
 *
 * 확장 필드:
 *   - reactionCounts: 모든 반응 타입의 count map (타입 추가 시 자동 포함)
 *   - topReactionType/Count/ColorToken: 대표 반응 (count 최대, 동률 시 displayOrder 우선)
 */
@Getter
@AllArgsConstructor
public class PostReactionStatusResponse {

    private Long postId;

    // --- 타입별 개별 필드 ---
    private Long likeCount;
    private Long curiousCount;
    private Long usefulCount;
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
