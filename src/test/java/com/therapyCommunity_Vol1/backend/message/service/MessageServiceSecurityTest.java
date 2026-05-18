package com.therapyCommunity_Vol1.backend.message.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.message.domain.Message;
import com.therapyCommunity_Vol1.backend.message.dto.BroadcastMessageRequest;
import com.therapyCommunity_Vol1.backend.message.dto.MessageResponse;
import com.therapyCommunity_Vol1.backend.message.repository.MessageRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageServiceSecurityTest {

    private MessageRepository messageRepository;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private MessageService messageService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        messageService = new MessageService(messageRepository, userRepository, eventPublisher);

        sender = User.builder().id(1L).email("sender@test.com").nickname("발신자").role(UserRole.THERAPIST).build();
        receiver = User.builder().id(2L).email("receiver@test.com").nickname("수신자").role(UserRole.THERAPIST).build();
    }

    @Test
    void THERAPIST가_공지_발송하면_예외_발생() {
        User therapist = User.builder().id(10L).email("t@test.com").nickname("치료사").role(UserRole.THERAPIST).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(therapist));

        BroadcastMessageRequest request = new BroadcastMessageRequest("공지", null);

        assertThatThrownBy(() -> messageService.broadcastMessage(10L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void USER가_공지_발송하면_예외_발생() {
        User user = User.builder().id(10L).email("u@test.com").nickname("사용자").role(UserRole.USER).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        BroadcastMessageRequest request = new BroadcastMessageRequest("공지", null);

        assertThatThrownBy(() -> messageService.broadcastMessage(10L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 참여자가_아닌_사용자가_쪽지_삭제시_예외_발생() {
        Message message = Message.create(sender, receiver, "테스트");
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.deleteMessage(999L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_ACCESS_DENIED));
    }

    @Test
    void 수신자가_삭제한_쪽지를_수신자가_조회하면_예외_발생() {
        Message message = Message.create(sender, receiver, "테스트");
        message.deleteByReceiver();
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.getMessage(2L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND));
    }

    @Test
    void 발신자가_삭제한_쪽지를_수신자는_여전히_조회_가능() {
        Message message = Message.create(sender, receiver, "테스트");
        message.deleteBySender();
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

        MessageResponse response = messageService.getMessage(2L, 1L);

        assertThat(response.getContent()).isEqualTo("테스트");
        assertThat(response.isRead()).isTrue();
    }

    @Test
    void 존재하지_않는_쪽지_조회시_예외_발생() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessage(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND));
    }

    @Test
    void 존재하지_않는_쪽지_삭제시_예외_발생() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.deleteMessage(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND));
    }

    @Test
    void 탈퇴한_사용자에게_발송된_쪽지_조회시_닉네임_처리() {
        User withdrawnReceiver = User.builder().id(3L).email("w@test.com").nickname("탈퇴예정").role(UserRole.USER).build();
        withdrawnReceiver.withdraw();

        Message message = Message.create(sender, withdrawnReceiver, "테스트");
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

        MessageResponse response = messageService.getMessage(1L, 1L);

        assertThat(response.getReceiverNickname()).isEqualTo("탈퇴한 회원");
    }
}
