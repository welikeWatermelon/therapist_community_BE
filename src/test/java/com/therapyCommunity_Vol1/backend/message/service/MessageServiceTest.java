package com.therapyCommunity_Vol1.backend.message.service;

import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.message.domain.Message;
import com.therapyCommunity_Vol1.backend.message.dto.*;
import com.therapyCommunity_Vol1.backend.message.repository.MessageRepository;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    private MessageRepository messageRepository;
    private UserService userService;
    private ApplicationEventPublisher eventPublisher;
    private MessageService messageService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        userService = mock(UserService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        messageService = new MessageService(messageRepository, userService, eventPublisher);

        sender = User.builder().id(1L).email("sender@test.com").nickname("발신자").role(UserRole.THERAPIST).build();
        receiver = User.builder().id(2L).email("receiver@test.com").nickname("수신자").role(UserRole.THERAPIST).build();
    }

    @Test
    void 쪽지_발송_성공() {
        when(userService.findById(1L)).thenReturn(sender);
        when(userService.findById(2L)).thenReturn(receiver);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageSendRequest request = new MessageSendRequest(2L, "안녕하세요");
        MessageResponse response = messageService.sendMessage(1L, request);

        assertThat(response.getSenderNickname()).isEqualTo("발신자");
        assertThat(response.getReceiverNickname()).isEqualTo("수신자");
        assertThat(response.getContent()).isEqualTo("안녕하세요");
        assertThat(response.isRead()).isFalse();
        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void 자기_자신에게_쪽지_보내면_예외_발생() {
        MessageSendRequest request = new MessageSendRequest(1L, "자기자신");

        assertThatThrownBy(() -> messageService.sendMessage(1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_SEND_MESSAGE_TO_SELF));
    }

    @Test
    void 존재하지_않는_수신자에게_쪽지_보내면_예외_발생() {
        when(userService.findById(1L)).thenReturn(sender);
        when(userService.findById(999L)).thenThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        MessageSendRequest request = new MessageSendRequest(999L, "테스트");

        assertThatThrownBy(() -> messageService.sendMessage(1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 받은_쪽지함_조회_성공() {
        Message message = Message.create(sender, receiver, "테스트");
        Page<Message> page = new PageImpl<>(List.of(message));
        when(messageRepository.findReceivedMessages(eq(2L), any(Pageable.class))).thenReturn(page);

        PagedResponse<MessageResponse> response = messageService.getReceivedMessages(2L, 0, 20);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getContent()).isEqualTo("테스트");
    }

    @Test
    void 보낸_쪽지함_조회_성공() {
        Message message = Message.create(sender, receiver, "테스트");
        Page<Message> page = new PageImpl<>(List.of(message));
        when(messageRepository.findSentMessages(eq(1L), any(Pageable.class))).thenReturn(page);

        PagedResponse<MessageResponse> response = messageService.getSentMessages(1L, 0, 20);

        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void 쪽지_상세_조회시_수신자면_읽음_처리() {
        Message message = Message.create(sender, receiver, "테스트");
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        MessageResponse response = messageService.getMessage(2L, 1L);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    void 쪽지_상세_조회시_발신자면_읽음_처리_안함() {
        Message message = Message.create(sender, receiver, "테스트");
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        MessageResponse response = messageService.getMessage(1L, 1L);

        assertThat(response.isRead()).isFalse();
    }

    @Test
    void 참여자가_아닌_사용자가_쪽지_조회시_예외_발생() {
        Message message = Message.create(sender, receiver, "테스트");
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.getMessage(999L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_ACCESS_DENIED));
    }

    @Test
    void 발신자가_삭제하면_발신자측만_삭제() {
        Message message = Message.create(sender, receiver, "테스트");
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        messageService.deleteMessage(1L, 1L);

        assertThat(message.isDeletedBySender()).isTrue();
        assertThat(message.isDeletedByReceiver()).isFalse();
    }

    @Test
    void 수신자가_삭제하면_수신자측만_삭제() {
        Message message = Message.create(sender, receiver, "테스트");
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        messageService.deleteMessage(2L, 1L);

        assertThat(message.isDeletedBySender()).isFalse();
        assertThat(message.isDeletedByReceiver()).isTrue();
    }

    @Test
    void 발신자가_삭제한_쪽지를_발신자가_조회하면_예외_발생() {
        Message message = Message.create(sender, receiver, "테스트");
        message.deleteBySender();
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.getMessage(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND));
    }

    @Test
    void 안읽은_쪽지_수_조회() {
        when(messageRepository.countUnreadMessages(2L)).thenReturn(5L);

        UnreadCountResponse response = messageService.getUnreadCount(2L);

        assertThat(response.getUnreadCount()).isEqualTo(5L);
    }

    @Test
    void 관리자_공지_쪽지_발송_성공() {
        User admin = User.builder().id(100L).email("admin@test.com").nickname("관리자").role(UserRole.ADMIN).build();
        when(userService.findById(100L)).thenReturn(admin);
        when(userService.findUserIdsByRole(UserRole.USER)).thenReturn(new ArrayList<>(List.of(1L, 2L)));
        when(userService.findUserIdsByRole(UserRole.THERAPIST)).thenReturn(new ArrayList<>(List.of(3L)));
        when(userService.findUsersByIds(any())).thenReturn(List.of(sender, receiver,
                User.builder().id(3L).email("t@t.com").nickname("치료사").role(UserRole.THERAPIST).build()));

        BroadcastMessageRequest request = new BroadcastMessageRequest("공지사항입니다", null);
        messageService.broadcastMessage(100L, request);

        verify(messageRepository).saveAll(anyList());
        verify(eventPublisher, times(3)).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void 관리자_공지_특정_역할_발송_성공() {
        User admin = User.builder().id(100L).email("admin@test.com").nickname("관리자").role(UserRole.ADMIN).build();
        when(userService.findById(100L)).thenReturn(admin);
        when(userService.findUserIdsByRole(UserRole.THERAPIST)).thenReturn(new ArrayList<>(List.of(1L, 2L)));
        when(userService.findUsersByIds(any())).thenReturn(List.of(sender, receiver));

        BroadcastMessageRequest request = new BroadcastMessageRequest("치료사 공지", UserRole.THERAPIST);
        messageService.broadcastMessage(100L, request);

        verify(messageRepository).saveAll(anyList());
    }

    @Test
    void 양쪽_삭제시_softDelete_처리() {
        Message message = Message.create(sender, receiver, "테스트");
        message.deleteBySender();
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        messageService.deleteMessage(2L, 1L);

        assertThat(message.isDeletedBySender()).isTrue();
        assertThat(message.isDeletedByReceiver()).isTrue();
        assertThat(message.isFullyDeleted()).isTrue();
        assertThat(message.isDeleted()).isTrue();
        assertThat(message.getDeletedAt()).isNotNull();
    }

    @Test
    void 한쪽만_삭제시_softDelete_미처리() {
        Message message = Message.create(sender, receiver, "테스트");
        when(messageRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(message));

        messageService.deleteMessage(1L, 1L);

        assertThat(message.isDeletedBySender()).isTrue();
        assertThat(message.isDeletedByReceiver()).isFalse();
        assertThat(message.isDeleted()).isFalse();
    }

    @Test
    void 페이지_크기가_100_초과시_100으로_제한() {
        Page<Message> page = new PageImpl<>(List.of());
        when(messageRepository.findReceivedMessages(eq(1L), any(Pageable.class))).thenReturn(page);

        messageService.getReceivedMessages(1L, 0, 500);

        verify(messageRepository).findReceivedMessages(eq(1L), argThat(pageable ->
                pageable.getPageSize() == 100));
    }

    @Test
    void 페이지_크기가_0_이하면_1로_보정() {
        Page<Message> page = new PageImpl<>(List.of());
        when(messageRepository.findSentMessages(eq(1L), any(Pageable.class))).thenReturn(page);

        messageService.getSentMessages(1L, 0, 0);

        verify(messageRepository).findSentMessages(eq(1L), argThat(pageable ->
                pageable.getPageSize() == 1));
    }

    @Test
    void 수신대상이_없으면_예외_발생() {
        User admin = User.builder().id(100L).email("admin@test.com").nickname("관리자").role(UserRole.ADMIN).build();
        when(userService.findById(100L)).thenReturn(admin);
        when(userService.findUserIdsByRole(UserRole.USER)).thenReturn(new ArrayList<>());
        when(userService.findUserIdsByRole(UserRole.THERAPIST)).thenReturn(new ArrayList<>());

        BroadcastMessageRequest request = new BroadcastMessageRequest("공지", null);

        assertThatThrownBy(() -> messageService.broadcastMessage(100L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BROADCAST_NO_RECIPIENTS));
    }
}
