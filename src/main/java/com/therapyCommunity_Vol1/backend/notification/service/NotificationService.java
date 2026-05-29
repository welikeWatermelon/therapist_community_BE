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
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final SseEmitterRepository sseEmitterRepository;
    private final TaskScheduler taskScheduler;

    private final long sseTimeoutMillis;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserService userService,
            SseEmitterRepository sseEmitterRepository,
            TaskScheduler taskScheduler,
            @Value("${notification.sse.timeout-millis:1800000}") long sseTimeoutMillis
    ) {
        this.notificationRepository = notificationRepository;
        this.userService = userService;
        this.sseEmitterRepository = sseEmitterRepository;
        this.taskScheduler = taskScheduler;
        this.sseTimeoutMillis = sseTimeoutMillis;
    }

    public SseEmitter subscribe(Long userId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis);
        String emitterId = sseEmitterRepository.save(userId, emitter);

        ScheduledFuture<?> heartbeat = taskScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                log.debug("heartbeat failed, removing emitter: userId={}, emitterId={}", userId, emitterId);
                sseEmitterRepository.remove(userId, emitterId);
            }
        }, Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS));

        Runnable cleanup = () -> {
            heartbeat.cancel(false);
            sseEmitterRepository.remove(userId, emitterId);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> {
            log.warn("SSE emitter error userId={}, emitterId={}: {}", userId, emitterId, e.getMessage());
            cleanup.run();
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected"));
        } catch (IOException e) {
            cleanup.run();
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

    public record SsePayload(Long receiverId, String eventId, NotificationResponse response) {}

    /**
     * 알림 DB 저장 + DTO 변환. 이벤트 리스너 전용 — 다른 서비스에서 직접 호출 금지.
     * REQUIRES_NEW: 호출자 트랜잭션과 독립적으로 커밋/롤백.
     * DB 일시 장애 시 최대 3회 재시도 (500ms → 1s → 2s).
     */
    @Retryable(retryFor = DataAccessException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<SsePayload> createNotifications(NotificationEvent event) {
        List<Long> receiverIds = event.getReceiverIds().stream()
                .filter(id -> !id.equals(event.getSenderId()))
                .toList();

        if (receiverIds.isEmpty()) return List.of();

        User sender = event.getSenderId() != null
                ? userService.findByIdOrNull(event.getSenderId())
                : null;

        String senderNickname;
        if (sender != null) {
            senderNickname = sender.getDisplayNickname();
        } else if (event.getSenderId() != null) {
            senderNickname = "알 수 없는 사용자";
        } else {
            senderNickname = null;
        }
        String content = event.getType().formatMessage(senderNickname, event.getExtraParams());

        List<SsePayload> payloads = new java.util.ArrayList<>();
        // TODO: VERIFICATION_SUBMITTED 활성화 시 receiverIds가 다수(admin 전원)가 되므로
        //       findAllById(receiverIds) batch 조회로 전환하여 N+1 방지 필요
        for (Long receiverId : receiverIds) {
            User receiver = userService.findByIdOrNull(receiverId);
            if (receiver == null) continue;

            Notification notification = Notification.create(
                    receiver, sender,
                    event.getType(), event.getReferenceId(), event.getPostId(), content
            );
            Notification saved = notificationRepository.save(notification);

            NotificationResponse response = NotificationResponse.from(saved);
            String eventId = SseEmitterRepository.createEventId(saved.getId());
            payloads.add(new SsePayload(receiverId, eventId, response));
        }
        return payloads;
    }

    public void sendSseNotifications(List<SsePayload> payloads) {
        for (SsePayload payload : payloads) {
            try {
                sseEmitterRepository.cacheEvent(payload.receiverId(), payload.eventId(), payload.response());
                sendToUser(payload.receiverId(), payload.eventId(), payload.response());
            } catch (Exception e) {
                log.error("알림 SSE 전송 실패: receiverId={}, eventId={}",
                        payload.receiverId(), payload.eventId(), e);
            }
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
                log.debug("SSE send failed (client disconnected) userId={}, emitterId={}", userId, emitterId);
                sseEmitterRepository.remove(userId, emitterId);
            } catch (IllegalStateException e) {
                log.debug("SSE emitter already completed userId={}, emitterId={}", userId, emitterId);
                sseEmitterRepository.remove(userId, emitterId);
            }
        });
    }
}
