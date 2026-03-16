package com.therapyCommunity_Vol1.backend.reaction.dto;

import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostReactionStatusResponse {

    private Long postId;
    private Long empathyCount;
    private Long appreciateCount;
    private Long helpfulCount;
    private PostReactionType myReactionType;
}
