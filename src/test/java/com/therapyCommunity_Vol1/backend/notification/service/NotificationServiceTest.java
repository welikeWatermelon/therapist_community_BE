package com.therapyCommunity_Vol1.backend.notification.service;

import com.therapyCommunity_Vol1.backend.notification.domain.Notification;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.notification.repository.NotificationRepository;
import com.therapyCommunity_Vol1.backend.notification.sse.SseEmitterRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private SseEmitterRepository sseEmitterRepository;
    private NotificationService notificationService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        sseEmitterRepository = mock(SseEmitterRepository.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        notificationService = new NotificationService(
                notificationRepository, userRepository, sseEmitterRepository,
                taskScheduler, 1800000L);

        sender = User.builder()
                .id(1L).email("sender@test.com").nickname("발신자").role(UserRole.THERAPIST)
                .build();
        receiver = User.builder()
                .id(2L).email("receiver@test.com").nickname("수신자").role(UserRole.THERAPIST)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification n = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(n, "id", 100L);
                    return n;
                });
    }

    @Test
    void createNotifications_DB_저장과_DTO_변환이_정상_완료된다() {
        // given
        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_COMMENT, 10L);

        // when
        List<NotificationService.SsePayload> payloads = notificationService.createNotifications(event);

        // then — DB 저장 정상 호출됨
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getReceiver().getId()).isEqualTo(2L);
        assertThat(notification.getSender().getId()).isEqualTo(1L);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.NEW_COMMENT);
        assertThat(notification.getContent()).isEqualTo("발신자님이 회원님의 게시글에 댓글을 남겼습니다.");

        // payload 검증 — 트랜잭션 내에서 DTO 변환 완료
        assertThat(payloads).hasSize(1);
        NotificationService.SsePayload payload = payloads.get(0);
        assertThat(payload.receiverId()).isEqualTo(2L);
        assertThat(payload.eventId()).startsWith("100_");
        assertThat(payload.response().getContent()).isEqualTo("발신자님이 회원님의 게시글에 댓글을 남겼습니다.");

        // SSE는 호출되지 않음 (분리됨)
        verify(sseEmitterRepository, never()).cacheEvent(any(), any(), any());
    }

    @Test
    void sendSseNotifications_실패시_예외가_전파되지_않는다() {
        // given — SSE cacheEvent에서 예외
        doThrow(new RuntimeException("SSE 장애"))
                .when(sseEmitterRepository).cacheEvent(eq(2L), any(), any());

        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_POST_REACTION, 10L, "좋아요");

        List<NotificationService.SsePayload> payloads = notificationService.createNotifications(event);

        // when — SSE 전송 (예외 발생하지만 전파 안 됨)
        assertThatCode(() -> notificationService.sendSseNotifications(payloads))
                .doesNotThrowAnyException();

        // then — cacheEvent 호출됨 (예외 발생)
        verify(sseEmitterRepository).cacheEvent(eq(2L), any(), any());
        verify(sseEmitterRepository, never()).getEmitters(2L);
    }

    @Test
    void DB_저장_실패시_예외가_전파된다() {
        // given
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("DB 커넥션 실패"));

        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_COMMENT, 10L);

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> notificationService.createNotifications(event)
        );

        verify(sseEmitterRepository, never()).cacheEvent(any(), any(), any());
    }
}
