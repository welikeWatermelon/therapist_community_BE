package com.therapyCommunity_Vol1.backend.message.service;

import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.message.domain.Message;
import com.therapyCommunity_Vol1.backend.message.dto.*;
import com.therapyCommunity_Vol1.backend.message.repository.MessageRepository;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MessageResponse sendMessage(Long senderId, MessageSendRequest request) {
        if (senderId.equals(request.getReceiverId())) {
            throw new CustomException(ErrorCode.CANNOT_SEND_MESSAGE_TO_SELF);
        }

        User sender = userService.findById(senderId);
        if (sender.isWithdrawn()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        User receiver = userService.findById(request.getReceiverId());
        if (receiver.isWithdrawn()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        Message message = Message.create(sender, receiver, request.getContent());
        messageRepository.save(message);

        eventPublisher.publishEvent(NotificationEvent.of(
                senderId, receiver.getId(),
                NotificationType.NEW_MESSAGE, message.getId()));

        return MessageResponse.from(message);
    }

    // TODO: 공지 전용 테이블 분리 (broadcast_notice + broadcast_read_status)
    //  현재는 쪽지(Message)로 공지를 발송하므로 유저 수만큼 INSERT가 발생한다.
    //  유저 규모가 커지면 공지 1행 + 읽음 상태만 lazy 기록하는 구조로 전환할 것.
    //  분리 시 공지는 쪽지함에서 빠지므로, 관리자 공지 전용 알림/조회 페이지가 별도로 필요하다.
    @Transactional
    public BroadcastResponse broadcastMessage(Long senderId, BroadcastMessageRequest request) {
        User sender = userService.findById(senderId);
        validateBroadcastPermission(sender);

        List<Long> receiverIds = resolveBroadcastReceiverIds(request.getTargetRole(), senderId);

        UUID broadcastId = UUID.randomUUID();
        List<User> receivers = userService.findUsersByIds(receiverIds);

        List<Message> messages = receivers.stream()
                .map(receiver -> Message.createBroadcast(sender, receiver, request.getContent(), broadcastId))
                .toList();

        messageRepository.saveAll(messages);
        publishBroadcastNotifications(messages, senderId);

        return new BroadcastResponse(broadcastId, messages.size());
    }

    private void validateBroadcastPermission(User sender) {
        if (sender.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private List<Long> resolveBroadcastReceiverIds(UserRole targetRole, Long senderId) {
        List<Long> receiverIds;
        if (targetRole != null) {
            receiverIds = new ArrayList<>(userService.findUserIdsByRole(targetRole));
        } else {
            receiverIds = new ArrayList<>(userService.findUserIdsByRole(UserRole.USER));
            receiverIds.addAll(userService.findUserIdsByRole(UserRole.THERAPIST));
        }

        receiverIds.remove(senderId);

        if (receiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.BROADCAST_NO_RECIPIENTS);
        }
        return receiverIds;
    }

    // TODO: 현재 수신자별로 이벤트를 개별 발행하므로 유저 수에 비례해 async 태스크가 증가한다.
    //  bulk 오버로드(NotificationEvent.of(senderId, List<Long>, ...))가 있으나
    //  referenceId가 메시지별로 달라 단순 전환이 불가하다.
    //  관리자 전용 공지 알림 페이지가 만들어지면 공지 알림 로직을 그쪽으로 이전할 것.
    private void publishBroadcastNotifications(List<Message> messages, Long senderId) {
        for (Message msg : messages) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    senderId, msg.getReceiver().getId(),
                    NotificationType.NEW_MESSAGE, msg.getId()));
        }
    }

    public PagedResponse<MessageResponse> getReceivedMessages(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Message> result = messageRepository.findReceivedMessages(userId, pageable);

        List<MessageResponse> items = result.getContent().stream()
                .map(MessageResponse::from)
                .toList();

        return PagedResponse.from(result, items);
    }

    public PagedResponse<MessageResponse> getSentMessages(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Message> result = messageRepository.findSentMessages(userId, pageable);

        List<MessageResponse> items = result.getContent().stream()
                .map(MessageResponse::from)
                .toList();

        return PagedResponse.from(result, items);
    }

    @Transactional
    public MessageResponse getMessage(Long userId, Long messageId) {
        Message message = findMessageOrThrow(messageId);

        if (!message.isParticipant(userId)) {
            throw new CustomException(ErrorCode.MESSAGE_ACCESS_DENIED);
        }

        // 발신자가 삭제한 쪽지는 발신자가 볼 수 없음
        if (message.isSender(userId) && message.isDeletedBySender()) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        // 수신자가 삭제한 쪽지는 수신자가 볼 수 없음
        if (message.isReceiver(userId) && message.isDeletedByReceiver()) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // 수신자가 조회하면 읽음 처리
        if (message.isReceiver(userId)) {
            message.markAsRead();
        }

        return MessageResponse.from(message);
    }

    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        Message message = findMessageOrThrow(messageId);

        if (!message.isParticipant(userId)) {
            throw new CustomException(ErrorCode.MESSAGE_ACCESS_DENIED);
        }

        if (message.isSender(userId)) {
            message.deleteBySender();
        }
        if (message.isReceiver(userId)) {
            message.deleteByReceiver();
        }

        if (message.isFullyDeleted()) {
            message.softDelete();
        }
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        long count = messageRepository.countUnreadMessages(userId);
        return new UnreadCountResponse(count);
    }

    private Message findMessageOrThrow(Long messageId) {
        return messageRepository.findByIdWithUsers(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
    }
}
