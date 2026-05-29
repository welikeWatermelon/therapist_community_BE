package com.therapyCommunity_Vol1.backend.notification.event;

import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class NotificationEventListenerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationEventListener listener = new NotificationEventListener(notificationService, meterRegistry);

    @Test
    void createNotifications_예외_발생시_전파되지_않고_에러_로그만_남긴다() {
        // given
        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_COMMENT, 10L);

        doThrow(new RuntimeException("DB 커넥션 실패"))
                .when(notificationService).createNotifications(event);

        // when & then — 예외가 밖으로 전파되지 않음
        assertThatCode(() -> listener.handleNotificationEvent(event))
                .doesNotThrowAnyException();

        verify(notificationService).createNotifications(event);
    }

    @Test
    void createNotifications_정상_실행시_예외_없이_완료된다() {
        // given
        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_POST_REACTION, 10L, "좋아요");

        // when & then
        assertThatCode(() -> listener.handleNotificationEvent(event))
                .doesNotThrowAnyException();

        verify(notificationService).createNotifications(event);
    }

    @Test
    void DB_저장_실패시_댓글_저장에_영향을_주지_않는다() {
        // given — notificationRepository.save()가 실패하는 시나리오
        // createNotifications가 DataAccessException을 던짐
        NotificationEvent event = NotificationEvent.of(
                1L, 2L, NotificationType.NEW_COMMENT, 10L);

        doThrow(new org.springframework.dao.DataAccessResourceFailureException("DB 커넥션 풀 소진"))
                .when(notificationService).createNotifications(event);

        // when — 리스너가 예외를 잡으므로 밖으로 전파되지 않음
        // 실제 아키텍처에서 이 메서드는 @Async + @TransactionalEventListener(AFTER_COMMIT)이므로
        // 댓글 트랜잭션 커밋 이후 별도 스레드에서 실행됨 → 댓글 저장에 영향 없음
        assertThatCode(() -> listener.handleNotificationEvent(event))
                .doesNotThrowAnyException();

        // then — createNotifications 호출은 시도됨 (실패했지만 예외 격리)
        verify(notificationService).createNotifications(event);
        // DB 실패 메트릭이 증가했는지 검증
        assertThat(meterRegistry.counter("notification.failure", "cause", "db").count()).isEqualTo(1.0);
    }
}
