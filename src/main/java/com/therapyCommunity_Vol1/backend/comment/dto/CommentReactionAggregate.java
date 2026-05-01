package com.therapyCommunity_Vol1.backend.comment.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;

/**
 * 댓글 응답에 박을 reaction 집계 — 카운트 + 현재 사용자의 반응 종류.
 * 응답 빌드 시점에 batch 조회 결과를 메모리에서 그룹화해 만든 후 CommentResponse/ReplyCommentResponse에 전달.
 */
public record CommentReactionAggregate(
        long likeCount,
        long dislikeCount,
        CommentReactionType myReactionType
) {
    public static CommentReactionAggregate empty() {
        return new CommentReactionAggregate(0L, 0L, null);
    }
}
