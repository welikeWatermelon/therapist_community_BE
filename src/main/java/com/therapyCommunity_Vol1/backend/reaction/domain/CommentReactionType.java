package com.therapyCommunity_Vol1.backend.reaction.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 댓글 반응 타입 enum. PostReactionType과 동일한 종류 — 도메인 일관성 (MEL-36).
 *
 * 이전엔 LIKE/DISLIKE 2종이었으나, 게시글과 같은 LIKE/CURIOUS/USEFUL 3종으로 통일.
 * 기존 DISLIKE row는 V## 마이그레이션으로 삭제됨 (LIKE와 의미 반대라 변환 부적절).
 *
 * - label: 프론트 표시용 한국어 라벨
 * - colorToken: 디자인 시스템 색상 토큰 (프론트에서 색상 매핑에 사용)
 * - displayOrder: 동률 시 대표 반응 결정 기준 (낮을수록 우선)
 */
@Getter
@RequiredArgsConstructor
public enum CommentReactionType {

    LIKE("좋아요", "primary", 0),
    CURIOUS("궁금해요", "success", 1),
    USEFUL("유용해요", "info", 2);

    private final String label;
    private final String colorToken;
    private final int displayOrder;
}
