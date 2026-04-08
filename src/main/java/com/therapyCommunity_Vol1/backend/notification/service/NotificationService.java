package com.therapyCommunity_Vol1.backend.notification.service;

import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.Notification;
import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;
import com.therapyCommunity_Vol1.backend.notification.dto.UnreadCountResponse;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.notification.repository.NotificationRepository;
import com.therapyCommunity_Vol1.backend.notification.sse.SseEmitterRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterRepository sseEmitterRepository;

    private static final Long SSE_TIMEOUT = 30L * 60 * 1000; // 30분

    public SseEmitter subscribe(Long userId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String emitterId = sseEmitterRepository.save(userId, emitter);

        emitter.onCompletion(() -> sseEmitterRepository.remove(userId, emitterId));
        emitter.onTimeout(() -> sseEmitterRepository.remove(userId, emitterId));
        emitter.onError(e -> {
            log.warn("SSE emitter error userId={}, emitterId={}: {}", userId, emitterId, e.getMessage());
            sseEmitterRepository.remove(userId, emitterId);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected"));
        } catch (IOException e) {
            sseEmitterRepository.remove(userId, emitterId);
            throw new CustomException(ErrorCode.SSE_CONNECTION_ERROR);
        }

        if (lastEventId != null && !lastEventId.isBlank()) {
            List<SseEmitterRepository.CachedEvent> missedEvents =
                    sseEmitterRepository.getEventsAfter(userId, lastEventId);
            for (SseEmitterRepository.CachedEvent event : missedEvents) {
                try {
                    emitter.send(SseEmitter.event()
                            .id(event.eventId())
                            .name("notification")
                            .data(event.data()));
                } catch (IOException e) {
                    sseEmitterRepository.remove(userId, emitterId);
                    break;
                }
            }
        }

        return emitter;
    }

    @Transactional
    public void createAndSend(NotificationEvent event) {
        List<Long> receiverIds = event.getReceiverIds().stream()
                .filter(id -> !id.equals(event.getSenderId()))
                .toList();

        if (receiverIds.isEmpty()) return;

        User sender = event.getSenderId() != null
                ? userRepository.findById(event.getSenderId()).orElse(null)
                : null;

        for (Long receiverId : receiverIds) {
            User receiver = userRepository.findById(receiverId).orElse(null);
            if (receiver == null) continue;

            Notification notification = Notification.create(
                    receiver, sender,
                    event.getType(), event.getReferenceId(), event.getContent()
            );
            notificationRepository.save(notification);

            NotificationResponse response = NotificationResponse.from(notification);
            String eventId = notification.getId() + "_" + System.currentTimeMillis();

            sseEmitterRepository.cacheEvent(receiverId, eventId, response);
            sendToUser(receiverId, eventId, response);
        }
    }

    public PagedResponse<NotificationResponse> getNotifications(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<Notification> result = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId, pageable);

        List<NotificationResponse> responses = result.getContent().stream()
                .map(NotificationResponse::from)
                .toList();

        return PagedResponse.from(result, responses);
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        long count = notificationRepository.countByReceiverIdAndReadFalse(userId);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByReceiverId(userId);
    }

    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notificationRepository.delete(notification);
    }

    private void sendToUser(Long userId, String eventId, NotificationResponse response) {
        Map<String, SseEmitter> userEmitters = sseEmitterRepository.getEmitters(userId);

        userEmitters.forEach((emitterId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name("notification")
                        .data(response));
            } catch (IOException e) {
                log.debug("SSE send failed userId={}, emitterId={}", userId, emitterId);
                sseEmitterRepository.remove(userId, emitterId);
            }
        });
    }
}
