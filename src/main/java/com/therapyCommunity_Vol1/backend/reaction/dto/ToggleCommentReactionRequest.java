package com.therapyCommunity_Vol1.backend.reaction.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ToggleCommentReactionRequest {

    @NotNull(message = "댓글 반을 타입은 필수입니다.")
    private CommentReactionType reactionType;
}
