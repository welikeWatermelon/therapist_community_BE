package com.therapyCommunity_Vol1.backend.reaction.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommentReactionStatusResponse {

    private Long commentId;
    private Long likeCount;
    private Long dislikeCount;
    private CommentReactionType myReactionType;
}
