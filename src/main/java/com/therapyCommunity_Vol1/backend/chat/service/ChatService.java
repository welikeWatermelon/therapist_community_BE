package com.therapyCommunity_Vol1.backend.chat.service;

import com.therapyCommunity_Vol1.backend.chat.domain.Conversation;
import com.therapyCommunity_Vol1.backend.chat.domain.Message;
import com.therapyCommunity_Vol1.backend.chat.dto.*;
import com.therapyCommunity_Vol1.backend.chat.repository.ConversationRepository;
import com.therapyCommunity_Vol1.backend.chat.repository.MessageRepository;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConversationWithCreatedFlag createConversation(Long senderId, CreateConversationRequest request) {
        if (senderId.equals(request.getRecipientId())) {
            throw new CustomException(ErrorCode.CANNOT_MESSAGE_SELF);
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (recipient.isWithdrawn()) {
            throw new CustomException(ErrorCode.CANNOT_MESSAGE_WITHDRAWN_USER);
        }

        Long smallerId = Math.min(senderId, request.getRecipientId());
        Long largerId = Math.max(senderId, request.getRecipientId());

        Optional<Conversation> existing = conversationRepository.findByParticipants(smallerId, largerId);

        Conversation conversation;
        boolean created;

        if (existing.isPresent()) {
            conversation = existing.get();
            created = false;
        } else {
            try {
                conversation = Conversation.create(sender, recipient, request.getContent());
                conversation = conversationRepository.saveAndFlush(conversation);
                created = true;
            } catch (DataIntegrityViolationException e) {
                conversation = conversationRepository.findByParticipants(smallerId, largerId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
                created = false;
            }
        }

        Message message = Message.create(conversation, sender, request.getContent());
        messageRepository.save(message);

        if (!created) {
            conversation.updateLastMessage(request.getContent());
        }

        eventPublisher.publishEvent(NotificationEvent.of(
                senderId, request.getRecipientId(),
                NotificationType.NEW_MESSAGE, conversation.getId()));

        ConversationResponse response = ConversationResponse.from(conversation, senderId, 0L);
        return new ConversationWithCreatedFlag(response, created);
    }

    public PagedResponse<ConversationResponse> getConversations(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<Conversation> result = conversationRepository.findByParticipantId(userId, pageable);

        List<Long> conversationIds = result.getContent().stream()
                .map(Conversation::getId)
                .toList();

        Map<Long, Long> unreadCounts = getUnreadCountMap(conversationIds, userId);

        List<ConversationResponse> responses = result.getContent().stream()
                .map(c -> ConversationResponse.from(c, userId, unreadCounts.getOrDefault(c.getId(), 0L)))
                .toList();

        return PagedResponse.from(result, responses);
    }

    public CursorPageResponse<MessageResponse> getMessages(Long userId, Long conversationId, Long before, int size) {
        Conversation conversation = findConversationOrThrow(conversationId);
        validateParticipant(conversation, userId);

        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable limit = PageRequest.of(0, safeSize + 1);

        List<Message> messages;
        if (before != null) {
            messages = messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(
                    conversationId, before, limit);
        } else {
            messages = messageRepository.findByConversationIdOrderByIdDesc(conversationId, limit);
        }

        boolean hasNext = messages.size() > safeSize;
        List<Message> page = hasNext ? new java.util.ArrayList<>(messages.subList(0, safeSize))
                : new java.util.ArrayList<>(messages);
        java.util.Collections.reverse(page);

        List<MessageResponse> responses = page.stream()
                .map(MessageResponse::from)
                .toList();

        Long nextCursor = hasNext ? page.get(0).getId() : null;

        return new CursorPageResponse<>(responses, safeSize, hasNext, nextCursor);
    }

    @Transactional
    public MessageResponse sendMessage(Long senderId, Long conversationId, SendMessageRequest request) {
        Conversation conversation = findConversationOrThrow(conversationId);
        validateParticipant(conversation, senderId);

        User other = conversation.getOtherParticipant(senderId);
        if (other.isWithdrawn()) {
            throw new CustomException(ErrorCode.CANNOT_MESSAGE_WITHDRAWN_USER);
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Message message = Message.create(conversation, sender, request.getContent());
        messageRepository.save(message);

        // TODO: 동시 메시지 전송 시 last-writer-wins로 lastMessageContent 순서가 뒤바뀔 수 있음.
        //  현재 규모에서는 허용. 필요 시 SELECT FOR UPDATE 또는 lastMessageAt 비교 후 갱신으로 개선.
        conversation.updateLastMessage(request.getContent());

        eventPublisher.publishEvent(NotificationEvent.of(
                senderId, other.getId(),
                NotificationType.NEW_MESSAGE, conversationId));

        return MessageResponse.from(message);
    }

    @Transactional
    public void markAsRead(Long userId, Long conversationId) {
        Conversation conversation = findConversationOrThrow(conversationId);
        validateParticipant(conversation, userId);

        messageRepository.markAllAsReadInConversation(conversationId, userId);
    }

    public UnreadConversationCountResponse getUnreadCount(Long userId) {
        long count = conversationRepository.countUnreadConversations(userId);
        return new UnreadConversationCountResponse(count);
    }

    private Map<Long, Long> getUnreadCountMap(List<Long> conversationIds, Long userId) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.countUnreadByConversationIds(conversationIds, userId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    private Conversation findConversationOrThrow(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private void validateParticipant(Conversation conversation, Long userId) {
        if (!conversation.isParticipant(userId)) {
            // 비참여자에게 대화 존재 여부를 노출하지 않기 위해 404로 통일
            throw new CustomException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }

    public record ConversationWithCreatedFlag(ConversationResponse conversation, boolean created) {}
}
