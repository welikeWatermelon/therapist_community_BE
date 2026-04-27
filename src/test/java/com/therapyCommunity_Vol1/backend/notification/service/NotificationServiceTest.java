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
        notificationService = new NotificationService(notificationRepository, userRepository, sseEmitterRepository);

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
    void SSE_실패시_DB_저장은_정상_완료된다() {
        // given
        doThrow(new RuntimeException("SSE 캐시 저장 실패"))
                .when(sseEmitterRepository).cacheEvent(eq(2L), any(), any());

        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_COMMENT, 10L);

        // when — 예외가 전파되지 않음
        assertThatCode(() -> notificationService.createAndSend(event))
                .doesNotThrowAnyException();

        // then — DB 저장은 정상 호출됨
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getReceiver().getId()).isEqualTo(2L);
        assertThat(saved.getSender().getId()).isEqualTo(1L);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.NEW_COMMENT);
        assertThat(saved.getContent()).isEqualTo("발신자님이 회원님의 게시글에 댓글을 남겼습니다.");
    }

    @Test
    void SSE_실패시_에러_로그가_출력되고_나머지_처리는_계속된다() {
        // given — SSE cacheEvent에서 예외
        doThrow(new RuntimeException("SSE 장애"))
                .when(sseEmitterRepository).cacheEvent(eq(2L), any(), any());

        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_POST_REACTION, 10L, "좋아요");

        // when
        notificationService.createAndSend(event);

        // then — DB 저장 정상, SSE cacheEvent 호출됨 (예외 발생)
        verify(notificationRepository).save(any(Notification.class));
        verify(sseEmitterRepository).cacheEvent(eq(2L), any(), any());
        // sendToUser는 cacheEvent 예외로 인해 호출되지 않음 (같은 try 블록)
        verify(sseEmitterRepository, never()).getEmitters(2L);
    }

    @Test
    void DB_저장_실패시_예외가_전파된다() {
        // given — DB save에서 예외 (NotificationEventListener의 try-catch가 잡을 것)
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("DB 커넥션 실패"));

        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_COMMENT, 10L);

        // when & then — createAndSend는 예외를 던짐
        // → NotificationEventListener.handleNotificationEvent()의 try-catch가 처리
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> notificationService.createAndSend(event)
        );

        // SSE 전송은 시도되지 않음
        verify(sseEmitterRepository, never()).cacheEvent(any(), any(), any());
    }
}
