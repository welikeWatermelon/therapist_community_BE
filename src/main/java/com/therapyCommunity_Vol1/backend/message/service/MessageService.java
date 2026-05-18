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
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MessageResponse sendMessage(Long senderId, MessageSendRequest request) {
        if (senderId.equals(request.getReceiverId())) {
            throw new CustomException(ErrorCode.CANNOT_SEND_MESSAGE_TO_SELF);
        }

        User sender = findUserOrThrow(senderId);
        User receiver = findUserOrThrow(request.getReceiverId());

        Message message = Message.create(sender, receiver, request.getContent());
        messageRepository.save(message);

        eventPublisher.publishEvent(NotificationEvent.of(
                senderId, receiver.getId(),
                NotificationType.NEW_MESSAGE, message.getId()));

        return MessageResponse.from(message);
    }

    @Transactional
    public void broadcastMessage(Long senderId, BroadcastMessageRequest request) {
        User sender = findUserOrThrow(senderId);

        if (sender.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<Long> receiverIds;
        if (request.getTargetRole() != null) {
            receiverIds = userRepository.findIdsByRole(request.getTargetRole());
        } else {
            receiverIds = userRepository.findIdsByRole(UserRole.USER);
            receiverIds = new ArrayList<>(receiverIds);
            receiverIds.addAll(userRepository.findIdsByRole(UserRole.THERAPIST));
        }

        // 발신자 자신 제외
        receiverIds.remove(senderId);

        if (receiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.BROADCAST_NO_RECIPIENTS);
        }

        UUID broadcastId = UUID.randomUUID();
        List<User> receivers = userRepository.findAllById(receiverIds);

        List<Message> messages = receivers.stream()
                .map(receiver -> Message.createBroadcast(sender, receiver, request.getContent(), broadcastId))
                .toList();

        messageRepository.saveAll(messages);

        eventPublisher.publishEvent(NotificationEvent.of(
                senderId, receiverIds,
                NotificationType.NEW_MESSAGE, null));
    }

    public PagedResponse<MessageResponse> getReceivedMessages(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> result = messageRepository.findReceivedMessages(userId, pageable);

        List<MessageResponse> items = result.getContent().stream()
                .map(MessageResponse::from)
                .toList();

        return PagedResponse.from(result, items);
    }

    public PagedResponse<MessageResponse> getSentMessages(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
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
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        long count = messageRepository.countUnreadMessages(userId);
        return new UnreadCountResponse(count);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Message findMessageOrThrow(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
    }
}
