package com.therapyCommunity_Vol1.backend.notification.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.Notification;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.dto.NotificationListResponse;
import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;
import com.therapyCommunity_Vol1.backend.notification.dto.UnreadCountResponse;
import com.therapyCommunity_Vol1.backend.notification.repository.NotificationRepository;
import com.therapyCommunity_Vol1.backend.outbox.service.OutboxService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String AGGREGATE_TYPE_NOTIFICATION = "NOTIFICATION";
    private static final String EVENT_TYPE_NOTIFICATION = "NOTIFICATION";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final UnreadCountCacheService unreadCountCacheService;

    /**
     * 알림 목록 조회
     */
    public NotificationListResponse getNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, pageable);

        Page<NotificationResponse> responsePage = notifications.map(NotificationResponse::from);
        return NotificationListResponse.from(responsePage);
    }

    /**
     * 안 읽은 알림 개수 조회 (Redis 캐시 사용)
     */
    public UnreadCountResponse getUnreadCount(Long userId) {
        long count = unreadCountCacheService.getUnreadCount(userId);
        return UnreadCountResponse.of(count);
    }

    /**
     * 단일 알림 읽음 처리
     */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        if (!notification.isRead()) {
            notification.markAsRead();
            unreadCountCacheService.decrement(userId);
        }
    }

    /**
     * 전체 알림 읽음 처리
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
        unreadCountCacheService.reset(userId);
    }

    /**
     * 댓글 알림 생성
     */
    @Transactional
    public void createCommentNotification(User recipient, User actor, Long postId, Long commentId) {
        if (shouldSkipNotification(recipient, actor)) {
            return;
        }

        String message = String.format("%s님이 회원님의 게시글에 댓글을 남겼습니다.", actor.getNickname());
        createAndSaveNotification(recipient, actor, NotificationType.COMMENT, commentId, "COMMENT", message);
    }

    /**
     * 대댓글 알림 생성
     */
    @Transactional
    public void createReplyNotification(User recipient, User actor, Long parentCommentId, Long replyId) {
        if (shouldSkipNotification(recipient, actor)) {
            return;
        }

        String message = String.format("%s님이 회원님의 댓글에 답글을 남겼습니다.", actor.getNickname());
        createAndSaveNotification(recipient, actor, NotificationType.REPLY, replyId, "COMMENT", message);
    }

    /**
     * 게시글 반응 알림 생성
     */
    @Transactional
    public void createPostReactionNotification(User recipient, User actor, Long postId, String reactionType) {
        if (shouldSkipNotification(recipient, actor)) {
            return;
        }

        String message = String.format("%s님이 회원님의 게시글에 '%s' 반응을 남겼습니다.",
                actor.getNickname(), reactionType);
        createAndSaveNotification(recipient, actor, NotificationType.POST_REACTION, postId, "POST", message);
    }

    /**
     * 댓글 반응 알림 생성 (LIKE만)
     */
    @Transactional
    public void createCommentReactionNotification(User recipient, User actor, Long commentId) {
        if (shouldSkipNotification(recipient, actor)) {
            return;
        }

        String message = String.format("%s님이 회원님의 댓글에 좋아요를 눌렀습니다.", actor.getNickname());
        createAndSaveNotification(recipient, actor, NotificationType.COMMENT_REACTION, commentId, "COMMENT", message);
    }

    private boolean shouldSkipNotification(User recipient, User actor) {
        // 자기 자신에게는 알림 X
        return recipient.getId().equals(actor.getId());
    }

    private void createAndSaveNotification(User recipient, User actor, NotificationType type,
                                            Long referenceId, String referenceType, String message) {
        // 알림 저장
        Notification notification = Notification.create(recipient, actor, type, referenceId, referenceType, message);
        notification = notificationRepository.save(notification);

        // Redis 캐시 증가
        unreadCountCacheService.increment(recipient.getId());

        // Outbox 이벤트 생성 (실시간 전송용)
        NotificationResponse response = NotificationResponse.from(notification);
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", recipient.getId());
        payload.put("notification", response);

        outboxService.createEvent(AGGREGATE_TYPE_NOTIFICATION,
                notification.getId().toString(), EVENT_TYPE_NOTIFICATION, payload);

        log.debug("Notification created for user: {}, type: {}", recipient.getId(), type);
    }
}
