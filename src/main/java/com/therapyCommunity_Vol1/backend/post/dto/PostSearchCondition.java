package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostSearchCondition {

    private final String keyword;
    private final TherapyArea therapyArea;
    private final PostType postType;

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    public boolean hasTherapyArea() {
        return therapyArea != null;
    }

    public boolean hasPostType() {
        return postType != null;
    }

    public boolean isEmpty() {
        return !hasKeyword() && !hasTherapyArea() && !hasPostType();
    }
}
