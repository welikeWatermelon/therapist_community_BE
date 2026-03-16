package com.therapyCommunity_Vol1.backend.reaction.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TogglePostReactionRequest {

    @NotNull(message = "게시글 반응 타입은 필수입니다.")
    private PostReactionType reactionType;
}
