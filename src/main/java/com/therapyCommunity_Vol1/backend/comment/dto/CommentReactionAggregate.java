package com.therapyCommunity_Vol1.backend.comment.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;

/**
 * 댓글 응답에 박을 reaction 집계 — 카운트 + 현재 사용자의 반응 종류.
 * MEL-36: 게시글과 동일한 LIKE/CURIOUS/USEFUL 3종 체계.
 */
public record CommentReactionAggregate(
        long likeCount,
        long curiousCount,
        long usefulCount,
        CommentReactionType myReactionType
) {
    public static CommentReactionAggregate empty() {
        return new CommentReactionAggregate(0L, 0L, 0L, null);
    }
}
