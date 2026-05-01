package com.therapyCommunity_Vol1.backend.reaction.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 댓글 reaction 상태 응답 (toggle / status 조회).
 * MEL-36: 게시글과 일관된 LIKE/CURIOUS/USEFUL 3종.
 */
@Getter
@AllArgsConstructor
public class CommentReactionStatusResponse {

    private Long commentId;
    private Long likeCount;
    private Long curiousCount;
    private Long usefulCount;
    private CommentReactionType myReactionType;
}
