package com.therapyCommunity_Vol1.backend.reaction.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 반응 타입 enum.
 *
 * 확장 포인트: 새 반응 타입 추가 시 여기에 enum 값만 추가하면
 * Repository(grouped count), Service(values() 순회), Response(legacy 필드 + top reaction)가
 * 자동으로 반영된다. 서비스/DTO 코드 수정 불필요.
 *
 * - label: 프론트 표시용 한국어 라벨
 * - colorToken: 디자인 시스템 색상 토큰 (프론트에서 색상 매핑에 사용)
 * - displayOrder: 동률 시 대표 반응 결정 기준 (낮을수록 우선)
 */
@Getter
@RequiredArgsConstructor
public enum PostReactionType {

    EMPATHY("공감", "primary", 0),
    APPRECIATE("잘 봤어요", "success", 1),
    HELPFUL("유익", "info", 2);

    private final String label;
    private final String colorToken;
    private final int displayOrder;
}
