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
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private SseEmitterRepository sseEmitterRepository;
    private TaskScheduler taskScheduler;
    private NotificationService notificationService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        sseEmitterRepository = mock(SseEmitterRepository.class);
        taskScheduler = mock(TaskScheduler.class);
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

    // ── subscribe cleanup 콜백 검증 (PR #95 리뷰 항목) ───────────

    @Test
    void subscribe_완료시_heartbeat가_취소되고_emitter가_정리된다() throws Exception {
        // Given
        ScheduledFuture<?> mockHeartbeat = mock(ScheduledFuture.class);
        when(sseEmitterRepository.save(eq(1L), any(SseEmitter.class))).thenReturn("1_1");
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenReturn(mockHeartbeat);

        SseEmitter emitter = notificationService.subscribe(1L, "");

        // When — onCompletion 콜백 트리거
        invokeCallback(emitter, "completionCallback");

        // Then
        verify(mockHeartbeat).cancel(false);
        verify(sseEmitterRepository).remove(1L, "1_1");
    }

    @Test
    void subscribe_타임아웃시_heartbeat가_취소되고_emitter가_정리된다() throws Exception {
        // Given
        ScheduledFuture<?> mockHeartbeat = mock(ScheduledFuture.class);
        when(sseEmitterRepository.save(eq(1L), any(SseEmitter.class))).thenReturn("1_1");
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenReturn(mockHeartbeat);

        SseEmitter emitter = notificationService.subscribe(1L, "");

        // When — onTimeout 콜백 트리거
        invokeCallback(emitter, "timeoutCallback");

        // Then
        verify(mockHeartbeat).cancel(false);
        verify(sseEmitterRepository).remove(1L, "1_1");
    }

    // ── createNotifications / sendSseNotifications 검증 ─────────

    @Test
    void createNotifications_DB_저장과_DTO_변환이_정상_완료된다() {
        // given
        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_COMMENT, 10L);

        // when
        List<NotificationService.SsePayload> payloads = notificationService.createNotifications(event);

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertThat(notification.getReceiver().getId()).isEqualTo(2L);
        assertThat(notification.getSender().getId()).isEqualTo(1L);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.NEW_COMMENT);
        assertThat(notification.getContent()).isEqualTo("발신자님이 회원님의 게시글에 댓글을 남겼습니다.");

        assertThat(payloads).hasSize(1);
        NotificationService.SsePayload payload = payloads.get(0);
        assertThat(payload.receiverId()).isEqualTo(2L);
        assertThat(payload.eventId()).startsWith("100_");
        assertThat(payload.response().getContent()).isEqualTo("발신자님이 회원님의 게시글에 댓글을 남겼습니다.");

        verify(sseEmitterRepository, never()).cacheEvent(any(), any(), any());
    }

    @Test
    void sendSseNotifications_실패시_예외가_전파되지_않는다() {
        // given
        doThrow(new RuntimeException("SSE 장애"))
                .when(sseEmitterRepository).cacheEvent(eq(2L), any(), any());

        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_POST_REACTION, 10L, "좋아요");

        List<NotificationService.SsePayload> payloads = notificationService.createNotifications(event);

        // when
        assertThatCode(() -> notificationService.sendSseNotifications(payloads))
                .doesNotThrowAnyException();

        // then
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

    // ── 헬퍼 ────────────────────────────────────────────────────

    private void invokeCallback(SseEmitter emitter, String callbackFieldName) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField(callbackFieldName);
        field.setAccessible(true);
        Object callback = field.get(emitter);
        Method run = callback.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        run.invoke(callback);
    }
}
