package com.therapyCommunity_Vol1.backend.notification.domain;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Getter
public enum NotificationType {
    NEW_COMMENT("%s님이 회원님의 게시글에 댓글을 남겼습니다.", 0),
    NEW_REPLY("%s님이 회원님의 댓글에 답글을 남겼습니다.", 0),
    NEW_POST_REACTION("%s님이 회원님의 게시글에 %s 반응을 남겼습니다.", 1),
    NEW_COMMENT_REACTION("%s님이 회원님의 댓글에 %s 반응을 남겼습니다.", 1),
    NEW_SCRAP("%s님이 회원님의 게시글을 스크랩했습니다.", 0),
    VERIFICATION_SUBMITTED("%s님이 치료사 인증을 신청했습니다.", 0),
    VERIFICATION_APPROVED("치료사 인증이 승인되었습니다.", 0),
    VERIFICATION_REJECTED("치료사 인증이 거절되었습니다.", 0),
    NEW_FOLLOW("%s님이 회원님을 팔로우했습니다.", 0);

    private final String messageTemplate;
    private final int requiredExtraParams;

    NotificationType(String messageTemplate, int requiredExtraParams) {
        this.messageTemplate = messageTemplate;
        this.requiredExtraParams = requiredExtraParams;
    }

    public String formatMessage(String senderNickname, List<String> extraParams) {
        if (senderNickname == null && (extraParams == null || extraParams.isEmpty())) {
            return messageTemplate;
        }

        int extraCount = (extraParams != null) ? extraParams.size() : 0;
        if (extraCount != requiredExtraParams) {
            log.warn("NotificationType {} expects {} extraParams but got {}", this, requiredExtraParams, extraCount);
        }

        Object[] args = new Object[1 + extraCount];
        args[0] = senderNickname;
        for (int i = 0; i < extraCount; i++) {
            args[i + 1] = extraParams.get(i);
        }

        try {
            return String.format(messageTemplate, args);
        } catch (Exception e) {
            log.error("알림 메시지 포맷 실패: type={}, senderNickname={}, extraParams={}", this, senderNickname, extraParams, e);
            return messageTemplate;
        }
    }

    public String formatMessage(String senderNickname, String... extras) {
        return formatMessage(senderNickname, extras.length > 0 ? List.of(extras) : null);
    }
}