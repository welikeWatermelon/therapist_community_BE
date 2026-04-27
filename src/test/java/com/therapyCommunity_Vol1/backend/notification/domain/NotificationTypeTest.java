package com.therapyCommunity_Vol1.backend.notification.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class NotificationTypeTest {

    @Test
    void 일반_알림_닉네임만_포맷된다() {
        String result = NotificationType.NEW_COMMENT.formatMessage("홍길동");

        assertThat(result).isEqualTo("홍길동님이 회원님의 게시글에 댓글을 남겼습니다.");
    }

    @Test
    void 반응_알림_닉네임과_라벨이_포맷된다() {
        String result = NotificationType.NEW_POST_REACTION.formatMessage("홍길동", "좋아요");

        assertThat(result).isEqualTo("홍길동님이 회원님의 게시글에 좋아요 반응을 남겼습니다.");
    }

    @Test
    void 시스템_알림_sender_null이면_템플릿_그대로_반환한다() {
        String result = NotificationType.VERIFICATION_APPROVED.formatMessage(null);

        assertThat(result).isEqualTo("치료사 인증이 승인되었습니다.");
    }

    @Test
    void 시스템_알림에_senderNickname을_넘겨도_여분_args는_무시된다() {
        String result = NotificationType.VERIFICATION_APPROVED.formatMessage("관리자");

        assertThat(result).isEqualTo("치료사 인증이 승인되었습니다.");
    }

    @Test
    void List_extraParams로_반응_알림이_정상_포맷된다() {
        String result = NotificationType.NEW_COMMENT_REACTION
                .formatMessage("홍길동", List.of("싫어요"));

        assertThat(result).isEqualTo("홍길동님이 회원님의 댓글에 싫어요 반응을 남겼습니다.");
    }

    @Test
    void 대댓글_알림이_정상_포맷된다() {
        String result = NotificationType.NEW_REPLY.formatMessage("김영준");

        assertThat(result).isEqualTo("김영준님이 회원님의 댓글에 답글을 남겼습니다.");
    }

    @Test
    void 스크랩_알림이_정상_포맷된다() {
        String result = NotificationType.NEW_SCRAP.formatMessage("이치료");

        assertThat(result).isEqualTo("이치료님이 회원님의 게시글을 스크랩했습니다.");
    }
}
