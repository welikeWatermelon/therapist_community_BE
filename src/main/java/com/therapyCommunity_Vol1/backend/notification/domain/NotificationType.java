package com.therapyCommunity_Vol1.backend.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    COMMENT("댓글"),
    REPLY("대댓글"),
    POST_REACTION("게시글 반응"),
    COMMENT_REACTION("댓글 반응");

    private final String description;
}
