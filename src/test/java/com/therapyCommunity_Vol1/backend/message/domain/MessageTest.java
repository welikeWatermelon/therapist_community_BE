package com.therapyCommunity_Vol1.backend.message.domain;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    private User createUser(Long id, String nickname) {
        return User.builder()
                .id(id)
                .email(id + "@test.com")
                .nickname(nickname)
                .role(UserRole.THERAPIST)
                .build();
    }

    @Test
    void 일반_쪽지를_생성할_수_있다() {
        User sender = createUser(1L, "발신자");
        User receiver = createUser(2L, "수신자");

        Message message = Message.create(sender, receiver, "안녕하세요");

        assertThat(message.getSender()).isEqualTo(sender);
        assertThat(message.getReceiver()).isEqualTo(receiver);
        assertThat(message.getContent()).isEqualTo("안녕하세요");
        assertThat(message.isRead()).isFalse();
        assertThat(message.isDeletedBySender()).isFalse();
        assertThat(message.isDeletedByReceiver()).isFalse();
        assertThat(message.getBroadcastId()).isNull();
    }

    @Test
    void 공지_쪽지를_생성할_수_있다() {
        User sender = createUser(1L, "관리자");
        User receiver = createUser(2L, "수신자");
        UUID broadcastId = UUID.randomUUID();

        Message message = Message.createBroadcast(sender, receiver, "공지사항", broadcastId);

        assertThat(message.getBroadcastId()).isEqualTo(broadcastId);
    }

    @Test
    void 읽음_처리를_할_수_있다() {
        User sender = createUser(1L, "발신자");
        User receiver = createUser(2L, "수신자");
        Message message = Message.create(sender, receiver, "테스트");

        message.markAsRead();

        assertThat(message.isRead()).isTrue();
        assertThat(message.getReadAt()).isNotNull();
    }

    @Test
    void 이미_읽은_쪽지를_다시_읽어도_readAt이_변경되지_않는다() {
        User sender = createUser(1L, "발신자");
        User receiver = createUser(2L, "수신자");
        Message message = Message.create(sender, receiver, "테스트");

        message.markAsRead();
        var firstReadAt = message.getReadAt();

        message.markAsRead();

        assertThat(message.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void 발신자가_삭제할_수_있다() {
        User sender = createUser(1L, "발신자");
        User receiver = createUser(2L, "수신자");
        Message message = Message.create(sender, receiver, "테스트");

        message.deleteBySender();

        assertThat(message.isDeletedBySender()).isTrue();
        assertThat(message.isDeletedByReceiver()).isFalse();
    }

    @Test
    void 수신자가_삭제할_수_있다() {
        User sender = createUser(1L, "발신자");
        User receiver = createUser(2L, "수신자");
        Message message = Message.create(sender, receiver, "테스트");

        message.deleteByReceiver();

        assertThat(message.isDeletedBySender()).isFalse();
        assertThat(message.isDeletedByReceiver()).isTrue();
    }

    @Test
    void 참여자_여부를_확인할_수_있다() {
        User sender = createUser(1L, "발신자");
        User receiver = createUser(2L, "수신자");
        Message message = Message.create(sender, receiver, "테스트");

        assertThat(message.isSender(1L)).isTrue();
        assertThat(message.isReceiver(2L)).isTrue();
        assertThat(message.isParticipant(1L)).isTrue();
        assertThat(message.isParticipant(2L)).isTrue();
        assertThat(message.isParticipant(3L)).isFalse();
    }
}
